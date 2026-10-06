-- BigQuery Standard SQL
-- 목적지 알람 생성/재설정 성공 시점의 설정 분포
--
-- 실행 전 아래 항목을 실제 Firebase export 값으로 교체한다.
--   1) `YOUR_GCP_PROJECT.YOUR_FIREBASE_DATASET.events_*`
--   2) observation_start_suffix / observation_end_suffix
--
-- settings_schema_version = 2인 이벤트만 모집단으로 사용한다. 파라미터가
-- 누락된 행은 0으로 보정하지 않고 NULL로 남겨 데이터 품질 문제를 드러낸다.
-- alarm_time은 GA UI custom dimension이 아니라 BigQuery 원시 파라미터에서
-- 분석한다. 원시 alarm ID는 전송되지 않으므로 생성과 재설정을 알람별로 연결하지 않는다.

DECLARE observation_start_suffix STRING DEFAULT '20260101'; -- REPLACE_ME_YYYYMMDD
DECLARE observation_end_suffix STRING DEFAULT '20260131';   -- REPLACE_ME_YYYYMMDD

WITH base AS (
  SELECT
    user_pseudo_id,
    platform,
    app_info.version AS app_version,
    event_name,
    IF(event_name = 'destination_alarm_created', 'create', 'update') AS operation,
    (
      SELECT COALESCE(value.int_value, SAFE_CAST(value.string_value AS INT64))
      FROM UNNEST(event_params)
      WHERE key = 'settings_schema_version'
      LIMIT 1
    ) AS settings_schema_version,
    (
      SELECT COALESCE(value.int_value, SAFE_CAST(value.string_value AS INT64))
      FROM UNNEST(event_params)
      WHERE key = 'limit_minutes'
      LIMIT 1
    ) AS limit_minutes,
    (
      SELECT value.string_value
      FROM UNNEST(event_params)
      WHERE key = 'alarm_time'
      LIMIT 1
    ) AS alarm_time,
    (
      SELECT value.string_value
      FROM UNNEST(event_params)
      WHERE key = 'repeat_days'
      LIMIT 1
    ) AS repeat_days,
    (
      SELECT value.string_value
      FROM UNNEST(event_params)
      WHERE key = 'alarm_sound_source'
      LIMIT 1
    ) AS alarm_sound_source,
    (
      SELECT COALESCE(value.int_value, SAFE_CAST(value.string_value AS INT64))
      FROM UNNEST(event_params)
      WHERE key = 'alarm_sound_list_confirmed'
      LIMIT 1
    ) AS alarm_sound_list_confirmed,
    (
      SELECT value.string_value
      FROM UNNEST(event_params)
      WHERE key = 'alarm_sound_surface'
      LIMIT 1
    ) AS alarm_sound_surface,
    (
      SELECT COALESCE(value.int_value, SAFE_CAST(value.string_value AS INT64))
      FROM UNNEST(event_params)
      WHERE key = 'alarm_sound_position'
      LIMIT 1
    ) AS alarm_sound_position,
    (
      SELECT COALESCE(value.int_value, SAFE_CAST(value.string_value AS INT64))
      FROM UNNEST(event_params)
      WHERE key = 'alarm_sound_list_size'
      LIMIT 1
    ) AS alarm_sound_list_size,
    (
      SELECT COALESCE(value.int_value, SAFE_CAST(value.string_value AS INT64))
      FROM UNNEST(event_params)
      WHERE key = 'alarm_sound_selection_changed'
      LIMIT 1
    ) AS alarm_sound_selection_changed
  FROM `YOUR_GCP_PROJECT.YOUR_FIREBASE_DATASET.events_*`
  WHERE
    _TABLE_SUFFIX BETWEEN observation_start_suffix AND observation_end_suffix
    AND user_pseudo_id IS NOT NULL
    AND event_name IN ('destination_alarm_created', 'destination_alarm_updated')
),

settings AS (
  SELECT *
  FROM base
  WHERE settings_schema_version = 2
),

limit_distribution AS (
  SELECT
    operation,
    platform,
    app_version,
    'limit_minutes' AS metric,
    CAST(limit_minutes AS STRING) AS value,
    COUNT(*) AS event_count,
    COUNT(DISTINCT user_pseudo_id) AS user_count,
    CAST(NULL AS FLOAT64) AS normalized_position
  FROM settings
  GROUP BY operation, platform, app_version, value
),

time_distribution AS (
  SELECT
    operation,
    platform,
    app_version,
    'alarm_time' AS metric,
    alarm_time AS value,
    COUNT(*) AS event_count,
    COUNT(DISTINCT user_pseudo_id) AS user_count,
    CAST(NULL AS FLOAT64) AS normalized_position
  FROM settings
  GROUP BY operation, platform, app_version, value
),

repeat_combination_distribution AS (
  SELECT
    operation,
    platform,
    app_version,
    'repeat_days' AS metric,
    repeat_days AS value,
    COUNT(*) AS event_count,
    COUNT(DISTINCT user_pseudo_id) AS user_count,
    CAST(NULL AS FLOAT64) AS normalized_position
  FROM settings
  GROUP BY operation, platform, app_version, value
),

sound_source_distribution AS (
  SELECT
    operation,
    platform,
    app_version,
    'alarm_sound_source' AS metric,
    alarm_sound_source AS value,
    COUNT(*) AS event_count,
    COUNT(DISTINCT user_pseudo_id) AS user_count,
    CAST(NULL AS FLOAT64) AS normalized_position
  FROM settings
  WHERE platform = 'ANDROID'
  GROUP BY operation, platform, app_version, value
),

weekday_distribution AS (
  SELECT
    operation,
    platform,
    app_version,
    'repeat_day' AS metric,
    weekday AS value,
    COUNT(*) AS event_count,
    COUNT(DISTINCT user_pseudo_id) AS user_count,
    CAST(NULL AS FLOAT64) AS normalized_position
  FROM settings
  CROSS JOIN UNNEST(
    IF(repeat_days IS NULL OR repeat_days = 'none', [], SPLIT(repeat_days, ','))
  ) AS weekday
  GROUP BY operation, platform, app_version, value
),

sound_position_distribution AS (
  SELECT
    operation,
    platform,
    app_version,
    CONCAT(
      'alarm_sound_position:',
      COALESCE(alarm_sound_surface, '__missing_surface__'),
      ':changed=',
      CAST(COALESCE(alarm_sound_selection_changed, -1) AS STRING)
    ) AS metric,
    CAST(alarm_sound_position AS STRING) AS value,
    COUNT(*) AS event_count,
    COUNT(DISTINCT user_pseudo_id) AS user_count,
    SAFE_DIVIDE(alarm_sound_position, alarm_sound_list_size) AS normalized_position
  FROM settings
  WHERE
    platform = 'ANDROID'
    AND alarm_sound_list_confirmed = 1
    AND alarm_sound_position IS NOT NULL
    AND alarm_sound_list_size IS NOT NULL
    AND alarm_sound_position BETWEEN 1 AND alarm_sound_list_size
  GROUP BY
    operation,
    platform,
    app_version,
    metric,
    value,
    normalized_position
)

SELECT * FROM limit_distribution
UNION ALL
SELECT * FROM time_distribution
UNION ALL
SELECT * FROM repeat_combination_distribution
UNION ALL
SELECT * FROM sound_source_distribution
UNION ALL
SELECT * FROM weekday_distribution
UNION ALL
SELECT * FROM sound_position_distribution
ORDER BY operation, platform, app_version, metric, value;
