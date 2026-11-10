package com.ringout.api.alarm.controller.docs;

import com.ringout.api.alarm.dto.request.AlarmUpsertRequest;
import com.ringout.api.alarm.dto.response.AlarmUpsertResponse;
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

@Tag(name = "알람 (Alarm)", description = "알람 API")
public interface AlarmControllerApi {

    @Operation(
        summary = "알람 생성 또는 전체 수정 (Upsert)",
        description = """
            인증된 사용자와 경로의 alarmId 조합으로 알람을 식별합니다.
            - 알람이 없으면 alarmVersion과 관계없이 새로 생성합니다.
            - 알람이 있고 요청 버전이 서버 버전보다 높으면 모든 설정을 요청 값으로 교체합니다.
            - 요청 버전이 서버 버전보다 낮거나 같으면 아무것도 바꾸지 않습니다.
            세 경우 모두 200 ALARM200을 반환하며, result는 요청 값이 아니라 서버에 최종 저장된 알람 정보입니다.
            서버는 전달받은 alarmVersion을 그대로 저장하며 임의로 증가시키지 않습니다.
            동일한 (사용자, alarmId)에 대한 동시 요청에서도 알람은 하나만 생성되며 최종 버전은 낮아지지 않습니다.
            alarmId는 사용자별로 고유하므로, 다른 사용자가 같은 alarmId를 쓰고 있어도 내 알람으로 새로 생성됩니다.
            부분 수정은 지원하지 않으므로 모든 필드가 필요합니다.
            """,
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "알람 생성·수정 또는 낮거나 같은 버전이라 변경 없음. 모두 서버에 최종 저장된 정보를 반환합니다.",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "ALARM200",
                      "message": "알람 동기화에 성공했습니다.",
                      "result": {
                        "alarmId": "alarm-001",
                        "time": "07:00",
                        "dayOfWeek": ["MONDAY", "FRIDAY"],
                        "repeatTime": 5,
                        "destinationName": "세종대학교",
                        "destinationLatitude": 37.5502,
                        "destinationLongitude": 127.0738,
                        "alarmVersion": 1
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = """
                요청 형식 오류.
                JSON이 깨졌거나 필드 값을 해당 자료형으로 해석할 수 없으면(예: time이 HH:mm이 아님, 정의되지 않은 요일, dayOfWeek가 배열이 아님, alarmVersion이 "abc") 요청 바인딩 단계에서 COMMON400을 반환합니다.
                숫자로 해석되는 문자열("2")은 숫자로 변환되고, 정수 필드(repeatTime, alarmVersion)에 들어온 소수는 소수점 이하가 버려진 값으로 처리됩니다.
                바인딩 이후 값 검증에 실패하면 ALARM400을 반환합니다.
                """,
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(name = "malformedRequestBody", value = """
                        {
                          "isSuccess": false,
                          "code": "COMMON400",
                          "message": "잘못된 요청입니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "invalidAlarmId", value = """
                        {
                          "isSuccess": false,
                          "code": "ALARM400",
                          "message": "알람 ID가 올바르지 않습니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "invalidAlarmRequest", value = """
                        {
                          "isSuccess": false,
                          "code": "ALARM400",
                          "message": "알람 요청 정보가 올바르지 않습니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "invalidAlarmVersion", value = """
                        {
                          "isSuccess": false,
                          "code": "ALARM400",
                          "message": "알람 버전 값이 올바르지 않습니다.",
                          "result": null
                        }
                        """)
                }
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "인증되지 않은 사용자",
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(name = "invalidToken", value = """
                        {
                          "isSuccess": false,
                          "code": "AUTH401",
                          "message": "인증되지 않은 사용자입니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "userNotFound", value = """
                        {
                          "isSuccess": false,
                          "code": "ALARM401",
                          "message": "인증되지 않은 사용자입니다.",
                          "result": null
                        }
                        """)
                }
            )
        )
    })
    ResponseEntity<CustomResponse<AlarmUpsertResponse>> upsertAlarm(
        @Parameter(hidden = true)
        CustomUserDetails customUserDetails,
        @Parameter(
            description = "클라이언트가 생성한 사용자별 알람 ID. 1자 이상 36자 이하이며 공백을 포함할 수 없습니다.",
            required = true,
            schema = @Schema(type = "string", minLength = 1, maxLength = 36, example = "alarm-001")
        )
        @PathVariable
        String alarmId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "생성하거나 전체 수정할 알람 설정. 부분 수정은 지원하지 않으므로 모든 필드가 필요합니다.",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AlarmUpsertRequest.class),
                examples = @ExampleObject(value = """
                    {
                      "time": "07:00",
                      "dayOfWeek": ["MONDAY", "FRIDAY"],
                      "repeatTime": 5,
                      "destinationName": "세종대학교",
                      "destinationLatitude": 37.5502,
                      "destinationLongitude": 127.0738,
                      "alarmVersion": 1
                    }
                    """)
            )
        )
        AlarmUpsertRequest request
    );
}
