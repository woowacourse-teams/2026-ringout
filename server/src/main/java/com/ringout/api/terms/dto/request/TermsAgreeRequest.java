package com.ringout.api.terms.dto.request;

import com.ringout.api.terms.domain.TermsType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record TermsAgreeRequest(
    @NotEmpty
    List<TermsType> termsTypes,
    @NotBlank
    String agreedAt
) {
}
