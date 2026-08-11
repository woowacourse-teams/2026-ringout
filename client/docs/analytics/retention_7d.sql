-- BigQuery Standard SQL
-- Ringout Android 목적지 미션: 첫 완료 및 첫 완료 후 7일 사용자 지표
--
-- 실행 전 아래 항목을 교체한다.
--   1) `YOUR_GCP_PROJECT.YOUR_FIREBASE_DATASET.events_*`
--   2) observation_start_suffix: Analytics 계측 릴리스 날짜(YYYYMMDD)
--   3) observation_end_suffix: export가 완료된 마지막 날짜(YYYYMMDD)
--
-- 이 쿼리는 계측 릴리스/조회 기간 안에서 first_open을 관측한 설치만
-- 진짜 첫 사용 코호트로 인정한다. 기존 설치처럼 first_open이 조회 범위 밖에
-- 있는 사용자는 이벤트가 있어도 지표에서 제외한다.
-- force_end_hold_started, force_end_hold_cancelled, force_end_hold_completed는
-- UI 제스처 진단 이벤트이므로 이 쿼리에서 제외한다. 강제 종료 경험은 실제 상태
-- 전이가 성공한 destination_mission_force_ended만으로 계산한다.

DECLARE observation_start_suffix STRING DEFAULT '20260101'; -- REPLACE_ME_YYYYMMDD
DECLARE observation_end_suffix STRING DEFAULT '20260131';   -- REPLACE_ME_YYYYMMDD
-- Firebase/Google Analytics 속성의 보고 시간대와 같게 설정한다.
DECLARE analysis_timezone STRING DEFAULT 'Asia/Seoul';
-- 비워 두면 버전 필터를 적용하지 않는다. 필요하면 ['1.0.0', '1.0.1']처럼 지정한다.
DECLARE instrumented_first_open_versions ARRAY<STRING> DEFAULT [];
DECLARE seven_days_us INT64 DEFAULT 7 * 24 * 60 * 60 * 1000000;

WITH extracted_events AS (
  SELECT
    user_pseudo_id,
    event_name,
    event_timestamp,
    app_info.version AS app_version,
    (
      SELECT COALESCE(
        value.int_value,
        CAST(value.double_value AS INT64),
        SAFE_CAST(value.string_value AS INT64)
      )
      FROM UNNEST(event_params)
      WHERE key = 'creation_index'
      LIMIT 1
    ) AS creation_index,
    (
      SELECT COALESCE(
        value.int_value,
        CAST(value.double_value AS INT64),
        SAFE_CAST(value.string_value AS INT64)
      )
      FROM UNNEST(event_params)
      WHERE key = 'use_index'
      LIMIT 1
    ) AS use_index,
    (
      SELECT COALESCE(
        value.int_value,
        CAST(value.double_value AS INT64),
        SAFE_CAST(value.string_value AS INT64)
      )
      FROM UNNEST(event_params)
      WHERE key = 'retry_attempt'
      LIMIT 1
    ) AS retry_attempt
  FROM `YOUR_GCP_PROJECT.YOUR_FIREBASE_DATASET.events_*`
  WHERE
    _TABLE_SUFFIX BETWEEN observation_start_suffix AND observation_end_suffix
    AND user_pseudo_id IS NOT NULL
    AND event_name IN (
      'first_open',
      'destination_alarm_created',
      'destination_alarm_ringing_started',
      'destination_mission_started',
      'destination_mission_completed',
      'destination_mission_expired',
      'destination_mission_force_ended'
    )
),

first_opens AS (
  SELECT
    user_pseudo_id,
    MIN(event_timestamp) AS first_open_at
  FROM extracted_events
  WHERE
    event_name = 'first_open'
    AND (
      ARRAY_LENGTH(instrumented_first_open_versions) = 0
      OR app_version IN UNNEST(instrumented_first_open_versions)
    )
  GROUP BY user_pseudo_id
),

first_creations AS (
  SELECT
    user_pseudo_id,
    MIN(event_timestamp) AS first_created_at
  FROM extracted_events
  WHERE
    event_name = 'destination_alarm_created'
    AND creation_index = 1
  GROUP BY user_pseudo_id
),

-- first_open과 첫 생성이 모두 관측된 설치만 왼쪽 절단이 없는 첫 사용 코호트로 사용한다.
observable_first_users AS (
  SELECT
    first_opens.user_pseudo_id,
    first_opens.first_open_at,
    first_creations.first_created_at
  FROM first_opens
  INNER JOIN first_creations USING (user_pseudo_id)
  WHERE first_creations.first_created_at >= first_opens.first_open_at
),

