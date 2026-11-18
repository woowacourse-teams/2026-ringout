package com.ringout.api.alarm.controller;

import com.ringout.api.alarm.controller.docs.AlarmControllerApi;
import com.ringout.api.alarm.dto.request.AlarmUpsertRequest;
import com.ringout.api.alarm.dto.response.AlarmListResponse;
import com.ringout.api.alarm.dto.response.AlarmResponse;
import com.ringout.api.alarm.service.AlarmService;
import com.ringout.api.alarm.status.AlarmSuccessStatus;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
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
@RequestMapping("/alarms")
public class AlarmController implements AlarmControllerApi {

    private final AlarmService alarmService;

    @Override
    @GetMapping
    public ResponseEntity<CustomResponse<AlarmListResponse>> findAlarms(
        @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        AlarmListResponse response = alarmService.getAlarms(customUserDetails.getUserId());

        return ResponseEntity.status(AlarmSuccessStatus.ALARMS_FOUND.getHttpStatus())
            .body(CustomResponse.onSuccess(AlarmSuccessStatus.ALARMS_FOUND, response));
    }

    @Override
    @PutMapping("/{alarmId}")
    public ResponseEntity<CustomResponse<AlarmResponse>> upsertAlarm(
        @AuthenticationPrincipal CustomUserDetails customUserDetails, @PathVariable String alarmId,
        @RequestBody(required = false) AlarmUpsertRequest request
    ) {
        AlarmResponse response = alarmService.upsertAlarm(customUserDetails.getUserId(), alarmId, request);

        return ResponseEntity.status(AlarmSuccessStatus.ALARM_UPSERTED.getHttpStatus())
            .body(CustomResponse.onSuccess(AlarmSuccessStatus.ALARM_UPSERTED, response));
    }

    @Override
    @DeleteMapping("/{alarmId}")
    public ResponseEntity<CustomResponse<Void>> deleteAlarm(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @PathVariable String alarmId
    ) {
        alarmService.deleteAlarm(customUserDetails.getUserId(), alarmId);

        return ResponseEntity.status(AlarmSuccessStatus.ALARM_DELETED.getHttpStatus())
            .body(CustomResponse.onSuccess(AlarmSuccessStatus.ALARM_DELETED, null));
    }
}
