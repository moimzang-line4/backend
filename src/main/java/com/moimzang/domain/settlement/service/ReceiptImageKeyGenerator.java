package com.moimzang.domain.settlement.service;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

// 영수증 인식(API-036)이 저장하는 이미지의 스토리지 키를 만든다. file 도메인의
// FileService.buildStorageKey와 같은 연/월/UUID/확장자 패턴이지만, 이 API는 File 엔티티를
// 만들지 않아 file 패키지를 호출할 이유가 없으므로 settlement 쪽에 따로 둔다
@Component
public class ReceiptImageKeyGenerator {

    public String generate(String extension) {
        LocalDate today = LocalDate.now();
        return "receipts/%d/%02d/%s.%s".formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID(), extension);
    }
}