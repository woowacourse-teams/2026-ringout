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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "이동 상태 (Movement)", description = "모임 회원 이동 상태 API")
public interface AlarmMovementControllerApi {

    @Operation(summary = "이동 행동 처리", description = "활성 알람에 대한 사용자의 이동 행동을 처리합니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "이동 상태 변경 성공", content = @Content(
            mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess":true,"code":"MOVEMENT200","message":"이동 상태 변경에 성공했습니다.","result":{"status":"MOVEMENT_STARTED"}}
                """))),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 이동 요청"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        @ApiResponse(responseCode = "403", description = "모임 회원이 아님"),
        @ApiResponse(responseCode = "404", description = "모임 또는 활성 알람을 찾을 수 없음"),
        @ApiResponse(responseCode = "409", description = "허용되지 않는 상태 전이")
    })
    ResponseEntity<CustomResponse<AlarmMovementResponse>> changeMovement(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        @PathVariable Long roomId,
        @RequestBody AlarmMovementRequest request
    );

    @Operation(summary = "모임 회원 이동 상태 조회", description = "현재 모임 회원의 이동 상태를 조회합니다.",
        security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "모임 회원 상태 조회 성공", content = @Content(
            mediaType = "application/json", examples = @ExampleObject(value = """
                {"isSuccess":true,"code":"ROOM200","message":"모임 회원 상태 조회에 성공했습니다.","result":{"members":[{"userId":1,"nickname":"성열","status":"IDLE"}]}}
                """))),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 모임 방 ID"),
        @ApiResponse(responseCode = "401", description = "인증되지 않았거나 만료된 토큰"),
        @ApiResponse(responseCode = "403", description = "모임 회원이 아님"),
        @ApiResponse(responseCode = "404", description = "모임을 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "이동 상태 데이터 정합성 오류")
    })
    ResponseEntity<CustomResponse<MemberMovementsResponse>> getMemberMovements(
        @Parameter(hidden = true) CustomUserDetails customUserDetails,
        @PathVariable Long roomId
    );
}
