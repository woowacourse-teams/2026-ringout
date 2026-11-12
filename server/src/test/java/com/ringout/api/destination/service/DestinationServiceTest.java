package com.ringout.api.destination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.destination.domain.Coordinate;
import com.ringout.api.destination.domain.Destination;
import com.ringout.api.destination.domain.DestinationAlias;
import com.ringout.api.destination.dto.request.DestinationUpsertRequest;
import com.ringout.api.destination.dto.response.DestinationListResponse;
import com.ringout.api.destination.dto.response.DestinationResponse;
import com.ringout.api.destination.dto.response.DestinationUpsertResponse;
import com.ringout.api.destination.repository.DestinationRepository;
import com.ringout.api.destination.status.DestinationErrorStatus;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DestinationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private DestinationRepository destinationRepository;

    @Mock
    private UserRepository userRepository;

    private DestinationService destinationService;

    @BeforeEach
    void setUp() {
        destinationService = new DestinationService(destinationRepository, userRepository, FIXED_CLOCK);
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
        void 목적지_목록을_버전과_함께_조회한다() {
            // given
            Long userId = 1L;
            Destination firstDestination = Destination.create(
                userWithId(userId),
                "destination-001",
                DestinationAlias.from("런닝 장소"),
                Coordinate.of(37.5665, 126.9780),
                1L
            );
            Destination secondDestination = Destination.create(
                userWithId(userId),
                "destination-002",
                DestinationAlias.from("헬스장"),
                Coordinate.of(37.4979, 127.0276),
                3L
            );
            given(destinationRepository.findOwnedDestinations(userId))
                .willReturn(List.of(firstDestination, secondDestination));

            // when
            DestinationListResponse response = destinationService.getDestinations(userId);

            // then
            assertThat(response.destinations()).containsExactly(
                new DestinationResponse("destination-001", "런닝 장소", 37.5665, 126.9780, 1L),
                new DestinationResponse("destination-002", "헬스장", 37.4979, 127.0276, 3L)
            );
            verify(destinationRepository).findOwnedDestinations(userId);
        }

        @Test
        void 목적지가_없으면_빈_목록을_조회한다() {
            // given
            Long userId = 1L;
            given(destinationRepository.findOwnedDestinations(userId)).willReturn(List.of());

            // when
            DestinationListResponse response = destinationService.getDestinations(userId);

            // then
            assertThat(response.destinations()).isEmpty();
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
    class 목적지_upsert {

        private static final Long USER_ID = 1L;
        private static final String DESTINATION_ID = "destination-001";

        private DestinationUpsertRequest request(Long destinationVersion) {
            return new DestinationUpsertRequest("회사", 37.5665, 126.9780, destinationVersion);
        }

        @Test
        void 검증된_값으로_upsert하고_서버에_최종_저장된_목적지를_반환한다() {
            // given
            Destination stored = Destination.create(userWithId(USER_ID), DESTINATION_ID, DestinationAlias.from("학교"),
                Coordinate.of(37.5502, 127.0738), 5L);
            given(destinationRepository.findOwnedDestinationForUpdate(USER_ID, DESTINATION_ID)).willReturn(Optional.of(stored));

            // when
            DestinationUpsertResponse response = destinationService.upsertDestination(USER_ID, DESTINATION_ID,
                new DestinationUpsertRequest("  회사  ", 37.5665, 126.9780, 3L));

            // then
            verify(destinationRepository).upsertOwnedDestination(USER_ID, DESTINATION_ID, "회사", 37.5665, 126.9780,
                3L, LocalDateTime.now(FIXED_CLOCK));
            assertThat(response).isEqualTo(
                new DestinationUpsertResponse(DESTINATION_ID, "학교", 37.5502, 127.0738, 5L));
        }

        @Test
        void 사용자_id가_없으면_upsert할_수_없다() {
            // when // then
            assertThatThrownBy(() -> destinationService.upsertDestination(null, DESTINATION_ID, request(1L)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_UNAUTHORIZED));
            verifyNoInteractions(destinationRepository);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", "dest 1", "1234567890123456789012345678901234567"})
        void 목적지_id가_올바르지_않으면_upsert할_수_없다(String destinationId) {
            // when // then
            assertThatThrownBy(() -> destinationService.upsertDestination(USER_ID, destinationId, request(1L)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_ID_INVALID));
            verifyNoInteractions(destinationRepository);
        }

        @Test
        void 요청_body가_없으면_upsert할_수_없다() {
            // when // then
            assertThatThrownBy(() -> destinationService.upsertDestination(USER_ID, DESTINATION_ID, null))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode())
                        .isEqualTo(DestinationErrorStatus.DESTINATION_UPSERT_REQUEST_INVALID));
            verifyNoInteractions(destinationRepository);
        }

        @ParameterizedTest
        @MethodSource("com.ringout.api.destination.service.DestinationServiceTest#invalidUpsertDestinationInfos")
        void 목적지_정보가_올바르지_않으면_upsert할_수_없다(String alias, Double latitude, Double longitude) {
            // given
            DestinationUpsertRequest request = new DestinationUpsertRequest(alias, latitude, longitude, 1L);

            // when // then
            assertThatThrownBy(() -> destinationService.upsertDestination(USER_ID, DESTINATION_ID, request))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode())
                        .isEqualTo(DestinationErrorStatus.DESTINATION_UPSERT_REQUEST_INVALID));
            verifyNoInteractions(destinationRepository);
        }

        @Test
        void 목적지_버전이_없으면_upsert할_수_없다() {
            // when // then
            assertThatThrownBy(() -> destinationService.upsertDestination(USER_ID, DESTINATION_ID, request(null)))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_VERSION_INVALID));
            verifyNoInteractions(destinationRepository);
        }
    }

    @Nested
    class 목적지_삭제 {

        @Test
        void 사용자와_목적지_id로_목적지를_삭제한다() {
            // given
            Long userId = 1L;
            String destinationId = "dest-1";

            // when
            destinationService.deleteDestination(userId, destinationId);

            // then
            verify(destinationRepository).deleteOwnedDestination(userId, destinationId);
        }

        @Test
        void 사용자_id가_없으면_목적지를_삭제할_수_없다() {
            // given
            Long userId = null;
            String destinationId = "dest-1";

            // when // then
            assertThatThrownBy(() -> destinationService.deleteDestination(userId, destinationId))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_UNAUTHORIZED));
            verify(destinationRepository, never()).deleteOwnedDestination(any(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {" ", "dest 1", "1234567890123456789012345678901234567"})
        void 목적지_id가_올바르지_않으면_삭제할_수_없다(String destinationId) {
            // given
            Long userId = 1L;

            // when // then
            assertThatThrownBy(() -> destinationService.deleteDestination(userId, destinationId))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_DELETE_ID_INVALID));
            verify(destinationRepository, never()).deleteOwnedDestination(any(), any());
        }
    }

    static Stream<Arguments> invalidUpsertDestinationInfos() {
        return Stream.of(
            Arguments.of(null, 37.5665, 126.9780),
            Arguments.of("   ", 37.5665, 126.9780),
            Arguments.of("1234567890123", 37.5665, 126.9780),
            Arguments.of("회사", null, 126.9780),
            Arguments.of("회사", 37.5665, null),
            Arguments.of("회사", 90.1, 126.9780),
            Arguments.of("회사", -90.1, 126.9780),
            Arguments.of("회사", 37.5665, 180.1),
            Arguments.of("회사", 37.5665, -180.1),
            Arguments.of("회사", Double.NaN, 126.9780)
        );
    }
}
