package com.ringout.api.destination.controller.docs;

import com.ringout.api.auth.CustomUserDetails;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.destination.dto.request.DestinationCreateRequest;
import com.ringout.api.destination.dto.request.DestinationSyncRequest;
import com.ringout.api.destination.dto.request.DestinationUpdateRequest;
import com.ringout.api.destination.dto.response.DestinationCreateResponse;
import com.ringout.api.destination.dto.response.DestinationResponse;
import com.ringout.api.destination.dto.response.DestinationSyncResponse;
import com.ringout.api.destination.dto.response.DestinationUpdateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "목적지 (Destination)", description = "목적지 API")
public interface DestinationControllerApi {

    @Operation(
        summary = "목적지 전체 조회",
        description = "사용자가 소유한 목적지들을 ID 오름차순으로 전체 조회합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "목적지 전체 조회 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "DESTINATION200",
                      "message": "조회되었습니다.",
                      "result": [
                        {
                          "destinationId": 1,
                          "alias": "런닝 장소",
                          "latitude": 37.5665,
                          "longitude": 126.9780
                        },
                        {
                          "destinationId": 2,
                          "alias": "헬스장",
                          "latitude": 37.4979,
                          "longitude": 127.0276
                        }
                      ]
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
                      "code": "DESTINATION401",
                      "message": "인증되지 않은 사용자입니다."
                    }
                    """)
            )
        )
    })
    ResponseEntity<CustomResponse<List<DestinationResponse>>> findDestinations(
        @Parameter(hidden = true)
        CustomUserDetails customUserDetails
    );

    @Operation(
        summary = "목적지 저장",
        description = "사용자의 목적지를 저장합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "목적지 저장 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "DESTINATION201",
                      "message": "목적지 생성에 성공했습니다.",
                      "result": {
                        "destinationId": 1
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "요청 필드 형식 오류",
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(name = "invalidAlias", value = """
                        {
                          "isSuccess": false,
                          "code": "COMMON400",
                          "message": "잘못된 요청입니다.",
                          "result": {
                            "alias": "위치 별명은 1자 이상 12자 이하로 입력해야 합니다."
                          }
                        }
                        """),
                    @ExampleObject(name = "invalidCoordinate", value = """
                        {
                          "isSuccess": false,
                          "code": "COMMON400",
                          "message": "잘못된 요청입니다.",
                          "result": {
                            "latitude": "좌표는 위도 -90~90, 경도 -180~180 범위의 숫자여야 합니다."
                          }
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
                      "code": "DESTINATION401",
                      "message": "인증되지 않은 사용자입니다."
                    }
                    """)
            )
        )
    })
    ResponseEntity<CustomResponse<DestinationCreateResponse>> createDestination(
        @Parameter(hidden = true)
        CustomUserDetails customUserDetails,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "저장할 목적지 정보",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DestinationCreateRequest.class),
                examples = @ExampleObject(value = """
                    {
                      "alias": "헬스장",
                      "latitude": 37.4979,
                      "longitude": 127.0276
                    }
                    """)
            )
        )
        DestinationCreateRequest request
    );

    @Operation(
        summary = "목적지 동기화",
        description = "사용자가 회원가입 시, 기기에 저장되어 있던 목적지 정보들을 서버에 저장합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "목적지 동기화 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "DESTINATION200",
                      "message": "목적지 동기화가 완료되었습니다.",
                      "result": {
                        "destinations": [
                          {
                            "clientDestinationId": 2,
                            "destinationId": 1,
                            "alias": "집",
                            "latitude": 37.4979,
                            "longitude": 127.0276
                          },
                          {
                            "clientDestinationId": 3,
                            "destinationId": 2,
                            "alias": "회사",
                            "latitude": 37.5665,
                            "longitude": 126.9780
                          }
                        ]
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "목적지 동기화 요청 형식 오류",
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(name = "invalidDestinationInfo", value = """
                        {
                          "isSuccess": false,
                          "code": "DESTINATION400",
                          "message": "목적지 정보가 올바르지 않습니다."
                        }
                        """),
                    @ExampleObject(name = "invalidCoordinate", value = """
                        {
                          "isSuccess": false,
                          "code": "DESTINATION400",
                          "message": "목적지 좌표가 올바르지 않습니다."
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
                      "code": "DESTINATION401",
                      "message": "인증되지 않은 사용자입니다."
                    }
                    """)
            )
        )
    })
    ResponseEntity<CustomResponse<DestinationSyncResponse>> syncDestinations(
        @Parameter(hidden = true)
        CustomUserDetails customUserDetails,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "서버에 저장할 기기 내 목적지 목록",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DestinationSyncRequest.class),
                examples = @ExampleObject(value = """
                    {
                      "destinations": [
                        {
                          "clientDestinationId": 2,
                          "alias": "집",
                          "latitude": 37.4979,
                          "longitude": 127.0276
                        },
                        {
                          "clientDestinationId": 3,
                          "alias": "회사",
                          "latitude": 37.5665,
                          "longitude": 126.9780
                        }
                      ]
                    }
                    """)
            )
        )
        DestinationSyncRequest request
    );

    @Operation(
        summary = "목적지 수정",
        description = "목적지의 별칭 또는 위도 또는 경도를 수정합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "목적지 수정 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "DESTINATION200",
                      "message": "목적지 정보가 수정되었습니다.",
                      "result": {
                        "destinationId": 1,
                        "alias": "회사",
                        "latitude": 37.5665,
                        "longitude": 126.9780
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "목적지 ID 또는 요청 본문 형식 오류",
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(name = "invalidDestinationIdType", value = """
                        {
                          "isSuccess": false,
                          "code": "COMMON400",
                          "message": "잘못된 요청입니다."
                        }
                        """),
                    @ExampleObject(name = "invalidDestinationIdValue", value = """
                        {
                          "isSuccess": false,
                          "code": "DESTINATION400",
                          "message": "목적지 ID가 올바르지 않습니다."
                        }
                        """),
                    @ExampleObject(name = "invalidRequestBody", value = """
                        {
                          "isSuccess": false,
                          "code": "DESTINATION400",
                          "message": "수정할 목적지 정보가 올바르지 않습니다."
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
                      "code": "DESTINATION401",
                      "message": "인증되지 않은 사용자입니다."
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "목적지 접근 권한 없음",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "DESTINATION403",
                      "message": "목적지에 접근할 권한이 없습니다."
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "목적지를 찾을 수 없음",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "DESTINATION404",
                      "message": "목적지를 찾을 수 없습니다."
                    }
                    """)
            )
        )
    })
    ResponseEntity<CustomResponse<DestinationUpdateResponse>> updateDestination(
        @Parameter(hidden = true)
        CustomUserDetails customUserDetails,
        @Parameter(
            description = "수정하려는 목적지 ID",
            required = true,
            schema = @Schema(type = "integer", format = "int64", example = "1")
        )
        @PathVariable
        Long destinationId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "수정할 목적지 정보. alias, latitude, longitude 중 최소 1개 이상 포함되어야 합니다.",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DestinationUpdateRequest.class),
                examples = @ExampleObject(value = """
                    {
                      "alias": "회사",
                      "latitude": 37.5665,
                      "longitude": 126.9780
                    }
                    """)
            )
        )
        DestinationUpdateRequest request
    );

    @Operation(
        summary = "목적지 삭제",
        description = "사용자의 목적지를 삭제합니다.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "목적지 삭제 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "DESTINATION200",
                      "message": "삭제되었습니다."
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "목적지 ID 형식 오류",
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(name = "invalidDestinationIdType", value = """
                        {
                          "isSuccess": false,
                          "code": "COMMON400",
                          "message": "잘못된 요청입니다."
                        }
                        """),
                    @ExampleObject(name = "invalidDestinationIdValue", value = """
                        {
                          "isSuccess": false,
                          "code": "DESTINATION400",
                          "message": "목적지 ID가 올바르지 않습니다."
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
                      "code": "DESTINATION401",
                      "message": "인증되지 않은 사용자입니다."
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "목적지 접근 권한 없음",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "DESTINATION403",
                      "message": "목적지에 접근할 권한이 없습니다."
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "목적지를 찾을 수 없음",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "DESTINATION404",
                      "message": "목적지를 찾을 수 없습니다."
                    }
                    """)
            )
        )
    })
    ResponseEntity<CustomResponse<Void>> deleteDestination(
        @Parameter(hidden = true)
        CustomUserDetails customUserDetails,
        @Parameter(
            description = "삭제하려는 목적지 ID",
            required = true,
            schema = @Schema(type = "integer", format = "int64", example = "1")
        )
        @PathVariable
        Long destinationId
    );
}
