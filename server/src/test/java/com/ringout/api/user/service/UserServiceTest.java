package com.ringout.api.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.dto.request.UpdateNicknameRequest;
import com.ringout.api.user.dto.response.UpdateNicknameResponse;
import com.ringout.api.user.dto.response.UserResponse;
import com.ringout.api.user.repository.UserRepository;
import com.ringout.api.user.status.UserErrorStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    void 회원_정보를_조회한다() {
        // given
        Long userId = 1L;
        User user = User.register(
            SocialProvider.KAKAO,
            "kakao-id",
            "uio6699@naver.com",
            LocalDateTime.of(2026, 8, 19, 10, 0)
        );
        user.changeNickname("서여");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        UserResponse response = userService.getUser(userId);

        // then
        assertThat(response.nickname()).isEqualTo("서여");
        assertThat(response.email()).isEqualTo("uio6699@naver.com");
    }

    @Test
    void 존재하지_않는_회원이면_USER404_예외가_발생한다() {
        // given
        Long userId = 1L;
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when // then
        assertThatThrownBy(() -> userService.getUser(userId))
            .isInstanceOf(GeneralException.class)
            .extracting(e -> ((GeneralException) e).getCode())
            .isEqualTo(UserErrorStatus.USER_NOT_FOUND);
    }

    @Test
    void 회원의_닉네임을_수정한다() {
        Long userId = 1L;
        User user = User.register(
            SocialProvider.KAKAO,
            "provider-id",
            "user@example.com",
            LocalDateTime.now()
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UpdateNicknameResponse response = userService.updateNickname(
            userId,
            new UpdateNicknameRequest("새로운닉네임")
        );

        assertThat(response.nickname()).isEqualTo("새로운닉네임");
        assertThat(user.getNickname().getValue()).isEqualTo("새로운닉네임");
    }

    @Test
    void 존재하지_않는_회원의_닉네임은_수정할_수_없다() {
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateNickname(
            userId,
            new UpdateNicknameRequest("새로운닉네임")
        ))
            .isInstanceOf(GeneralException.class)
            .extracting("code")
            .isEqualTo(UserErrorStatus.USER_NOT_FOUND);

    }

    @Test
    void 허용되지_않은_형식의_닉네임은_수정할_수_없다() {
        Long userId = 1L;
        User user = User.register(
            SocialProvider.KAKAO,
            "provider-id",
            "user@example.com",
            LocalDateTime.now()
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateNickname(
            userId,
            new UpdateNicknameRequest("잘못된_닉네임")
        ))
            .isInstanceOf(GeneralException.class)
            .extracting("code")
            .isEqualTo(UserErrorStatus.USER_NICKNAME_INVALID_FORMAT);
    }

    @Test
    void 회원을_탈퇴한다() {
        Long userId = 1L;
        User user = User.register(
            SocialProvider.KAKAO,
            "provider-id",
            "user@example.com",
            LocalDateTime.now()
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.withdraw(userId);

        verify(userRepository).delete(user);
    }

    @Test
    void 존재하지_않는_회원은_탈퇴할_수_없다() {
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdraw(userId))
            .isInstanceOf(GeneralException.class)
            .extracting("code")
            .isEqualTo(UserErrorStatus.USER_NOT_FOUND);
    }
}
