package com.moimzang.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.settlement.dto.request.ReceiptRegisterRequest;
import com.moimzang.domain.settlement.dto.response.ReceiptRegisterResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptResponse;
import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import com.moimzang.domain.settlement.exception.SettlementErrorCode;
import com.moimzang.domain.settlement.mapper.SettlementMapper;
import com.moimzang.domain.settlement.repository.SettlementTargetRepository;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import com.moimzang.global.storage.FileStorage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementTargetRepository settlementTargetRepository;

    @Mock
    private ScheduleRoundRepository scheduleRoundRepository;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private BilledAmountCalculator billedAmountCalculator;

    // mapper는 mock하지 않는다 — 매핑 누락을 테스트에서도 실제 생성 코드로 검증한다
    private final SettlementMapper settlementMapper = Mappers.getMapper(SettlementMapper.class);
    private final ReceiptAmountValidator receiptAmountValidator = new ReceiptAmountValidator();

    private SettlementService settlementService;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementService(
                settlementTargetRepository, scheduleRoundRepository, settlementMapper,
                receiptAmountValidator, billedAmountCalculator, fileStorage);
    }

    private ScheduleRound roundWithId(Long id) {
        ScheduleRound round = ScheduleRound.create(mock(Schedule.class), 1, "1차");
        ReflectionTestUtils.setField(round, "id", id);
        return round;
    }

    private SettlementTarget settlementTargetWithId(Long id, int billedAmount, PaymentStatus paymentStatus) {
        SettlementTarget target = BeanUtils.instantiateClass(SettlementTarget.class);
        ReflectionTestUtils.setField(target, "id", id);
        ReflectionTestUtils.setField(target, "billedAmount", billedAmount);
        ReflectionTestUtils.setField(target, "paymentStatus", paymentStatus);
        return target;
    }

    private ReceiptRegisterRequest.ReceiptRegisterRequestBuilder defaultRequest() {
        return ReceiptRegisterRequest.builder()
                .storeName("스타벅스 신촌점")
                .transactedAt(LocalDateTime.of(2026, 11, 13, 19, 0, 0))
                .totalAmount(50000)
                .clubSubsidyAmount(10000);
    }

    @Test
    void getReceipt_정상_조회하고_입금완료자가_없으면_isLocked_false다() {
        ScheduleRound round = roundWithId(1L);
        ReflectionTestUtils.setField(round, "totalAmount", 50000);
        ReflectionTestUtils.setField(round, "clubSubsidyAmount", 10000);
        ReflectionTestUtils.setField(round, "storeName", "스타벅스 신촌점");
        ReflectionTestUtils.setField(round, "imageUrl", "https://storage.local/files/1.jpg");
        ReflectionTestUtils.setField(round, "updatedAt", LocalDateTime.of(2026, 11, 13, 20, 0, 0));

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(1L, PaymentStatus.PAID)).willReturn(false);

        ReceiptResponse response = settlementService.getReceipt(1L);

        assertThat(response.getRoundId()).isEqualTo(1L);
        assertThat(response.getLabel()).isEqualTo("1차");
        assertThat(response.getStoreName()).isEqualTo("스타벅스 신촌점");
        assertThat(response.getTotalAmount()).isEqualTo(50000);
        assertThat(response.getClubSubsidyAmount()).isEqualTo(10000);
        assertThat(response.getImageUrl()).isEqualTo("https://storage.local/files/1.jpg");
        assertThat(response.getRegisteredAt()).isEqualTo(LocalDateTime.of(2026, 11, 13, 20, 0, 0));
        assertThat(response.getIsLocked()).isFalse();
    }

    @Test
    void getReceipt_입금완료자가_1명이라도_있으면_isLocked_true다() {
        ScheduleRound round = roundWithId(1L);

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(1L, PaymentStatus.PAID)).willReturn(true);

        ReceiptResponse response = settlementService.getReceipt(1L);

        assertThat(response.getIsLocked()).isTrue();
    }

    @Test
    void getReceipt_존재하지_않는_차수면_CommonErrorCode_NOT_FOUND를_던진다() {
        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.getReceipt(1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void registerReceipt_정상_등록하고_imageKey를_URL로_변환해_저장한다() {
        ScheduleRound round = roundWithId(1L);
        ReceiptRegisterRequest request = defaultRequest().imageKey("files/2026/11/receipt.jpg").build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(1L, PaymentStatus.PAID)).willReturn(false);
        given(settlementTargetRepository.findByScheduleRoundId(1L)).willReturn(List.of());
        given(fileStorage.toUrlOrNull("files/2026/11/receipt.jpg")).willReturn("https://storage.local/files/2026/11/receipt.jpg");

        ReceiptRegisterResponse response = settlementService.registerReceipt(1L, request);

        assertThat(response.getRoundId()).isEqualTo(1L);
        assertThat(response.getTotalAmount()).isEqualTo(50000);
        assertThat(response.getClubSubsidyAmount()).isEqualTo(10000);
        assertThat(round.getStoreName()).isEqualTo("스타벅스 신촌점");
        assertThat(round.getImageUrl()).isEqualTo("https://storage.local/files/2026/11/receipt.jpg");
        verify(fileStorage).toUrlOrNull("files/2026/11/receipt.jpg");
    }

    @Test
    void registerReceipt_imageKey_없이_등록하면_기존_이미지를_그대로_유지한다() {
        ScheduleRound round = roundWithId(1L);
        ReflectionTestUtils.setField(round, "imageUrl", "https://storage.local/files/old.jpg");
        ReceiptRegisterRequest request = defaultRequest().build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(1L, PaymentStatus.PAID)).willReturn(false);
        given(settlementTargetRepository.findByScheduleRoundId(1L)).willReturn(List.of());

        settlementService.registerReceipt(1L, request);

        assertThat(round.getImageUrl()).isEqualTo("https://storage.local/files/old.jpg");
        verify(fileStorage, never()).toUrlOrNull(any());
    }

    @Test
    void registerReceipt_금액이_바뀌면_기존_정산명단의_청구액을_재계산한다() {
        ScheduleRound round = roundWithId(1L);
        ReflectionTestUtils.setField(round, "totalAmount", 30000);
        ReflectionTestUtils.setField(round, "clubSubsidyAmount", 0);
        SettlementTarget target1 = settlementTargetWithId(10L, 15000, PaymentStatus.UNPAID);
        SettlementTarget target2 = settlementTargetWithId(11L, 15000, PaymentStatus.UNPAID);
        ReceiptRegisterRequest request = defaultRequest().totalAmount(50000).clubSubsidyAmount(10000).build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(1L, PaymentStatus.PAID)).willReturn(false);
        given(settlementTargetRepository.findByScheduleRoundId(1L)).willReturn(List.of(target1, target2));
        given(billedAmountCalculator.calculate(50000, 10000, 2)).willReturn(20000);

        settlementService.registerReceipt(1L, request);

        assertThat(target1.getBilledAmount()).isEqualTo(20000);
        assertThat(target2.getBilledAmount()).isEqualTo(20000);
        verify(billedAmountCalculator).calculate(50000, 10000, 2);
    }

    @Test
    void registerReceipt_금액이_그대로면_재계산하지_않는다() {
        ScheduleRound round = roundWithId(1L);
        ReflectionTestUtils.setField(round, "totalAmount", 50000);
        ReflectionTestUtils.setField(round, "clubSubsidyAmount", 10000);
        ReceiptRegisterRequest request = defaultRequest().build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(1L, PaymentStatus.PAID)).willReturn(false);

        settlementService.registerReceipt(1L, request);

        verify(settlementTargetRepository, never()).findByScheduleRoundId(any());
        verify(billedAmountCalculator, never()).calculate(any(Integer.class), any(Integer.class), any(Integer.class));
    }

    @Test
    void registerReceipt_금액이_바뀌어도_정산명단이_없으면_계산기를_호출하지_않는다() {
        ScheduleRound round = roundWithId(1L);
        ReflectionTestUtils.setField(round, "totalAmount", 30000);
        ReflectionTestUtils.setField(round, "clubSubsidyAmount", 0);
        ReceiptRegisterRequest request = defaultRequest().totalAmount(50000).clubSubsidyAmount(10000).build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(1L, PaymentStatus.PAID)).willReturn(false);
        given(settlementTargetRepository.findByScheduleRoundId(1L)).willReturn(List.of());

        settlementService.registerReceipt(1L, request);

        verify(billedAmountCalculator, never()).calculate(any(Integer.class), any(Integer.class), any(Integer.class));
    }

    @Test
    void registerReceipt_입금완료자가_있으면_SETTLEMENT_423_RECEIPT_LOCKED_BY_PAYMENT를_던진다() {
        ScheduleRound round = roundWithId(1L);
        ReceiptRegisterRequest request = defaultRequest().build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(1L, PaymentStatus.PAID)).willReturn(true);

        assertThatThrownBy(() -> settlementService.registerReceipt(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_423_RECEIPT_LOCKED_BY_PAYMENT);
        verify(settlementTargetRepository, never()).findByScheduleRoundId(any());
    }

    @Test
    void registerReceipt_이미_영수증이_등록된_차수여도_409를_던지지_않고_수정을_허용한다() {
        ScheduleRound round = roundWithId(1L);
        ReflectionTestUtils.setField(round, "totalAmount", 10000);
        ReflectionTestUtils.setField(round, "clubSubsidyAmount", 0);
        ReceiptRegisterRequest request = defaultRequest().build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(1L, PaymentStatus.PAID)).willReturn(false);
        given(settlementTargetRepository.findByScheduleRoundId(1L)).willReturn(List.of());

        ReceiptRegisterResponse response = settlementService.registerReceipt(1L, request);

        assertThat(response.getTotalAmount()).isEqualTo(50000);
        assertThat(round.getTotalAmount()).isEqualTo(50000);
    }

    @Test
    void registerReceipt_금액이_0이면_SETTLEMENT_400_AMOUNT_NOT_POSITIVE를_던진다() {
        ScheduleRound round = roundWithId(1L);
        ReceiptRegisterRequest request = defaultRequest().totalAmount(0).build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));

        assertThatThrownBy(() -> settlementService.registerReceipt(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_400_AMOUNT_NOT_POSITIVE);
    }

    @Test
    void registerReceipt_지원금이_총액을_초과하면_SETTLEMENT_400_SUBSIDY_OUT_OF_RANGE를_던진다() {
        ScheduleRound round = roundWithId(1L);
        ReceiptRegisterRequest request = defaultRequest().totalAmount(10000).clubSubsidyAmount(20000).build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));

        assertThatThrownBy(() -> settlementService.registerReceipt(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_400_SUBSIDY_OUT_OF_RANGE);
    }

    @Test
    void registerReceipt_존재하지_않는_차수면_CommonErrorCode_NOT_FOUND를_던진다() {
        ReceiptRegisterRequest request = defaultRequest().build();
        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.registerReceipt(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }
}
