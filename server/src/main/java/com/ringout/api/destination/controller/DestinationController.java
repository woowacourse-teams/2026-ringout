package com.ringout.api.destination.controller;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.destination.controller.docs.DestinationControllerApi;
import com.ringout.api.destination.dto.request.DestinationUpsertRequest;
import com.ringout.api.destination.dto.response.DestinationListResponse;
import com.ringout.api.destination.dto.response.DestinationUpsertResponse;
import com.ringout.api.destination.service.DestinationService;
import com.ringout.api.destination.status.DestinationSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/destinations")
public class DestinationController implements DestinationControllerApi {

    private final DestinationService destinationService;

    @Override
    @GetMapping
    public ResponseEntity<CustomResponse<DestinationListResponse>> findDestinations(
        @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        DestinationListResponse response = destinationService.getDestinations(customUserDetails.getUserId());

        return ResponseEntity.status(DestinationSuccessStatus.DESTINATIONS_FOUND.getHttpStatus())
            .body(CustomResponse.onSuccess(DestinationSuccessStatus.DESTINATIONS_FOUND, response));
    }

    @Override
    @PutMapping("/{destinationId}")
    public ResponseEntity<CustomResponse<DestinationUpsertResponse>> upsertDestination(
        @AuthenticationPrincipal CustomUserDetails customUserDetails, @PathVariable String destinationId,
        @RequestBody(required = false) DestinationUpsertRequest request
    ) {
        DestinationUpsertResponse response = destinationService.upsertDestination(customUserDetails.getUserId(),
            destinationId, request);

        return ResponseEntity.status(DestinationSuccessStatus.DESTINATION_UPSERTED.getHttpStatus())
            .body(CustomResponse.onSuccess(DestinationSuccessStatus.DESTINATION_UPSERTED, response));
    }

    @Override
    @DeleteMapping("/{destinationId}")
    public ResponseEntity<CustomResponse<Void>> deleteDestination(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @PathVariable String destinationId
    ) {
        destinationService.deleteDestination(customUserDetails.getUserId(), destinationId);

        return ResponseEntity.status(DestinationSuccessStatus.DESTINATION_DELETED.getHttpStatus())
            .body(CustomResponse.onSuccess(DestinationSuccessStatus.DESTINATION_DELETED, null));
    }
}
