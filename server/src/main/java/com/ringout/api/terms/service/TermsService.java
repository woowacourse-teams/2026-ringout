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
import com.ringout.api.terms.dto.response.TermsAgreementStatusResponse;
import com.ringout.api.terms.dto.response.TermsAgreementsResponse;
import com.ringout.api.terms.repository.UserAgreementRepository;
import com.ringout.api.terms.repository.TermsRepository;
import com.ringout.api.terms.status.TermsErrorStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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

    public TermsAgreementsResponse getTermsAgreements(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<UserAgreement> userAgreements = userAgreementRepository.findActiveWithTermsByUserId(userId);

        List<TermsAgreementStatusResponse> agreements = Arrays.stream(TermsType.values())
            .map(type -> toAgreementStatus(type, findLatestEffectiveTerms(type, today), userAgreements))
            .toList();

        return TermsAgreementsResponse.from(agreements);
    }

    private TermsAgreementStatusResponse toAgreementStatus(TermsType type, Terms latestTerms,
        List<UserAgreement> userAgreements) {
        List<UserAgreement> agreementsOfType = userAgreements.stream()
            .filter(userAgreement -> userAgreement.getType() == type)
            .toList();
        Optional<UserAgreement> lastAgreement = agreementsOfType.stream()
            .reduce((a, b) -> a.getVersion().isAfter(b.getVersion()) ? a : b);
        boolean agreedToLatest = agreementsOfType.stream()
            .anyMatch(userAgreement -> userAgreement.getTerms().getId().equals(latestTerms.getId()));

        return new TermsAgreementStatusResponse(
            type,
            latestTerms.getId(),
            latestTerms.getVersion().getVersion(),
            lastAgreement.map(userAgreement -> userAgreement.getVersion().getVersion()).orElse(null),
            lastAgreement.map(this::toAgreedAt).orElse(null),
            !agreedToLatest
        );
    }

    private OffsetDateTime toAgreedAt(UserAgreement userAgreement) {
        return userAgreement.getCreated_at().atZone(clock.getZone()).toOffsetDateTime();
    }

    private Terms findLatestEffectiveTerms(TermsType type, LocalDate referenceDate) {
        return findEffectiveTermsIfExists(type, referenceDate)
            .orElseThrow(() -> {
                log.error("시행 중인 약관이 등록되어 있지 않습니다. type={}, referenceDate={}", type, referenceDate);
                return new GeneralException(TermsErrorStatus.EFFECTIVE_TERMS_MISSING);
            });
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
        return findEffectiveTermsIfExists(type, referenceDate)
            .orElseThrow(() -> new GeneralException(TermsErrorStatus.TERMS_NOT_EFFECTIVE));
    }

    private Optional<Terms> findEffectiveTermsIfExists(TermsType type, LocalDate referenceDate) {
        return termsRepository.findByType(type).stream()
            .filter(terms -> terms.isEffectiveOn(referenceDate))
            .reduce((a, b) -> a.isNewerThan(b) ? a : b);
    }
}
