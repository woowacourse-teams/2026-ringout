package com.ringout.api.room.service;

import com.ringout.api.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ActiveRoomsCacheEvictor {

    /**
     * 커밋 전에 비우면, 커밋 사이에 다른 요청이 이전 데이터를 다시 캐시에 넣을 수 있어 커밋 후에 비운다.
     * 롤백되면 바뀐 것이 없으므로 비우지 않는다.
     */
    @CacheEvict(cacheNames = CacheConfig.ACTIVE_ROOMS, allEntries = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRoomListChanged(RoomListChangedEvent event) {
    }
}
