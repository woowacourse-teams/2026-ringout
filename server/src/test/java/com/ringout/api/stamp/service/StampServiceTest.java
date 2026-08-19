package com.ringout.api.stamp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ringout.api.member.repository.MemberRepository;
import com.ringout.api.stamp.domain.GoalResult;
import com.ringout.api.stamp.domain.Stamp;
import com.ringout.api.stamp.dto.response.CreateGiveUpResponse;
import com.ringout.api.stamp.dto.response.CreateStampResponse;
import com.ringout.api.stamp.dto.response.FindMonthlyStampsResponse;
import com.ringout.api.stamp.repository.StampRepository;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StampServiceTest {

    @Mock
    private StampRepository stampRepository;

    @Mock
    private MemberRepository memberRepository;

    private StampService stampService;

    private final Long memberId = 1L;

    @BeforeEach
    void setUp() {
        stampService = new StampService(stampRepository, memberRepository);
    }

    @Test
    void 스탬프_생성에_성공한다() {
        // given
        LocalDate completedAt = LocalDate.of(2026, 8, 13);
        given(stampRepository.existsByMemberIdAndRecordDate(memberId, completedAt)).willReturn(false);

        // when
        CreateStampResponse response = stampService.createStamp(memberId, completedAt);

        // then
        assertThat(response.completedAt()).isEqualTo(completedAt);
        assertThat(response.goalResult()).isEqualTo(GoalResult.SUCCESS);
        verify(stampRepository).save(any(Stamp.class));
    }

    @Test
    void 이미_저장된_날짜에_스탬프를_생성하면_예외가_발생한다() {
        // given
        LocalDate completedAt = LocalDate.of(2026, 8, 13);
        given(stampRepository.existsByMemberIdAndRecordDate(memberId, completedAt)).willReturn(true);

        // when // then
        assertThatThrownBy(() -> stampService.createStamp(memberId, completedAt))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("이미 저장된 스탬프입니다.");
        verify(stampRepository, never()).save(any(Stamp.class));
    }

    @Test
    void 목표_실패_기록에_성공한다() {
        // given
        LocalDate terminatedAt = LocalDate.of(2026, 8, 13);
        given(stampRepository.existsByMemberIdAndRecordDate(memberId, terminatedAt)).willReturn(false);

        // when
        CreateGiveUpResponse response = stampService.createGiveUp(memberId, terminatedAt);

        // then
        assertThat(response.terminatedAt()).isEqualTo(terminatedAt);
        assertThat(response.goalResult()).isEqualTo(GoalResult.FAILURE);
        verify(stampRepository).save(any(Stamp.class));
    }

    @Test
    void 이미_기록된_날짜에_실패를_기록하면_예외가_발생한다() {
        // given
        LocalDate terminatedAt = LocalDate.of(2026, 8, 13);
        given(stampRepository.existsByMemberIdAndRecordDate(memberId, terminatedAt)).willReturn(true);

        // when // then
        assertThatThrownBy(() -> stampService.createGiveUp(memberId, terminatedAt))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("이미 기록된 날짜입니다.");
        verify(stampRepository, never()).save(any(Stamp.class));
    }

    @Test
    void 해당_월의_성공한_날짜_목록을_조회한다() {
        // given
        Integer year = 2026;
        Integer month = 8;
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 1);
        List<LocalDate> successDates = List.of(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 13));
        given(stampRepository.findSuccessDatesByMemberIdAndPeriod(memberId, startDate, endDate))
            .willReturn(successDates);

        // when
        FindMonthlyStampsResponse response = stampService.findMonthlyStamps(memberId, year, month);

        // then
        assertThat(response.year()).isEqualTo(year);
        assertThat(response.month()).isEqualTo(month);
        assertThat(response.successDates()).containsExactlyElementsOf(successDates);
    }

    @Test
    void 성공한_날짜가_없으면_빈_목록을_반환한다() {
        // given
        Integer year = 2026;
        Integer month = 8;
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 1);
        given(stampRepository.findSuccessDatesByMemberIdAndPeriod(memberId, startDate, endDate))
            .willReturn(Collections.emptyList());

        // when
        FindMonthlyStampsResponse response = stampService.findMonthlyStamps(memberId, year, month);

        // then
        assertThat(response.successDates()).isEmpty();
    }

    @Test
    void 연말_달을_조회하면_다음해_1월_직전까지를_기간으로_조회한다() {
        // given
        Integer year = 2026;
        Integer month = 12;
        LocalDate startDate = LocalDate.of(2026, 12, 1);
        LocalDate endDate = LocalDate.of(2027, 1, 1);
        given(stampRepository.findSuccessDatesByMemberIdAndPeriod(memberId, startDate, endDate))
            .willReturn(Collections.emptyList());

        // when
        stampService.findMonthlyStamps(memberId, year, month);

        // then
        verify(stampRepository).findSuccessDatesByMemberIdAndPeriod(memberId, startDate, endDate);
    }
}
