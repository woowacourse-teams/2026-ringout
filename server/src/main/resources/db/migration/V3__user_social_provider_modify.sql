-- User 엔티티의 enum 필드를 DB 스키마 수준에서도 제한한다.
ALTER TABLE `user`
    MODIFY COLUMN social_provider ENUM ('GOOGLE', 'KAKAO', 'APPLE') NOT NULL,
    MODIFY COLUMN role ENUM ('USER', 'ADMIN') NOT NULL;