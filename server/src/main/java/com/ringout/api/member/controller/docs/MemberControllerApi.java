package com.ringout.api.member.controller.docs;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.SwaggerConfig;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.member.dto.request.UpdateNicknameRequest;
import com.ringout.api.member.dto.response.UpdateNicknameResponse;
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

@Tag(name = "회원 (Member)", description = "회원 정보 관리 API")
public interface MemberControllerApi {

  @Operation(
      summary = "내 닉네임 수정",
      description = "로그인한 회원의 닉네임을 최대 10자 이내로 변경합니다. 닉네임 중복은 허용됩니다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "닉네임 수정 성공"),
      @ApiResponse(
          responseCode = "400",
          description = "닉네임이 비어 있거나 10자를 초과한 경우",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = @ExampleObject(
                  value = """
                      {
                        "isSuccess": false,
                        "code": "COMMON400",
                        "message": "잘못된 요청입니다.",
                        "result": {
                          "nickname": "닉네임은 최대 10자입니다."
                        }
                      }
                      """
              )
          )
      ),
      @ApiResponse(responseCode = "404", description = "회원이 존재하지 않는 경우")
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
      description = "로그인한 회원의 정보를 삭제하고 탈퇴 처리합니다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "회원 탈퇴 성공"),
      @ApiResponse(responseCode = "404", description = "회원이 존재하지 않는 경우")
  })
  ResponseEntity<CustomResponse<Void>> withdraw(
      @Parameter(hidden = true) CustomUserDetails userDetails
  );
}
