package com.ringout.api.admin.service;

import com.ringout.api.admin.domain.AdminAccount;
import com.ringout.api.admin.domain.AdminStatus;
import com.ringout.api.admin.repository.AdminAccountRepository;
import com.ringout.api.admin.security.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAccountService {
    private final AdminAccountRepository repository;

    @Transactional
    public AdminAccount login(OidcUser user) {
        if (user.getSubject() == null || user.getSubject().isBlank()
                || !Boolean.TRUE.equals(user.getEmailVerified())
                || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new OAuth2AuthenticationException("invalid_google_identity");
        }
        AdminAccount account = repository.findByGoogleSubject(user.getSubject())
                .orElseGet(() -> repository.save(AdminAccount.pending(user.getSubject(), user.getEmail(), user.getFullName())));
        account.updateProfile(user.getEmail(), user.getFullName());
        return account;
    }

    @Transactional(readOnly = true)
    public boolean isApproved(AdminPrincipal principal) {
        return repository.existsByIdAndStatus(principal.id(), AdminStatus.APPROVED);
    }
}
