package com.ringout.api.destination.service;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.destination.domain.Coordinate;
import com.ringout.api.destination.domain.Destination;
import com.ringout.api.destination.domain.DestinationAlias;
import com.ringout.api.destination.dto.request.DestinationSyncRequest;
import com.ringout.api.destination.dto.request.DestinationSyncRequest.DestinationSyncItemRequest;
import com.ringout.api.destination.dto.request.DestinationUpsertRequest;
import com.ringout.api.destination.dto.response.DestinationResponse;
import com.ringout.api.destination.dto.response.DestinationSyncResponse;
import com.ringout.api.destination.dto.response.DestinationSyncResponse.DestinationSyncItemResponse;
import com.ringout.api.destination.dto.response.DestinationUpsertResponse;
import com.ringout.api.destination.repository.DestinationRepository;
import com.ringout.api.destination.status.DestinationErrorStatus;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    public List<DestinationResponse> getDestinations(Long userId) {
        validateAuthenticatedUserExists(userId);

        return destinationRepository.findOwnedDestinations(userId).stream()
            .map(DestinationResponse::from)
            .toList();
    }

    @Transactional
    public DestinationSyncResponse syncDestinations(Long userId, DestinationSyncRequest request) {
        validateAuthenticatedUserExists(userId);
        validateDestinationSyncRequestBodyExists(request);
        validateSyncRequestDestinationIdsAreUsable(request.destinations());

        User user = userRepository.getReferenceById(userId);
        List<DestinationSyncItemResponse> syncedDestinations = new ArrayList<>();
        for (DestinationSyncItemRequest item : request.destinations()) {
            Destination destination = createDestinationForSync(user, item);
            Destination savedDestination = saveDestination(destination);
            syncedDestinations.add(DestinationSyncItemResponse.of(item.clientDestinationId(), savedDestination));
        }

        return new DestinationSyncResponse(syncedDestinations);
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

    private void validateDestinationSyncRequestBodyExists(DestinationSyncRequest request) {
        if (request == null || request.hasNoDestinationList()) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_SYNC_REQUEST_INVALID);
        }
    }

    private void validateSyncRequestDestinationIdsAreUsable(List<DestinationSyncItemRequest> destinations) {
        Set<Long> clientDestinationIds = new HashSet<>();
        Set<String> destinationIds = new HashSet<>();
        for (DestinationSyncItemRequest destination : destinations) {
            validateDestinationSyncItemExists(destination);
            validateClientDestinationIdIsPositive(destination.clientDestinationId());
            validateClientDestinationIdIsUnique(clientDestinationIds, destination.clientDestinationId());
            validateDestinationIdIsUniqueInSyncRequest(destinationIds, destination.destinationId());
        }
    }

    private void validateDestinationSyncItemExists(DestinationSyncItemRequest destination) {
        if (destination == null) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_SYNC_REQUEST_INVALID);
        }
    }

    private void validateClientDestinationIdIsPositive(Long clientDestinationId) {
        if (clientDestinationId == null || clientDestinationId <= 0) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_SYNC_REQUEST_INVALID);
        }
    }

    private void validateClientDestinationIdIsUnique(Set<Long> clientDestinationIds, Long clientDestinationId) {
        if (!clientDestinationIds.add(clientDestinationId)) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_SYNC_REQUEST_INVALID);
        }
    }

    private void validateDestinationIdIsUniqueInSyncRequest(Set<String> destinationIds, String destinationId) {
        if (!destinationIds.add(destinationId)) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_SYNC_REQUEST_INVALID);
        }
    }

    private Destination saveDestination(Destination destination) {
        try {
            return destinationRepository.save(destination);
        } catch (DataIntegrityViolationException exception) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_ID_DUPLICATED);
        }
    }

    private Destination createDestinationForSync(User user, DestinationSyncItemRequest request) {
        try {
            return Destination.create(user, request.destinationId(), DestinationAlias.from(request.alias()),
                Coordinate.of(request.latitude(), request.longitude()));
        } catch (GeneralException exception) {
            throw convertToDestinationSyncException(exception);
        }
    }

    private GeneralException convertToDestinationSyncException(GeneralException exception) {
        if (DestinationErrorStatus.DESTINATION_COORDINATE_INVALID.equals(exception.getCode())) {
            return new GeneralException(DestinationErrorStatus.DESTINATION_SYNC_COORDINATE_INVALID);
        }

        return new GeneralException(DestinationErrorStatus.DESTINATION_SYNC_REQUEST_INVALID);
    }
}
