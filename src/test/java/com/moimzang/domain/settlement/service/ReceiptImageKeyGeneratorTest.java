package com.moimzang.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReceiptImageKeyGeneratorTest {

    private final ReceiptImageKeyGenerator generator = new ReceiptImageKeyGenerator();

    @Test
    void generate_연_월_UUID_확장자_형식의_키를_만든다() {
        LocalDate today = LocalDate.now();
        String prefix = "receipts/%d/%02d/".formatted(today.getYear(), today.getMonthValue());

        String key = generator.generate("jpg");

        assertThat(key).startsWith(prefix).endsWith(".jpg");
    }

    @Test
    void generate_호출마다_다른_키를_만든다() {
        assertThat(generator.generate("png")).isNotEqualTo(generator.generate("png"));
    }
}
