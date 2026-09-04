-- user_agreement 와 terms 에 중복 저장되던 type 컬럼을 terms 에만 유지한다.
-- user_agreement 는 terms_id 로 terms 를 조인해 type 을 조회할 수 있으므로 컬럼을 제거한다.
ALTER TABLE user_agreement
    DROP COLUMN type;

-- terms.type 을 VARCHAR 대신 ENUM 으로 변경해 허용 값을 스키마 수준에서 제약한다.
ALTER TABLE terms
    MODIFY COLUMN type ENUM ('SERVICE', 'PRIVACY') NOT NULL;
