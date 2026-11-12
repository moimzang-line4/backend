package com.moimzang.domain.settlement.service;

import com.moimzang.domain.settlement.exception.SettlementErrorCode;
import com.moimzang.global.exception.CustomException;
import org.springframework.stereotype.Component;

// 영수증 등록(API-034)의 금액 검증. 표준 Bean Validation으로는 SETTLEMENT_400 전용 코드를
// 못 내려주므로(실패 시 전부 COMMON_400으로 묶임) Service가 직접 호출해 검증한다
@Component
public class ReceiptAmountValidator {

    public void validate(int totalAmount, int clubSubsidyAmount) {
        if (totalAmount <= 0) {
            throw new CustomException(SettlementErrorCode.SETTLEMENT_400_AMOUNT_NOT_POSITIVE);
        }
        if (clubSubsidyAmount < 0 || clubSubsidyAmount > totalAmount) {
            throw new CustomException(SettlementErrorCode.SETTLEMENT_400_SUBSIDY_OUT_OF_RANGE);
        }
    }
}
