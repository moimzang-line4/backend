package com.moimzang.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moimzang.domain.settlement.exception.SettlementErrorCode;
import com.moimzang.global.exception.CustomException;
import org.junit.jupiter.api.Test;

class ReceiptAmountValidatorTest {

    private final ReceiptAmountValidator validator = new ReceiptAmountValidator();

    @Test
    void validate_금액과_지원금이_정상범위면_예외를_던지지_않는다() {
        assertThatCode(() -> validator.validate(50000, 10000)).doesNotThrowAnyException();
    }

    @Test
    void validate_지원금이_0이어도_통과한다() {
        assertThatCode(() -> validator.validate(50000, 0)).doesNotThrowAnyException();
    }

    @Test
    void validate_지원금이_총액과_같아도_통과한다() {
        assertThatCode(() -> validator.validate(50000, 50000)).doesNotThrowAnyException();
    }

    @Test
    void validate_금액이_0이면_SETTLEMENT_400_AMOUNT_NOT_POSITIVE를_던진다() {
        assertThatThrownBy(() -> validator.validate(0, 0))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_400_AMOUNT_NOT_POSITIVE);
    }

    @Test
    void validate_금액이_음수면_SETTLEMENT_400_AMOUNT_NOT_POSITIVE를_던진다() {
        assertThatThrownBy(() -> validator.validate(-1000, 0))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_400_AMOUNT_NOT_POSITIVE);
    }

    @Test
    void validate_지원금이_음수면_SETTLEMENT_400_SUBSIDY_OUT_OF_RANGE를_던진다() {
        assertThatThrownBy(() -> validator.validate(50000, -1))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_400_SUBSIDY_OUT_OF_RANGE);
    }

    @Test
    void validate_지원금이_총액을_초과하면_SETTLEMENT_400_SUBSIDY_OUT_OF_RANGE를_던진다() {
        assertThatThrownBy(() -> validator.validate(50000, 50001))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_400_SUBSIDY_OUT_OF_RANGE);
    }
}
