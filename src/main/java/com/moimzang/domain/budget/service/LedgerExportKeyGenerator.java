package com.moimzang.domain.budget.service;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

// 장부 엑셀 다운로드(API-075)가 저장하는 xlsx의 스토리지 키를 만든다. settlement 도메인의
// ReceiptImageKeyGenerator와 같은 연/월/UUID 패턴이고, 확장자는 항상 xlsx로 고정이라 받지 않는다
@Component
public class LedgerExportKeyGenerator {

    public String generate() {
        LocalDate today = LocalDate.now();
        return "ledger-exports/%d/%02d/%s.xlsx".formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID());
    }
}
