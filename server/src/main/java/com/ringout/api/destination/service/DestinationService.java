package com.ringout.api.destination.service;

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
import com.ringout.api.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DestinationService {

    private final DestinationRepository destinationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public DestinationUpsertResponse upsertDestination(Long userId, String destinationId,
        DestinationUpsertRequest request) {
        validateAuthenticatedUserExists(userId);
        validateDestinationIdIsUsable(destinationId);
        validateDestinationUpsertRequestBodyExists(request);

        DestinationAlias alias = createUpsertAlias(request.alias());
        Coordinate coordinate = createUpsertCoordinate(request.latitude(), request.longitude());

        validateDestinationVersionIsUsable(request.destinationVersion());

        destinationRepository.upsertOwnedDestination(userId, destinationId, alias.getValue(), coordinate.getLatitude(),
            coordinate.getLongitude(), request.destinationVersion(), LocalDateTime.now(clock));

        return DestinationUpsertResponse.from(findUpsertedDestination(userId, destinationId));
    }

    @Transactional(readOnly = true)
    public DestinationListResponse getDestinations(Long userId) {
        validateAuthenticatedUserExists(userId);

        List<DestinationResponse> destinations = destinationRepository.findOwnedDestinations(userId).stream()
            .map(DestinationResponse::from)
            .toList();

        return new DestinationListResponse(destinations);
    }

    @Transactional
    public void deleteDestination(Long userId, String destinationId) {
        validateAuthenticatedUserExists(userId);
        validateDestinationIdToDeleteIsUsable(destinationId);

        destinationRepository.deleteOwnedDestination(userId, destinationId);
    }

    private void validateAuthenticatedUserExists(Long userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_UNAUTHORIZED);
        }
    }

    private void validateDestinationIdIsUsable(String destinationId) {
        if (!Destination.isValidDestinationId(destinationId)) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_ID_INVALID);
        }
    }

    private void validateDestinationIdToDeleteIsUsable(String destinationId) {
        if (!Destination.isValidDestinationId(destinationId)) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_DELETE_ID_INVALID);
        }
    }

    private void validateDestinationUpsertRequestBodyExists(DestinationUpsertRequest request) {
        if (request == null) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_UPSERT_REQUEST_INVALID);
        }
    }

    private void validateDestinationVersionIsUsable(Long destinationVersion) {
        if (!Destination.isValidDestinationVersion(destinationVersion)) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_VERSION_INVALID);
        }
    }

    private DestinationAlias createUpsertAlias(String alias) {
        try {
            return DestinationAlias.from(alias);
        } catch (GeneralException exception) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_UPSERT_REQUEST_INVALID);
        }
    }

    private Coordinate createUpsertCoordinate(Double latitude, Double longitude) {
        try {
            return Coordinate.of(latitude, longitude);
        } catch (GeneralException exception) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_UPSERT_REQUEST_INVALID);
        }
    }

    private Destination findUpsertedDestination(Long userId, String destinationId) {
        return destinationRepository.findOwnedDestinationForUpdate(userId, destinationId)
            .orElseThrow(() -> new IllegalStateException(
                "Upsert한 목적지를 찾을 수 없습니다. userId=" + userId + ", destinationId=" + destinationId));
    }
}
