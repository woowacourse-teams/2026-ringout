package com.ringout.api.destination.service;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.destination.domain.Coordinate;
import com.ringout.api.destination.domain.Destination;
import com.ringout.api.destination.domain.DestinationAlias;
import com.ringout.api.destination.dto.request.DestinationUpdateRequest;
import com.ringout.api.destination.dto.response.DestinationCreateResponse;
import com.ringout.api.destination.dto.response.DestinationUpdateResponse;
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
        validateAuthenticatedUserExists(userId);

        Destination destination = Destination.create(userId, DestinationAlias.from(alias),
            Coordinate.of(latitude, longitude));

        return new DestinationCreateResponse(destinationRepository.save(destination).getId());
    }

    @Transactional
    public DestinationUpdateResponse updateDestination(Long userId, Long destinationId,
        DestinationUpdateRequest request) {
        validateAuthenticatedUserExists(userId);
        validateDestinationIdIsPositive(destinationId);
        validateDestinationUpdateRequestBodyExists(request);

        Destination destination = findDestinationByIdAndValidateOwner(userId, destinationId);
        destination.update(request.alias(), request.latitude(), request.longitude());

        return new DestinationUpdateResponse(destination.getId(), destination.getAlias().getValue(),
            destination.getCoordinate().getLatitude(), destination.getCoordinate().getLongitude());
    }

    @Transactional
    public void deleteDestination(Long userId, Long destinationId) {
        validateAuthenticatedUserExists(userId);
        validateDestinationIdIsPositive(destinationId);

        Destination destination = findDestinationByIdAndValidateOwner(userId, destinationId);
        destinationRepository.delete(destination);
    }

    private void validateAuthenticatedUserExists(Long userId) {
        if (userId == null) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_UNAUTHORIZED);
        }
    }

    private void validateDestinationIdIsPositive(Long destinationId) {
        if (destinationId == null || destinationId <= 0) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_ID_INVALID);
        }
    }

    private void validateDestinationUpdateRequestBodyExists(DestinationUpdateRequest request) {
        if (request == null) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_UPDATE_REQUEST_INVALID);
        }
    }

    private Destination findDestinationByIdAndValidateOwner(Long userId, Long destinationId) {
        Destination destination = destinationRepository.findById(destinationId)
            .orElseThrow(() -> new GeneralException(DestinationErrorStatus.DESTINATION_NOT_FOUND));
        validateDestinationBelongsToUser(destination, userId);
        return destination;
    }

    private void validateDestinationBelongsToUser(Destination destination, Long userId) {
        if (!destination.isOwnedBy(userId)) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_FORBIDDEN);
        }
    }
}
