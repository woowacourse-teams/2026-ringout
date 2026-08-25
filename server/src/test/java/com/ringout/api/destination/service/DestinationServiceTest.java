package com.ringout.api.destination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.destination.domain.Coordinate;
import com.ringout.api.destination.domain.Destination;
import com.ringout.api.destination.domain.DestinationAlias;
import com.ringout.api.destination.dto.request.DestinationSyncRequest;
import com.ringout.api.destination.dto.request.DestinationSyncRequest.DestinationSyncItemRequest;
import com.ringout.api.destination.dto.request.DestinationUpdateRequest;
import com.ringout.api.destination.dto.response.DestinationCreateResponse;
import com.ringout.api.destination.dto.response.DestinationResponse;
import com.ringout.api.destination.dto.response.DestinationSyncResponse;
import com.ringout.api.destination.dto.response.DestinationUpdateResponse;
import com.ringout.api.destination.repository.DestinationRepository;
import com.ringout.api.destination.status.DestinationErrorStatus;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DestinationServiceTest {

    @Mock
    private DestinationRepository destinationRepository;

    @Mock
    private UserRepository userRepository;

    private DestinationService destinationService;

    @BeforeEach
    void setUp() {
        destinationService = new DestinationService(destinationRepository, userRepository);
        lenient().when(userRepository.existsById(any())).thenReturn(true);
    }

    private User userWithId(Long id) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(id);
        return user;
    }

    @Nested
    class 목적지_조회 {

        @Test
        void 목적지_목록_조회에_성공한다() {
            // given
            Long userId = 1L;
            Destination firstDestination = Destination.create(
                userWithId(userId),
                DestinationAlias.from("런닝 장소"),
                Coordinate.of(37.5665, 126.9780)
            );
            Destination secondDestination = Destination.create(
                userWithId(userId),
                DestinationAlias.from("헬스장"),
                Coordinate.of(37.4979, 127.0276)
            );
            ReflectionTestUtils.setField(firstDestination, "id", 1L);
            ReflectionTestUtils.setField(secondDestination, "id", 2L);
            given(destinationRepository.findOwnedDestinations(userId))
                .willReturn(List.of(firstDestination, secondDestination));

            // when
            List<DestinationResponse> responses = destinationService.getDestinations(userId);

            // then
            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).destinationId()).isEqualTo(1L);
            assertThat(responses.get(0).alias()).isEqualTo("런닝 장소");
            assertThat(responses.get(0).latitude()).isEqualTo(37.5665);
            assertThat(responses.get(0).longitude()).isEqualTo(126.9780);
            assertThat(responses.get(1).destinationId()).isEqualTo(2L);
            assertThat(responses.get(1).alias()).isEqualTo("헬스장");
            verify(destinationRepository).findOwnedDestinations(userId);
        }

        @Test
        void 목적지가_없으면_빈_목록을_조회한다() {
            // given
            Long userId = 1L;
            given(destinationRepository.findOwnedDestinations(userId)).willReturn(List.of());

            // when
            List<DestinationResponse> responses = destinationService.getDestinations(userId);

            // then
            assertThat(responses).isEmpty();
        }

        @Test
        void 사용자_id가_없으면_목적지_목록을_조회할_수_없다() {
            // given
            Long userId = null;

            // when // then
            assertThatThrownBy(() -> destinationService.getDestinations(userId))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_UNAUTHORIZED));
            verify(destinationRepository, never()).findOwnedDestinations(any());
        }

        @Test
        void 사용자가_존재하지_않으면_목적지_목록을_조회할_수_없다() {
            // given
            Long userId = 1L;
            given(userRepository.existsById(userId)).willReturn(false);

            // when // then
            assertThatThrownBy(() -> destinationService.getDestinations(userId))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_UNAUTHORIZED));
            verify(destinationRepository, never()).findOwnedDestinations(any());
        }
    }

    @Nested
    class 목적지_동기화 {

        @Test
        void 목적지_동기화에_성공한다() {
            // given
            Long userId = 1L;
            AtomicLong destinationId = new AtomicLong(1L);
            DestinationSyncRequest request = new DestinationSyncRequest(List.of(
                new DestinationSyncItemRequest(2L, "집", 37.4979, 127.0276),
                new DestinationSyncItemRequest(3L, "회사", 37.5665, 126.9780)
            ));

            given(destinationRepository.save(any(Destination.class)))
                .willAnswer(invocation -> {
                    Destination destination = invocation.getArgument(0);
                    ReflectionTestUtils.setField(destination, "id", destinationId.getAndIncrement());
                    return destination;
                });

            // when
            DestinationSyncResponse response = destinationService.syncDestinations(userId, request);

            // then
            assertThat(response.destinations()).hasSize(2);
            assertThat(response.destinations().get(0).clientDestinationId()).isEqualTo(2L);
            assertThat(response.destinations().get(0).destinationId()).isEqualTo(1L);
            assertThat(response.destinations().get(0).alias()).isEqualTo("집");
            assertThat(response.destinations().get(0).latitude()).isEqualTo(37.4979);
            assertThat(response.destinations().get(0).longitude()).isEqualTo(127.0276);
            assertThat(response.destinations().get(1).clientDestinationId()).isEqualTo(3L);
            assertThat(response.destinations().get(1).destinationId()).isEqualTo(2L);
            assertThat(response.destinations().get(1).alias()).isEqualTo("회사");
            verify(destinationRepository, times(2)).save(any(Destination.class));
        }

        @Test
        void 사용자_id가_없으면_목적지를_동기화할_수_없다() {
            // given
            Long userId = null;
            DestinationSyncRequest request = new DestinationSyncRequest(List.of(
                new DestinationSyncItemRequest(2L, "집", 37.4979, 127.0276)
            ));

            // when // then
            assertThatThrownBy(() -> destinationService.syncDestinations(userId, request))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_UNAUTHORIZED));
            verify(destinationRepository, never()).save(any(Destination.class));
        }

        @Test
        void 요청_body가_없으면_목적지를_동기화할_수_없다() {
            // given
            Long userId = 1L;
            DestinationSyncRequest request = null;

            // when // then
            assertThatThrownBy(() -> destinationService.syncDestinations(userId, request))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_SYNC_REQUEST_INVALID));
            verify(destinationRepository, never()).save(any(Destination.class));
        }

        @Test
        void 목적지_목록이_비어있으면_빈_목록으로_동기화한다() {
            // given
            Long userId = 1L;
            DestinationSyncRequest request = new DestinationSyncRequest(List.of());

            // when
            DestinationSyncResponse response = destinationService.syncDestinations(userId, request);

            // then
            assertThat(response.destinations()).isEmpty();
            verify(destinationRepository, never()).save(any(Destination.class));
        }

        @Test
        void 목적지_목록이_없으면_동기화할_수_없다() {
            // given
            Long userId = 1L;
            DestinationSyncRequest request = new DestinationSyncRequest(null);

            // when // then
            assertThatThrownBy(() -> destinationService.syncDestinations(userId, request))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_SYNC_REQUEST_INVALID));
            verify(destinationRepository, never()).save(any(Destination.class));
        }

        @Test
        void 목적지_항목이_null_이라면_동기화할_수_없다() {
            // given
            Long userId = 1L;
            DestinationSyncRequest request = new DestinationSyncRequest(Collections.singletonList(null));

            // when // then
            assertThatThrownBy(() -> destinationService.syncDestinations(userId, request))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_SYNC_REQUEST_INVALID));
            verify(destinationRepository, never()).save(any(Destination.class));
        }

        @Test
        void 클라이언트_목적지_id가_올바르지_않으면_동기화할_수_없다() {
            // given
            Long userId = 1L;
            DestinationSyncRequest request = new DestinationSyncRequest(List.of(
                new DestinationSyncItemRequest(0L, "집", 37.4979, 127.0276)
            ));

            // when // then
            assertThatThrownBy(() -> destinationService.syncDestinations(userId, request))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_SYNC_REQUEST_INVALID));
            verify(destinationRepository, never()).save(any(Destination.class));
        }

        @Test
        void 클라이언트_목적지_id가_중복되면_동기화할_수_없다() {
            // given
            Long userId = 1L;
            DestinationSyncRequest request = new DestinationSyncRequest(List.of(
                new DestinationSyncItemRequest(2L, "집", 37.4979, 127.0276),
                new DestinationSyncItemRequest(2L, "회사", 37.5665, 126.9780)
            ));

            // when // then
            assertThatThrownBy(() -> destinationService.syncDestinations(userId, request))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_SYNC_REQUEST_INVALID));
            verify(destinationRepository, never()).save(any(Destination.class));
        }

        @Test
        void 목적지_별칭이_올바르지_않으면_동기화할_수_없다() {
            // given
            Long userId = 1L;
            DestinationSyncRequest request = new DestinationSyncRequest(List.of(
                new DestinationSyncItemRequest(2L, "", 37.4979, 127.0276)
            ));

            // when // then
            assertThatThrownBy(() -> destinationService.syncDestinations(userId, request))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_SYNC_REQUEST_INVALID));
            verify(destinationRepository, never()).save(any(Destination.class));
        }

        @Test
        void 목적지_좌표가_올바르지_않으면_동기화할_수_없다() {
            // given
            Long userId = 1L;
            DestinationSyncRequest request = new DestinationSyncRequest(List.of(
                new DestinationSyncItemRequest(2L, "집", 91.0, 127.0276)
            ));

            // when // then
            assertThatThrownBy(() -> destinationService.syncDestinations(userId, request))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(
                        DestinationErrorStatus.DESTINATION_SYNC_COORDINATE_INVALID));
            verify(destinationRepository, never()).save(any(Destination.class));
        }
    }

    @Nested
    class 목적지_생성 {

        @Test
        void 목적지_생성에_성공한다() {
            // given
            Long userId = 1L;
            String alias = "헬스장";
            Double latitude = 37.4979;
            Double longitude = 127.0276;
            Long destinationId = 10L;

            given(destinationRepository.save(any(Destination.class)))
                .willAnswer(invocation -> {
                    Destination destination = invocation.getArgument(0);
                    ReflectionTestUtils.setField(destination, "id", destinationId);
                    return destination;
                });

            // when
            DestinationCreateResponse response = destinationService.createDestination(userId, alias, latitude,
                longitude);

            // then
            assertThat(response.destinationId()).isEqualTo(destinationId);
            verify(destinationRepository).save(any(Destination.class));
        }

        @Test
        void 사용자_id가_없으면_목적지를_생성할_수_없다() {
            // given
            Long userId = null;
            String alias = "헬스장";
            Double latitude = 37.4979;
            Double longitude = 127.0276;

            // when // then
            assertThatThrownBy(() -> destinationService.createDestination(userId, alias, latitude, longitude))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_UNAUTHORIZED));
            verify(destinationRepository, never()).save(any(Destination.class));
        }
    }

    @Nested
    class 목적지_수정 {

        @Test
        void 목적지_수정에_성공한다() {
            // given
            Long userId = 1L;
            Long destinationId = 10L;
            Destination destination = Destination.create(
                userWithId(userId),
                DestinationAlias.from("헬스장"),
                Coordinate.of(37.4979, 127.0276)
            );
            ReflectionTestUtils.setField(destination, "id", destinationId);
            given(destinationRepository.findById(destinationId)).willReturn(Optional.of(destination));

            // when
            DestinationUpdateResponse response = destinationService.updateDestination(
                userId,
                destinationId,
                new DestinationUpdateRequest("회사", 37.5665, 126.9780)
            );

            // then
            assertThat(response.destinationId()).isEqualTo(destinationId);
            assertThat(response.alias()).isEqualTo("회사");
            assertThat(response.latitude()).isEqualTo(37.5665);
            assertThat(response.longitude()).isEqualTo(126.9780);
        }

        @Test
        void 일부_필드만_수정하면_나머지는_기존_값을_유지한다() {
            // given
            Long userId = 1L;
            Long destinationId = 10L;
            Destination destination = Destination.create(
                userWithId(userId),
                DestinationAlias.from("헬스장"),
                Coordinate.of(37.4979, 127.0276)
            );
            ReflectionTestUtils.setField(destination, "id", destinationId);
            given(destinationRepository.findById(destinationId)).willReturn(Optional.of(destination));

            // when
            DestinationUpdateResponse response = destinationService.updateDestination(
                userId,
                destinationId,
                new DestinationUpdateRequest("회사", null, null)
            );

            // then
            assertThat(response.destinationId()).isEqualTo(destinationId);
            assertThat(response.alias()).isEqualTo("회사");
            assertThat(response.latitude()).isEqualTo(37.4979);
            assertThat(response.longitude()).isEqualTo(127.0276);
        }

        @Test
        void 사용자_id가_없으면_목적지를_수정할_수_없다() {
            // given
            Long userId = null;
            Long destinationId = 10L;

            // when // then
            assertThatThrownBy(
                () -> destinationService.updateDestination(userId, destinationId,
                    new DestinationUpdateRequest("회사", null, null)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_UNAUTHORIZED));
            verify(destinationRepository, never()).findById(any());
        }

        @Test
        void 목적지_id가_올바르지_않으면_수정할_수_없다() {
            // given
            Long userId = 1L;
            Long destinationId = 0L;

            // when // then
            assertThatThrownBy(
                () -> destinationService.updateDestination(userId, destinationId,
                    new DestinationUpdateRequest("회사", null, null)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_ID_INVALID));
            verify(destinationRepository, never()).findById(any());
        }

        @Test
        void 수정할_정보가_없으면_수정할_수_없다() {
            // given
            Long userId = 1L;
            Long destinationId = 10L;

            // when // then
            assertThatThrownBy(
                () -> destinationService.updateDestination(userId, destinationId,
                    new DestinationUpdateRequest(null, null, null)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(
                        DestinationErrorStatus.DESTINATION_UPDATE_REQUEST_INVALID));
            verify(destinationRepository, never()).findById(any());
        }

        @Test
        void 수정할_정보가_올바르지_않으면_수정할_수_없다() {
            // given
            Long userId = 1L;
            Long destinationId = 10L;
            Destination destination = Destination.create(
                userWithId(userId),
                DestinationAlias.from("헬스장"),
                Coordinate.of(37.4979, 127.0276)
            );
            given(destinationRepository.findById(destinationId)).willReturn(Optional.of(destination));

            // when // then
            assertThatThrownBy(
                () -> destinationService.updateDestination(userId, destinationId,
                    new DestinationUpdateRequest("", null, null)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(
                        DestinationErrorStatus.DESTINATION_UPDATE_REQUEST_INVALID));
        }

        @Test
        void 목적지가_없으면_수정할_수_없다() {
            // given
            Long userId = 1L;
            Long destinationId = 10L;
            given(destinationRepository.findById(destinationId)).willReturn(Optional.empty());

            // when // then
            assertThatThrownBy(
                () -> destinationService.updateDestination(userId, destinationId,
                    new DestinationUpdateRequest("회사", null, null)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_NOT_FOUND));
        }

        @Test
        void 다른_사용자의_목적지는_수정할_수_없다() {
            // given
            Long userId = 1L;
            Long otherUserId = 2L;
            Long destinationId = 10L;
            Destination destination = Destination.create(
                userWithId(otherUserId),
                DestinationAlias.from("헬스장"),
                Coordinate.of(37.4979, 127.0276)
            );
            given(destinationRepository.findById(destinationId)).willReturn(Optional.of(destination));

            // when // then
            assertThatThrownBy(
                () -> destinationService.updateDestination(userId, destinationId,
                    new DestinationUpdateRequest("회사", null, null)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_FORBIDDEN));
        }
    }

    @Nested
    class 목적지_삭제 {

        @Test
        void 목적지_삭제에_성공한다() {
            // given
            Long userId = 1L;
            Long destinationId = 10L;
            Destination destination = Destination.create(userWithId(userId), null, null);
            given(destinationRepository.findById(destinationId)).willReturn(Optional.of(destination));

            // when
            destinationService.deleteDestination(userId, destinationId);

            // then
            verify(destinationRepository).delete(destination);
        }

        @Test
        void 사용자_id가_없으면_목적지를_삭제할_수_없다() {
            // given
            Long userId = null;
            Long destinationId = 10L;

            // when // then
            assertThatThrownBy(() -> destinationService.deleteDestination(userId, destinationId))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_UNAUTHORIZED));
            verify(destinationRepository, never()).findById(any());
            verify(destinationRepository, never()).delete(any());
        }

        @Test
        void 목적지_id가_올바르지_않으면_삭제할_수_없다() {
            // given
            Long userId = 1L;
            Long destinationId = 0L;

            // when // then
            assertThatThrownBy(() -> destinationService.deleteDestination(userId, destinationId))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_ID_INVALID));
            verify(destinationRepository, never()).findById(any());
            verify(destinationRepository, never()).delete(any());
        }

        @Test
        void 목적지가_없으면_삭제할_수_없다() {
            // given
            Long userId = 1L;
            Long destinationId = 10L;
            given(destinationRepository.findById(destinationId)).willReturn(Optional.empty());

            // when // then
            assertThatThrownBy(() -> destinationService.deleteDestination(userId, destinationId))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_NOT_FOUND));
            verify(destinationRepository, never()).delete(any());
        }

        @Test
        void 다른_사용자의_목적지는_삭제할_수_없다() {
            // given
            Long userId = 1L;
            Long otherUserId = 2L;
            Long destinationId = 10L;
            Destination destination = Destination.create(userWithId(otherUserId), null, null);
            given(destinationRepository.findById(destinationId)).willReturn(Optional.of(destination));

            // when // then
            assertThatThrownBy(() -> destinationService.deleteDestination(userId, destinationId))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_FORBIDDEN));
            verify(destinationRepository, never()).delete(any());
        }
    }
}
