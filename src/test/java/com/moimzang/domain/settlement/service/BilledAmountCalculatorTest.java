package com.moimzang.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BilledAmountCalculatorTest {

    private final BilledAmountCalculator calculator = new BilledAmountCalculator();

    @Test
    void calculate_나누어떨어지면_그대로_반환한다() {
        int billedAmount = calculator.calculate(10000, 0, 10);

        assertThat(billedAmount).isEqualTo(1000);
    }

    @Test
    void calculate_100원_미만은_버린다() {
        int billedAmount = calculator.calculate(33000, 0, 7);

        assertThat(billedAmount).isEqualTo(4700);
    }

    @Test
    void calculate_지원금이_영수증_금액과_같으면_0원이다() {
        int billedAmount = calculator.calculate(5000, 5000, 4);

        assertThat(billedAmount).isEqualTo(0);
    }

    @Test
    void calculate_인원이_1명이면_나눈_값_그대로_버림만_적용한다() {
        int billedAmount = calculator.calculate(12345, 345, 1);

        assertThat(billedAmount).isEqualTo(12000);
    }

    @Test
    void calculate_인원이_0명이면_예외를_던진다() {
        assertThatThrownBy(() -> calculator.calculate(10000, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
