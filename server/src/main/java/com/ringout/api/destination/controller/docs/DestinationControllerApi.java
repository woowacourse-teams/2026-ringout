package com.ringout.api.destination.controller.docs;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.SwaggerConfig;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.destination.dto.request.DestinationSyncRequest;
import com.ringout.api.destination.dto.request.DestinationUpsertRequest;
import com.ringout.api.destination.dto.response.DestinationResponse;
import com.ringout.api.destination.dto.response.DestinationSyncResponse;
import com.ringout.api.destination.dto.response.DestinationUpsertResponse;
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
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
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
                          "destinationId": "dest-1",
                          "alias": "런닝 장소",
                          "latitude": 37.5665,
                          "longitude": 126.9780
                        },
                        {
                          "destinationId": "dest-2",
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
        summary = "목적지 생성 또는 전체 수정 (Upsert)",
        description = """
            인증된 사용자와 경로의 destinationId 조합으로 목적지를 식별합니다.
            - 목적지가 없으면 destinationVersion과 관계없이 새로 생성합니다.
            - 목적지가 있고 요청 버전이 서버 버전보다 높으면 모든 필드를 요청 값으로 교체합니다.
            - 요청 버전이 서버 버전보다 낮거나 같으면 아무것도 바꾸지 않습니다.
            세 경우 모두 200을 반환하며, result는 요청 값이 아니라 서버에 최종 저장된 목적지 정보입니다.
            서버는 전달받은 destinationVersion을 그대로 저장하며 임의로 증가시키지 않습니다.
            동일한 (사용자, destinationId)에 대한 동시 요청에서도 목적지는 하나만 생성되며 최종 버전은 낮아지지 않습니다.
            destinationId는 사용자별로 고유하므로, 다른 사용자가 같은 destinationId를 쓰고 있어도 내 목적지로 새로 생성됩니다.
            """,
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "목적지 생성·수정 또는 낮거나 같은 버전이라 변경 없음. 모두 서버에 최종 저장된 정보를 반환합니다.",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "DESTINATION200",
                      "message": "목적지 동기화에 성공했습니다.",
                      "result": {
                        "destinationId": "destination-001",
                        "alias": "헬스장",
                        "latitude": 37.4979,
                        "longitude": 127.0276,
                        "destinationVersion": 2
                      }
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = """
                요청 형식 오류.
                JSON이 깨졌거나 필드 자료형이 맞지 않으면(예: destinationVersion이 문자열) 요청 바인딩 단계에서 COMMON400을 반환합니다.
                바인딩 이후 값 검증에 실패하면 DESTINATION400을 반환합니다.
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
                    @ExampleObject(name = "invalidDestinationId", value = """
                        {
                          "isSuccess": false,
                          "code": "DESTINATION400",
                          "message": "목적지 ID가 올바르지 않습니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "invalidDestinationInfo", value = """
                        {
                          "isSuccess": false,
                          "code": "DESTINATION400",
                          "message": "목적지 정보가 올바르지 않습니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "invalidDestinationVersion", value = """
                        {
                          "isSuccess": false,
                          "code": "DESTINATION400",
                          "message": "목적지 버전 값이 올바르지 않습니다.",
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
                          "code": "DESTINATION401",
                          "message": "인증되지 않은 사용자입니다.",
                          "result": null
                        }
                        """)
                }
            )
        )
    })
    ResponseEntity<CustomResponse<DestinationUpsertResponse>> upsertDestination(
        @Parameter(hidden = true)
        CustomUserDetails customUserDetails,
        @Parameter(
            description = "클라이언트가 생성한 사용자별 목적지 ID. 1자 이상 36자 이하이며 공백을 포함할 수 없습니다.",
            required = true,
            schema = @Schema(type = "string", minLength = 1, maxLength = 36, example = "destination-001")
        )
        @PathVariable
        String destinationId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "생성하거나 전체 수정할 목적지 정보. 부분 수정은 지원하지 않으므로 모든 필드가 필요합니다.",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DestinationUpsertRequest.class),
                examples = @ExampleObject(value = """
                    {
                      "alias": "헬스장",
                      "latitude": 37.4979,
                      "longitude": 127.0276,
                      "destinationVersion": 2
                    }
                    """)
            )
        )
        DestinationUpsertRequest request
    );

    @Operation(
        summary = "목적지 동기화",
        description = "사용자가 회원가입 시, 기기에 저장되어 있던 목적지 정보들을 서버에 저장합니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
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
                            "destinationId": "dest-2",
                            "alias": "집",
                            "latitude": 37.4979,
                            "longitude": 127.0276
                          },
                          {
                            "clientDestinationId": 3,
                            "destinationId": "dest-3",
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
        ),
        @ApiResponse(
            responseCode = "409",
            description = "사용자 안에서 중복된 목적지 ID",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "DESTINATION409",
                      "message": "이미 존재하는 목적지입니다.",
                      "result": null
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
                          "destinationId": "dest-2",
                          "alias": "집",
                          "latitude": 37.4979,
                          "longitude": 127.0276
                        },
                        {
                          "clientDestinationId": 3,
                          "destinationId": "dest-3",
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
        summary = "목적지 삭제",
        description = """
            사용자의 목적지를 삭제합니다.
            destinationId와 인증된 사용자로 삭제할 목적지를 식별합니다.
            동일한 목적지에 삭제 요청을 여러 번 보내도 최종 상태는 같으며, 이미 삭제되었거나 사용자에게 없는 목적지도 200을 반환합니다.
            """,
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "목적지 삭제 성공 또는 이미 삭제되었거나 사용자에게 없는 목적지",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": true,
                      "code": "DESTINATION200",
                      "message": "삭제되었습니다.",
                      "result": null
                    }
                    """)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "목적지 식별자가 공백을 포함하거나 36자를 초과하는 경우",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "isSuccess": false,
                      "code": "DESTINATION400",
                      "message": "목적지 아이디가 올바르지 않습니다.",
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
                examples = {
                    @ExampleObject(name = "invalidToken", value = """
                        {
                          "isSuccess": false,
                          "code": "AUTH401",
                          "message": "인증되지 않은 사용자입니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "expiredToken", value = """
                        {
                          "isSuccess": false,
                          "code": "AUTH401",
                          "message": "액세스 토큰이 만료되었습니다.",
                          "result": null
                        }
                        """),
                    @ExampleObject(name = "userNotFound", value = """
                        {
                          "isSuccess": false,
                          "code": "DESTINATION401",
                          "message": "인증되지 않은 사용자입니다.",
                          "result": null
                        }
                        """)
                }
            )
        )
    })
    ResponseEntity<CustomResponse<Void>> deleteDestination(
        @Parameter(hidden = true)
        CustomUserDetails customUserDetails,
        @Parameter(
            description = "삭제하려는 목적지 ID. 1자 이상 36자 이하이며 공백을 포함할 수 없습니다.",
            required = true,
            schema = @Schema(type = "string", minLength = 1, maxLength = 36, example = "dest-1")
        )
        @PathVariable
        String destinationId
    );
}
