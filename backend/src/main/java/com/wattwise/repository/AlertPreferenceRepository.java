package com.wattwise.repository;

import com.wattwise.model.entity.AlertPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlertPreferenceRepository extends JpaRepository<AlertPreference, Long> {

    Optional<AlertPreference> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /** Elimina las preferencias de alerta que apuntan a un electrodoméstico (usado antes del borrado de electrodomésticos; ver FK NO ACTION en V1). */
    void deleteByApplianceId(Long applianceId);
}
