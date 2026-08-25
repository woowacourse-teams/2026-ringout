package com.ringout.api.destination.service;

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
import com.ringout.api.destination.dto.response.DestinationSyncResponse.DestinationSyncItemResponse;
import com.ringout.api.destination.dto.response.DestinationUpdateResponse;
import com.ringout.api.destination.repository.DestinationRepository;
import com.ringout.api.destination.status.DestinationErrorStatus;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DestinationService {

    private final DestinationRepository destinationRepository;
    private final UserRepository userRepository;

    @Transactional
    public DestinationCreateResponse createDestination(Long userId, String alias, Double latitude, Double longitude) {
        validateAuthenticatedUserExists(userId);

        User user = userRepository.getReferenceById(userId);
        Destination destination = Destination.create(user, DestinationAlias.from(alias),
            Coordinate.of(latitude, longitude));

        return new DestinationCreateResponse(destinationRepository.save(destination).getId());
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
        validateSyncRequestClientDestinationIdsAreUsable(request.destinations());

        User user = userRepository.getReferenceById(userId);
        List<DestinationSyncItemResponse> syncedDestinations = new ArrayList<>();
        for (DestinationSyncItemRequest item : request.destinations()) {
            Destination destination = createDestinationForSync(user, item);
            Destination savedDestination = destinationRepository.save(destination);
            syncedDestinations.add(DestinationSyncItemResponse.of(item.clientDestinationId(), savedDestination));
        }

        return new DestinationSyncResponse(syncedDestinations);
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
        if (userId == null || !userRepository.existsById(userId)) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_UNAUTHORIZED);
        }
    }

    private void validateDestinationIdIsPositive(Long destinationId) {
        if (destinationId == null || destinationId <= 0) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_ID_INVALID);
        }
    }

    private void validateDestinationUpdateRequestBodyExists(DestinationUpdateRequest request) {
        if (request == null || request.hasNoUpdateField()) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_UPDATE_REQUEST_INVALID);
        }
    }

    private void validateDestinationSyncRequestBodyExists(DestinationSyncRequest request) {
        if (request == null || request.hasNoDestinationList()) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_SYNC_REQUEST_INVALID);
        }
    }

    private void validateSyncRequestClientDestinationIdsAreUsable(List<DestinationSyncItemRequest> destinations) {
        Set<Long> clientDestinationIds = new HashSet<>();
        for (DestinationSyncItemRequest destination : destinations) {
            validateDestinationSyncItemExists(destination);
            validateClientDestinationIdIsPositive(destination.clientDestinationId());
            validateClientDestinationIdIsUnique(clientDestinationIds, destination.clientDestinationId());
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

    private Destination createDestinationForSync(User user, DestinationSyncItemRequest request) {
        try {
            return Destination.create(user, DestinationAlias.from(request.alias()),
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
