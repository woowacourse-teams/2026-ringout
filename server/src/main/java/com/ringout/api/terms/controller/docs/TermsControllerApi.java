package com.ringout.api.terms.controller.docs;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.terms.dto.response.CheckRequiredTermsAgreedResponse;
import com.ringout.api.terms.dto.request.TermsAgreeRequest;
import com.ringout.api.terms.dto.response.TermsAgreeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "약관 (Terms)", description = "약관 동의 및 필수 약관 동의 여부 확인 API")
public interface TermsControllerApi {

  @Operation(
      summary = "약관 동의",
      description = """
          요청한 약관 타입들에 대한 회원의 동의를 저장합니다.

          - 요청에는 서비스의 **모든 필수 약관 타입**(`SERVICE`, `PRIVACY`)이 포함되어야 합니다. 일부만 보내면 실패합니다.
          - 각 약관 타입마다 오늘 날짜 기준으로 **현재 시행 중인 버전**(시행일이 오늘보다 이후가 아닌 것 중 가장 최신 버전)을 찾아 동의 이력을 저장합니다.
          - 이미 동의한 약관 타입은 다시 저장하지 않고 건너뛰며, 요청한 약관 중 **새로 저장할 동의가 하나도 없으면** 실패합니다.
          - `agreedAt`은 `yyyy-MM-dd` 형식의 날짜 문자열이어야 합니다.
          - Authorization 헤더는 인증/인가 기능이 구현되기 전까지 회원 ID를 그대로 담아 전달하는 임시 방식입니다. (예: `Bearer 1` 또는 `1`)
          """
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "약관 동의 저장 성공",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = @ExampleObject(
                  name = "약관 동의 성공",
                  value = """
                      {
                        "isSuccess": true,
                        "code": "TERMS201",
                        "message": "약관 동의가 저장되었습니다.",
                        "result": {
                          "agreedTerms": ["SERVICE", "PRIVACY"],
                          "agreedAt": "2026-08-10"
                        }
                      }
                      """
              )
          )
      ),
      @ApiResponse(
          responseCode = "400",
          description = "요청 값이 올바르지 않거나 약관 동의 조건을 만족하지 않는 경우",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = {
                  @ExampleObject(
                      name = "요청 값 형식 오류",
                      summary = "termsTypes가 비어있거나 agreedAt이 빈 문자열인 경우 (Bean Validation)",
                      value = """
                          {
                            "isSuccess": false,
                            "code": "COMMON400",
                            "message": "잘못된 요청입니다.",
                            "result": {
                              "termsTypes": "must not be empty",
                              "agreedAt": "must not be blank"
                            }
                          }
                          """
                  ),
                  @ExampleObject(
                      name = "필수 약관 미포함",
                      summary = "SERVICE, PRIVACY 중 일부만 요청한 경우",
                      value = """
                          {
                            "isSuccess": false,
                            "code": "TERMS400",
                            "message": "필수 약관에 모두 동의해야 합니다."
                          }
                          """
                  ),
                  @ExampleObject(
                      name = "동의 시각 형식 오류",
                      summary = "agreedAt이 yyyy-MM-dd 형식이 아닌 경우",
                      value = """
                          {
                            "isSuccess": false,
                            "code": "TERMS400",
                            "message": "동의 시각이 올바르지 않습니다."
                          }
                          """
                  ),
                  @ExampleObject(
                      name = "시행 중인 약관 없음",
                      summary = "요청한 약관 타입에 오늘 기준으로 시행 중인 버전이 없는 경우",
                      value = """
                          {
                            "isSuccess": false,
                            "code": "TERMS400",
                            "message": "현재 시행 중인 약관이 아닙니다."
                          }
                          """
                  )
              }
          )
      ),
      @ApiResponse(
          responseCode = "409",
          description = "요청한 약관에 이미 모두 동의한 경우",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = @ExampleObject(
                  name = "이미 동의한 약관",
                  value = """
                      {
                        "isSuccess": false,
                        "code": "TERMS409",
                        "message": "이미 동의한 약관입니다."
                      }
                      """
              )
          )
      )
  })
  ResponseEntity<CustomResponse<TermsAgreeResponse>> termsAgree(
      @Parameter(
          name = "Authorization",
          description = "임시 인증 헤더입니다. 인증/인가 기능이 구현되기 전까지 회원 ID를 그대로 전달합니다.",
          example = "Bearer 1",
          in = ParameterIn.HEADER
      )
      String authorization,

      @RequestBody(
          description = "동의할 약관 타입 목록과 동의 시각",
          required = true,
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = @ExampleObject(
                  name = "약관 동의 요청",
                  value = """
                      {
                        "termsTypes": ["SERVICE", "PRIVACY"],
                        "agreedAt": "2026-08-10"
                      }
                      """
              )
          )
      )
      TermsAgreeRequest termsAgreeRequest
  );

  @Operation(
      summary = "필수 약관 전체 동의 여부 확인",
      description = """
          현재 시행 중인 모든 필수 약관 타입(`SERVICE`, `PRIVACY`)에 대해 회원이 전부 동의했는지 확인합니다.

          - 필수 약관 타입 중 하나라도 오늘 날짜 기준으로 시행 중인 버전이 없으면 오류가 발생합니다.
          - Authorization 헤더는 인증/인가 기능이 구현되기 전까지 회원 ID를 그대로 담아 전달하는 임시 방식입니다. (예: `Bearer 1` 또는 `1`)
          """
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "필수 약관 동의 여부 조회 성공",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = {
                  @ExampleObject(
                      name = "모든 필수 약관에 동의함",
                      value = """
                          {
                            "isSuccess": true,
                            "code": "COMMON200",
                            "message": "성공입니다.",
                            "result": {
                              "agreements": true
                            }
                          }
                          """
                  ),
                  @ExampleObject(
                      name = "동의하지 않은 필수 약관이 있음",
                      value = """
                          {
                            "isSuccess": true,
                            "code": "COMMON200",
                            "message": "성공입니다.",
                            "result": {
                              "agreements": false
                            }
                          }
                          """
                  )
              }
          )
      ),
      @ApiResponse(
          responseCode = "400",
          description = "필수 약관 중 현재 시행 중인 버전이 없는 경우",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = @ExampleObject(
                  name = "시행 중인 약관 없음",
                  value = """
                      {
                        "isSuccess": false,
                        "code": "TERMS400",
                        "message": "현재 시행 중인 약관이 아닙니다."
                      }
                      """
              )
          )
      )
  })
  ResponseEntity<CustomResponse<CheckRequiredTermsAgreedResponse>> checkRequiredTermsAgreed(
      @Parameter(
          name = "Authorization",
          description = "임시 인증 헤더입니다. 인증/인가 기능이 구현되기 전까지 회원 ID를 그대로 전달합니다.",
          example = "Bearer 1",
          in = ParameterIn.HEADER
      )
      String authorization
  );
}
