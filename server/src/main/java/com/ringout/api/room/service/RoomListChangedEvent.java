package com.ringout.api.room.service;

/**
 * 활성 모임방 목록이나 방별 인원 수가 바뀌었음을 알린다.
 * 트랜잭션이 커밋된 뒤 활성 모임방 캐시를 비운다.
 */
public record RoomListChangedEvent() {
}
