package com.ringout.api.terms.dto.response;

import com.ringout.api.terms.domain.TermsType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TermsAgreementStatusResponse(
    @Schema(description = "약관 종류 (SERVICE: 서비스 이용약관, PRIVACY: 개인정보처리방침)", example = "SERVICE")
    TermsType type,
    @Schema(description = "최신 시행 약관의 id", example = "3")
    Long termsId,
    @Schema(description = "최신 시행 버전", example = "2026-09-01", type = "string")
    LocalDate latestVersion,
    @Schema(description = "사용자가 마지막으로 동의한 버전. 동의 기록이 없으면 null", example = "2026-08-01",
        type = "string", nullable = true)
    LocalDate agreedVersion,
    @Schema(description = "agreedVersion에 동의한 시각. 동의 기록이 없으면 null", example = "2026-08-10T14:32:11+09:00",
        nullable = true)
    OffsetDateTime agreedAt,
    @Schema(description = "최신 시행 버전에 동의하지 않았으면 true (한 번도 동의하지 않은 경우 포함)", example = "true")
    boolean needsReagreement
) {
}
