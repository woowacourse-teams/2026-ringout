package com.ringout.api.destination.controller;

import com.ringout.api.auth.CustomUserDetails;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.destination.controller.docs.DestinationControllerApi;
import com.ringout.api.destination.dto.request.DestinationCreateRequest;
import com.ringout.api.destination.dto.request.DestinationUpdateRequest;
import com.ringout.api.destination.dto.response.DestinationCreateResponse;
import com.ringout.api.destination.dto.response.DestinationResponse;
import com.ringout.api.destination.dto.response.DestinationUpdateResponse;
import com.ringout.api.destination.service.DestinationService;
import com.ringout.api.destination.status.DestinationSuccessStatus;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
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
    public ResponseEntity<CustomResponse<List<DestinationResponse>>> findDestinations(
        @Parameter(hidden = true)
        @RequestAttribute(value = "customUserDetails", required = false) CustomUserDetails customUserDetails
    ) {
        List<DestinationResponse> response = destinationService.getDestinations(customUserDetails.userId());

        return ResponseEntity.status(DestinationSuccessStatus.DESTINATIONS_FOUND.getHttpStatus())
            .body(CustomResponse.of(DestinationSuccessStatus.DESTINATIONS_FOUND, response));
    }

    @Override
    @PostMapping
    public ResponseEntity<CustomResponse<DestinationCreateResponse>> createDestination(
        @Parameter(hidden = true)
        @RequestAttribute(value = "customUserDetails", required = false) CustomUserDetails customUserDetails,
        @Valid @RequestBody DestinationCreateRequest request
    ) {
        DestinationCreateResponse response = destinationService.createDestination(customUserDetails.userId(),
            request.alias(), request.latitude(), request.longitude());

        return ResponseEntity.status(DestinationSuccessStatus.DESTINATION_CREATED.getHttpStatus())
            .body(CustomResponse.of(DestinationSuccessStatus.DESTINATION_CREATED, response));
    }

    @Override
    @PatchMapping("/{destinationId}")
    public ResponseEntity<CustomResponse<DestinationUpdateResponse>> updateDestination(
        @Parameter(hidden = true)
        @RequestAttribute(value = "customUserDetails", required = false) CustomUserDetails customUserDetails,
        @PathVariable Long destinationId,
        @RequestBody(required = false) DestinationUpdateRequest request
    ) {
        DestinationUpdateResponse response = destinationService.updateDestination(customUserDetails.userId(),
            destinationId, request);

        return ResponseEntity.status(DestinationSuccessStatus.DESTINATION_UPDATED.getHttpStatus())
            .body(CustomResponse.of(DestinationSuccessStatus.DESTINATION_UPDATED, response));
    }

    @Override
    @DeleteMapping("/{destinationId}")
    public ResponseEntity<CustomResponse<Void>> deleteDestination(
        @Parameter(hidden = true)
        @RequestAttribute(value = "customUserDetails", required = false) CustomUserDetails customUserDetails,
        @PathVariable Long destinationId
    ) {
        destinationService.deleteDestination(customUserDetails.userId(), destinationId);

        return ResponseEntity.status(DestinationSuccessStatus.DESTINATION_DELETED.getHttpStatus())
            .body(CustomResponse.of(DestinationSuccessStatus.DESTINATION_DELETED, null));
    }
}
