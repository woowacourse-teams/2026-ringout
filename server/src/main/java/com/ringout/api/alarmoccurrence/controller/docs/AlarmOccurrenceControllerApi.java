package com.ringout.api.alarmoccurrence.controller.docs;

import com.ringout.api.alarmoccurrence.dto.request.AlarmOccurrenceEventRequest;
import com.ringout.api.alarmoccurrence.dto.request.AlarmOccurrenceStartRequest;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrenceDetailResponse;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrencesResponse;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.SwaggerConfig;
import com.ringout.api.config.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import org.springframework.http.ResponseEntity;

@Tag(name = "알람 사용 기록 (Alarm Occurrence)", description = "개인 알람 사용 기록 API")
public interface AlarmOccurrenceControllerApi {

    @Operation(summary = "개인 알람 사용 기록 조회",
        description = "선택한 월의 알람 실행 기록을 날짜별로 묶어 조회합니다. 각 날짜에는 알람 실행별 울림 타임라인"
            + "(울림~끔, 도착·강제 종료)이 포함됩니다. 조회 범위는 그 달 1일 00:00:00부터 다음 달 1일 00:00:00 직전까지이며, "
            + "각 실행은 최초 울림 시각을 서버 시간대로 환산한 날짜에 묶입니다. 재울림·종료가 다음 날이나 다음 달로 넘어가도 "
            + "최초 울림 날짜에 포함합니다. days에는 그 달의 모든 날짜가 날짜 오름차순으로 들어가며, 기록이 없는 날짜는 "
            + "occurrences가 빈 배열입니다. occurrences는 최초 울림 시각 오름차순, ringings는 울림 시각 오름차순입니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponses({
        @ApiResponse(responseCode = "200",
            description = "알람 사용 기록 조회 성공. days에는 그 달의 모든 날짜가 들어가며 아래 예시는 8월 17~18일만 발췌",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess":true,"code":"COMMON200","message":"정상적인 요청입니다.","result":{
                "month":"2026-08","days":[
                {"date":"2026-08-17","occurrences":[{"alarmId":"8f2c1d4e-7a9b-4c3d-9e1f-2a3b4c5d6e7f",
                "alarmOccurrenceId":"5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f","alarmTime":"06:30","ringings":[
                {"type":"INITIAL","eventId":null,"ringingAt":"2026-08-17T06:30:00+09:00","dismissedAt":"2026-08-17T06:31:02+09:00"},
                {"type":"REPEAT","eventId":"b1e2c3d4-0000-4000-8000-000000000001","ringingAt":"2026-08-17T06:35:00+09:00","dismissedAt":null}],
                "endType":"ARRIVED","endedAt":"2026-08-17T06:53:10+09:00"}]},
                {"date":"2026-08-18","occurrences":[]}]}}
                """))),
        @ApiResponse(responseCode = "400", description = "month 누락 또는 형식(YYYY-MM) 오류. 예: 2026-8, 2026-13, 2026-08-17",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess":false,"code":"COMMON400","message":"잘못된 요청입니다.","result":null}
                """))),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    ResponseEntity<CustomResponse<AlarmOccurrencesResponse>> getAlarmOccurrences(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        @Parameter(description = "조회할 연월 (YYYY-MM)", example = "2026-08", required = true,
            schema = @Schema(type = "string", pattern = "^\\d{4}-(0[1-9]|1[0-2])$")) YearMonth month
    );

    @Operation(summary = "알람 실행 시작",
        description = "알람이 처음 울린 시점에 실행 기록을 생성하고 서버가 실행 식별자를 발급합니다. "
            + "최초 울림 시각은 기기가 보낸 startedAt으로 저장합니다. "
            + "같은 alarmId와 scheduledAt으로 다시 요청하면 기존 기록을 반환합니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "알람 실행 기록 생성", content = @Content(
            mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess":true,"code":"COMMON201_1","message":"알람 실행 기록이 저장되었습니다.","result":{
                "alarmOccurrenceId":"5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f","startedAt":"2026-09-23T07:00:02+09:00",
                "ringings":[{"type":"INITIAL","eventId":null,"ringingAt":"2026-09-23T07:00:02+09:00","dismissedAt":null}],
                "arrivedAt":null,"forceEndedAt":null}}
                """))),
        @ApiResponse(responseCode = "200", description = "이미 생성된 알람 실행 기록 반환 (재시도)"),
        @ApiResponse(responseCode = "400",
            description = "alarmId, scheduledAt, startedAt 누락·형식 오류 또는 startedAt이 서버 시각보다 1분 넘게 미래"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    ResponseEntity<CustomResponse<AlarmOccurrenceDetailResponse>> startAlarmOccurrence(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        AlarmOccurrenceStartRequest request
    );

    @Operation(summary = "알람 실행 이벤트 저장",
        description = "진행 중인 알람 실행에 재울림, 울림 끔, 도착, 강제 종료 이벤트를 병합합니다. "
            + "모든 시각은 기기에서 이벤트가 발생한 시각이며, eventId를 보낼 때는 ringingAt을 함께 보냅니다. "
            + "같은 요청을 다시 보내도 결과가 같습니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "알람 실행 이벤트 저장 성공", content = @Content(
            mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess":true,"code":"COMMON200","message":"정상적인 요청입니다.","result":{
                "alarmOccurrenceId":"5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f","startedAt":"2026-09-23T07:00:02+09:00",
                "ringings":[
                {"type":"INITIAL","eventId":null,"ringingAt":"2026-09-23T07:00:02+09:00","dismissedAt":"2026-09-23T07:01:10+09:00"},
                {"type":"REPEAT","eventId":"b1e2c3d4-0000-4000-8000-000000000001","ringingAt":"2026-09-23T07:05:00+09:00","dismissedAt":"2026-09-23T07:05:40+09:00"}],
                "arrivedAt":"2026-09-23T07:48:10+09:00","forceEndedAt":null}}
                """))),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 실행 식별자 또는 이벤트 요청 "
            + "(eventId와 ringingAt 불일치, 서버 시각보다 1분 넘게 미래인 시각, 이전 이벤트보다 빠른 시각)"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        @ApiResponse(responseCode = "403", description = "다른 사용자의 실행 기록"),
        @ApiResponse(responseCode = "404", description = "실행 기록이 없음"),
        @ApiResponse(responseCode = "409", description = "이미 종료된 실행에 대한 변경 요청")
    })
    ResponseEntity<CustomResponse<AlarmOccurrenceDetailResponse>> recordAlarmOccurrenceEvent(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        @Parameter(description = "알람 실행 식별자 (UUID)", example = "5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f")
        String alarmOccurrenceId,
        AlarmOccurrenceEventRequest request
    );
}
