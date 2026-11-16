package com.ringout.api.forest.controller.docs;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.SwaggerConfig;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.forest.dto.request.ForestOnboardingCompleteRequest;
import com.ringout.api.forest.dto.request.ForestTreePlantRequest;
import com.ringout.api.forest.dto.response.ForestOnboardingResponse;
import com.ringout.api.forest.dto.response.ForestTreeResponse;
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

@Tag(name = "숲 (Forest)", description = "숲 키우기 API")
public interface ForestControllerApi {

    @Operation(
        summary = "숲 온보딩 완료",
        description = """
            인증된 사용자의 숲 온보딩 완료 시각을 저장합니다.
            - 완료 시각이 없으면 요청 시각을 저장하고 201을 반환합니다.
            - 이미 완료 시각이 있으면 요청 값을 반영하지 않고 처음 저장된 시각을 200으로 반환합니다.
            result는 요청 값이 아니라 서버에 최종 저장된 완료 시각입니다.
            완료 시각은 서버 시간대로 변환해 초 단위까지 저장합니다.
            동일한 사용자의 동시 요청에서도 완료 시각은 하나만 저장되며, 409를 반환하지 않습니다.
            """,
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "온보딩 완료 시각 신규 저장",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "FOREST201",
                      "message": "숲 온보딩 완료에 성공했습니다.",
                      "result": {
                        "onboardingCompletedAt": "2026-11-10T08:00:00+09:00"
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "200",
            description = "이미 온보딩을 완료함. 변경 없이 서버에 저장된 완료 시각을 반환합니다.",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "FOREST200",
                      "message": "숲 온보딩 동기화에 성공했습니다.",
                      "result": {
                        "onboardingCompletedAt": "2026-11-10T08:00:00+09:00"
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = """
                요청 형식 오류.
                JSON이 깨졌거나 completedAt이 ISO-8601 offset 시각 형식이 아니면 요청 바인딩 단계에서 COMMON400을 반환합니다.
                요청 본문이나 completedAt이 없으면 FOREST400을 반환합니다.
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
                    @ExampleObject(name = "missingCompletedAt", value = """
                        {
                          "isSuccess": false,
                          "code": "FOREST400",
                          "message": "온보딩 요청 정보가 올바르지 않습니다.",
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
                          "code": "FOREST401",
                          "message": "인증되지 않은 사용자입니다.",
                          "result": null
                        }
                        """)
                }
            )
        )
    })
    ResponseEntity<CustomResponse<ForestOnboardingResponse>> completeOnboarding(
        @Parameter(hidden = true)
        CustomUserDetails customUserDetails,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "숲 온보딩 완료 시각",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ForestOnboardingCompleteRequest.class),
                examples = @ExampleObject(value = """
                    {
                      "completedAt": "2026-11-10T08:00:00+09:00"
                    }
                    """)
            )
        )
        ForestOnboardingCompleteRequest request
    );

    @Operation(
        summary = "나무 심기",
        description = """
            인증된 사용자와 경로의 growthCycle 조합으로 나무를 식별해 저장합니다.
            - 같은 회차의 나무가 없으면 빈 칸(0~15) 중 하나를 무작위로 배정해 저장하고 201을 반환합니다.
            - 같은 회차의 나무가 있으면 요청 값을 반영하지 않고, 칸도 새로 배정하지 않으며 저장된 나무를 200으로 반환합니다.
            result는 요청 값이 아니라 서버에 최종 저장된 나무 정보입니다.
            새 회차를 저장하기 전에 서버의 도착 성공 기록으로 획득 자격을 검증합니다.
            - 도착 성공은 삭제되지 않은 알람 실행 기록 중 종료 유형이 ARRIVED인 기록이며, 서버 시간대 날짜 기준으로 같은 날은 하루로 셉니다.
            - 서로 다른 성공 날짜 수가 growthCycle × 5 이상이고, plantedAt의 날짜가 growthCycle × 5번째 성공 날짜와 같아야 합니다.
            - 조건을 만족하지 않으면 409(FOREST409)를 반환합니다. 재요청해도 해결되지 않는 정합성 오류입니다.
            plantedAt은 서버 시간대로 변환해 초 단위까지 저장합니다.
            같은 회차의 동시 요청에서도 나무는 하나만 저장되며, 서로 다른 회차의 동시 요청에서도 같은 칸이 배정되지 않습니다.
            """,
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "나무 신규 저장",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "FOREST201",
                      "message": "나무 저장에 성공했습니다.",
                      "result": {
                        "growthCycle": 1,
                        "kind": "pine",
                        "slotIndex": 7,
                        "plantedAt": "2026-11-05T07:48:10+09:00",
                        "acknowledgedAt": null
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "200",
            description = "같은 회차의 나무가 이미 있음. 변경 없이 서버에 저장된 나무를 반환합니다.",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "FOREST200",
                      "message": "나무 동기화에 성공했습니다.",
                      "result": {
                        "growthCycle": 1,
                        "kind": "oak",
                        "slotIndex": 3,
                        "plantedAt": "2026-11-05T07:48:10+09:00",
                        "acknowledgedAt": null
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = """
                요청 오류.
                JSON이 깨졌거나 plantedAt이 ISO-8601 offset 시각 형식이 아니거나 kind가 문자열이 아니면 요청 바인딩 단계에서 COMMON400을 반환합니다.
                growthCycle이 1~16의 정수가 아니면 FOREST400(나무 회차)을 반환합니다.
                요청 본문이 없거나, kind가 없거나 허용 값이 아니거나, plantedAt이 없으면 FOREST400(나무 요청 정보)을 반환합니다.
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
                    @ExampleObject(name = "invalidGrowthCycle", value = """
                        {
                          "isSuccess": false,
                          "code": "FOREST400",
                          "message": "나무 회차가 올바르지 않습니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "invalidTreeRequest", value = """
                        {
                          "isSuccess": false,
                          "code": "FOREST400",
                          "message": "나무 요청 정보가 올바르지 않습니다.",
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
                          "code": "FOREST401",
                          "message": "인증되지 않은 사용자입니다.",
                          "result": null
                        }
                        """)
                }
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "서버의 도착 성공 기록으로 확인한 획득 자격과 요청이 일치하지 않음",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "FOREST409",
                      "message": "나무 획득 기록이 서버 기록과 일치하지 않습니다.",
                      "result": null
                    }
                    """)
            )
        )
    })
    ResponseEntity<CustomResponse<ForestTreeResponse>> plantTree(
        @Parameter(hidden = true)
        CustomUserDetails customUserDetails,
        @Parameter(
            description = "나무 획득 회차 (1~16). n회차는 도착 성공 5×n일째에 획득합니다.",
            required = true,
            schema = @Schema(type = "integer", minimum = "1", maximum = "16", example = "1")
        )
        @PathVariable
        String growthCycle,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "획득한 나무 정보. 칸 번호는 서버가 배정하므로 보내지 않습니다.",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ForestTreePlantRequest.class),
                examples = @ExampleObject(value = """
                    {
                      "kind": "pine",
                      "plantedAt": "2026-11-05T07:48:10+09:00"
                    }
                    """)
            )
        )
        ForestTreePlantRequest request
    );
}
