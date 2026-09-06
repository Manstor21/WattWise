package com.wattwise.repository;

import com.wattwise.model.entity.Appliance;
import com.wattwise.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplianceRepository extends JpaRepository<Appliance, Long> {

    List<Appliance> findByUserIdOrderByCreatedAtAsc(Long userId);

    List<Appliance> findByUser(User user);

    long countByUserId(Long userId);
}
