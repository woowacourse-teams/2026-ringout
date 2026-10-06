-- BigQuery Standard SQL script
-- alarm_settings_saves.sql의 고정 fixture 검증.
-- 실제 export 테이블을 읽지 않으며, CI 또는 BigQuery 편집기에서 직접 실행한다.

CREATE TEMP TABLE fixture AS
SELECT * FROM UNNEST([
  STRUCT('u1' AS user_pseudo_id, 'ANDROID' AS platform, 'create' AS operation,
    2 AS schema_version, '06:20' AS alarm_time, 'mon,fri' AS repeat_days,
    'system_default' AS sound_source, 1 AS confirmed, 'editor_picker' AS surface,
    1 AS position, 3 AS list_size, 0 AS changed),
  STRUCT('u1', 'ANDROID', 'update', 2, '06:20', 'mon,wed',
    'device_alarm', 1, 'onboarding_step', 3, 3, 1),
  STRUCT('u2', 'IOS', 'create', 2, '23:59', 'none',
    CAST(NULL AS STRING), CAST(NULL AS INT64), CAST(NULL AS STRING),
    CAST(NULL AS INT64), CAST(NULL AS INT64), CAST(NULL AS INT64)),
  -- 범위를 벗어난 위치는 alarm_settings_saves.sql에서 제외되어야 한다.
  STRUCT('u3', 'ANDROID', 'create', 2, '08:00', 'none',
    'device_alarm', 1, 'editor_picker', 4, 3, 0),
  -- 이전 계약 버전은 분석 모집단에 포함하지 않는다.
  STRUCT('u4', 'ANDROID', 'create', 1, '07:00', 'none',
    'device_alarm', 1, 'editor_picker', 1, 2, 0)
]);

CREATE TEMP TABLE settings AS
SELECT * FROM fixture WHERE schema_version = 2;

ASSERT (
  SELECT COUNT(*) FROM settings
) = 4 AS 'schema version filter must retain only version 2 rows';

ASSERT (
  SELECT COUNT(*)
  FROM settings
  WHERE platform = 'ANDROID'
    AND confirmed = 1
    AND position BETWEEN 1 AND list_size
) = 2 AS 'Android sound position must enforce the list range';

ASSERT (
  SELECT COUNT(*)
  FROM settings
  WHERE platform = 'IOS'
    AND sound_source IS NULL
    AND confirmed IS NULL
) = 1 AS 'iOS fixture must not contain Android sound parameters';

ASSERT (
  SELECT COUNT(*)
  FROM settings
  CROSS JOIN UNNEST(
    IF(repeat_days = 'none', [], SPLIT(repeat_days, ','))
  ) AS weekday
  WHERE weekday = 'mon'
) = 2 AS 'weekday split must count Monday across create and update';

ASSERT (
  SELECT COUNT(*)
  FROM settings
  WHERE platform = 'ANDROID'
    AND sound_source = 'device_alarm'
) = 2 AS 'Android sound source distribution must be retained';

SELECT 'alarm_settings_saves fixture passed' AS result;
