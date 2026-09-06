package com.wattwise.repository;

import com.wattwise.model.entity.AlertPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlertPreferenceRepository extends JpaRepository<AlertPreference, Long> {

    Optional<AlertPreference> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
