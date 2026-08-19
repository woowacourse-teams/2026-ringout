package com.ringout.api.stamp.repository;

import com.ringout.api.stamp.domain.Stamp;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StampRepository extends JpaRepository<Stamp, Long> {

  boolean existsByMemberIdAndRecordDate(Long memberId, LocalDate recordDate);

  @Query(
      value = """
            select s.record_date
            from stamp s
            where s.member_id = :memberId 
            and s.result = 'SUCCESS' 
            and s.record_date >= :startDate
            and s.record_date < :endDate
            order by s.record_date
          """,
      nativeQuery = true
  )
  List<LocalDate> findSuccessDatesByMemberIdAndPeriod(
      @Param("memberId") Long memberId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);
}
