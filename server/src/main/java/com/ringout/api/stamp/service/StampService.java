package com.ringout.api.stamp.service;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import com.ringout.api.stamp.domain.GoalResult;
import com.ringout.api.stamp.domain.Stamp;
import com.ringout.api.stamp.dto.response.CreateGiveUpResponse;
import com.ringout.api.stamp.dto.response.CreateStampResponse;
import com.ringout.api.stamp.dto.response.FindMonthlyStampsResponse;
import com.ringout.api.stamp.repository.StampRepository;
import com.ringout.api.stamp.status.StampErrorStatus;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class StampService {

    private final StampRepository stampRepository;
    private final UserRepository userRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreateStampResponse createStamp(Long userId, LocalDate completedAt) {
        if (stampRepository.existsByUserIdAndRecordDate(userId, completedAt)) {
            throw new GeneralException(StampErrorStatus.STAMP_ALREADY_CREATED);
        }

        User user = userRepository.getReferenceById(userId);
        Stamp stamp = Stamp.of(completedAt, GoalResult.SUCCESS, user);
        stampRepository.save(stamp);

        return CreateStampResponse.of(completedAt, GoalResult.SUCCESS);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreateGiveUpResponse createGiveUp(Long userId, LocalDate terminatedAt) {
        if (stampRepository.existsByUserIdAndRecordDate(userId, terminatedAt)) {
            throw new GeneralException(StampErrorStatus.STAMP_ALREADY_CREATED);
        }

        User user = userRepository.getReferenceById(userId);
        Stamp stamp = Stamp.of(terminatedAt, GoalResult.FAILURE, user);
        stampRepository.save(stamp);

        return CreateGiveUpResponse.of(terminatedAt, GoalResult.FAILURE);
    }

    public FindMonthlyStampsResponse findMonthlyStamps(Long userId, Integer year, Integer month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1);

        List<LocalDate> successDates = stampRepository.findSuccessDatesByUserIdAndPeriod(userId,
            startDate, endDate);
        return FindMonthlyStampsResponse.of(year, month, successDates);
    }
}
