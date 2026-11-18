-- campaign: 클라이언트 화면 이동에 필요한 값을 FCM data 키-값으로 보내기 위해 deeplink 를 data(JSON) 로 대체한다.
ALTER TABLE campaign
    DROP COLUMN deeplink,
    ADD COLUMN data JSON NULL;
