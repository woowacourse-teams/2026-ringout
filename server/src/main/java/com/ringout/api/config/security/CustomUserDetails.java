package com.ringout.api.config.security;

import com.ringout.api.user.domain.Role;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CustomUserDetails implements UserDetails {

  private final Long userId;
  private final Role role;

  public CustomUserDetails(Long userId, Role role) {
    if (userId == null) {
      throw new IllegalArgumentException("회원 ID는 비어 있을 수 없습니다.");
    }
    if (role == null) {
      throw new IllegalArgumentException("회원 역할은 비어 있을 수 없습니다.");
    }

    this.userId = userId;
    this.role = role;
  }

  public Long getUserId() {
    return userId;
  }

  public Role getRole() {
    return role;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getPassword() {
    return null;
  }

  @Override
  public String getUsername() {
    return userId.toString();
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
