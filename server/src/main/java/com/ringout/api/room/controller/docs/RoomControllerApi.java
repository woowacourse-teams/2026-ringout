package com.ringout.api.room.controller.docs;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.SwaggerConfig;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.room.dto.request.RoomCreateRequest;
import com.ringout.api.room.dto.request.RoomKickRequest;
import com.ringout.api.room.dto.request.RoomUpdateRequest;
import com.ringout.api.room.dto.response.RoomCreateResponse;
import com.ringout.api.room.dto.response.RoomUpdateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "모임 방 (Room)", description = "모임 방 API")
public interface RoomControllerApi {

    @Operation(
        summary = "모임 방 생성",
        description = "새로운 모임 방을 생성합니다. 요청한 사용자는 방장(OWNER)으로 자동 가입되며, 생성된 방의 상세 정보를 반환합니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "모임 방 생성 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "ROOM201",
                      "message": "모임 방이 생성되었습니다.",
                      "result": {
                        "roomId": 1,
                        "name": "아침 운동 모임",
                        "description": "매주 함께 운동하고 인증하는 모임입니다.",
                        "imageUrl": "/images/default-room.png",
                        "activityDays": ["MONDAY", "WEDNESDAY", "FRIDAY"],
                        "activityTime": "08:00",
                        "memberCount": 1,
                        "membershipRole": "OWNER",
                        "createdAt": "2026-09-28T09:00:00",
                        "members": [
                          {
                            "userId": 1,
                            "nickname": "링아웃",
                            "profileImageUrl": null
                          }
                        ]
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청 값 검증 또는 JSON 형식 오류",
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(name = "invalidName", value = """
                        {
                          "isSuccess": false,
                          "code": "ROOM400",
                          "message": "모임 방 이름의 형식이 올바르지 않습니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "invalidDescription", value = """
                        {
                          "isSuccess": false,
                          "code": "ROOM400",
                          "message": "모임 소개의 형식이 올바르지 않습니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "missingActivityDays", value = """
                        {
                          "isSuccess": false,
                          "code": "ROOM400",
                          "message": "활동 요일을 1개 이상 선택해야 합니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "invalidActivityDay", value = """
                        {
                          "isSuccess": false,
                          "code": "COMMON400",
                          "message": "잘못된 요청입니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "invalidActivityTime", value = """
                        {
                          "isSuccess": false,
                          "code": "COMMON400",
                          "message": "잘못된 요청입니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "malformedJson", value = """
                        {
                          "isSuccess": false,
                          "code": "COMMON400",
                          "message": "잘못된 요청입니다.",
                          "result": null
                        }
                        """)
                }
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Authorization Bearer 토큰이 없거나 유효하지 않음, 또는 만료됨",
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(name = "unauthorized", value = """
                        {
                          "isSuccess": false,
                          "code": "AUTH401",
                          "message": "인증되지 않은 사용자입니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "expiredAccessToken", value = """
                        {
                          "isSuccess": false,
                          "code": "AUTH401",
                          "message": "액세스 토큰이 만료되었습니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "authenticatedUserNotFound", value = """
                        {
                          "isSuccess": false,
                          "code": "ROOM401",
                          "message": "인증되지 않은 사용자입니다.",
                          "result": null
                        }
                        """)
                }
            )
        )
    })
    ResponseEntity<CustomResponse<RoomCreateResponse>> createRoom(
        @Parameter(hidden = true)
        CustomUserDetails customUserDetails,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "생성할 모임 방 정보",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RoomCreateRequest.class),
                examples = @ExampleObject(value = """
                    {
                      "name": "아침 운동 모임",
                      "description": "매주 함께 운동하고 인증하는 모임입니다.",
                      "activityDays": ["MONDAY", "WEDNESDAY", "FRIDAY"],
                      "activityTime": "08:00"
                    }
                    """)
            )
        )
        RoomCreateRequest request
    );

    @Operation(
        summary = "모임 방 수정",
        description = "방장만 모임 방의 이름, 소개, 대표 이미지를 수정할 수 있습니다. 대표 이미지는 현재 기본 이미지 URL로 응답합니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "모임 방 수정 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "ROOM200",
                      "message": "방 정보 수정에 성공했습니다.",
                      "result": {
                        "roomId": 1,
                        "name": "새로운 아침 운동 모임",
                        "description": "매주 아침 함께 운동하는 모임입니다.",
                        "imageUrl": "/images/default-room.png"
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(responseCode = "400", description = "수정 요청 또는 필드 형식이 올바르지 않음"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        @ApiResponse(responseCode = "403", description = "모임 방 수정 권한 없음"),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 모임 방")
    })
    ResponseEntity<CustomResponse<RoomUpdateResponse>> updateRoom(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        @Parameter(description = "수정할 모임 방 식별자", example = "1") Long roomId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "변경할 모임 방 정보",
            content = @Content(
                mediaType = "multipart/form-data",
                schema = @Schema(implementation = RoomUpdateRequest.class)
            )
        )
        RoomUpdateRequest request
    );

    @Operation(
        summary = "모임 방 삭제",
        description = "방장만 모임 방을 삭제할 수 있습니다. 삭제된 방과 해당 방의 블랙리스트는 조회 대상에서 제외됩니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "모임 방 삭제 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "ROOM200",
                      "message": "방 삭제에 성공했습니다.",
                      "result": null
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
                      "code": "ROOM401",
                      "message": "인증되지 않은 사용자입니다.",
                      "result": null
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "모임 방 삭제 권한 없음",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "ROOM403",
                      "message": "모임 방을 삭제할 권한이 없습니다.",
                      "result": null
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "존재하지 않거나 이미 삭제된 모임 방",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "ROOM404",
                      "message": "존재하지 않는 모임 방입니다.",
                      "result": null
                    }
                    """)
            )
        )
    })
    ResponseEntity<CustomResponse<Void>> deleteRoom(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        @Parameter(description = "삭제할 모임 방 식별자", required = true, example = "1") Long roomId
    );

    @Operation(
        summary = "모임 회원 추방",
        description = "방장이 참여 중인 일반 회원을 추방합니다. 추방된 회원은 블랙리스트에 등록되어 해당 모임에 다시 가입할 수 없습니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "회원 추방 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "ROOM200",
                      "message": "회원 추방에 성공했습니다.",
                      "result": null
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "추방 대상이 모임 회원이 아니거나 방장 자신을 추방하려는 요청",
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(name = "notRoomMember", value = """
                        {
                          "isSuccess": false,
                          "code": "ROOM400",
                          "message": "해당 사용자는 모임에 참여하고 있지 않습니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "hostSelfKick", value = """
                        {
                          "isSuccess": false,
                          "code": "ROOM400",
                          "message": "방장은 자신을 추방할 수 없습니다.",
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
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "ROOM401",
                      "message": "인증되지 않은 사용자입니다.",
                      "result": null
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "회원 추방 권한 없음",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "ROOM403",
                      "message": "회원을 추방할 권한이 없습니다.",
                      "result": null
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "모임 방 또는 추방 대상 사용자를 찾을 수 없음",
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(name = "roomNotFound", value = """
                        {
                          "isSuccess": false,
                          "code": "ROOM404",
                          "message": "존재하지 않는 모임 방입니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "userNotFound", value = """
                        {
                          "isSuccess": false,
                          "code": "USER404",
                          "message": "존재하지 않는 사용자입니다.",
                          "result": null
                        }
                        """)
                }
            )
        )
    })
    ResponseEntity<CustomResponse<Void>> kickMember(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        @Parameter(description = "회원을 추방할 모임 방 식별자", required = true, example = "1") Long roomId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "추방할 회원 정보",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RoomKickRequest.class),
                examples = @ExampleObject(value = """
                    {
                      "userId": 10
                    }
                    """)
            )
        )
        RoomKickRequest request
    );
}
