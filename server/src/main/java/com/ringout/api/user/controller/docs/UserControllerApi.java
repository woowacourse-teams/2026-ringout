package com.ringout.api.user.controller.docs;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.SwaggerConfig;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.user.dto.request.UpdateNicknameRequest;
import com.ringout.api.user.dto.response.UpdateNicknameResponse;
import com.ringout.api.user.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "회원 (User)", description = "회원 정보 관리 API")
public interface UserControllerApi {

    @Operation(
        summary = "내 회원 정보 조회",
        description = "로그인한 회원의 닉네임과 이메일을 조회합니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "회원 정보 조회 성공",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(
                    value = """
                        {
                          "isSuccess": true,
                          "code": "USER200",
                          "message": "회원 정보 조회에 성공했습니다.",
                          "result": {
                            "nickname": "서여",
                            "email": "uio6699@naver.com"
                          }
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "인증되지 않은 사용자",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = {
                    @ExampleObject(
                        name = "인증 정보 없음 또는 유효하지 않은 토큰",
                        value = """
                            {
                              "isSuccess": false,
                              "code": "COMMON401",
                              "message": "인증되지 않은 사용자입니다.",
                              "result": null
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "만료된 액세스 토큰",
                        value = """
                            {
                              "isSuccess": false,
                              "code": "AUTH401",
                              "message": "액세스 토큰이 만료되었습니다.",
                              "result": null
                            }
                            """
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 사용자",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(
                    value = """
                        {
                          "isSuccess": false,
                          "code": "USER404",
                          "message": "존재하지 않는 사용자입니다.",
                          "result": null
                        }
                        """
                )
            )
        )
    })
    ResponseEntity<CustomResponse<UserResponse>> getUser(
        @Parameter(hidden = true) CustomUserDetails userDetails
    );

    @Operation(
        summary = "내 닉네임 수정",
        description = "로그인한 회원의 닉네임을 한글, 영문, 숫자로 구성된 1~10자로 변경합니다. 닉네임 중복은 허용됩니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "닉네임 수정 성공",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(
                    value = """
                        {
                          "isSuccess": true,
                          "code": "USER200",
                          "message": "회원 정보 수정에 성공했습니다.",
                          "result": {
                            "nickname": "용감한토끼"
                          }
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "닉네임이 비어 있거나, 10자를 초과하거나, 허용되지 않은 문자를 포함한 경우",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(
                    value = """
                        {
                          "isSuccess": false,
                          "code": "USER400",
                          "message": "닉네임은 최대 10자입니다.",
                          "result": null
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "인증되지 않은 사용자",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(
                    value = """
                        {
                          "isSuccess": false,
                          "code": "COMMON401",
                          "message": "인증되지 않은 사용자입니다.",
                          "result": null
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 사용자",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(
                    value = """
                        {
                          "isSuccess": false,
                          "code": "USER404",
                          "message": "존재하지 않는 사용자입니다.",
                          "result": null
                        }
                        """
                )
            )
        )
    })
    ResponseEntity<CustomResponse<UpdateNicknameResponse>> updateNickname(
        @Parameter(hidden = true) CustomUserDetails userDetails,

        @RequestBody(
            description = "변경할 닉네임",
            required = true,
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(value = "{\"nickname\": \"용감한토끼\"}")
            )
        )
        UpdateNicknameRequest request
    );

    @Operation(
        summary = "회원 탈퇴",
        description = "로그인한 회원의 정보를 삭제하고 탈퇴 처리합니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "회원 탈퇴 성공",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(
                    value = """
                        {
                          "isSuccess": true,
                          "code": "COMMON200",
                          "message": "성공입니다.",
                          "result": null
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "인증되지 않은 사용자",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(
                    value = """
                        {
                          "isSuccess": false,
                          "code": "COMMON401",
                          "message": "인증되지 않은 사용자입니다.",
                          "result": null
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 사용자",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = @ExampleObject(
                    value = """
                        {
                          "isSuccess": false,
                          "code": "USER404",
                          "message": "존재하지 않는 사용자입니다.",
                          "result": null
                        }
                        """
                )
            )
        )
    })
    ResponseEntity<CustomResponse<Void>> withdraw(
        @Parameter(hidden = true) CustomUserDetails userDetails
    );
}
