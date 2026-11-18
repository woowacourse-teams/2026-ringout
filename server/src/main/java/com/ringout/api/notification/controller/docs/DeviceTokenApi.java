package com.ringout.api.notification.controller.docs;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.SwaggerConfig;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.notification.dto.request.DeviceTokenDeleteRequest;
import com.ringout.api.notification.dto.request.DeviceTokenRegisterRequest;
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

@Tag(name = "디바이스 토큰 (DeviceToken)", description = "푸시 알림 디바이스 토큰 API")
public interface DeviceTokenApi {

    @Operation(
        summary = "디바이스 토큰 등록",
        description = "로그인 직후 또는 FCM 토큰이 갱신될 때 호출합니다. 이미 등록된 토큰이면 현재 사용자로 소유자를 변경합니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "디바이스 토큰 등록 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "PUSHALARM200",
                      "message": "디바이스 토큰이 등록되었습니다.",
                      "result": null
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청 필드 형식 오류",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "COMMON400",
                      "message": "잘못된 요청입니다.",
                      "result": {
                        "fcmToken": "널이어서는 안됩니다"
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "인증되지 않은 사용자",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "AUTH401",
                      "message": "인증되지 않은 사용자입니다."
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 사용자",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "USER404",
                      "message": "존재하지 않는 사용자입니다."
                    }
                    """)
            )
        )
    })
    ResponseEntity<CustomResponse<Void>> register(
        @Parameter(hidden = true)
        CustomUserDetails customUserDetails,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "등록할 디바이스 토큰 정보",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DeviceTokenRegisterRequest.class),
                examples = @ExampleObject(value = """
                    {
                      "platform": "IOS",
                      "fcmToken": "fcm-token-example"
                    }
                    """)
            )
        )
        DeviceTokenRegisterRequest request
    );

    @Operation(
        summary = "디바이스 토큰 삭제",
        description = "로그아웃 시 호출합니다. 토큰이 없거나 다른 사용자의 토큰이면 아무 작업도 하지 않고 성공을 반환합니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "디바이스 토큰 삭제 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "PUSHALARM200",
                      "message": "디바이스 토큰이 삭제되었습니다.",
                      "result": null
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청 필드 형식 오류",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "COMMON400",
                      "message": "잘못된 요청입니다.",
                      "result": {
                        "fcmToken": "공백일 수 없습니다"
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "인증되지 않은 사용자",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "AUTH401",
                      "message": "인증되지 않은 사용자입니다."
                    }
                    """)
            )
        )
    })
    ResponseEntity<CustomResponse<Void>> unregister(
        @Parameter(hidden = true)
        CustomUserDetails customUserDetails,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "삭제할 디바이스 토큰",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DeviceTokenDeleteRequest.class),
                examples = @ExampleObject(value = """
                    {
                      "fcmToken": "fcm-token-example"
                    }
                    """)
            )
        )
        DeviceTokenDeleteRequest request
    );
}
