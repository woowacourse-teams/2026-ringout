package com.ringout.api.stamp.controller.docs;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.stamp.dto.response.CreateGiveUpResponse;
import com.ringout.api.stamp.dto.response.CreateStampResponse;
import com.ringout.api.stamp.dto.response.FindMonthlyStampsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "스탬프 (Stamp)", description = "목적지 도착 성공/실패 기록 및 월별 스탬프 조회 API")
public interface StampControllerApi {

  @Operation(
      summary = "목적지 도착 성공 기록",
      description = """
          회원이 목적지에 도착 성공한 날짜를 스탬프로 저장합니다.

          - 동일한 회원이 같은 날짜에 이미 성공/실패 기록을 남긴 경우 실패합니다.
          """
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "도착 성공 스탬프 저장 성공",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = @ExampleObject(
                  name = "도착 성공 기록 성공",
                  value = """
                      {
                        "isSuccess": true,
                        "code": "STAMP201",
                        "message": "목적지 도착 성공 결과가 저장되었습니다.",
                        "result": {
                          "completedAt": "2026-08-13",
                          "goalResult": "SUCCESS"
                        }
                      }
                      """
              )
          )
      ),
      @ApiResponse(
          responseCode = "409",
          description = "해당 날짜에 이미 기록이 존재하는 경우",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = @ExampleObject(
                  name = "이미 저장된 스탬프",
                  value = """
                      {
                        "isSuccess": false,
                        "code": "STAMP409",
                        "message": "이미 기록된 날짜입니다.",
                        "result": "이미 저장된 스탬프입니다."
                      }
                      """
              )
          )
      )
  })
  ResponseEntity<CustomResponse<CreateStampResponse>> createStamp(
      @Parameter(hidden = true)
      CustomUserDetails customUserDetails,

      @Parameter(
          name = "completedAt",
          description = "목적지에 도착 성공한 날짜 (yyyy-MM-dd)",
          example = "2026-08-13",
          in = ParameterIn.QUERY,
          required = true
      )
      LocalDate completedAt
  );

  @Operation(
      summary = "목적지 도착 실패(포기) 기록",
      description = """
          회원이 목표를 포기했거나 도착에 실패한 날짜를 기록합니다.

          - 동일한 회원이 같은 날짜에 이미 성공/실패 기록을 남긴 경우 실패합니다.
          """
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "실패 기록 저장 성공",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = @ExampleObject(
                  name = "목표 실패 기록 성공",
                  value = """
                      {
                        "isSuccess": true,
                        "code": "STAMP201",
                        "message": "목표 실패가 기록되었습니다.",
                        "result": {
                          "terminatedAt": "2026-08-13",
                          "goalResult": "FAILURE"
                        }
                      }
                      """
              )
          )
      ),
      @ApiResponse(
          responseCode = "409",
          description = "해당 날짜에 이미 기록이 존재하는 경우",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = @ExampleObject(
                  name = "이미 기록된 날짜",
                  value = """
                      {
                        "isSuccess": false,
                        "code": "STAMP409",
                        "message": "이미 기록된 날짜입니다.",
                        "result": "이미 기록된 날짜입니다."
                      }
                      """
              )
          )
      )
  })
  ResponseEntity<CustomResponse<CreateGiveUpResponse>> createGiveUp(
      @Parameter(hidden = true)
      CustomUserDetails customUserDetails,

      @Parameter(
          name = "terminatedAt",
          description = "목표를 포기했거나 도착에 실패한 날짜 (yyyy-MM-dd)",
          example = "2026-08-13",
          in = ParameterIn.QUERY,
          required = true
      )
      LocalDate terminatedAt
  );

  @Operation(
      summary = "월별 스탬프(도착 성공 날짜) 조회",
      description = """
          요청한 연/월에 해당 회원이 목적지 도착에 성공한 날짜 목록을 조회합니다.

          - 실패(포기) 기록은 포함되지 않고, 도착 성공 날짜만 반환됩니다.
          """
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "월별 성공 날짜 조회 성공",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              examples = {
                  @ExampleObject(
                      name = "성공 날짜가 있는 경우",
                      value = """
                          {
                            "isSuccess": true,
                            "code": "COMMON200",
                            "message": "성공입니다.",
                            "result": {
                              "year": 2026,
                              "month": 8,
                              "successDates": ["2026-08-01", "2026-08-13"]
                            }
                          }
                          """
                  ),
                  @ExampleObject(
                      name = "성공 날짜가 없는 경우",
                      value = """
                          {
                            "isSuccess": true,
                            "code": "COMMON200",
                            "message": "성공입니다.",
                            "result": {
                              "year": 2026,
                              "month": 8,
                              "successDates": []
                            }
                          }
                          """
                  )
              }
          )
      )
  })
  ResponseEntity<CustomResponse<FindMonthlyStampsResponse>> findMonthlyStamps(
      @Parameter(hidden = true)
      CustomUserDetails customUserDetails,

      @Parameter(
          name = "year",
          description = "조회할 연도",
          example = "2026",
          in = ParameterIn.QUERY,
          required = true
      )
      Integer year,

      @Parameter(
          name = "month",
          description = "조회할 월 (1~12)",
          example = "8",
          in = ParameterIn.QUERY,
          required = true
      )
      Integer month
  );
}
