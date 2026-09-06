package com.wattwise.repository;

import com.wattwise.model.entity.PriceRecord;
import com.wattwise.model.enums.PriceSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PriceRecordRepository extends JpaRepository<PriceRecord, Long> {

    Optional<PriceRecord> findByTimestamp(LocalDateTime timestamp);

    boolean existsByTimestamp(LocalDateTime timestamp);

    List<PriceRecord> findByDateOrderByTimestampAsc(LocalDate date);

    List<PriceRecord> findByTimestampBetweenOrderByTimestampAsc(LocalDateTime from, LocalDateTime to);

    List<PriceRecord> findBySourceAndTimestampAfterOrderByTimestampAsc(PriceSource source, LocalDateTime since);
}
