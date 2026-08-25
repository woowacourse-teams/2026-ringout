package com.ringout.api.user.service;

import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.dto.request.UpdateNicknameRequest;
import com.ringout.api.user.dto.response.UpdateNicknameResponse;
import com.ringout.api.user.dto.response.UserResponse;
import com.ringout.api.user.repository.UserRepository;
import com.ringout.api.user.status.UserErrorStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));

        return new UserResponse(user.getNickname().getValue(), user.getEmail());
    }

    @Transactional
    public User register(
        SocialProvider socialProvider,
        String providerId,
        String email,
        LocalDateTime registeredAt
    ) {
        if (userRepository.findBySocialProviderAndSocialProviderId(socialProvider, providerId)
            .isPresent()) {
            throw new GeneralException(UserErrorStatus.USER_ALREADY_EXISTS);
        }

        return userRepository.save(
            User.register(socialProvider, providerId, email, registeredAt)
        );
    }

    @Transactional
    public UpdateNicknameResponse updateNickname(
        Long userId,
        UpdateNicknameRequest request
    ) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));

        user.changeNickname(request.nickname());

        return new UpdateNicknameResponse(user.getNickname().getValue());
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));

        userRepository.delete(user);
    }
}
