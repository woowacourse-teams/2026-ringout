package com.ringout.api.pushalarm.controller;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.pushalarm.controller.docs.DeviceTokenApi;
import com.ringout.api.pushalarm.dto.request.DeviceTokenDeleteRequest;
import com.ringout.api.pushalarm.dto.request.DeviceTokenRegisterRequest;
import com.ringout.api.pushalarm.service.DeviceTokenService;
import com.ringout.api.pushalarm.status.PushalarmSuccessStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/device-tokens")
public class DeviceTokenController implements DeviceTokenApi {

    private final DeviceTokenService deviceTokenService;

    @Override
    @PostMapping
    public ResponseEntity<CustomResponse<Void>> register(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @RequestBody @Valid DeviceTokenRegisterRequest request
    ) {
        deviceTokenService.register(customUserDetails.getUserId(), request);

        return ResponseEntity.status(PushalarmSuccessStatus.DEVICE_TOKEN_REGISTERED.getHttpStatus())
            .body(CustomResponse.onSuccess(PushalarmSuccessStatus.DEVICE_TOKEN_REGISTERED, null));
    }

    @Override
    @DeleteMapping
    public ResponseEntity<CustomResponse<Void>> unregister(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @RequestBody @Valid DeviceTokenDeleteRequest request
    ) {
        deviceTokenService.unregister(customUserDetails.getUserId(), request);

        return ResponseEntity.status(PushalarmSuccessStatus.DEVICE_TOKEN_DELETED.getHttpStatus())
            .body(CustomResponse.onSuccess(PushalarmSuccessStatus.DEVICE_TOKEN_DELETED, null));
    }
}
