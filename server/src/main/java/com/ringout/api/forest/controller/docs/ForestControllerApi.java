package com.ringout.api.forest.controller.docs;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.SwaggerConfig;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.forest.dto.request.ForestOnboardingCompleteRequest;
import com.ringout.api.forest.dto.response.ForestOnboardingResponse;
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
}
