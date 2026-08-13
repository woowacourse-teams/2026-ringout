package com.ringout.api.destination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.destination.domain.Destination;
import com.ringout.api.destination.dto.response.DestinationCreateResponse;
import com.ringout.api.destination.repository.DestinationRepository;
import com.ringout.api.destination.status.DestinationErrorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DestinationServiceTest {

    @Mock
    private DestinationRepository destinationRepository;

    private DestinationService destinationService;

    @BeforeEach
    void setUp() {
        destinationService = new DestinationService(destinationRepository);
    }

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
        DestinationCreateResponse response = destinationService.createDestination(userId, alias, latitude, longitude);

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
