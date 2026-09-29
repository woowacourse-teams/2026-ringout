package com.ringout.api.terms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record TermsAgreementsResponse(
    @Schema(description = "모든 약관의 최신 시행 버전에 동의했으면 true", example = "false")
    boolean allAgreed,
    @Schema(description = "약관별 동의 상태. 모든 약관을 SERVICE, PRIVACY 순서로 포함")
    List<TermsAgreementStatusResponse> agreements
) {

  public static TermsAgreementsResponse from(List<TermsAgreementStatusResponse> agreements) {
    boolean allAgreed = agreements.stream().noneMatch(TermsAgreementStatusResponse::needsReagreement);
    return new TermsAgreementsResponse(allAgreed, agreements);
  }
}
