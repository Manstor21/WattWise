package com.wattwise.repository;

import com.wattwise.model.entity.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    Optional<PushToken> findFirstByToken(String token);

    List<PushToken> findByUserId(Long userId);

    void deleteByToken(String token);

    void deleteByUserId(Long userId);

    boolean existsByToken(String token);

    long countByUserId(Long userId);
}