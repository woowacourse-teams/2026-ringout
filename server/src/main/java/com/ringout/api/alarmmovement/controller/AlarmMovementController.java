package com.ringout.api.alarmmovement.controller;

import com.ringout.api.alarmmovement.controller.docs.AlarmMovementControllerApi;
import com.ringout.api.alarmmovement.dto.request.AlarmMovementRequest;
import com.ringout.api.alarmmovement.dto.response.AlarmMovementResponse;
import com.ringout.api.alarmmovement.service.AlarmMovementService;
import com.ringout.api.alarmmovement.status.AlarmMovementSuccessStatus;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms")
public class AlarmMovementController implements AlarmMovementControllerApi {

    private final AlarmMovementService alarmMovementService;

    @Override
    @PostMapping("/{roomId}/movements")
    public ResponseEntity<CustomResponse<AlarmMovementResponse>> changeMovement(
        @AuthenticationPrincipal CustomUserDetails customUserDetails, @PathVariable Long roomId,
        @RequestBody AlarmMovementRequest request) {
        AlarmMovementResponse response = alarmMovementService.changeMovement(customUserDetails.getUserId(), roomId,
            request);

        return ResponseEntity.status(AlarmMovementSuccessStatus.MOVEMENT_CHANGED.getHttpStatus())
            .body(CustomResponse.onSuccess(AlarmMovementSuccessStatus.MOVEMENT_CHANGED, response));
    }
}
