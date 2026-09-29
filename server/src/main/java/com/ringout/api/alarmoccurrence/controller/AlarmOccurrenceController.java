package com.ringout.api.alarmoccurrence.controller;

import com.ringout.api.alarmoccurrence.controller.docs.AlarmOccurrenceControllerApi;
import com.ringout.api.alarmoccurrence.dto.request.AlarmOccurrenceEventRequest;
import com.ringout.api.alarmoccurrence.dto.request.AlarmOccurrenceStartRequest;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrenceDetailResponse;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrenceStartResult;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrencesResponse;
import com.ringout.api.alarmoccurrence.service.AlarmOccurrenceService;
import com.ringout.api.alarmoccurrence.status.AlarmOccurrenceSuccessStatus;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/alarm-occurrences")
public class AlarmOccurrenceController implements AlarmOccurrenceControllerApi {

    private final AlarmOccurrenceService alarmOccurrenceService;

    @Override
    @GetMapping
    public ResponseEntity<CustomResponse<AlarmOccurrencesResponse>> getAlarmOccurrences(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        AlarmOccurrencesResponse response = alarmOccurrenceService.getAlarmOccurrences(
            customUserDetails.getUserId(), date);

        return ResponseEntity.status(AlarmOccurrenceSuccessStatus.ALARM_OCCURRENCE_OK.getHttpStatus())
            .body(CustomResponse.onSuccess(AlarmOccurrenceSuccessStatus.ALARM_OCCURRENCE_OK, response));
    }

    @Override
    @PostMapping
    public ResponseEntity<CustomResponse<AlarmOccurrenceDetailResponse>> startAlarmOccurrence(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @RequestBody AlarmOccurrenceStartRequest request) {
        AlarmOccurrenceStartResult result = alarmOccurrenceService.startAlarmOccurrence(
            customUserDetails.getUserId(), request);
        AlarmOccurrenceSuccessStatus status = result.created()
            ? AlarmOccurrenceSuccessStatus.ALARM_OCCURRENCE_CREATED
            : AlarmOccurrenceSuccessStatus.ALARM_OCCURRENCE_OK;

        return ResponseEntity.status(status.getHttpStatus())
            .body(CustomResponse.onSuccess(status, result.response()));
    }

    @Override
    @PatchMapping("/{alarmOccurrenceId}")
    public ResponseEntity<CustomResponse<AlarmOccurrenceDetailResponse>> recordAlarmOccurrenceEvent(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @PathVariable String alarmOccurrenceId,
        @RequestBody AlarmOccurrenceEventRequest request) {
        AlarmOccurrenceDetailResponse response = alarmOccurrenceService.recordAlarmOccurrenceEvent(
            customUserDetails.getUserId(), alarmOccurrenceId, request);

        return ResponseEntity.status(AlarmOccurrenceSuccessStatus.ALARM_OCCURRENCE_OK.getHttpStatus())
            .body(CustomResponse.onSuccess(AlarmOccurrenceSuccessStatus.ALARM_OCCURRENCE_OK, response));
    }
}
