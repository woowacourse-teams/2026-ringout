package com.ringout.api.member.domain;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.common.BaseEntity;
import com.ringout.api.destination.domain.Destination;
import com.ringout.api.member.utils.NicknameGenerator;
import com.ringout.api.stamp.domain.Stamp;
import com.ringout.api.terms.domain.MemberAgreement;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "member",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_member_social_identity",
        columnNames = {"social_provider", "social_provider_id"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "social_provider", nullable = false, length = 20)
  private SocialProvider socialProvider;

  @Column(name = "social_provider_id", nullable = false, length = 255)
  private String socialProviderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Embedded
  private Nickname nickname;

  @Column(length = 320)
  private String email;

  @Column(nullable = false)
  private LocalDateTime lastLoginAt;

  @Column(nullable = false)
  private LocalDateTime lastAccessedAt;

  @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Stamp> stamps = new ArrayList<>();

  @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Destination> destinations = new ArrayList<>();

  @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<MemberAgreement> memberAgreements = new ArrayList<>();

  private Member(
      SocialProvider socialProvider,
      String socialProviderId,
      String email,
      LocalDateTime joinedAt
  ) {
    if (socialProvider == null) {
      throw new IllegalArgumentException("소셜 로그인 제공자는 비어 있을 수 없습니다.");
    }
    if (socialProviderId == null || socialProviderId.isBlank()) {
      throw new IllegalArgumentException("소셜 사용자 식별자는 비어 있을 수 없습니다.");
    }
    if (joinedAt == null) {
      throw new IllegalArgumentException("가입 시각은 비어 있을 수 없습니다.");
    }

    this.socialProvider = socialProvider;
    this.socialProviderId = socialProviderId;
    this.nickname = new Nickname(NicknameGenerator.generate());
    this.role = Role.USER;
    this.email = email;
    this.lastLoginAt = joinedAt;
    this.lastAccessedAt = joinedAt;
  }

  public static Member register(
      SocialProvider socialProvider,
      String socialProviderId,
      String email,
      LocalDateTime joinedAt
  ) {
    return new Member(socialProvider, socialProviderId, email, joinedAt);
  }

  public void login(LocalDateTime loginAt, String email) {
    if (loginAt == null) {
      throw new IllegalArgumentException("로그인 시각은 비어 있을 수 없습니다.");
    }
    this.lastLoginAt = loginAt;
    this.lastAccessedAt = loginAt;
    if (email != null && !email.isBlank()) {
      this.email = email;
    }
  }

  public void changeNickname(String nickname) {
    this.nickname = new Nickname(nickname);
  }
}
