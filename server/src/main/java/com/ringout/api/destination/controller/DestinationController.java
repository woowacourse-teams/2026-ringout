package com.ringout.api.destination.controller;

import com.ringout.api.auth.CustomUserDetails;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.destination.controller.docs.DestinationControllerApi;
import com.ringout.api.destination.dto.request.DestinationCreateRequest;
import com.ringout.api.destination.dto.response.DestinationCreateResponse;
import com.ringout.api.destination.service.DestinationService;
import com.ringout.api.destination.status.DestinationErrorStatus;
import com.ringout.api.destination.status.DestinationSuccessStatus;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    @PostMapping
    public ResponseEntity<CustomResponse<DestinationCreateResponse>> createDestination(
        @Parameter(hidden = true)
        @RequestAttribute(value = "customUserDetails", required = false) CustomUserDetails customUserDetails,
        @Valid @RequestBody DestinationCreateRequest request
    ) {
        if (customUserDetails == null) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_UNAUTHORIZED);
        }

        DestinationCreateResponse response = destinationService.createDestination(customUserDetails.userId(),
            request.alias(), request.latitude(), request.longitude());

        return ResponseEntity.status(DestinationSuccessStatus.DESTINATION_CREATED.getHttpStatus())
            .body(CustomResponse.of(DestinationSuccessStatus.DESTINATION_CREATED, response));
    }
}
