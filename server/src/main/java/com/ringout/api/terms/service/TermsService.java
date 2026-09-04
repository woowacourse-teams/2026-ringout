package com.ringout.api.terms.service;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import com.ringout.api.terms.domain.UserAgreement;
import com.ringout.api.terms.domain.Terms;
import com.ringout.api.terms.domain.TermsType;
import com.ringout.api.terms.dto.request.TermsAgreeRequest;
import com.ringout.api.terms.dto.response.CheckRequiredTermsAgreedResponse;
import com.ringout.api.terms.dto.response.TermsAgreeResponse;
import com.ringout.api.terms.repository.UserAgreementRepository;
import com.ringout.api.terms.repository.TermsRepository;
import com.ringout.api.terms.status.TermsErrorStatus;
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

    private final UserAgreementRepository userAgreementRepository;
    private final TermsRepository termsRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TermsAgreeResponse termsAgree(Long userId, TermsAgreeRequest request) {
        LocalDate agreedAt = parseAgreedAt(request.agreedAt());
        List<TermsType> requestedTypes = request.termsTypes();

        validateAllRequiredTermsIncluded(requestedTypes);

        List<Terms> effectiveTerms = requestedTypes.stream()
            .map(type -> findEffectiveTerms(type, LocalDate.now(clock)))
            .toList();

        List<Terms> newlyAgreedTerms = effectiveTerms.stream()
            .filter(
                terms -> !userAgreementRepository.existsByUserIdAndTermsId(userId, terms.getId()))
            .toList();

        validateHasNewAgreements(newlyAgreedTerms);

        saveAgreements(userId, newlyAgreedTerms);

        List<String> agreedTermTypeNames = requestedTypes.stream()
            .map(Enum::name)
            .toList();

        return TermsAgreeResponse.of(agreedTermTypeNames, agreedAt);
    }

    public CheckRequiredTermsAgreedResponse checkRequiredTermsAgreed(Long userId) {
        boolean agreement = TermsType.required().stream()
            .map(type -> findEffectiveTerms(type, LocalDate.now(clock)))
            .allMatch(
                terms -> userAgreementRepository.existsByUserIdAndTermsId(userId, terms.getId()));

        return CheckRequiredTermsAgreedResponse.of(agreement);
    }

    private static void validateHasNewAgreements(List<Terms> newlyAgreedTerms) {
        if (newlyAgreedTerms.isEmpty()) {
            throw new GeneralException(TermsErrorStatus.TERMS_ALREADY_AGREED);
        }
    }

    private void saveAgreements(Long userId, List<Terms> newlyAgreedTerms) {
        User user = userRepository.getReferenceById(userId);
        List<UserAgreement> agreements = newlyAgreedTerms.stream()
            .map(terms -> UserAgreement.of(user, terms, terms.getVersion()))
            .toList();
        userAgreementRepository.saveAll(agreements);
    }

    private LocalDate parseAgreedAt(String agreedAt) {
        try {
            return LocalDate.parse(agreedAt);
        } catch (DateTimeParseException e) {
            throw new GeneralException(TermsErrorStatus.TERMS_AGREED_AT_INVALID);
        }
    }

    private void validateAllRequiredTermsIncluded(List<TermsType> requestedTypes) {
        if (!TermsType.includeAllRequired(requestedTypes)) {
            throw new GeneralException(TermsErrorStatus.TERMS_NOT_AGREED);
        }
    }

    private Terms findEffectiveTerms(TermsType type, LocalDate referenceDate) {
        return termsRepository.findByType(type).stream()
            .filter(terms -> terms.isEffectiveOn(referenceDate))
            .reduce((a, b) -> a.isNewerThan(b) ? a : b)
            .orElseThrow(() -> new GeneralException(TermsErrorStatus.TERMS_NOT_EFFECTIVE));
    }
}
