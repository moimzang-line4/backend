package com.moimzang.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.global.ai.ReceiptOcrResult;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReceiptOcrResultNormalizerTest {

    private final ReceiptOcrResultNormalizer normalizer = new ReceiptOcrResultNormalizer();

    @Test
    void normalize_세_필드_모두_정상이면_그대로_변환한다() {
        ReceiptOcrResult raw = new ReceiptOcrResult("스타벅스 신촌점", "2026-11-13T19:00:00", 50000);

        NormalizedReceiptOcrResult result = normalizer.normalize(raw);

        assertThat(result.storeName()).isEqualTo("스타벅스 신촌점");
        assertThat(result.transactedAt()).isEqualTo(LocalDateTime.of(2026, 11, 13, 19, 0, 0));
        assertThat(result.totalAmount()).isEqualTo(50000);
    }

    @Test
    void normalize_storeName이_null이면_null을_유지한다() {
        ReceiptOcrResult raw = new ReceiptOcrResult(null, "2026-11-13T19:00:00", 50000);
        assertThat(normalizer.normalize(raw).storeName()).isNull();
    }

    @Test
    void normalize_storeName이_빈값이면_null로_정규화한다() {
        ReceiptOcrResult raw = new ReceiptOcrResult("   ", "2026-11-13T19:00:00", 50000);
        assertThat(normalizer.normalize(raw).storeName()).isNull();
    }

    @Test
    void normalize_storeName_앞뒤_공백은_잘라낸다() {
        ReceiptOcrResult raw = new ReceiptOcrResult("  스타벅스  ", "2026-11-13T19:00:00", 50000);
        assertThat(normalizer.normalize(raw).storeName()).isEqualTo("스타벅스");
    }

    @Test
    void normalize_transactedAt이_null이면_null을_유지한다() {
        ReceiptOcrResult raw = new ReceiptOcrResult("스타벅스", null, 50000);
        assertThat(normalizer.normalize(raw).transactedAt()).isNull();
    }

    @Test
    void normalize_transactedAt_형식이_깨지면_null로_정규화한다() {
        ReceiptOcrResult raw = new ReceiptOcrResult("스타벅스", "2026년 11월 13일", 50000);
        assertThat(normalizer.normalize(raw).transactedAt()).isNull();
    }

    @Test
    void normalize_totalAmount가_null이면_null을_유지한다() {
        ReceiptOcrResult raw = new ReceiptOcrResult("스타벅스", "2026-11-13T19:00:00", null);
        assertThat(normalizer.normalize(raw).totalAmount()).isNull();
    }

    @Test
    void normalize_totalAmount가_0이면_null로_정규화한다() {
        ReceiptOcrResult raw = new ReceiptOcrResult("스타벅스", "2026-11-13T19:00:00", 0);
        assertThat(normalizer.normalize(raw).totalAmount()).isNull();
    }

    @Test
    void normalize_totalAmount가_음수면_null로_정규화한다() {
        ReceiptOcrResult raw = new ReceiptOcrResult("스타벅스", "2026-11-13T19:00:00", -1000);
        assertThat(normalizer.normalize(raw).totalAmount()).isNull();
    }
}
