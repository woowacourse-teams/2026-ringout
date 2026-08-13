package com.ringout.api.terms.service;

import com.ringout.api.common.response.code.status.ErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.terms.domain.MemberAgreement;
import com.ringout.api.terms.domain.Terms;
import com.ringout.api.terms.domain.TermsType;
import com.ringout.api.terms.dto.response.CheckRequiredTermsAgreedResponse;
import com.ringout.api.terms.dto.request.TermsAgreeRequest;
import com.ringout.api.terms.dto.response.TermsAgreeResponse;
import com.ringout.api.terms.repository.MemberAgreementRepository;
import com.ringout.api.terms.repository.TermsRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class TermsService {

  private final MemberAgreementRepository memberAgreementRepository;
  private final TermsRepository termsRepository;
  private final Clock clock;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TermsAgreeResponse termsAgree(Long memberId, TermsAgreeRequest request) {
    LocalDate agreedAt = parseAgreedAt(request.agreedAt());
    List<TermsType> requestedTypes = request.termsTypes();

    validateAllRequiredTermsIncluded(requestedTypes);

    List<Terms> effectiveTerms = requestedTypes.stream()
        .map(type -> findEffectiveTerms(type, LocalDate.now(clock)))
        .toList();

    List<Terms> newlyAgreedTerms = effectiveTerms.stream()
        .filter(
            terms -> !memberAgreementRepository.existsByMemberIdAndTermsId(memberId, terms.getId()))
        .toList();

    validateHasNewAgreements(newlyAgreedTerms);

    saveAgreements(memberId, newlyAgreedTerms);

    List<String> agreedTermTypeNames = requestedTypes.stream()
        .map(Enum::name)
        .toList();

    return TermsAgreeResponse.of(agreedTermTypeNames, agreedAt);
  }

  public CheckRequiredTermsAgreedResponse checkRequiredTermsAgreed(Long memberId) {
    boolean agreement = TermsType.required().stream()
        .map(type -> findEffectiveTerms(type, LocalDate.now(clock)))
        .allMatch(
            terms -> memberAgreementRepository.existsByMemberIdAndTermsId(memberId, terms.getId()));

    return CheckRequiredTermsAgreedResponse.of(agreement);
  }

  private static void validateHasNewAgreements(List<Terms> newlyAgreedTerms) {
    if (newlyAgreedTerms.isEmpty()) {
      throw new GeneralException(ErrorStatus.TERMS_ALREADY_AGREED);
    }
  }

  private void saveAgreements(Long memberId, List<Terms> newlyAgreedTerms) {
    List<MemberAgreement> agreements = newlyAgreedTerms.stream()
        .map(terms -> MemberAgreement.of(memberId, terms.getId(), terms.getType(),
            terms.getVersion()))
        .toList();
    memberAgreementRepository.saveAll(agreements);
  }

  private LocalDate parseAgreedAt(String agreedAt) {
    try {
      return LocalDate.parse(agreedAt);
    } catch (DateTimeParseException e) {
      throw new GeneralException(ErrorStatus.TERMS_AGREED_AT_INVALID);
    }
  }

  private void validateAllRequiredTermsIncluded(List<TermsType> requestedTypes) {
    if (!TermsType.includeAllRequired(requestedTypes)) {
      throw new GeneralException(ErrorStatus.TERMS_NOT_AGREED);
    }
  }

  private Terms findEffectiveTerms(TermsType type, LocalDate referenceDate) {
    return termsRepository.findByType(type).stream()
        .filter(terms -> terms.isEffectiveOn(referenceDate))
        .reduce((a, b) -> a.isNewerThan(b) ? a : b)
        .orElseThrow(() -> new GeneralException(ErrorStatus.TERMS_NOT_EFFECTIVE));
  }
}
