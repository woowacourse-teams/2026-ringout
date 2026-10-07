package com.ringout.api.alarmmovement.controller.docs;

import com.ringout.api.alarmmovement.dto.request.AlarmMovementRequest;
import com.ringout.api.alarmmovement.dto.response.AlarmMovementResponse;
import com.ringout.api.alarmmovement.dto.response.MemberMovementsResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "이동 상태 (Movement)", description = "모임 회원 이동 상태 API")
public interface AlarmMovementControllerApi {

    @Operation(
        summary = "이동 행동 처리",
        description = """
            path roomId의 활성 모임에 현재 참여 중인 인증 사용자가 알람 실행의 이동 행동을 처리합니다.
            alarmOccurrenceId는 알람 실행 API가 발급한 AlarmOccurrence UUID 식별자이며 action은 START_MOVEMENT, GIVE_UP, ARRIVE 중 하나입니다.
            응답 status는 요청 처리 후 MOVEMENT_STARTED, GAVE_UP, ARRIVED 중 해당 상태입니다.
            """,
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "이동 행동 처리 성공",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "startMovement", value = """
                    {"isSuccess": true, "code": "MOVEMENT200", "message": "이동 상태 변경에 성공했습니다.", "result": {"status": "MOVEMENT_STARTED"}}
                    """),
                @ExampleObject(name = "giveUp", value = """
                    {"isSuccess": true, "code": "MOVEMENT200", "message": "이동 상태 변경에 성공했습니다.", "result": {"status": "GAVE_UP"}}
                    """),
                @ExampleObject(name = "arrive", value = """
                    {"isSuccess": true, "code": "MOVEMENT200", "message": "이동 상태 변경에 성공했습니다.", "result": {"status": "ARRIVED"}}
                    """)
            })
        ),
        @ApiResponse(
            responseCode = "400",
            description = "필수 입력 누락 또는 값 검증 실패; 본문 누락·잘못된 JSON·enum 변환 오류는 COMMON400",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "alarmOccurrenceIdRequired", value = """
                    {"isSuccess": false, "code": "MOVEMENT400", "message": "알람 실행 ID가 필요합니다.", "result": null}
                    """),
                @ExampleObject(name = "alarmOccurrenceIdInvalid", value = """
                    {"isSuccess": false, "code": "MOVEMENT400", "message": "알람 실행 ID가 올바르지 않습니다.", "result": null}
                    """),
                @ExampleObject(name = "actionRequired", value = """
                    {"isSuccess": false, "code": "MOVEMENT400", "message": "이동 행동이 필요합니다.", "result": null}
                    """),
                @ExampleObject(name = "missingBodyOrUnreadableJsonUnknownActionOrInvalidLong", value = """
                    {"isSuccess": false, "code": "COMMON400", "message": "잘못된 요청입니다.", "result": null}
                    """)
            })
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Authorization 토큰 누락·무효·만료",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "unauthorized", value = """
                    {"isSuccess": false, "code": "AUTH401", "message": "인증되지 않은 사용자입니다.", "result": null}
                    """),
                @ExampleObject(name = "expiredToken", value = """
                    {"isSuccess": false, "code": "AUTH401", "message": "액세스 토큰이 만료되었습니다.", "result": null}
                    """)
            })
        ),
        @ApiResponse(
            responseCode = "403",
            description = "요청한 roomId의 현재 참여자가 아니거나, 대상 알람 실행의 소유자가 아님",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "notRoomMember", value = """
                    {"isSuccess": false, "code": "MOVEMENT403", "message": "해당 모임의 회원이 아닙니다.", "result": null}
                    """),
                @ExampleObject(name = "notAlarmOccurrenceOwner", value = """
                    {"isSuccess": false, "code": "MOVEMENT403", "message": "해당 알람에 대한 이동 상태를 변경할 권한이 없습니다.", "result": null}
                    """)
            })
        ),
        @ApiResponse(
            responseCode = "404",
            description = "활성 모임 방 또는 알람 실행이 없음",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "roomNotFound", value = """
                    {"isSuccess": false, "code": "ROOM404", "message": "존재하지 않는 모임 방입니다.", "result": null}
                    """),
                @ExampleObject(name = "alarmOccurrenceNotFound", value = """
                    {"isSuccess": false, "code": "ALARM404", "message": "존재하지 않는 알람 입니다.", "result": null}
                    """)
            })
        ),
        @ApiResponse(
            responseCode = "409",
            description = "이미 이동을 시작한 알람에 다시 START_MOVEMENT를 요청하거나, 이미 GAVE_UP 또는 ARRIVED 상태인 알람에 행동을 요청함",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "alreadyStarted", value = """
                    {"isSuccess": false, "code": "MOVEMENT409", "message": "이미 이동을 시작한 상태입니다.", "result": null}
                    """),
                @ExampleObject(name = "alreadyGaveUp", value = """
                    {"isSuccess": false, "code": "MOVEMENT409", "message": "이미 이동을 포기한 알람입니다.", "result": null}
                    """),
                @ExampleObject(name = "alreadyArrived", value = """
                    {"isSuccess": false, "code": "MOVEMENT409", "message": "이미 목적지에 도착한 알람입니다.", "result": null}
                    """)
            })
        ),
        @ApiResponse(
            responseCode = "500",
            description = "예상하지 못한 서버 오류",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "unexpectedError", value = """
                    {"isSuccess": false, "code": "COMMON500", "message": "서버 에러, 관리자에게 문의 바랍니다.", "result": "예외 메시지"}
                    """))
        )
    })
    ResponseEntity<CustomResponse<AlarmMovementResponse>> changeMovement(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        @Parameter(description = "이동 행동을 요청할 활성 모임 방 ID", required = true, example = "1")
        @PathVariable Long roomId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "알람 실행 UUID와 수행할 이동 행동",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AlarmMovementRequest.class),
                examples = @ExampleObject(value = """
                    {"alarmOccurrenceId":"5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f", "action":"START_MOVEMENT"}
                    """)
            )
        )
        @RequestBody AlarmMovementRequest request
    );

    @Operation(
        summary = "모임 회원 이동 상태 조회",
        description = """
            인증된 현재 모임 참여자의 상태를 조회합니다. 목록은 탈퇴·추방되지 않은 활성 참여자만 포함하며 닉네임 첫 글자 기준 한글(가–힣), 영문(A–Z, a–z), 그 외 문자 순으로 정렬하고 각 그룹은 문자열 오름차순으로 정렬합니다. 영문 대소문자는 구분합니다.
            각 회원은 서버 기준 오늘 00:00 이상 다음 날 00:00 미만에 실제 알람이 시작된 실행만 대상으로 상태를 반환합니다. 대상 실행 중 startedAt이 가장 최근인 것을 선택하며, 시작 시각이 같으면 ID가 큰 실행을 선택합니다. 오늘 알람 실행이 없으면 IDLE, 알람 실행은 있으나 이동 시작 전이면 ALARM_TRIGGERED입니다. 이동 시작 후 2분 미만이면 MOVEMENT_STARTED, 2분 이상이면 MOVING이며 종료 상태는 GAVE_UP 또는 ARRIVED입니다.
            """,
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "모임 회원 상태 조회 성공",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {
                  "isSuccess": true,
                  "code": "ROOM200",
                  "message": "모임 회원 상태 조회에 성공했습니다.",
                  "result": {
                    "members": [
                      {"userId": 3, "nickname": "가나다", "profileImageUrl": null, "status": "IDLE"},
                      {"userId": 1, "nickname": "Alice", "profileImageUrl": "https://s3.example.com/profiles/1.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=example", "status": "MOVEMENT_STARTED"},
                      {"userId": 2, "nickname": "@runner", "profileImageUrl": null, "status": "ARRIVED"}
                    ]
                  }
                }
                """))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "roomId가 0 이하이면 ROOM400; Long 변환 실패는 COMMON400",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "roomIdMustBePositive", value = """
                    {"isSuccess": false, "code": "ROOM400", "message": "모임 방 ID는 양수여야 합니다.", "result": null}
                    """),
                @ExampleObject(name = "roomIdConversionFailed", value = """
                    {"isSuccess": false, "code": "COMMON400", "message": "잘못된 요청입니다.", "result": null}
                    """)
            })
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Authorization 토큰 누락·무효·만료",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "unauthorized", value = """
                    {"isSuccess": false, "code": "AUTH401", "message": "인증되지 않은 사용자입니다.", "result": null}
                    """),
                @ExampleObject(name = "expiredToken", value = """
                    {"isSuccess": false, "code": "AUTH401", "message": "액세스 토큰이 만료되었습니다.", "result": null}
                    """)
            })
        ),
        @ApiResponse(
            responseCode = "403",
            description = "요청 사용자가 현재 해당 모임에 참여 중이 아님",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess": false, "code": "ROOM403", "message": "모임 회원 상태를 조회할 권한이 없습니다.", "result": null}
                """))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "존재하지 않거나 삭제된 모임 방",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess": false, "code": "ROOM404", "message": "존재하지 않는 모임 방입니다.", "result": null}
                """))
        )
    })
    ResponseEntity<CustomResponse<MemberMovementsResponse>> getMemberMovements(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        @Parameter(description = "회원 상태를 조회할 모임 방 ID", required = true, example = "1")
        @PathVariable Long roomId
    );
}
