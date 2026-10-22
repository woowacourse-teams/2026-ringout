package com.ringout.api.admin.repository;

import com.ringout.api.admin.domain.AdminAccount;
import com.ringout.api.admin.domain.AdminStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {
    Optional<AdminAccount> findByGoogleSubject(String googleSubject);
    boolean existsByIdAndStatus(Long id, AdminStatus status);
}
