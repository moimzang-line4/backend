package com.moimzang.domain.settlement.service;

import org.springframework.stereotype.Component;

// 1인 청구액 = ⌊(영수증 금액 − 지원금) ÷ 인원수⌋를 100원 단위로 버림(decisions #3). API-034/038/041이 공유한다
@Component
public class BilledAmountCalculator {

    private static final int ROUND_UNIT = 100;

    public int calculate(int totalAmount, int subsidyAmount, int headcount) {
        if (headcount <= 0) {
            throw new IllegalArgumentException("인원수는 1명 이상이어야 한다: " + headcount);
        }
        int perPerson = (totalAmount - subsidyAmount) / headcount;
        return (perPerson / ROUND_UNIT) * ROUND_UNIT;
    }
}
