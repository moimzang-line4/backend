package com.moimzang.domain.settlement.exception;

import com.moimzang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SettlementErrorCode implements ErrorCode {

    SETTLEMENT_400_AMOUNT_NOT_POSITIVE(HttpStatus.BAD_REQUEST, "SETTLEMENT_400_AMOUNT_NOT_POSITIVE", "금액은 양의 정수여야 합니다."),
    SETTLEMENT_400_SUBSIDY_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "SETTLEMENT_400_SUBSIDY_OUT_OF_RANGE", "지원금은 0원 이상이며 영수증 금액 이하여야 합니다."),
    SETTLEMENT_409_RECEIPT_ALREADY_REGISTERED(HttpStatus.CONFLICT, "SETTLEMENT_409_RECEIPT_ALREADY_REGISTERED", "이미 영수증이 등록된 차수입니다."),
    SETTLEMENT_423_RECEIPT_LOCKED_BY_PAYMENT(HttpStatus.LOCKED, "SETTLEMENT_423_RECEIPT_LOCKED_BY_PAYMENT", "입금 내역이 있어 영수증을 수정할 수 없습니다."),
    SETTLEMENT_423_PAID_CANNOT_DELETE(HttpStatus.LOCKED, "SETTLEMENT_423_PAID_CANNOT_DELETE", "입금 완료 상태는 삭제할 수 없습니다."),
    SETTLEMENT_409_RECEIPT_NOT_REGISTERED(HttpStatus.CONFLICT, "SETTLEMENT_409_RECEIPT_NOT_REGISTERED", "영수증이 등록되지 않았습니다."),
    SETTLEMENT_423_PAID_MEMBER_LOCKED(HttpStatus.LOCKED, "SETTLEMENT_423_PAID_MEMBER_LOCKED", "입금 완료된 인원이 포함되어 있어 명단을 수정할 수 없습니다."),
    SETTLEMENT_423_ROUND_LOCKED_BY_PAYMENT(HttpStatus.LOCKED, "SETTLEMENT_423_ROUND_LOCKED_BY_PAYMENT", "차수에 연결된 입금 내역이 있어 삭제할 수 없습니다."),
    SETTLEMENT_409_UNREGISTERED_ROUND_INCLUDED(HttpStatus.CONFLICT, "SETTLEMENT_409_UNREGISTERED_ROUND_INCLUDED", "영수증이 등록되지 않은 차수가 포함되어 있습니다."),
    SETTLEMENT_409_ACCOUNT_NOT_SET(HttpStatus.CONFLICT, "SETTLEMENT_409_ACCOUNT_NOT_SET", "입금 안내 계좌가 설정되지 않았습니다."),
    SETTLEMENT_400_ROUND_SELECTION_REQUIRED(HttpStatus.BAD_REQUEST, "SETTLEMENT_400_ROUND_SELECTION_REQUIRED", "포함할 차수를 1개 이상 선택해주세요."),
    RECEIPT_502_RECOGNITION_FAILED(HttpStatus.BAD_GATEWAY, "RECEIPT_502_RECOGNITION_FAILED", "영수증 인식에 실패했습니다. 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
