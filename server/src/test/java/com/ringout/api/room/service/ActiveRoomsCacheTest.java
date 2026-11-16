package com.ringout.api.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ringout.api.config.CacheConfig;
import com.ringout.api.file.service.ImageFileService;
import com.ringout.api.room.domain.ActivityDay;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.repository.RoomMemberCount;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.user.domain.User;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 캐시 프록시와 트랜잭션 이벤트를 띄워, 캐시 적중·동시 미스·커밋 후 무효화를 검증한다.
 * DB 대신 저장소를 목으로 두고 호출 횟수로 DB 조회 여부를 판단한다.
 */
@SpringJUnitConfig(ActiveRoomsCacheTest.TestConfig.class)
class ActiveRoomsCacheTest {

    @Autowired
    private ActiveRoomsReader activeRoomsReader;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomUserRepository roomUserRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(CacheConfig.ACTIVE_ROOMS).clear();
        reset(roomRepository, roomUserRepository);
        given(roomRepository.findAllActiveOrderByLatestActivityAtDescIdAsc()).willReturn(List.of(room(10L)));
        given(roomUserRepository.countActiveMembersByRoom()).willReturn(List.of(new RoomMemberCount(10L, 3L)));
    }

    @Nested
    class 캐시_적중 {

        @Test
        void 두_번째_조회부터는_DB를_조회하지_않는다() {
            // when
            List<ActiveRoom> first = activeRoomsReader.read();
            List<ActiveRoom> second = activeRoomsReader.read();

            // then
            assertThat(second).isSameAs(first);
            verify(roomRepository, times(1)).findAllActiveOrderByLatestActivityAtDescIdAsc();
            verify(roomUserRepository, times(1)).countActiveMembersByRoom();
        }

        @Test
        void 캐시된_목록은_수정할_수_없다() {
            // when
            List<ActiveRoom> rooms = activeRoomsReader.read();

            // then
            assertThat(rooms).isUnmodifiable();
            assertThat(rooms.get(0).activityDays()).isUnmodifiable();
        }
    }

    @Nested
    class 동시_미스 {

        @Test
        void 캐시가_빈_순간_동시에_요청해도_DB_조회는_한_번이다() throws Exception {
            // given: DB 조회가 느려 요청들이 계산 도중에 겹치게 한다.
            given(roomRepository.findAllActiveOrderByLatestActivityAtDescIdAsc()).willAnswer(invocation -> {
                Thread.sleep(200);
                return List.of(room(10L));
            });
            int requestCount = 20;
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(requestCount);
            List<Future<List<ActiveRoom>>> results = new ArrayList<>();

            // when
            for (int i = 0; i < requestCount; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return activeRoomsReader.read();
                }));
            }
            start.countDown();
            for (Future<List<ActiveRoom>> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).hasSize(1);
            }
            executor.shutdown();

            // then
            verify(roomRepository, times(1)).findAllActiveOrderByLatestActivityAtDescIdAsc();
        }
    }

    @Nested
    class 무효화 {

        @Test
        void 커밋된_뒤에_캐시를_비운다() {
            // given
            activeRoomsReader.read();
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);

            // when
            transaction.executeWithoutResult(status -> {
                eventPublisher.publishEvent(new RoomListChangedEvent());
                activeRoomsReader.read();   // 커밋 전에는 아직 캐시가 남아 있다
            });
            activeRoomsReader.read();       // 커밋 후에는 다시 DB를 조회한다

            // then
            verify(roomRepository, times(2)).findAllActiveOrderByLatestActivityAtDescIdAsc();
        }

        @Test
        void 롤백되면_캐시를_비우지_않는다() {
            // given
            activeRoomsReader.read();
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);

            // when
            transaction.executeWithoutResult(status -> {
                eventPublisher.publishEvent(new RoomListChangedEvent());
                status.setRollbackOnly();
            });
            activeRoomsReader.read();

            // then
            verify(roomRepository, times(1)).findAllActiveOrderByLatestActivityAtDescIdAsc();
        }
    }

    private static Room room(Long roomId) {
        Room room = Room.of(mock(User.class), null, "아침 운동 모임", null,
            List.of(ActivityDay.MONDAY), LocalTime.of(8, 0));
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }

    @Configuration
    @EnableTransactionManagement
    @Import({CacheConfig.class, ActiveRoomsReader.class, ActiveRoomsCacheEvictor.class})
    static class TestConfig {

        @Bean
        RoomRepository roomRepository() {
            return mock(RoomRepository.class);
        }

        @Bean
        RoomUserRepository roomUserRepository() {
            return mock(RoomUserRepository.class);
        }

        @Bean
        ImageFileService imageFileService() {
            return mock(ImageFileService.class);
        }

        /** DB 없이 트랜잭션 동기화(커밋·롤백 콜백)만 동작하는 트랜잭션 매니저 */
        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                }
            };
        }
    }
}
