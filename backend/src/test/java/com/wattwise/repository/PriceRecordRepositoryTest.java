package com.wattwise.repository;

import com.wattwise.model.entity.PriceRecord;
import com.wattwise.model.enums.PriceSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PriceRecordRepositoryTest {

    @Autowired
    private PriceRecordRepository repository;

    private PriceRecord record(LocalDateTime ts, double total) {
        PriceRecord r = new PriceRecord();
        r.setTimestamp(ts);
        r.setPriceEurPerKwh(BigDecimal.valueOf(total));
        r.setTotalEurPerKwh(BigDecimal.valueOf(total));
        r.setSource(PriceSource.ESIOS);
        return r;
    }

    @Test
    void savesAndFindsByTimestampRange() {
        LocalDateTime base = LocalDateTime.of(2025, 1, 5, 0, 0);
        repository.save(record(base, 0.10));
        repository.save(record(base.plusMinutes(15), 0.12));
        repository.save(record(base.plusMinutes(30), 0.30));

        List<PriceRecord> found = repository.findByTimestampBetweenOrderByTimestampAsc(
                base, base.plusMinutes(15));

        assertThat(found).hasSize(2);
        assertThat(found.get(0).getTotalEurPerKwh()).isEqualByComparingTo("0.10");
        assertThat(found.get(1).getTotalEurPerKwh()).isEqualByComparingTo("0.12");
    }

    @Test
    void existsByTimestampDetectsDuplicates() {
        LocalDateTime base = LocalDateTime.of(2025, 1, 5, 0, 0);
        repository.save(record(base, 0.10));

        assertThat(repository.existsByTimestamp(base)).isTrue();
        assertThat(repository.existsByTimestamp(base.plusMinutes(15))).isFalse();
    }
}