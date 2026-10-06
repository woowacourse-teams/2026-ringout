package com.ringout.api.terms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.user.repository.UserRepository;
import com.ringout.api.terms.domain.Terms;
import com.ringout.api.terms.domain.TermsType;
import com.ringout.api.terms.domain.TermsVersion;
import com.ringout.api.terms.domain.UserAgreement;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TermsServiceTest {

    @Mock
    private UserAgreementRepository userAgreementRepository;

    @Mock
    private TermsRepository termsRepository;

    @Mock
    private UserRepository userRepository;

    private TermsService termsService;

    private final Long userId = 1L;
    private final LocalDate today = LocalDate.of(2026, 8, 13);

    private Terms serviceTerms;
    private Terms privacyTerms;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        termsService = new TermsService(userAgreementRepository, termsRepository, userRepository, clock);

        serviceTerms = mock(Terms.class);
        privacyTerms = mock(Terms.class);
    }

    @Test
    void 필수_약관에_모두_동의하면_저장된다() {
        // given
        given(termsRepository.findByType(TermsType.SERVICE)).willReturn(List.of(serviceTerms));
        given(termsRepository.findByType(TermsType.PRIVACY)).willReturn(List.of(privacyTerms));
        given(serviceTerms.isEffectiveOn(today)).willReturn(true);
        given(privacyTerms.isEffectiveOn(today)).willReturn(true);
        given(serviceTerms.getId()).willReturn(10L);
        given(privacyTerms.getId()).willReturn(20L);
        given(userAgreementRepository.existsByUserIdAndTermsId(userId, 10L)).willReturn(false);
        given(userAgreementRepository.existsByUserIdAndTermsId(userId, 20L)).willReturn(false);

        TermsAgreeRequest request = new TermsAgreeRequest(List.of(TermsType.SERVICE, TermsType.PRIVACY), "2026-08-10");

        // when
        TermsAgreeResponse response = termsService.termsAgree(userId, request);

        // then
        assertThat(response.agreedTerms()).containsExactlyInAnyOrder("SERVICE", "PRIVACY");
        assertThat(response.agreedAt()).isEqualTo(LocalDate.of(2026, 8, 10));
        verify(userAgreementRepository).saveAll(any());
    }

    @Test
    void 동의_시각_형식이_올바르지_않으면_예외가_발생한다() {
        // given
        TermsAgreeRequest request = new TermsAgreeRequest(List.of(TermsType.SERVICE, TermsType.PRIVACY), "2026/08/10");

        // when // then
        assertThatThrownBy(() -> termsService.termsAgree(userId, request))
            .isInstanceOf(GeneralException.class)
            .extracting(e -> ((GeneralException) e).getCode())
            .isEqualTo(TermsErrorStatus.TERMS_AGREED_AT_INVALID);
    }

    @Test
    void 필수_약관중_일부만_요청하면_예외가_발생한다() {
        // given
        TermsAgreeRequest request = new TermsAgreeRequest(List.of(TermsType.SERVICE), "2026-08-10");

        // when // then
        assertThatThrownBy(() -> termsService.termsAgree(userId, request))
            .isInstanceOf(GeneralException.class)
            .extracting(e -> ((GeneralException) e).getCode())
            .isEqualTo(TermsErrorStatus.TERMS_NOT_AGREED);
    }

    @Test
    void 시행중인_약관이_없으면_예외가_발생한다() {
        // given
        given(termsRepository.findByType(TermsType.SERVICE)).willReturn(List.of(serviceTerms));
        given(serviceTerms.isEffectiveOn(today)).willReturn(false);

        TermsAgreeRequest request = new TermsAgreeRequest(List.of(TermsType.SERVICE, TermsType.PRIVACY), "2026-08-10");

        // when // then
        assertThatThrownBy(() -> termsService.termsAgree(userId, request))
            .isInstanceOf(GeneralException.class)
            .extracting(e -> ((GeneralException) e).getCode())
            .isEqualTo(TermsErrorStatus.TERMS_NOT_EFFECTIVE);
    }

    @Test
    void 동일_타입에_시행중인_약관이_여러개면_최신_버전이_적용된다() {
        // given
        Terms older = mock(Terms.class);
        Terms newer = mock(Terms.class);
        given(termsRepository.findByType(TermsType.SERVICE)).willReturn(List.of(older, newer));
        given(termsRepository.findByType(TermsType.PRIVACY)).willReturn(List.of(privacyTerms));
        given(older.isEffectiveOn(today)).willReturn(true);
        given(newer.isEffectiveOn(today)).willReturn(true);
        given(older.isNewerThan(newer)).willReturn(false);
        given(newer.getId()).willReturn(99L);
        given(privacyTerms.isEffectiveOn(today)).willReturn(true);
        given(privacyTerms.getId()).willReturn(20L);
        given(userAgreementRepository.existsByUserIdAndTermsId(userId, 99L)).willReturn(false);
        given(userAgreementRepository.existsByUserIdAndTermsId(userId, 20L)).willReturn(false);

        TermsAgreeRequest request = new TermsAgreeRequest(List.of(TermsType.SERVICE, TermsType.PRIVACY), "2026-08-10");

        // when
        termsService.termsAgree(userId, request);

        // then
        verify(userAgreementRepository).existsByUserIdAndTermsId(userId, 99L);
    }

    @Test
    void 이미_모두_동의한_약관이면_예외가_발생한다() {
        // given
        given(termsRepository.findByType(TermsType.SERVICE)).willReturn(List.of(serviceTerms));
        given(termsRepository.findByType(TermsType.PRIVACY)).willReturn(List.of(privacyTerms));
        given(serviceTerms.isEffectiveOn(today)).willReturn(true);
        given(privacyTerms.isEffectiveOn(today)).willReturn(true);
        given(serviceTerms.getId()).willReturn(10L);
        given(privacyTerms.getId()).willReturn(20L);
        given(userAgreementRepository.existsByUserIdAndTermsId(userId, 10L)).willReturn(true);
        given(userAgreementRepository.existsByUserIdAndTermsId(userId, 20L)).willReturn(true);

        TermsAgreeRequest request = new TermsAgreeRequest(List.of(TermsType.SERVICE, TermsType.PRIVACY), "2026-08-10");

        // when // then
        assertThatThrownBy(() -> termsService.termsAgree(userId, request))
            .isInstanceOf(GeneralException.class)
            .extracting(e -> ((GeneralException) e).getCode())
            .isEqualTo(TermsErrorStatus.TERMS_ALREADY_AGREED);
        verify(userAgreementRepository, never()).saveAll(any());
    }

    @Test
    void 일부만_이미_동의한_경우_나머지만_저장된다() {
        // given
        given(termsRepository.findByType(TermsType.SERVICE)).willReturn(List.of(serviceTerms));
        given(termsRepository.findByType(TermsType.PRIVACY)).willReturn(List.of(privacyTerms));
        given(serviceTerms.isEffectiveOn(today)).willReturn(true);
        given(privacyTerms.isEffectiveOn(today)).willReturn(true);
        given(serviceTerms.getId()).willReturn(10L);
        given(privacyTerms.getId()).willReturn(20L);
        given(userAgreementRepository.existsByUserIdAndTermsId(userId, 10L)).willReturn(true);
        given(userAgreementRepository.existsByUserIdAndTermsId(userId, 20L)).willReturn(false);

        TermsAgreeRequest request = new TermsAgreeRequest(List.of(TermsType.SERVICE, TermsType.PRIVACY), "2026-08-10");

        // when
        TermsAgreeResponse response = termsService.termsAgree(userId, request);

        // then
        assertThat(response.agreedTerms()).containsExactlyInAnyOrder("SERVICE", "PRIVACY");
        verify(userAgreementRepository).saveAll(any());
    }

    @Test
    void 필수_약관에_모두_동의했으면_true를_반환한다() {
        // given
        given(termsRepository.findByType(TermsType.SERVICE)).willReturn(List.of(serviceTerms));
        given(termsRepository.findByType(TermsType.PRIVACY)).willReturn(List.of(privacyTerms));
        given(serviceTerms.isEffectiveOn(today)).willReturn(true);
        given(privacyTerms.isEffectiveOn(today)).willReturn(true);
        given(serviceTerms.getId()).willReturn(10L);
        given(privacyTerms.getId()).willReturn(20L);
        given(userAgreementRepository.existsByUserIdAndTermsId(userId, 10L)).willReturn(true);
        given(userAgreementRepository.existsByUserIdAndTermsId(userId, 20L)).willReturn(true);

        // when
        CheckRequiredTermsAgreedResponse response = termsService.checkRequiredTermsAgreed(userId);

        // then
        assertThat(response.agreements()).isTrue();
    }

    @Test
    void 필수_약관중_하나라도_동의하지_않았으면_false를_반환한다() {
        // given
        given(termsRepository.findByType(TermsType.SERVICE)).willReturn(List.of(serviceTerms));
        given(serviceTerms.isEffectiveOn(today)).willReturn(true);
        given(serviceTerms.getId()).willReturn(10L);
        given(userAgreementRepository.existsByUserIdAndTermsId(userId, 10L)).willReturn(false);

        // when
        CheckRequiredTermsAgreedResponse response = termsService.checkRequiredTermsAgreed(userId);

        // then
        assertThat(response.agreements()).isFalse();
    }

    @Test
    void 필수_약관중_시행중인_버전이_없으면_예외가_발생한다() {
        // given
        given(termsRepository.findByType(TermsType.SERVICE)).willReturn(List.of(serviceTerms));
        given(serviceTerms.isEffectiveOn(today)).willReturn(false);

        // when // then
        assertThatThrownBy(() -> termsService.checkRequiredTermsAgreed(userId))
            .isInstanceOf(GeneralException.class)
            .extracting(e -> ((GeneralException) e).getCode())
            .isEqualTo(TermsErrorStatus.TERMS_NOT_EFFECTIVE);
    }

    @Nested
    class 최신_약관_동의_여부_조회 {

        private final Terms serviceV1 = termsWithId(10L, TermsType.SERVICE, LocalDate.of(2026, 8, 1));
        private final Terms privacyV1 = termsWithId(20L, TermsType.PRIVACY, LocalDate.of(2026, 8, 1));
        private final LocalDateTime agreedAt = LocalDateTime.of(2026, 8, 10, 14, 32, 11);

        @Test
        void 모든_약관의_최신_버전에_동의했으면_재동의가_필요없다() {
            // given
            givenTerms(List.of(serviceV1), List.of(privacyV1));
            given(userAgreementRepository.findActiveWithTermsByUserId(userId))
                .willReturn(List.of(agreementOf(serviceV1, agreedAt), agreementOf(privacyV1, agreedAt)));

            // when
            TermsAgreementsResponse response = termsService.getTermsAgreements(userId);

            // then
            assertThat(response.allAgreed()).isTrue();
            assertThat(response.agreements()).containsExactly(
                new TermsAgreementStatusResponse(TermsType.SERVICE, 10L, LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 8, 1), OffsetDateTime.parse("2026-08-10T14:32:11Z"), false),
                new TermsAgreementStatusResponse(TermsType.PRIVACY, 20L, LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 8, 1), OffsetDateTime.parse("2026-08-10T14:32:11Z"), false)
            );
        }

        @Test
        void 새_버전이_시행되면_이전_버전_동의자는_재동의가_필요하다() {
            // given
            Terms serviceV2 = termsWithId(11L, TermsType.SERVICE, LocalDate.of(2026, 8, 12));
            givenTerms(List.of(serviceV1, serviceV2), List.of(privacyV1));
            given(userAgreementRepository.findActiveWithTermsByUserId(userId))
                .willReturn(List.of(agreementOf(serviceV1, agreedAt), agreementOf(privacyV1, agreedAt)));

            // when
            TermsAgreementsResponse response = termsService.getTermsAgreements(userId);

            // then
            assertThat(response.allAgreed()).isFalse();
            assertThat(response.agreements().get(0)).isEqualTo(new TermsAgreementStatusResponse(
                TermsType.SERVICE, 11L, LocalDate.of(2026, 8, 12),
                LocalDate.of(2026, 8, 1), OffsetDateTime.parse("2026-08-10T14:32:11Z"), true));
            assertThat(response.agreements().get(1).needsReagreement()).isFalse();
        }

        @Test
        void 여러_버전에_동의했으면_가장_최신_동의_버전과_시각을_반환한다() {
            // given
            Terms serviceV2 = termsWithId(11L, TermsType.SERVICE, LocalDate.of(2026, 8, 12));
            LocalDateTime reagreedAt = LocalDateTime.of(2026, 8, 12, 9, 0);
            givenTerms(List.of(serviceV1, serviceV2), List.of(privacyV1));
            given(userAgreementRepository.findActiveWithTermsByUserId(userId)).willReturn(List.of(
                agreementOf(serviceV2, reagreedAt), agreementOf(serviceV1, agreedAt), agreementOf(privacyV1, agreedAt)));

            // when
            TermsAgreementsResponse response = termsService.getTermsAgreements(userId);

            // then
            assertThat(response.allAgreed()).isTrue();
            assertThat(response.agreements().get(0).agreedVersion()).isEqualTo(LocalDate.of(2026, 8, 12));
            assertThat(response.agreements().get(0).agreedAt()).isEqualTo(OffsetDateTime.parse("2026-08-12T09:00:00Z"));
        }

        @Test
        void 동의_기록이_없는_약관은_동의_버전과_시각이_null이고_재동의가_필요하다() {
            // given
            givenTerms(List.of(serviceV1), List.of(privacyV1));
            given(userAgreementRepository.findActiveWithTermsByUserId(userId))
                .willReturn(List.of(agreementOf(serviceV1, agreedAt)));

            // when
            TermsAgreementsResponse response = termsService.getTermsAgreements(userId);

            // then
            assertThat(response.allAgreed()).isFalse();
            assertThat(response.agreements().get(1)).isEqualTo(new TermsAgreementStatusResponse(
                TermsType.PRIVACY, 20L, LocalDate.of(2026, 8, 1), null, null, true));
        }

        @Test
        void 시행일이_지나지_않은_버전은_최신_버전으로_보지_않는다() {
            // given
            Terms futureService = termsWithId(12L, TermsType.SERVICE, LocalDate.of(2026, 9, 1));
            givenTerms(List.of(serviceV1, futureService), List.of(privacyV1));
            given(userAgreementRepository.findActiveWithTermsByUserId(userId))
                .willReturn(List.of(agreementOf(serviceV1, agreedAt), agreementOf(privacyV1, agreedAt)));

            // when
            TermsAgreementsResponse response = termsService.getTermsAgreements(userId);

            // then
            assertThat(response.allAgreed()).isTrue();
            assertThat(response.agreements().get(0).termsId()).isEqualTo(10L);
            assertThat(response.agreements().get(0).latestVersion()).isEqualTo(LocalDate.of(2026, 8, 1));
        }

        @Test
        void 시행_중인_약관이_없으면_서버_오류가_발생한다() {
            // given
            given(userAgreementRepository.findActiveWithTermsByUserId(userId)).willReturn(List.of());
            given(termsRepository.findByType(TermsType.SERVICE)).willReturn(List.of());

            // when // then
            assertThatThrownBy(() -> termsService.getTermsAgreements(userId))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(TermsErrorStatus.EFFECTIVE_TERMS_MISSING);
        }

        private void givenTerms(List<Terms> service, List<Terms> privacy) {
            given(termsRepository.findByType(TermsType.SERVICE)).willReturn(service);
            given(termsRepository.findByType(TermsType.PRIVACY)).willReturn(privacy);
        }

        private Terms termsWithId(Long id, TermsType type, LocalDate version) {
            Terms terms = Terms.of(type, TermsVersion.from(version));
            ReflectionTestUtils.setField(terms, "id", id);
            return terms;
        }

        private UserAgreement agreementOf(Terms terms, LocalDateTime createdAt) {
            UserAgreement userAgreement = UserAgreement.of(null, terms, terms.getVersion());
            ReflectionTestUtils.setField(userAgreement, "created_at", createdAt);
            return userAgreement;
        }
    }
}
