package com.ringout.api.stamp.service;

import com.ringout.api.stamp.domain.GoalResult;
import com.ringout.api.stamp.domain.Stamp;
import com.ringout.api.stamp.dto.response.CreateGiveUpResponse;
import com.ringout.api.stamp.dto.response.CreateStampResponse;
import com.ringout.api.stamp.repository.StampRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class StampService {

  private final StampRepository stampRepository;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CreateStampResponse createStamp(Long memberId, LocalDate completedAt) {
    if (stampRepository.existsByMemberIdAndRecordDate(memberId, completedAt)) {
      throw new IllegalStateException("이미 저장된 스탬프입니다.");
    }

    Stamp stamp = Stamp.of(completedAt, GoalResult.SUCCESS, memberId);
    stampRepository.save(stamp);

    return CreateStampResponse.of(completedAt, GoalResult.SUCCESS);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CreateGiveUpResponse createGiveUp(Long memberId, LocalDate terminatedAt) {
    if (stampRepository.existsByMemberIdAndRecordDate(memberId, terminatedAt)) {
      throw new IllegalStateException("이미 기록된 날짜입니다.");
    }

    Stamp stamp = Stamp.of(terminatedAt, GoalResult.FAILURE, memberId);
    stampRepository.save(stamp);

    return CreateGiveUpResponse.of(terminatedAt, GoalResult.FAILURE);
  }
}