first_completions AS (
  SELECT
    users.user_pseudo_id,
    MIN(events.event_timestamp) AS first_completed_at
  FROM observable_first_users AS users
  INNER JOIN extracted_events AS events
    ON events.user_pseudo_id = users.user_pseudo_id
    AND events.event_name = 'destination_mission_completed'
    AND events.use_index = 1
    AND events.retry_attempt >= 0
    AND events.event_timestamp >= users.first_created_at
  GROUP BY users.user_pseudo_id
),

first_completion_population AS (
  SELECT
    users.user_pseudo_id,
    users.first_created_at,
    completions.first_completed_at
  FROM observable_first_users AS users
  LEFT JOIN first_completions AS completions USING (user_pseudo_id)
),

-- observation_end_suffix가 포함하는 마지막 시각까지 168시간을 온전히 관측한 사용자만 남긴다.
mature_first_completers AS (
  SELECT
    user_pseudo_id,
    first_completed_at,
    first_completed_at + seven_days_us AS window_end_at
  FROM first_completions
  WHERE
    first_completed_at + seven_days_us <= UNIX_MICROS(
      TIMESTAMP(
        DATE_ADD(PARSE_DATE('%Y%m%d', observation_end_suffix), INTERVAL 1 DAY),
        analysis_timezone
      )
    )
),

post_completion_user_flags AS (
  SELECT
    cohort.user_pseudo_id,
    MAX(IF(
      events.event_name = 'destination_mission_started'
        AND events.use_index = 2
        AND events.retry_attempt >= 0,
      1,
      0
    )) AS used_second_time,
    MAX(IF(
      events.event_name = 'destination_mission_started'
        AND events.use_index >= 3
        AND events.retry_attempt >= 0,
      1,
      0
    )) AS used_third_or_more,
    MAX(IF(
      events.event_name = 'destination_mission_expired'
        AND events.use_index >= 1
        AND events.retry_attempt >= 0,
      1,
      0
    )) AS experienced_expiry,
    MAX(IF(
      events.event_name = 'destination_mission_force_ended'
        AND events.use_index >= 1
        AND events.retry_attempt >= 0,
      1,
      0
    )) AS experienced_force_end
  FROM mature_first_completers AS cohort
  LEFT JOIN extracted_events AS events
    ON events.user_pseudo_id = cohort.user_pseudo_id
    AND events.event_timestamp > cohort.first_completed_at
    AND events.event_timestamp <= cohort.window_end_at
  GROUP BY cohort.user_pseudo_id
),

metric_rows AS (
  SELECT
    1 AS metric_order,
    'first_completion_rate' AS metric_name,
    COUNT(DISTINCT user_pseudo_id) AS denominator_users,
    COUNT(DISTINCT IF(first_completed_at IS NOT NULL, user_pseudo_id, NULL)) AS numerator_users
  FROM first_completion_population

  UNION ALL

  SELECT
    2,
    'second_use_within_7d_rate',
    COUNT(DISTINCT user_pseudo_id),
    COUNT(DISTINCT IF(used_second_time = 1, user_pseudo_id, NULL))
  FROM post_completion_user_flags

  UNION ALL

  SELECT
    3,
    'three_or_more_uses_within_7d_rate',
    COUNT(DISTINCT user_pseudo_id),
    COUNT(DISTINCT IF(used_third_or_more = 1, user_pseudo_id, NULL))
  FROM post_completion_user_flags

  UNION ALL

  SELECT
    4,
    'expiry_experience_within_7d_rate',
    COUNT(DISTINCT user_pseudo_id),
    COUNT(DISTINCT IF(experienced_expiry = 1, user_pseudo_id, NULL))
  FROM post_completion_user_flags

  UNION ALL

  SELECT
    5,
    'force_end_experience_within_7d_rate',
    COUNT(DISTINCT user_pseudo_id),
    COUNT(DISTINCT IF(experienced_force_end = 1, user_pseudo_id, NULL))
  FROM post_completion_user_flags
)

SELECT
  metric_name,
  denominator_users,
  numerator_users,
  SAFE_DIVIDE(numerator_users, denominator_users) AS user_rate
FROM metric_rows
ORDER BY metric_order;
