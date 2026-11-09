-- device: 유저당 최신 기기 1개만 유지하고, 새 토큰은 같은 row 에 덮어쓴다.
-- 1) 알림 기록이 남길 device(유저별 가장 최근 등록 row)를 가리키도록 옮긴다.
UPDATE notification n
    JOIN device d ON d.id = n.device_id
    JOIN (SELECT user_id, MAX(id) AS keep_id
          FROM device
          GROUP BY user_id) k ON k.user_id = d.user_id
SET n.device_id = k.keep_id
WHERE n.device_id <> k.keep_id;

-- 2) 남길 row 를 제외한 나머지 device 를 삭제한다.
DELETE d
FROM device d
    JOIN (SELECT user_id, MAX(id) AS keep_id
          FROM device
          GROUP BY user_id) k ON k.user_id = d.user_id
WHERE d.id <> k.keep_id;

-- 3) 로그아웃/기기 이전 시 토큰을 비울 수 있도록 NULL 을 허용하고, 유저당 1개를 제약으로 보장한다.
ALTER TABLE device
    MODIFY COLUMN fcm_token VARCHAR(255) NULL,
    ADD CONSTRAINT uk_device_user UNIQUE (user_id);
