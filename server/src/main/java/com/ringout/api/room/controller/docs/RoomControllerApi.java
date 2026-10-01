package com.ringout.api.room.controller.docs;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.SwaggerConfig;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.room.dto.request.RoomCreateRequest;
import com.ringout.api.room.dto.request.RoomKickRequest;
import com.ringout.api.room.dto.request.RoomUpdateRequest;
import com.ringout.api.room.dto.response.RoomCreateResponse;
import com.ringout.api.room.dto.response.RoomDetailResponse;
import com.ringout.api.room.dto.response.RoomListResponse;
import com.ringout.api.room.dto.response.RoomRecordsResponse;
import com.ringout.api.room.dto.response.RoomUpdateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "모임 방 (Room)", description = "모임 방 API")
public interface RoomControllerApi {

    @Operation(
        summary = "모임 방 목록 조회",
        description = "삭제되지 않은 전체 모임 방을 latestActivityAt 내림차순, roomId 오름차순으로 조회합니다. latestActivityAt은 방 정보 수정, 활성 회원의 알람 기록, 활성 회원의 이동 상태 변경이 성공한 시각을 반영합니다. 참여 인원은 삭제되지 않은 참여 관계를 집계합니다. 활동 요일은 월요일부터 일요일 순으로 반환하고, 대표 이미지가 없으면 /images/default-room.png를 반환합니다. 유효한 Access Token이면 본인의 현재 참여 여부를 isJoined로 반환합니다. 토큰이 없거나 유효하지 않으면 비로그인 요청으로 처리하며 isJoined는 false입니다. description은 값이 없으면 null입니다.",
        parameters = @Parameter(
            name = "Authorization",
            in = ParameterIn.HEADER,
            required = false,
            description = "선택적 Access Token. 유효한 토큰일 때만 모임 참여 여부를 함께 반환하며, 토큰이 없거나 만료·위조·유효하지 않은 토큰은 비로그인 요청으로 처리합니다.",
            schema = @Schema(type = "string", example = "Bearer {accessToken}")
        )
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "모임 방 목록 조회 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "ROOM200",
                      "message": "방 목록 조회에 성공했습니다.",
                      "result": {
                        "rooms": [
                          {
                            "roomId": 1,
                            "name": "아침 운동 모임",
                            "description": "매주 함께 운동하고 인증하는 모임입니다.",
                            "imageUrl": "https://example.com/images/room-1.png",
                            "activityDays": ["MONDAY", "WEDNESDAY", "FRIDAY"],
                            "activityTime": "08:00",
                            "memberCount": 12,
                            "isJoined": true,
                            "createdAt": "2026-09-20T10:30:00"
                          }
                        ]
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "예상하지 못한 서버 오류",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "COMMON500",
                      "message": "서버 에러, 관리자에게 문의 바랍니다.",
                      "result": "예외 메시지"
                    }
                    """)
            )
        )
    })
    ResponseEntity<CustomResponse<RoomListResponse>> getRooms(
        @Parameter(hidden = true) CustomUserDetails customUserDetails
    );

    @Operation(
        summary = "모임 방 상세 정보 조회",
        description = """
            인증된 현재 참여자만 삭제되지 않은 모임 방의 상세 정보를 조회할 수 있습니다.
            membershipRole은 방장이면 OWNER, 일반 참여자이면 MEMBER입니다.
            """,
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "모임 방 상세 정보 조회 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "ROOM200",
                      "message": "방 상세 정보 조회에 성공했습니다.",
                      "result": {
                        "roomId": 1,
                        "name": "아침 운동 모임",
                        "description": "매주 함께 운동하고 인증하는 모임입니다.",
                        "imageUrl": "https://example.com/images/room-1.png",
                        "activityDays": ["MONDAY", "WEDNESDAY", "FRIDAY"],
                        "activityTime": "08:00",
                        "memberCount": 1,
                        "membershipRole": "OWNER",
                        "createdAt": "2026-09-20T10:30:00",
                        "members": [{"userId": 1, "nickname": "가나다", "profileImageUrl": null}]
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "roomId를 Long으로 변환할 수 없음 (문자열 또는 Long 범위 초과); 0 또는 음수는 조회 결과가 없어 ROOM404",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess": false, "code": "COMMON400", "message": "잘못된 요청입니다.", "result": null}
                """))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "보안 필터 인증 실패는 AUTH401, 인증된 사용자가 DB에 없으면 ROOM401",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "unauthorized", value = """
                    {"isSuccess": false, "code": "AUTH401", "message": "인증되지 않은 사용자입니다.", "result": null}
                    """),
                @ExampleObject(name = "expiredAccessToken", value = """
                    {"isSuccess": false, "code": "AUTH401", "message": "액세스 토큰이 만료되었습니다.", "result": null}
                    """),
                @ExampleObject(name = "authenticatedUserNotFound", value = """
                    {"isSuccess": false, "code": "ROOM401", "message": "인증되지 않은 사용자입니다.", "result": null}
                    """)
            })
        ),
        @ApiResponse(
            responseCode = "403",
            description = "현재 참여하지 않은 방 (탈퇴·추방된 참여 관계 포함)",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess": false, "code": "ROOM403", "message": "참여하지 않은 방입니다.", "result": null}
                """))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "존재하지 않거나 삭제된 모임 방",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess": false, "code": "ROOM404", "message": "존재하지 않는 모임 방입니다.", "result": null}
                """))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "서비스에서 상세 정보 조회 중 예기치 않은 오류 발생",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess": false, "code": "ROOM500", "message": "모임 방 상세 정보를 조회하는 중 오류가 발생했습니다.", "result": null}
                """))
        )
    })
    ResponseEntity<CustomResponse<RoomDetailResponse>> getRoom(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        @Parameter(description = "조회할 모임 방 식별자", required = true, example = "1") Long roomId
    );

    @Operation(
        summary = "모임 회원 활동 기록 조회",
        description = "인증된 현재 참여자가 활동 날짜의 모임 회원별 알람 및 이동 기록을 조회합니다. 기록이 없는 회원도 records가 빈 배열인 상태로 반환합니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "모임 회원 기록 조회 성공", content = @Content(
            mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess":true,"code":"RECORD200","message":"모임 회원 기록 조회에 성공했습니다.","result":{"memberRecords":[{"userId":1,"nickname":"아이아티스트님","profileImageUrl":null,"records":[]}]}}
                """))),
        @ApiResponse(responseCode = "400", description = "date 누락 또는 형식 오류", content = @Content(
            mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess":false,"code":"RECORD400","message":"조회 날짜의 형식이 올바르지 않습니다.","result":null}
                """))),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = @Content(
            mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess":false,"code":"AUTH401","message":"인증되지 않은 사용자입니다.","result":null}
                """))),
        @ApiResponse(responseCode = "403", description = "현재 모임 회원이 아님", content = @Content(
            mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess":false,"code":"RECORD403","message":"해당 모임의 회원이 아닙니다.","result":null}
                """))),
        @ApiResponse(responseCode = "404", description = "존재하지 않거나 삭제된 모임 방", content = @Content(
            mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess":false,"code":"ROOM404","message":"존재하지 않는 모임 방입니다.","result":null}
                """)))
    })
    ResponseEntity<CustomResponse<RoomRecordsResponse>> getRoomRecords(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        @Parameter(description = "조회할 모임 방 식별자", required = true, example = "1") Long roomId,
        @Parameter(description = "활동 날짜", required = true, example = "2026-09-16") String date
    );

    @Operation(
        summary = "모임 방 참여",
        description = "인증된 사용자를 모임 방의 MEMBER로 참여시키고, 방 상세 정보와 동일한 형식의 응답을 반환합니다. 요청 본문과 쿼리 파라미터는 없습니다. 활성 참여 관계가 이미 있으면 409, 해당 방의 활성 블랙리스트에 있으면 403을 반환합니다. 성공 응답의 members는 활성 참여자만 포함하며, 닉네임은 한글·영문·그 외 문자 그룹 순으로 정렬됩니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "모임 방 참여 성공",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {
                  "isSuccess": true,
                  "code": "ROOM201",
                  "message": "모임 방 참여에 성공했습니다.",
                  "result": {
                    "roomId": 1,
                    "name": "아침 운동 모임",
                    "description": "매주 함께 운동하고 인증하는 모임입니다.",
                    "imageUrl": "https://example.com/images/room-1.png",
                    "activityDays": ["MONDAY", "WEDNESDAY", "FRIDAY"],
                    "activityTime": "08:00",
                    "memberCount": 4,
                    "membershipRole": "MEMBER",
                    "createdAt": "2026-09-20T10:30:00",
                    "members": [
                      {"userId": 1, "nickname": "가나다", "profileImageUrl": "https://example.com/profiles/1.png"},
                      {"userId": 2, "nickname": "성열", "profileImageUrl": null},
                      {"userId": 3, "nickname": "Alice", "profileImageUrl": "https://example.com/profiles/3.png"},
                      {"userId": 4, "nickname": "@runner", "profileImageUrl": null}
                    ]
                  }
                }
                """))
        ),
        @ApiResponse(responseCode = "400", description = "roomId를 Long으로 변환할 수 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"isSuccess": false, "code": "COMMON400", "message": "잘못된 요청입니다.", "result": null}
            """))),
        @ApiResponse(responseCode = "401", description = "보안 필터 인증 실패 또는 인증된 사용자 삭제", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "unauthorized", value = """
                {"isSuccess": false, "code": "AUTH401", "message": "인증되지 않은 사용자입니다.", "result": null}
                """),
            @ExampleObject(name = "expiredAccessToken", value = """
                {"isSuccess": false, "code": "AUTH401", "message": "액세스 토큰이 만료되었습니다.", "result": null}
                """),
            @ExampleObject(name = "authenticatedUserNotFound", value = """
                {"isSuccess": false, "code": "ROOM401", "message": "인증되지 않은 사용자입니다.", "result": null}
                """)
        })),
        @ApiResponse(responseCode = "403", description = "해당 모임에서 추방된 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"isSuccess": false, "code": "ROOM403", "message": "해당 모임에 참여할 수 없는 사용자입니다.", "result": null}
            """))),
        @ApiResponse(responseCode = "404", description = "존재하지 않거나 삭제된 모임 방", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"isSuccess": false, "code": "ROOM404", "message": "존재하지 않는 모임 방입니다.", "result": null}
            """))),
        @ApiResponse(responseCode = "409", description = "이미 참여 중인 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"isSuccess": false, "code": "ROOM409", "message": "이미 참여 중인 모임입니다.", "result": null}
            """))),
        @ApiResponse(responseCode = "500", description = "예기치 않은 서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"isSuccess": false, "code": "COMMON500", "message": "서버 에러, 관리자에게 문의 바랍니다.", "result": "예외 메시지"}
            """)))
    })
    ResponseEntity<CustomResponse<RoomDetailResponse>> joinRoom(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        @Parameter(description = "참여할 모임 방 식별자", required = true, example = "1") Long roomId
    );

    @Operation(
        summary = "모임 방 탈퇴",
        description = "인증된 현재 참여자가 본인의 참여 관계를 삭제하고 모임 방에서 탈퇴합니다. 방장은 탈퇴할 수 없으며, 탈퇴한 MEMBER는 다시 참여할 수 있습니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "모임 방 탈퇴 성공", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"isSuccess": true, "code": "ROOM200", "message": "모임 방 탈퇴에 성공했습니다.", "result": null}
            """))),
        @ApiResponse(responseCode = "400", description = "roomId를 Long으로 변환할 수 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"isSuccess": false, "code": "COMMON400", "message": "잘못된 요청입니다.", "result": null}
            """))),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"isSuccess": false, "code": "AUTH401", "message": "인증되지 않은 사용자입니다.", "result": null}
            """))),
        @ApiResponse(responseCode = "403", description = "방장의 탈퇴 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"isSuccess": false, "code": "ROOM403", "message": "방장은 모임에서 탈퇴할 수 없습니다.", "result": null}
            """))),
        @ApiResponse(responseCode = "404", description = "존재하지 않거나 삭제된 모임 방", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"isSuccess": false, "code": "ROOM404", "message": "존재하지 않는 모임 방입니다.", "result": null}
            """))),
        @ApiResponse(responseCode = "409", description = "현재 참여하지 않은 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {"isSuccess": false, "code": "ROOM409", "message": "참여 중인 모임이 아닙니다.", "result": null}
            """)))
    })
    ResponseEntity<CustomResponse<Void>> leaveRoom(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        @Parameter(description = "탈퇴할 모임 방 식별자", required = true, example = "1") Long roomId
    );

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
            description = "도메인 입력값이 유효하지 않거나 요청 본문을 역직렬화할 수 없음",
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
                    @ExampleObject(name = "duplicateOrNullActivityDay", value = """
                        {
                          "isSuccess": false,
                          "code": "ROOM400",
                          "message": "올바르지 않은 활동 요일입니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "missingActivityTime", value = """
                        {
                          "isSuccess": false,
                          "code": "ROOM400",
                          "message": "활동 시간의 형식이 올바르지 않습니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "invalidActivityDayLiteralOrTimeFormat", value = """
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
        ),
        @ApiResponse(
            responseCode = "500",
            description = "예상하지 못한 서버 오류",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess": false, "code": "COMMON500", "message": "서버 에러, 관리자에게 문의 바랍니다.", "result": "예외 메시지"}
                """))
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
        description = "방장만 방 이름·소개 정보를 수정하거나 이미지 파일을 전달할 수 있습니다. name, description, image, removeImage 중 하나 이상을 전달해야 합니다. image를 전달하면 S3에 저장해 대표 이미지를 교체하고, removeImage를 true로 전달하면 기존 이미지를 삭제해 기본 이미지로 전환합니다. image와 removeImage=true는 함께 전달할 수 없습니다. 응답에는 이미지가 있으면 presigned 조회 URL을, 없으면 /images/default-room.png를 반환합니다. name 또는 description 변경 시 최신 활동 시각을 갱신합니다.",
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
                        "imageUrl": "https://example.com/images/room-1.png?signature=test"
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "수정할 필드가 없거나 전달한 값 또는 이미지가 유효하지 않음; image와 removeImage=true를 함께 전달한 경우; roomId 변환 실패는 COMMON400",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "noUpdateField", value = """
                    {"isSuccess": false, "code": "ROOM400", "message": "수정할 정보를 하나 이상 입력해주세요.", "result": null}
                    """),
                @ExampleObject(name = "invalidName", value = """
                    {"isSuccess": false, "code": "ROOM400", "message": "모임 방 이름의 형식이 올바르지 않습니다.", "result": null}
                    """),
                @ExampleObject(name = "invalidDescription", value = """
                    {"isSuccess": false, "code": "ROOM400", "message": "모임 소개의 형식이 올바르지 않습니다.", "result": null}
                    """),
                @ExampleObject(name = "invalidImage", value = """
                    {"isSuccess": false, "code": "ROOM400", "message": "모임 대표 이미지의 형식이 올바르지 않습니다.", "result": null}
                    """),
                @ExampleObject(name = "imageRemoveConflict", value = """
                    {"isSuccess": false, "code": "ROOM400", "message": "대표 이미지 교체와 기본 이미지 전환을 동시에 요청할 수 없습니다.", "result": null}
                    """),
                @ExampleObject(name = "invalidRoomId", value = """
                    {"isSuccess": false, "code": "COMMON400", "message": "잘못된 요청입니다.", "result": null}
                    """)
            })
        ),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필터 실패는 AUTH401, 인증 사용자를 찾지 못하면 ROOM401",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "unauthorized", value = """
                    {"isSuccess": false, "code": "AUTH401", "message": "인증되지 않은 사용자입니다.", "result": null}
                    """),
                @ExampleObject(name = "expiredToken", value = """
                    {"isSuccess": false, "code": "AUTH401", "message": "액세스 토큰이 만료되었습니다.", "result": null}
                    """),
                @ExampleObject(name = "authenticatedUserNotFound", value = """
                    {"isSuccess": false, "code": "ROOM401", "message": "인증되지 않은 사용자입니다.", "result": null}
                    """)
            })
        ),
        @ApiResponse(
            responseCode = "403",
            description = "요청 사용자가 방장이 아님",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess": false, "code": "ROOM403", "message": "모임 방을 수정할 권한이 없습니다.", "result": null}
                """))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "해당 ID의 방 레코드가 없음",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess": false, "code": "ROOM404", "message": "존재하지 않는 모임 방입니다.", "result": null}
                """))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "예상하지 못한 서버 오류",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess": false, "code": "COMMON500", "message": "서버 에러, 관리자에게 문의 바랍니다.", "result": "예외 메시지"}
                """))
        )
    })
    ResponseEntity<CustomResponse<RoomUpdateResponse>> updateRoom(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        @Parameter(description = "수정할 모임 방 식별자", required = true, example = "1") Long roomId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "multipart/form-data. name, description, image, removeImage 중 하나 이상 전달합니다. image를 전달하면 S3에 저장해 대표 이미지를 교체하고, removeImage=true를 전달하면 기본 이미지로 전환합니다.",
            content = @Content(
                mediaType = "multipart/form-data",
                schema = @Schema(implementation = RoomUpdateRequest.class)
            )
        )
        RoomUpdateRequest request
    );

    @Operation(
        summary = "모임 방 삭제",
        description = "방장만 활성 모임 방을 삭제할 수 있습니다. 방은 soft delete 처리되고 해당 방의 활성 블랙리스트도 함께 soft delete 처리됩니다.",
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
            responseCode = "400",
            description = "roomId를 Long으로 변환할 수 없음; 0 또는 음수는 활성 방을 찾을 수 없어 ROOM404",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess": false, "code": "COMMON400", "message": "잘못된 요청입니다.", "result": null}
                """))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필터 실패는 AUTH401, 인증 사용자를 찾지 못하면 ROOM401",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "unauthorized", value = """
                    {"isSuccess": false, "code": "AUTH401", "message": "인증되지 않은 사용자입니다.", "result": null}
                    """),
                @ExampleObject(name = "expiredToken", value = """
                    {"isSuccess": false, "code": "AUTH401", "message": "액세스 토큰이 만료되었습니다.", "result": null}
                    """),
                @ExampleObject(name = "authenticatedUserNotFound", value = """
                    {"isSuccess": false, "code": "ROOM401", "message": "인증되지 않은 사용자입니다.", "result": null}
                    """)
            })
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
        ),
        @ApiResponse(
            responseCode = "500",
            description = "예상하지 못한 서버 오류",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess": false, "code": "COMMON500", "message": "서버 에러, 관리자에게 문의 바랍니다.", "result": "예외 메시지"}
                """))
        )
    })
    ResponseEntity<CustomResponse<Void>> deleteRoom(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        @Parameter(description = "삭제할 모임 방 식별자", required = true, example = "1") Long roomId
    );

    @Operation(
        summary = "모임 회원 추방",
        description = "방장만 현재 참여 중인 다른 회원을 추방할 수 있습니다. 대상의 참여 관계를 soft delete하고 해당 방의 블랙리스트에 등록합니다. 방장 본인은 추방할 수 없습니다.",
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
            description = "대상이 현재 참여자가 아니거나 방장 본인임; roomId 변환 실패, userId 누락, 잘못된 JSON은 COMMON400; 0 또는 음수 roomId는 ROOM404",
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
                        """),
                    @ExampleObject(name = "invalidRoomIdOrMalformedJson", value = """
                        {
                          "isSuccess": false,
                          "code": "COMMON400",
                          "message": "잘못된 요청입니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "missingTargetUserId", value = """
                        {
                          "isSuccess": false,
                          "code": "COMMON400",
                          "message": "잘못된 요청입니다.",
                          "result": {"userId": "must not be null"}
                        }
                        """)
                }
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필터 실패는 AUTH401, 인증 사용자를 찾지 못하면 ROOM401",
            content = @Content(mediaType = "application/json", examples = {
                @ExampleObject(name = "unauthorized", value = """
                    {"isSuccess": false, "code": "AUTH401", "message": "인증되지 않은 사용자입니다.", "result": null}
                    """),
                @ExampleObject(name = "expiredToken", value = """
                    {"isSuccess": false, "code": "AUTH401", "message": "액세스 토큰이 만료되었습니다.", "result": null}
                    """),
                @ExampleObject(name = "authenticatedUserNotFound", value = """
                    {"isSuccess": false, "code": "ROOM401", "message": "인증되지 않은 사용자입니다.", "result": null}
                    """)
            })
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
        ),
        @ApiResponse(
            responseCode = "500",
            description = "예상하지 못한 서버 오류",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess": false, "code": "COMMON500", "message": "서버 에러, 관리자에게 문의 바랍니다.", "result": "예외 메시지"}
                """))
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
