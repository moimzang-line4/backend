package com.moimzang.domain.budget.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LedgerExportKeyGeneratorTest {

    private final LedgerExportKeyGenerator generator = new LedgerExportKeyGenerator();

    @Test
    void generate_연_월_UUID_형식의_xlsx_키를_만든다() {
        LocalDate today = LocalDate.now();
        String prefix = "ledger-exports/%d/%02d/".formatted(today.getYear(), today.getMonthValue());

        String key = generator.generate();

        assertThat(key).startsWith(prefix).endsWith(".xlsx");
    }

    @Test
    void generate_호출마다_다른_키를_만든다() {
        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }
}
