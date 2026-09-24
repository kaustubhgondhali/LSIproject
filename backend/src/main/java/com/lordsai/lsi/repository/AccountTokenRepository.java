package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.AccountToken;
import com.lordsai.lsi.entity.enums.TokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    Optional<AccountToken> findByTokenHash(String tokenHash);

    void deleteByUserIdAndPurpose(Long userId, TokenPurpose purpose);
}
