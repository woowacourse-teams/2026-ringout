package com.ringout.api.stamp.repository;

import com.ringout.api.stamp.domain.Stamp;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StampRepository extends JpaRepository<Stamp, Long> {

  boolean existsByMemberIdAndRecordDate(Long memberId, LocalDate recordDate);
}
