package com.ringout.api.stamp.domain;

import com.ringout.api.common.BaseEntity;
import com.ringout.api.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "stamp",
    indexes = {
        @Index(name = "idx_member_id_record_date", columnList = "member_id, record_date", unique = true)
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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id")
  private Member member;

  @Builder(access = AccessLevel.PRIVATE)
  public Stamp(LocalDate recordDate, GoalResult result, Member member) {
    this.recordDate = recordDate;
    this.result = result;
    this.member = member;
  }

  public static Stamp of(LocalDate recordDate, GoalResult result, Member member) {
    return Stamp.builder()
        .recordDate(recordDate)
        .result(result)
        .member(member)
        .build();
  }
}
