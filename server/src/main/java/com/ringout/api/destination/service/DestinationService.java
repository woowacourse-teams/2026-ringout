package com.ringout.api.destination.service;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.destination.domain.Coordinate;
import com.ringout.api.destination.domain.Destination;
import com.ringout.api.destination.domain.DestinationAlias;
import com.ringout.api.destination.dto.response.DestinationCreateResponse;
import com.ringout.api.destination.repository.DestinationRepository;
import com.ringout.api.destination.status.DestinationErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DestinationService {

    private final DestinationRepository destinationRepository;

    @Transactional
    public DestinationCreateResponse createDestination(Long userId, String alias, Double latitude, Double longitude) {
        if (userId == null) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_UNAUTHORIZED);
        }

        Destination destination = Destination.create(userId, DestinationAlias.from(alias),
            Coordinate.of(latitude, longitude));

        return new DestinationCreateResponse(destinationRepository.save(destination).getId());
    }

    @Transactional
    public void deleteDestination(Long userId, Long destinationId) {
        if (userId == null) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_UNAUTHORIZED);
        }
        if (destinationId == null || destinationId <= 0) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_ID_INVALID);
        }

        Destination destination = destinationRepository.findById(destinationId)
            .orElseThrow(() -> new GeneralException(DestinationErrorStatus.DESTINATION_NOT_FOUND));
        if (!destination.isOwnedBy(userId)) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_FORBIDDEN);
        }

        destinationRepository.delete(destination);
    }
}
