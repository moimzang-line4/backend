package com.moimzang.domain.budget.exception;

import com.moimzang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BudgetErrorCode implements ErrorCode {

    LEDGER_409_AUTO_ENTRY_LOCKED(HttpStatus.CONFLICT, "LEDGER_409_AUTO_ENTRY_LOCKED",
            "자동 기록 내역은 수정할 수 없고, 원본이 살아있으면 삭제도 할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
