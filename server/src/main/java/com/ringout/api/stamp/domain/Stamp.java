package com.ringout.api.stamp.domain;

import com.ringout.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "stamp",
    indexes = {
        @Index(name = "idx_member_id_record_date", columnList = "memberId, recordDate", unique = true)
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stamp extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "record_date", nullable = false, updatable = false)
  private LocalDate recordDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private GoalResult result;

  private Long memberId;

  @Builder(access = AccessLevel.PRIVATE)
  public Stamp(LocalDate recordDate, GoalResult result, Long memberId) {
    this.recordDate = recordDate;
    this.result = result;
    this.memberId = memberId;
  }

  public static Stamp of(LocalDate recordDate, GoalResult result, Long memberId) {
    return Stamp.builder()
        .recordDate(recordDate)
        .result(result)
        .memberId(memberId)
        .build();
  }
}
