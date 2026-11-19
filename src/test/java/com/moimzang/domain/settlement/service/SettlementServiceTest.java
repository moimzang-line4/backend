package com.moimzang.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.repository.RoundAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import com.moimzang.domain.budget.service.LedgerSyncService;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.domain.settlement.dto.request.AccountSetRequest;
import com.moimzang.domain.settlement.dto.request.PaymentConfirmRequest;
import com.moimzang.domain.settlement.dto.request.ReceiptRegisterRequest;
import com.moimzang.domain.settlement.dto.request.SettlementNoticeRequest;
import com.moimzang.domain.settlement.dto.request.SettlementTargetSaveRequest;
import com.moimzang.domain.settlement.dto.response.AccountResponse;
import com.moimzang.domain.settlement.dto.response.PaymentStatusResponse;
import com.moimzang.domain.settlement.dto.response.PaymentSummaryListResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptOcrResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptRegisterResponse;
import com.moimzang.domain.settlement.dto.response.ReceiptResponse;
import com.moimzang.domain.settlement.dto.response.SettlementHomeResponse;
import com.moimzang.domain.settlement.dto.response.SettlementNoticeResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetImportResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetListResponse;
import com.moimzang.domain.settlement.dto.response.SettlementTargetSaveResponse;
import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import com.moimzang.domain.settlement.enums.PaymentStatusFilter;
import com.moimzang.domain.settlement.exception.SettlementErrorCode;
import com.moimzang.domain.settlement.mapper.SettlementMapper;
import com.moimzang.domain.settlement.repository.PaymentSummary;
import com.moimzang.domain.settlement.repository.SettlementTargetRepository;
import com.moimzang.global.ai.ReceiptOcrClient;
import com.moimzang.global.ai.ReceiptOcrException;
import com.moimzang.global.ai.ReceiptOcrResult;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import com.moimzang.global.storage.FileStorage;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementTargetRepository settlementTargetRepository;

    @Mock
    private ScheduleRoundRepository scheduleRoundRepository;

    @Mock
    private RoundAttendeeRepository roundAttendeeRepository;

    @Mock
    private ScheduleAttendeeRepository scheduleAttendeeRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private LedgerSyncService ledgerSyncService;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private BilledAmountCalculator billedAmountCalculator;

    @Mock
    private ReceiptOcrClient receiptOcrClient;

    @Mock
    private ReceiptImageKeyGenerator receiptImageKeyGenerator;

    // mapper는 mock하지 않는다 — 매핑 누락을 테스트에서도 실제 생성 코드로 검증한다
    private final SettlementMapper settlementMapper = Mappers.getMapper(SettlementMapper.class);
    private final ReceiptAmountValidator receiptAmountValidator = new ReceiptAmountValidator();
    private final ReceiptImageValidator receiptImageValidator = new ReceiptImageValidator();
    private final ReceiptOcrResultNormalizer receiptOcrResultNormalizer = new ReceiptOcrResultNormalizer();

    private SettlementService settlementService;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementService(
                settlementTargetRepository, scheduleRoundRepository, roundAttendeeRepository, scheduleAttendeeRepository,
                scheduleRepository, ledgerEntryRepository, ledgerSyncService, settlementMapper, receiptAmountValidator,
                billedAmountCalculator, fileStorage, receiptOcrClient, receiptImageValidator, receiptImageKeyGenerator,
                receiptOcrResultNormalizer);
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

    private SettlementTarget settlementTargetWithAttendee(Long id, ScheduleAttendee attendee, int billedAmount,
                                                            PaymentStatus paymentStatus, LocalDateTime createdAt) {
        SettlementTarget target = BeanUtils.instantiateClass(SettlementTarget.class);
        ReflectionTestUtils.setField(target, "id", id);
        ReflectionTestUtils.setField(target, "scheduleAttendee", attendee);
        ReflectionTestUtils.setField(target, "billedAmount", billedAmount);
        ReflectionTestUtils.setField(target, "paymentStatus", paymentStatus);
        ReflectionTestUtils.setField(target, "createdAt", createdAt);
        return target;
    }

    // 일정 소속 비교(saveSettlementTargets)에만 쓰이는 스텁이라 그 경로를 타지 않는 테스트에서는
    // 쓰이지 않는다 — lenient로 strict stub 검사를 피한다
    private ScheduleAttendee attendeeWithId(Long id, Long scheduleId, String name) {
        Schedule schedule = mock(Schedule.class);
        lenient().when(schedule.getId()).thenReturn(scheduleId);
        ScheduleAttendee attendee = BeanUtils.instantiateClass(ScheduleAttendee.class);
        ReflectionTestUtils.setField(attendee, "id", id);
        ReflectionTestUtils.setField(attendee, "schedule", schedule);
        ReflectionTestUtils.setField(attendee, "name", name);
        return attendee;
    }

    private ScheduleRound roundWithId(Long id, Long scheduleId) {
        return roundWithId(id, scheduleId, 1, "1차");
    }

    private ScheduleRound roundWithId(Long id, Long scheduleId, Integer roundNo, String label) {
        Schedule schedule = mock(Schedule.class);
        lenient().when(schedule.getId()).thenReturn(scheduleId);
        ScheduleRound round = ScheduleRound.create(schedule, roundNo, label);
        ReflectionTestUtils.setField(round, "id", id);
        return round;
    }

    // scheduleTitle은 정산 공지 생성(generateSettlementNotice) 성공 경로에서만 쓰이고, 400/404/409로
    // 끝나는 테스트는 그 전에 던져서 쓰이지 않는다 — lenient로 strict stub 검사를 피한다
    private Schedule scheduleWithTitle(String title) {
        Schedule schedule = mock(Schedule.class);
        lenient().when(schedule.getTitle()).thenReturn(title);
        return schedule;
    }

    private void setReceiptAndAccount(ScheduleRound round, int totalAmount, int clubSubsidyAmount,
                                       String bankName, String accountNumber, String accountHolder) {
        ReflectionTestUtils.setField(round, "totalAmount", totalAmount);
        ReflectionTestUtils.setField(round, "clubSubsidyAmount", clubSubsidyAmount);
        ReflectionTestUtils.setField(round, "bankName", bankName);
        ReflectionTestUtils.setField(round, "accountNumber", accountNumber);
        ReflectionTestUtils.setField(round, "accountHolder", accountHolder);
    }

    private RoundAttendee checkedInRoundAttendeeWithAttendee(ScheduleRound round, ScheduleAttendee attendee) {
        RoundAttendee roundAttendee = RoundAttendee.create(round, attendee);
        ReflectionTestUtils.setField(roundAttendee, "isCheckedIn", true);
        return roundAttendee;
    }

    private ScheduleAttendee attendeeWithId(Long id, Long scheduleId, String name, LocalDateTime createdAt) {
        ScheduleAttendee attendee = attendeeWithId(id, scheduleId, name);
        ReflectionTestUtils.setField(attendee, "createdAt", createdAt);
        return attendee;
    }

    private SettlementTarget settlementTargetWithRound(Long id, ScheduleRound round, ScheduleAttendee attendee,
                                                         int billedAmount, PaymentStatus paymentStatus, LocalDateTime paidAt) {
        SettlementTarget target = BeanUtils.instantiateClass(SettlementTarget.class);
        ReflectionTestUtils.setField(target, "id", id);
        ReflectionTestUtils.setField(target, "scheduleRound", round);
        ReflectionTestUtils.setField(target, "scheduleAttendee", attendee);
        ReflectionTestUtils.setField(target, "billedAmount", billedAmount);
        ReflectionTestUtils.setField(target, "paymentStatus", paymentStatus);
        ReflectionTestUtils.setField(target, "paidAt", paidAt);
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
    void registerReceipt_등록_후_그_일정의_장부_자동기록을_재계산한다() {
        ScheduleRound round = roundWithId(1L, 10L);
        ReceiptRegisterRequest request = defaultRequest().build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(1L, PaymentStatus.PAID)).willReturn(false);
        given(settlementTargetRepository.findByScheduleRoundId(1L)).willReturn(List.of());

        settlementService.registerReceipt(1L, request);

        verify(ledgerSyncService).syncSchedule(10L);
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

    private MockMultipartFile receiptImage() {
        return new MockMultipartFile("image", "receipt.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }

    @Test
    void recognizeReceipt_정상_인식하면_정규화된_필드와_imageKey를_반환한다() {
        given(scheduleRoundRepository.existsById(1L)).willReturn(true);
        given(receiptOcrClient.recognize(any(), eq("image/jpeg")))
                .willReturn(new ReceiptOcrResult("스타벅스 신촌점", "2026-11-13T19:00:00", 50000));
        given(receiptImageKeyGenerator.generate("jpg")).willReturn("receipts/2026/11/abc.jpg");

        ReceiptOcrResponse response = settlementService.recognizeReceipt(1L, receiptImage());

        assertThat(response.getStoreName()).isEqualTo("스타벅스 신촌점");
        assertThat(response.getTransactedAt()).isEqualTo(LocalDateTime.of(2026, 11, 13, 19, 0, 0));
        assertThat(response.getTotalAmount()).isEqualTo(50000);
        assertThat(response.getImageKey()).isEqualTo("receipts/2026/11/abc.jpg");
        verify(fileStorage).store(eq("receipts/2026/11/abc.jpg"), any(), anyLong(), eq("image/jpeg"));
    }

    @Test
    void recognizeReceipt_일부_항목만_인식하면_그_항목만_null로_응답한다() {
        given(scheduleRoundRepository.existsById(1L)).willReturn(true);
        given(receiptOcrClient.recognize(any(), any()))
                .willReturn(new ReceiptOcrResult(null, "2026-11-13T19:00:00", 50000));
        given(receiptImageKeyGenerator.generate("jpg")).willReturn("receipts/2026/11/abc.jpg");

        ReceiptOcrResponse response = settlementService.recognizeReceipt(1L, receiptImage());

        assertThat(response.getStoreName()).isNull();
        assertThat(response.getTransactedAt()).isEqualTo(LocalDateTime.of(2026, 11, 13, 19, 0, 0));
        assertThat(response.getTotalAmount()).isEqualTo(50000);
    }

    @Test
    void recognizeReceipt_세_필드_모두_null이어도_200으로_응답하고_이미지는_저장한다() {
        given(scheduleRoundRepository.existsById(1L)).willReturn(true);
        given(receiptOcrClient.recognize(any(), any())).willReturn(new ReceiptOcrResult(null, null, null));
        given(receiptImageKeyGenerator.generate("jpg")).willReturn("receipts/2026/11/abc.jpg");

        ReceiptOcrResponse response = settlementService.recognizeReceipt(1L, receiptImage());

        assertThat(response.getStoreName()).isNull();
        assertThat(response.getTransactedAt()).isNull();
        assertThat(response.getTotalAmount()).isNull();
        assertThat(response.getImageKey()).isEqualTo("receipts/2026/11/abc.jpg");
        verify(fileStorage).store(eq("receipts/2026/11/abc.jpg"), any(), anyLong(), any());
    }

    @Test
    void recognizeReceipt_AI_호출이_실패하면_RECEIPT_502_RECOGNITION_FAILED를_던지고_이미지를_저장하지_않는다() {
        given(scheduleRoundRepository.existsById(1L)).willReturn(true);
        willThrow(new ReceiptOcrException("Gemini 호출에 실패했습니다."))
                .given(receiptOcrClient).recognize(any(), any());

        assertThatThrownBy(() -> settlementService.recognizeReceipt(1L, receiptImage()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.RECEIPT_502_RECOGNITION_FAILED);
        verify(fileStorage, never()).store(any(), any(), anyLong(), any());
    }

    @Test
    void recognizeReceipt_이미지가_없으면_COMMON_400을_던진다() {
        given(scheduleRoundRepository.existsById(1L)).willReturn(true);

        assertThatThrownBy(() -> settlementService.recognizeReceipt(1L, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
        verify(receiptOcrClient, never()).recognize(any(), any());
    }

    @Test
    void recognizeReceipt_빈_파일이면_COMMON_400을_던진다() {
        given(scheduleRoundRepository.existsById(1L)).willReturn(true);
        MockMultipartFile emptyImage = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> settlementService.recognizeReceipt(1L, emptyImage))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
        verify(receiptOcrClient, never()).recognize(any(), any());
    }

    @Test
    void recognizeReceipt_허용되지_않는_확장자면_COMMON_400을_던진다() {
        given(scheduleRoundRepository.existsById(1L)).willReturn(true);
        MockMultipartFile textFile = new MockMultipartFile("image", "receipt.txt", "text/plain", new byte[] {1});

        assertThatThrownBy(() -> settlementService.recognizeReceipt(1L, textFile))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
        verify(receiptOcrClient, never()).recognize(any(), any());
    }

    @Test
    void recognizeReceipt_존재하지_않는_차수면_CommonErrorCode_NOT_FOUND를_던진다() {
        given(scheduleRoundRepository.existsById(1L)).willReturn(false);

        assertThatThrownBy(() -> settlementService.recognizeReceipt(1L, receiptImage()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
        verify(receiptOcrClient, never()).recognize(any(), any());
    }

    @Test
    void deleteReceipt_정상_삭제하면_settlementTarget_roundAttendee_ledger_scheduleRound_sync_순서로_처리한다() {
        ScheduleRound round = roundWithId(1L, 10L);

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(1L, PaymentStatus.PAID)).willReturn(false);

        settlementService.deleteReceipt(1L);

        InOrder inOrder = inOrder(settlementTargetRepository, roundAttendeeRepository, ledgerEntryRepository,
                scheduleRoundRepository, ledgerSyncService);
        inOrder.verify(settlementTargetRepository).deleteByScheduleRoundId(1L);
        inOrder.verify(roundAttendeeRepository).deleteByScheduleRoundId(1L);
        inOrder.verify(ledgerEntryRepository).clearScheduleRoundReference(1L);
        inOrder.verify(scheduleRoundRepository).delete(round);
        inOrder.verify(ledgerSyncService).syncSchedule(10L);
    }

    @Test
    void deleteReceipt_입금완료자가_있으면_SETTLEMENT_423_ROUND_LOCKED_BY_PAYMENT를_던지고_아무것도_지우지_않는다() {
        ScheduleRound round = roundWithId(1L);

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(1L, PaymentStatus.PAID)).willReturn(true);

        assertThatThrownBy(() -> settlementService.deleteReceipt(1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_423_ROUND_LOCKED_BY_PAYMENT);

        verify(settlementTargetRepository, never()).deleteByScheduleRoundId(any());
        verify(roundAttendeeRepository, never()).deleteByScheduleRoundId(any());
        verify(ledgerEntryRepository, never()).clearScheduleRoundReference(any());
        verify(scheduleRoundRepository, never()).delete(any());
        verify(ledgerSyncService, never()).syncSchedule(any());
    }

    @Test
    void deleteReceipt_존재하지_않는_차수면_CommonErrorCode_NOT_FOUND를_던진다() {
        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.deleteReceipt(1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void getSettlementTargets_이름으로_검색한다() {
        ScheduleRound round = roundWithId(1L);
        ScheduleAttendee attendee = attendeeWithId(100L, 10L, "김모임");
        SettlementTarget target = settlementTargetWithAttendee(1L, attendee, 20000, PaymentStatus.UNPAID,
                LocalDateTime.of(2026, 11, 13, 19, 0, 0));

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(settlementTargetRepository.search(1L, "김모임", null, null, 21)).willReturn(List.of(target));
        given(settlementTargetRepository.countByScheduleRoundId(1L)).willReturn(1L);
        given(settlementTargetRepository.findFirstByScheduleRoundId(1L)).willReturn(Optional.of(target));

        SettlementTargetListResponse response = settlementService.getSettlementTargets(1L, "김모임", null, null);

        assertThat(response.getLabel()).isEqualTo("1차");
        assertThat(response.getTargetCount()).isEqualTo(1L);
        assertThat(response.getBilledAmount()).isEqualTo(20000);
        assertThat(response.getTargets()).hasSize(1);
        assertThat(response.getTargets().get(0).getName()).isEqualTo("김모임");
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void getSettlementTargets_size보다_1건_더_가져와서_다음_페이지가_있으면_hasNext와_nextCursor를_만든다() {
        ScheduleRound round = roundWithId(1L);
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 13, 19, 0, 0);
        ScheduleAttendee attendee1 = attendeeWithId(100L, 10L, "김모임");
        ScheduleAttendee attendee2 = attendeeWithId(101L, 10L, "박모임");
        SettlementTarget target1 = settlementTargetWithAttendee(1L, attendee1, 20000, PaymentStatus.UNPAID, createdAt);
        SettlementTarget target2 = settlementTargetWithAttendee(2L, attendee2, 20000, PaymentStatus.UNPAID, createdAt);

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(settlementTargetRepository.search(1L, null, null, null, 2)).willReturn(List.of(target1, target2));
        given(settlementTargetRepository.countByScheduleRoundId(1L)).willReturn(2L);
        given(settlementTargetRepository.findFirstByScheduleRoundId(1L)).willReturn(Optional.of(target1));

        SettlementTargetListResponse response = settlementService.getSettlementTargets(1L, null, null, 1);

        assertThat(response.getTargets()).hasSize(1);
        assertThat(response.getTargets().get(0).getSettlementTargetId()).isEqualTo(1L);
        assertThat(response.getNextCursor()).isEqualTo(CursorCodec.encode(
                String.valueOf(createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()), 1L));
    }

    @Test
    void getSettlementTargets_잘못된_cursor면_COMMON_400을_던진다() {
        ScheduleRound round = roundWithId(1L);
        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));

        assertThatThrownBy(() -> settlementService.getSettlementTargets(1L, null, "잘못된cursor값", null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void getSettlementTargets_존재하지_않는_차수면_CommonErrorCode_NOT_FOUND를_던진다() {
        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.getSettlementTargets(1L, null, null, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void saveSettlementTargets_기존_유지는_재계산하고_신규는_추가하고_빠진_대상은_삭제한다() {
        ScheduleRound round = roundWithId(1L, 10L);
        ReflectionTestUtils.setField(round, "totalAmount", 30000);
        ReflectionTestUtils.setField(round, "clubSubsidyAmount", 0);

        ScheduleAttendee keepAttendee = attendeeWithId(100L, 10L, "김모임");
        ScheduleAttendee newAttendee = attendeeWithId(101L, 10L, "박모임");
        SettlementTarget keepTarget = settlementTargetWithAttendee(1L, keepAttendee, 15000, PaymentStatus.UNPAID, LocalDateTime.now());
        ScheduleAttendee removedAttendee = attendeeWithId(102L, 10L, "최모임");
        SettlementTarget removeTarget = settlementTargetWithAttendee(2L, removedAttendee, 15000, PaymentStatus.UNPAID, LocalDateTime.now());

        SettlementTargetSaveRequest request = SettlementTargetSaveRequest.builder()
                .attendeeIds(List.of(100L, 101L))
                .build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(scheduleAttendeeRepository.findAllById(List.of(100L, 101L))).willReturn(List.of(keepAttendee, newAttendee));
        given(settlementTargetRepository.findByScheduleRoundId(1L)).willReturn(List.of(keepTarget, removeTarget));
        given(billedAmountCalculator.calculate(30000, 0, 2)).willReturn(15000);

        SettlementTargetSaveResponse response = settlementService.saveSettlementTargets(1L, request);

        assertThat(response.getLabel()).isEqualTo("1차");
        assertThat(response.getTargetCount()).isEqualTo(2L);
        assertThat(response.getBilledAmount()).isEqualTo(15000);
        assertThat(keepTarget.getBilledAmount()).isEqualTo(15000);
        verify(settlementTargetRepository).save(argThat(saved -> saved.getScheduleAttendee() == newAttendee));
        verify(settlementTargetRepository).deleteAll(List.of(removeTarget));
    }

    @Test
    void saveSettlementTargets_attendeeIds를_distinct로_정리한다() {
        ScheduleRound round = roundWithId(1L, 10L);
        ReflectionTestUtils.setField(round, "totalAmount", 20000);
        ReflectionTestUtils.setField(round, "clubSubsidyAmount", 0);
        ScheduleAttendee attendee = attendeeWithId(100L, 10L, "김모임");

        SettlementTargetSaveRequest request = SettlementTargetSaveRequest.builder()
                .attendeeIds(List.of(100L, 100L))
                .build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(scheduleAttendeeRepository.findAllById(List.of(100L))).willReturn(List.of(attendee));
        given(settlementTargetRepository.findByScheduleRoundId(1L)).willReturn(List.of());
        given(billedAmountCalculator.calculate(20000, 0, 1)).willReturn(20000);

        SettlementTargetSaveResponse response = settlementService.saveSettlementTargets(1L, request);

        assertThat(response.getTargetCount()).isEqualTo(1L);
        verify(billedAmountCalculator).calculate(20000, 0, 1);
    }

    @Test
    void saveSettlementTargets_명단이_비어있으면_COMMON_400을_던진다() {
        ScheduleRound round = roundWithId(1L);
        SettlementTargetSaveRequest request = SettlementTargetSaveRequest.builder().attendeeIds(List.of()).build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));

        assertThatThrownBy(() -> settlementService.saveSettlementTargets(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
        verify(scheduleAttendeeRepository, never()).findAllById(any());
    }

    @Test
    void saveSettlementTargets_영수증이_등록되지_않았으면_SETTLEMENT_409_RECEIPT_NOT_REGISTERED를_던진다() {
        ScheduleRound round = roundWithId(1L);
        SettlementTargetSaveRequest request = SettlementTargetSaveRequest.builder().attendeeIds(List.of(100L)).build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));

        assertThatThrownBy(() -> settlementService.saveSettlementTargets(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_409_RECEIPT_NOT_REGISTERED);
    }

    @Test
    void saveSettlementTargets_그_일정_소속이_아닌_참가자가_섞이면_COMMON_400을_던진다() {
        ScheduleRound round = roundWithId(1L, 10L);
        ReflectionTestUtils.setField(round, "totalAmount", 20000);
        ReflectionTestUtils.setField(round, "clubSubsidyAmount", 0);
        ScheduleAttendee otherScheduleAttendee = attendeeWithId(200L, 99L, "다른일정참가자");

        SettlementTargetSaveRequest request = SettlementTargetSaveRequest.builder().attendeeIds(List.of(200L)).build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(scheduleAttendeeRepository.findAllById(List.of(200L))).willReturn(List.of(otherScheduleAttendee));

        assertThatThrownBy(() -> settlementService.saveSettlementTargets(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void saveSettlementTargets_존재하지_않는_참가자_id가_섞이면_COMMON_400을_던진다() {
        ScheduleRound round = roundWithId(1L, 10L);
        ReflectionTestUtils.setField(round, "totalAmount", 20000);
        ReflectionTestUtils.setField(round, "clubSubsidyAmount", 0);

        SettlementTargetSaveRequest request = SettlementTargetSaveRequest.builder().attendeeIds(List.of(999L)).build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(scheduleAttendeeRepository.findAllById(List.of(999L))).willReturn(List.of());

        assertThatThrownBy(() -> settlementService.saveSettlementTargets(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void saveSettlementTargets_PAID인_사람을_빼면_SETTLEMENT_423_PAID_MEMBER_LOCKED를_던진다() {
        ScheduleRound round = roundWithId(1L, 10L);
        ReflectionTestUtils.setField(round, "totalAmount", 20000);
        ReflectionTestUtils.setField(round, "clubSubsidyAmount", 0);

        ScheduleAttendee keepAttendee = attendeeWithId(100L, 10L, "김모임");
        ScheduleAttendee paidAttendee = attendeeWithId(101L, 10L, "박모임");
        SettlementTarget keepTarget = settlementTargetWithAttendee(1L, keepAttendee, 10000, PaymentStatus.UNPAID, LocalDateTime.now());
        SettlementTarget paidTarget = settlementTargetWithAttendee(2L, paidAttendee, 10000, PaymentStatus.PAID, LocalDateTime.now());

        SettlementTargetSaveRequest request = SettlementTargetSaveRequest.builder().attendeeIds(List.of(100L)).build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(scheduleAttendeeRepository.findAllById(List.of(100L))).willReturn(List.of(keepAttendee));
        given(settlementTargetRepository.findByScheduleRoundId(1L)).willReturn(List.of(keepTarget, paidTarget));

        assertThatThrownBy(() -> settlementService.saveSettlementTargets(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_423_PAID_MEMBER_LOCKED);
        verify(settlementTargetRepository, never()).deleteAll(any());
        verify(billedAmountCalculator, never()).calculate(any(Integer.class), any(Integer.class), any(Integer.class));
    }

    @Test
    void saveSettlementTargets_존재하지_않는_차수면_CommonErrorCode_NOT_FOUND를_던진다() {
        SettlementTargetSaveRequest request = SettlementTargetSaveRequest.builder().attendeeIds(List.of(100L)).build();
        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.saveSettlementTargets(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void importSettlementTargets_출석_완료자만_반환하고_청구액을_미리보기로_계산한다() {
        ScheduleRound round = roundWithId(1L);
        ReflectionTestUtils.setField(round, "totalAmount", 30000);
        ReflectionTestUtils.setField(round, "clubSubsidyAmount", 0);
        ScheduleAttendee attendee = attendeeWithId(100L, 10L, "김모임");
        RoundAttendee checkedIn = checkedInRoundAttendeeWithAttendee(round, attendee);

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(roundAttendeeRepository.findCheckedInByScheduleRoundId(1L)).willReturn(List.of(checkedIn));
        given(billedAmountCalculator.calculate(30000, 0, 1)).willReturn(30000);

        SettlementTargetImportResponse response = settlementService.importSettlementTargets(1L);

        assertThat(response.getAttendees()).hasSize(1);
        assertThat(response.getAttendees().get(0).getName()).isEqualTo("김모임");
        assertThat(response.getExpectedBilledAmount()).isEqualTo(30000);
    }

    @Test
    void importSettlementTargets_출석_완료자가_없으면_계산기를_호출하지_않고_0을_반환한다() {
        ScheduleRound round = roundWithId(1L);
        ReflectionTestUtils.setField(round, "totalAmount", 30000);
        ReflectionTestUtils.setField(round, "clubSubsidyAmount", 0);

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));
        given(roundAttendeeRepository.findCheckedInByScheduleRoundId(1L)).willReturn(List.of());

        SettlementTargetImportResponse response = settlementService.importSettlementTargets(1L);

        assertThat(response.getAttendees()).isEmpty();
        assertThat(response.getExpectedBilledAmount()).isEqualTo(0);
        verify(billedAmountCalculator, never()).calculate(any(Integer.class), any(Integer.class), any(Integer.class));
    }

    @Test
    void importSettlementTargets_영수증이_등록되지_않았으면_SETTLEMENT_409_RECEIPT_NOT_REGISTERED를_던진다() {
        ScheduleRound round = roundWithId(1L);

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));

        assertThatThrownBy(() -> settlementService.importSettlementTargets(1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_409_RECEIPT_NOT_REGISTERED);
    }

    @Test
    void importSettlementTargets_존재하지_않는_차수면_CommonErrorCode_NOT_FOUND를_던진다() {
        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.importSettlementTargets(1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void updatePaymentStatus_그_사람의_일정_내_모든_차수를_함께_갱신한다() {
        ScheduleAttendee attendee = attendeeWithId(200L, 100L, "김모임");
        ScheduleRound round1 = roundWithId(10L, 100L);
        ScheduleRound round2 = roundWithId(11L, 100L);
        SettlementTarget target1 = settlementTargetWithRound(1L, round1, attendee, 10000, PaymentStatus.UNPAID, null);
        SettlementTarget target2 = settlementTargetWithRound(2L, round2, attendee, 15000, PaymentStatus.UNPAID, null);
        PaymentConfirmRequest request = PaymentConfirmRequest.builder().paid(true).build();

        given(settlementTargetRepository.findByScheduleAndAttendee(100L, 200L)).willReturn(List.of(target1, target2));

        PaymentStatusResponse response = settlementService.updatePaymentStatus(100L, 200L, request);

        assertThat(target1.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(target2.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(target1.getPaidAt()).isNotNull();
        assertThat(target2.getPaidAt()).isNotNull();
        assertThat(response.getAttendeeId()).isEqualTo(200L);
        assertThat(response.getName()).isEqualTo("김모임");
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.getPaidAt()).isNotNull();
        verify(ledgerSyncService).syncSchedule(100L);
    }

    @Test
    void updatePaymentStatus_이미_같은_상태면_멱등하게_처리하고_기존_paidAt을_유지한다() {
        ScheduleAttendee attendee = attendeeWithId(200L, 100L, "김모임");
        ScheduleRound round = roundWithId(10L, 100L);
        LocalDateTime originalPaidAt = LocalDateTime.of(2026, 11, 10, 12, 0, 0);
        SettlementTarget target = settlementTargetWithRound(1L, round, attendee, 10000, PaymentStatus.PAID, originalPaidAt);
        PaymentConfirmRequest request = PaymentConfirmRequest.builder().paid(true).build();

        given(settlementTargetRepository.findByScheduleAndAttendee(100L, 200L)).willReturn(List.of(target));

        PaymentStatusResponse response = settlementService.updatePaymentStatus(100L, 200L, request);

        assertThat(target.getPaidAt()).isEqualTo(originalPaidAt);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.getPaidAt()).isEqualTo(originalPaidAt);
    }

    @Test
    void updatePaymentStatus_그_일정에_정산대상이_없으면_COMMON_404를_던진다() {
        PaymentConfirmRequest request = PaymentConfirmRequest.builder().paid(true).build();
        given(settlementTargetRepository.findByScheduleAndAttendee(100L, 200L)).willReturn(List.of());

        assertThatThrownBy(() -> settlementService.updatePaymentStatus(100L, 200L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void removeFromPayments_정상_삭제하고_각_차수_남은_인원으로_재계산한다() {
        ScheduleAttendee attendee = attendeeWithId(200L, 100L, "김모임");
        ScheduleRound round1 = roundWithId(10L, 100L);
        ReflectionTestUtils.setField(round1, "totalAmount", 30000);
        ReflectionTestUtils.setField(round1, "clubSubsidyAmount", 0);
        ScheduleRound round2 = roundWithId(11L, 100L);
        ReflectionTestUtils.setField(round2, "totalAmount", 20000);
        ReflectionTestUtils.setField(round2, "clubSubsidyAmount", 0);

        SettlementTarget target1 = settlementTargetWithRound(1L, round1, attendee, 15000, PaymentStatus.UNPAID, null);
        SettlementTarget target2 = settlementTargetWithRound(2L, round2, attendee, 10000, PaymentStatus.UNPAID, null);
        ScheduleAttendee other1 = attendeeWithId(201L, 100L, "박모임");
        SettlementTarget remainingTarget1 = settlementTargetWithRound(3L, round1, other1, 15000, PaymentStatus.UNPAID, null);
        ScheduleAttendee other2 = attendeeWithId(202L, 100L, "최모임");
        SettlementTarget remainingTarget2 = settlementTargetWithRound(4L, round2, other2, 10000, PaymentStatus.UNPAID, null);

        given(settlementTargetRepository.findByScheduleAndAttendee(100L, 200L)).willReturn(List.of(target1, target2));
        given(settlementTargetRepository.findByScheduleRoundId(10L)).willReturn(List.of(target1, remainingTarget1));
        given(settlementTargetRepository.findByScheduleRoundId(11L)).willReturn(List.of(target2, remainingTarget2));
        given(billedAmountCalculator.calculate(30000, 0, 1)).willReturn(30000);
        given(billedAmountCalculator.calculate(20000, 0, 1)).willReturn(20000);

        settlementService.removeFromPayments(100L, 200L);

        verify(settlementTargetRepository).deleteAll(List.of(target1, target2));
        assertThat(remainingTarget1.getBilledAmount()).isEqualTo(30000);
        assertThat(remainingTarget2.getBilledAmount()).isEqualTo(20000);
    }

    @Test
    void removeFromPayments_모든_차수에서_그_한_명뿐이면_계산기를_호출하지_않는다() {
        ScheduleAttendee attendee = attendeeWithId(200L, 100L, "김모임");
        ScheduleRound round = roundWithId(10L, 100L);
        SettlementTarget target = settlementTargetWithRound(1L, round, attendee, 10000, PaymentStatus.UNPAID, null);

        given(settlementTargetRepository.findByScheduleAndAttendee(100L, 200L)).willReturn(List.of(target));
        given(settlementTargetRepository.findByScheduleRoundId(10L)).willReturn(List.of(target));

        settlementService.removeFromPayments(100L, 200L);

        verify(settlementTargetRepository).deleteAll(List.of(target));
        verify(billedAmountCalculator, never()).calculate(any(Integer.class), any(Integer.class), any(Integer.class));
    }

    @Test
    void removeFromPayments_한_차수라도_PAID면_SETTLEMENT_423_PAID_CANNOT_DELETE를_던진다() {
        ScheduleAttendee attendee = attendeeWithId(200L, 100L, "김모임");
        ScheduleRound round1 = roundWithId(10L, 100L);
        ScheduleRound round2 = roundWithId(11L, 100L);
        SettlementTarget target1 = settlementTargetWithRound(1L, round1, attendee, 10000, PaymentStatus.UNPAID, null);
        SettlementTarget target2 = settlementTargetWithRound(2L, round2, attendee, 10000, PaymentStatus.PAID, LocalDateTime.now());

        given(settlementTargetRepository.findByScheduleAndAttendee(100L, 200L)).willReturn(List.of(target1, target2));

        assertThatThrownBy(() -> settlementService.removeFromPayments(100L, 200L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_423_PAID_CANNOT_DELETE);
        verify(settlementTargetRepository, never()).deleteAll(any());
    }

    @Test
    void removeFromPayments_그_일정에_정산대상이_없으면_COMMON_404를_던진다() {
        given(settlementTargetRepository.findByScheduleAndAttendee(100L, 200L)).willReturn(List.of());

        assertThatThrownBy(() -> settlementService.removeFromPayments(100L, 200L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void getPaymentSummaries_status가_없으면_기본값_UNPAID로_조회한다() {
        given(scheduleRepository.findById(100L)).willReturn(Optional.of(mock(Schedule.class)));
        given(settlementTargetRepository.searchPaymentSummaries(100L, PaymentStatusFilter.UNPAID, null, null, null, 21))
                .willReturn(List.of());

        settlementService.getPaymentSummaries(100L, null, null, null, null);

        verify(settlementTargetRepository).searchPaymentSummaries(100L, PaymentStatusFilter.UNPAID, null, null, null, 21);
    }

    @Test
    void getPaymentSummaries_status를_지정하면_그대로_파싱해서_전달한다() {
        given(scheduleRepository.findById(100L)).willReturn(Optional.of(mock(Schedule.class)));
        given(settlementTargetRepository.searchPaymentSummaries(100L, PaymentStatusFilter.PAID, null, null, null, 21))
                .willReturn(List.of());

        settlementService.getPaymentSummaries(100L, "PAID", null, null, null);

        verify(settlementTargetRepository).searchPaymentSummaries(100L, PaymentStatusFilter.PAID, null, null, null, 21);
    }

    @Test
    void getPaymentSummaries_이름으로_검색한다() {
        ScheduleAttendee attendee = attendeeWithId(200L, 100L, "김모임", LocalDateTime.now());
        PaymentSummary summary = new PaymentSummary(attendee, 20000, PaymentStatus.UNPAID);

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(mock(Schedule.class)));
        given(settlementTargetRepository.searchPaymentSummaries(100L, PaymentStatusFilter.UNPAID, "김모임", null, null, 21))
                .willReturn(List.of(summary));

        PaymentSummaryListResponse response = settlementService.getPaymentSummaries(100L, null, "김모임", null, null);

        assertThat(response.getPayments()).hasSize(1);
        assertThat(response.getPayments().get(0).getName()).isEqualTo("김모임");
    }

    @Test
    void getPaymentSummaries_size보다_1건_더_가져와서_hasNext와_nextCursor를_만든다() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 13, 19, 0, 0);
        ScheduleAttendee attendee1 = attendeeWithId(200L, 100L, "김모임", createdAt);
        ScheduleAttendee attendee2 = attendeeWithId(201L, 100L, "박모임", createdAt);
        PaymentSummary summary1 = new PaymentSummary(attendee1, 20000, PaymentStatus.UNPAID);
        PaymentSummary summary2 = new PaymentSummary(attendee2, 20000, PaymentStatus.UNPAID);

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(mock(Schedule.class)));
        given(settlementTargetRepository.searchPaymentSummaries(100L, PaymentStatusFilter.UNPAID, null, null, null, 2))
                .willReturn(List.of(summary1, summary2));

        PaymentSummaryListResponse response = settlementService.getPaymentSummaries(100L, null, null, null, 1);

        assertThat(response.getPayments()).hasSize(1);
        assertThat(response.getPayments().get(0).getAttendeeId()).isEqualTo(200L);
        assertThat(response.getNextCursor()).isEqualTo(CursorCodec.encode(
                String.valueOf(createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()), 200L));
    }

    @Test
    void getPaymentSummaries_여러_차수_합산_금액을_그대로_응답한다() {
        ScheduleAttendee attendee = attendeeWithId(200L, 100L, "김모임", LocalDateTime.now());
        PaymentSummary summary = new PaymentSummary(attendee, 40000, PaymentStatus.UNPAID);

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(mock(Schedule.class)));
        given(settlementTargetRepository.searchPaymentSummaries(100L, PaymentStatusFilter.UNPAID, null, null, null, 21))
                .willReturn(List.of(summary));

        PaymentSummaryListResponse response = settlementService.getPaymentSummaries(100L, null, null, null, null);

        assertThat(response.getPayments().get(0).getTotalBilledAmount()).isEqualTo(40000);
        assertThat(response.getPayments().get(0).getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void getPaymentSummaries_존재하지_않는_일정이면_CommonErrorCode_NOT_FOUND를_던진다() {
        given(scheduleRepository.findById(100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.getPaymentSummaries(100L, null, null, null, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void getPaymentSummaries_잘못된_status면_COMMON_400을_던진다() {
        given(scheduleRepository.findById(100L)).willReturn(Optional.of(mock(Schedule.class)));

        assertThatThrownBy(() -> settlementService.getPaymentSummaries(100L, "WRONG", null, null, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void setAccount_정상_저장한다() {
        ScheduleRound round = roundWithId(1L);
        AccountSetRequest request = AccountSetRequest.builder()
                .bankName("국민은행").accountNumber("123-456").accountHolder("김모임").build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));

        AccountResponse response = settlementService.setAccount(1L, request);

        assertThat(response.getRoundId()).isEqualTo(1L);
        assertThat(response.getBankName()).isEqualTo("국민은행");
        assertThat(response.getAccountNumber()).isEqualTo("123-456");
        assertThat(response.getAccountHolder()).isEqualTo("김모임");
        assertThat(round.getBankName()).isEqualTo("국민은행");
    }

    @Test
    void setAccount_이미_설정된_계좌를_덮어쓴다() {
        ScheduleRound round = roundWithId(1L);
        ReflectionTestUtils.setField(round, "bankName", "신한은행");
        ReflectionTestUtils.setField(round, "accountNumber", "999-999");
        ReflectionTestUtils.setField(round, "accountHolder", "박모임");
        AccountSetRequest request = AccountSetRequest.builder()
                .bankName("국민은행").accountNumber("123-456").accountHolder("김모임").build();

        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.of(round));

        settlementService.setAccount(1L, request);

        assertThat(round.getBankName()).isEqualTo("국민은행");
        assertThat(round.getAccountNumber()).isEqualTo("123-456");
        assertThat(round.getAccountHolder()).isEqualTo("김모임");
    }

    @Test
    void setAccount_존재하지_않는_차수면_COMMON_404를_던진다() {
        AccountSetRequest request = AccountSetRequest.builder()
                .bankName("국민은행").accountNumber("123-456").accountHolder("김모임").build();
        given(scheduleRoundRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.setAccount(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void generateSettlementNotice_정상_생성하고_combinedAmount와_noticeText를_만든다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        ScheduleRound round1 = roundWithId(10L, 100L, 1, "1차");
        ScheduleRound round2 = roundWithId(11L, 100L, 2, "2차");
        setReceiptAndAccount(round1, 40000, 0, "국민은행", "123-456", "김모임");
        setReceiptAndAccount(round2, 30000, 0, "국민은행", "123-456", "김모임");
        SettlementTarget target1 = settlementTargetWithId(1L, 20000, PaymentStatus.UNPAID);
        SettlementTarget target2 = settlementTargetWithId(2L, 15000, PaymentStatus.UNPAID);
        SettlementNoticeRequest request = SettlementNoticeRequest.builder().roundIds(List.of(10L, 11L)).build();

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findAllById(List.of(10L, 11L))).willReturn(List.of(round1, round2));
        given(settlementTargetRepository.findFirstByScheduleRoundId(10L)).willReturn(Optional.of(target1));
        given(settlementTargetRepository.findFirstByScheduleRoundId(11L)).willReturn(Optional.of(target2));

        SettlementNoticeResponse response = settlementService.generateSettlementNotice(100L, request);

        assertThat(response.getScheduleTitle()).isEqualTo("정기 모임");
        assertThat(response.getCombinedAmount()).isEqualTo(35000);
        assertThat(response.getRounds()).hasSize(2);
        assertThat(response.getRounds().get(0).getLabel()).isEqualTo("1차");
        assertThat(response.getRounds().get(0).getBilledAmount()).isEqualTo(20000);
        assertThat(response.getRounds().get(1).getLabel()).isEqualTo("2차");
        assertThat(response.getRounds().get(1).getBilledAmount()).isEqualTo(15000);
        assertThat(response.getAccount().getBankName()).isEqualTo("국민은행");
        assertThat(response.getNoticeText())
                .contains("[정기 모임] 정산 안내")
                .contains("1차: 20,000원")
                .contains("2차: 15,000원")
                .contains("총 입금액: 35,000원")
                .contains("국민은행 123-456 (김모임)");
    }

    @Test
    void generateSettlementNotice_차수를_선택하지_않으면_SETTLEMENT_400_ROUND_SELECTION_REQUIRED를_던진다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        SettlementNoticeRequest request = SettlementNoticeRequest.builder().roundIds(List.of()).build();

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));

        assertThatThrownBy(() -> settlementService.generateSettlementNotice(100L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_400_ROUND_SELECTION_REQUIRED);
    }

    @Test
    void generateSettlementNotice_영수증_미등록_차수가_포함되면_SETTLEMENT_409_UNREGISTERED_ROUND_INCLUDED를_던진다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        ScheduleRound round = roundWithId(10L, 100L, 1, "1차");
        SettlementNoticeRequest request = SettlementNoticeRequest.builder().roundIds(List.of(10L)).build();

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findAllById(List.of(10L))).willReturn(List.of(round));

        assertThatThrownBy(() -> settlementService.generateSettlementNotice(100L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_409_UNREGISTERED_ROUND_INCLUDED);
    }

    @Test
    void generateSettlementNotice_계좌_미설정_차수가_포함되면_SETTLEMENT_409_ACCOUNT_NOT_SET을_던진다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        ScheduleRound round = roundWithId(10L, 100L, 1, "1차");
        ReflectionTestUtils.setField(round, "totalAmount", 40000);
        ReflectionTestUtils.setField(round, "clubSubsidyAmount", 0);
        SettlementNoticeRequest request = SettlementNoticeRequest.builder().roundIds(List.of(10L)).build();

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findAllById(List.of(10L))).willReturn(List.of(round));

        assertThatThrownBy(() -> settlementService.generateSettlementNotice(100L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_409_ACCOUNT_NOT_SET);
    }

    @Test
    void generateSettlementNotice_다른_일정_소속_차수가_포함되면_COMMON_400을_던진다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        ScheduleRound otherScheduleRound = roundWithId(10L, 999L, 1, "1차");
        SettlementNoticeRequest request = SettlementNoticeRequest.builder().roundIds(List.of(10L)).build();

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findAllById(List.of(10L))).willReturn(List.of(otherScheduleRound));

        assertThatThrownBy(() -> settlementService.generateSettlementNotice(100L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void generateSettlementNotice_존재하지_않는_차수가_섞이면_COMMON_400을_던진다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        SettlementNoticeRequest request = SettlementNoticeRequest.builder().roundIds(List.of(999L)).build();

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findAllById(List.of(999L))).willReturn(List.of());

        assertThatThrownBy(() -> settlementService.generateSettlementNotice(100L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void generateSettlementNotice_중복된_roundId는_제거하고_처리한다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        ScheduleRound round = roundWithId(10L, 100L, 1, "1차");
        setReceiptAndAccount(round, 40000, 0, "국민은행", "123-456", "김모임");
        SettlementTarget target = settlementTargetWithId(1L, 20000, PaymentStatus.UNPAID);
        SettlementNoticeRequest request = SettlementNoticeRequest.builder().roundIds(List.of(10L, 10L)).build();

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findAllById(List.of(10L))).willReturn(List.of(round));
        given(settlementTargetRepository.findFirstByScheduleRoundId(10L)).willReturn(Optional.of(target));

        SettlementNoticeResponse response = settlementService.generateSettlementNotice(100L, request);

        assertThat(response.getRounds()).hasSize(1);
        assertThat(response.getCombinedAmount()).isEqualTo(20000);
        verify(scheduleRoundRepository).findAllById(List.of(10L));
    }

    @Test
    void generateSettlementNotice_존재하지_않는_일정이면_CommonErrorCode_NOT_FOUND를_던진다() {
        SettlementNoticeRequest request = SettlementNoticeRequest.builder().roundIds(List.of(10L)).build();
        given(scheduleRepository.findById(100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.generateSettlementNotice(100L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void generateSettlementNotice_계좌가_다른_차수_중_roundNo가_가장_작은_차수의_계좌를_대표로_쓴다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        ScheduleRound round1 = roundWithId(10L, 100L, 1, "1차");
        ScheduleRound round2 = roundWithId(11L, 100L, 2, "2차");
        setReceiptAndAccount(round1, 40000, 0, "국민은행", "111-111", "1차계좌주");
        setReceiptAndAccount(round2, 30000, 0, "신한은행", "222-222", "2차계좌주");
        SettlementTarget target1 = settlementTargetWithId(1L, 20000, PaymentStatus.UNPAID);
        SettlementTarget target2 = settlementTargetWithId(2L, 15000, PaymentStatus.UNPAID);
        // 요청 배열 순서를 일부러 2차, 1차로 뒤집어 보내도 대표 계좌는 roundNo 기준으로 고정된다
        SettlementNoticeRequest request = SettlementNoticeRequest.builder().roundIds(List.of(11L, 10L)).build();

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findAllById(List.of(11L, 10L))).willReturn(List.of(round2, round1));
        given(settlementTargetRepository.findFirstByScheduleRoundId(10L)).willReturn(Optional.of(target1));
        given(settlementTargetRepository.findFirstByScheduleRoundId(11L)).willReturn(Optional.of(target2));

        SettlementNoticeResponse response = settlementService.generateSettlementNotice(100L, request);

        assertThat(response.getAccount().getBankName()).isEqualTo("국민은행");
        assertThat(response.getAccount().getAccountHolder()).isEqualTo("1차계좌주");
        assertThat(response.getRounds().get(0).getLabel()).isEqualTo("1차");
        assertThat(response.getRounds().get(1).getLabel()).isEqualTo("2차");
    }

    @Test
    void generateSettlementNotice_정산_명단이_없는_차수가_포함되면_SETTLEMENT_409_UNREGISTERED_ROUND_INCLUDED를_던진다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        ScheduleRound round = roundWithId(10L, 100L, 1, "1차");
        setReceiptAndAccount(round, 40000, 0, "국민은행", "123-456", "김모임");
        SettlementNoticeRequest request = SettlementNoticeRequest.builder().roundIds(List.of(10L)).build();

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findAllById(List.of(10L))).willReturn(List.of(round));
        given(settlementTargetRepository.findFirstByScheduleRoundId(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.generateSettlementNotice(100L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_409_UNREGISTERED_ROUND_INCLUDED);
    }

    @Test
    void getSettlementHome_전원_입금완료_차수는_정산_완료로_표시한다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        ScheduleRound round = roundWithId(10L, 100L, 1, "1차");
        setReceiptAndAccount(round, 40000, 0, "국민은행", "111-111", "김모임");

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(100L)).willReturn(List.of(round));
        given(settlementTargetRepository.countByScheduleRoundId(10L)).willReturn(2L);
        given(settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(10L, PaymentStatus.UNPAID)).willReturn(false);
        given(settlementTargetRepository.findByScheduleForAssistant(100L, null)).willReturn(List.of());

        SettlementHomeResponse response = settlementService.getSettlementHome(100L);

        assertThat(response.getReceipts()).hasSize(1);
        assertThat(response.getReceipts().get(0).getSettlementStatus()).isEqualTo("정산 완료");
    }

    @Test
    void getSettlementHome_일부_미입금_차수는_정산중으로_표시한다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        ScheduleRound round = roundWithId(10L, 100L, 1, "1차");
        setReceiptAndAccount(round, 40000, 0, "국민은행", "111-111", "김모임");

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(100L)).willReturn(List.of(round));
        given(settlementTargetRepository.countByScheduleRoundId(10L)).willReturn(2L);
        given(settlementTargetRepository.existsByScheduleRoundIdAndPaymentStatus(10L, PaymentStatus.UNPAID)).willReturn(true);
        given(settlementTargetRepository.findByScheduleForAssistant(100L, null)).willReturn(List.of());

        SettlementHomeResponse response = settlementService.getSettlementHome(100L);

        assertThat(response.getReceipts().get(0).getSettlementStatus()).isEqualTo("정산중");
    }

    @Test
    void getSettlementHome_정산_대상이_0명이면_rate_0을_반환한다() {
        Schedule schedule = scheduleWithTitle("정기 모임");

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(100L)).willReturn(List.of());
        given(settlementTargetRepository.findByScheduleForAssistant(100L, null)).willReturn(List.of());

        SettlementHomeResponse response = settlementService.getSettlementHome(100L);

        assertThat(response.getProgress().getTotalCount()).isEqualTo(0L);
        assertThat(response.getProgress().getPaidCount()).isEqualTo(0L);
        assertThat(response.getProgress().getRate()).isEqualTo(0);
        assertThat(response.getStatus()).isEqualTo("정산중");
    }

    @Test
    void getSettlementHome_계좌가_다른_차수_중_roundNo가_가장_작은_차수의_계좌를_대표로_쓴다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        ScheduleRound round1 = roundWithId(10L, 100L, 1, "1차");
        ScheduleRound round2 = roundWithId(11L, 100L, 2, "2차");
        setReceiptAndAccount(round1, 40000, 0, "국민은행", "111-111", "1차계좌주");
        setReceiptAndAccount(round2, 30000, 0, "신한은행", "222-222", "2차계좌주");

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        // 리포지토리가 반환하는 순서를 일부러 뒤섞어도 대표 계좌는 roundNo 기준으로 고정된다
        given(scheduleRoundRepository.findOrderedRounds(100L)).willReturn(List.of(round2, round1));
        given(settlementTargetRepository.countByScheduleRoundId(any())).willReturn(0L);
        given(settlementTargetRepository.findByScheduleForAssistant(100L, null)).willReturn(List.of());

        SettlementHomeResponse response = settlementService.getSettlementHome(100L);

        assertThat(response.getAccount().getBankName()).isEqualTo("국민은행");
        assertThat(response.getAccount().getAccountHolder()).isEqualTo("1차계좌주");
    }

    @Test
    void getSettlementHome_존재하지_않는_일정이면_CommonErrorCode_NOT_FOUND를_던진다() {
        given(scheduleRepository.findById(100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.getSettlementHome(100L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void getSettlementHome_정산_명단이_없는_차수는_정산중으로_표시한다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        ScheduleRound round = roundWithId(10L, 100L, 1, "1차");
        setReceiptAndAccount(round, 40000, 0, "국민은행", "111-111", "김모임");

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(100L)).willReturn(List.of(round));
        given(settlementTargetRepository.countByScheduleRoundId(10L)).willReturn(0L);
        given(settlementTargetRepository.findByScheduleForAssistant(100L, null)).willReturn(List.of());

        SettlementHomeResponse response = settlementService.getSettlementHome(100L);

        assertThat(response.getReceipts().get(0).getSettlementStatus()).isEqualTo("정산중");
        verify(settlementTargetRepository, never()).existsByScheduleRoundIdAndPaymentStatus(any(), any());
    }

    @Test
    void getSettlementHome_영수증_미등록_차수는_receipts에서_제외한다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        ScheduleRound registeredRound = roundWithId(10L, 100L, 1, "1차");
        setReceiptAndAccount(registeredRound, 40000, 0, "국민은행", "111-111", "김모임");
        ScheduleRound unregisteredRound = roundWithId(11L, 100L, 2, "2차");

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(100L)).willReturn(List.of(registeredRound, unregisteredRound));
        given(settlementTargetRepository.countByScheduleRoundId(10L)).willReturn(0L);
        given(settlementTargetRepository.findByScheduleForAssistant(100L, null)).willReturn(List.of());

        SettlementHomeResponse response = settlementService.getSettlementHome(100L);

        assertThat(response.getReceipts()).hasSize(1);
        assertThat(response.getReceipts().get(0).getRoundId()).isEqualTo(10L);
    }

    @Test
    void getSettlementHome_사람_단위로_집계해_모든_차수가_PAID여야_완료로_센다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        ScheduleAttendee attendee1 = attendeeWithId(200L, 100L, "김모임");
        ScheduleAttendee attendee2 = attendeeWithId(201L, 100L, "박모임");
        SettlementTarget a1r1 = settlementTargetWithAttendee(1L, attendee1, 20000, PaymentStatus.PAID, LocalDateTime.now());
        SettlementTarget a1r2 = settlementTargetWithAttendee(2L, attendee1, 15000, PaymentStatus.PAID, LocalDateTime.now());
        SettlementTarget a2r1 = settlementTargetWithAttendee(3L, attendee2, 20000, PaymentStatus.PAID, LocalDateTime.now());
        SettlementTarget a2r2 = settlementTargetWithAttendee(4L, attendee2, 15000, PaymentStatus.UNPAID, LocalDateTime.now());

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(100L)).willReturn(List.of());
        given(settlementTargetRepository.findByScheduleForAssistant(100L, null))
                .willReturn(List.of(a1r1, a1r2, a2r1, a2r2));

        SettlementHomeResponse response = settlementService.getSettlementHome(100L);

        assertThat(response.getProgress().getTotalCount()).isEqualTo(2L);
        assertThat(response.getProgress().getPaidCount()).isEqualTo(1L);
        assertThat(response.getProgress().getRate()).isEqualTo(50);
    }

    @Test
    void getSettlementHome_전체가_정산_완료면_상위_status도_정산_완료를_반환한다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        ScheduleAttendee attendee = attendeeWithId(200L, 100L, "김모임");
        SettlementTarget target = settlementTargetWithAttendee(1L, attendee, 20000, PaymentStatus.PAID, LocalDateTime.now());

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(100L)).willReturn(List.of());
        given(settlementTargetRepository.findByScheduleForAssistant(100L, null)).willReturn(List.of(target));

        SettlementHomeResponse response = settlementService.getSettlementHome(100L);

        assertThat(response.getStatus()).isEqualTo("정산 완료");
    }

    @Test
    void getSettlementHome_설정된_계좌가_없으면_account에_null을_반환한다() {
        Schedule schedule = scheduleWithTitle("정기 모임");
        ScheduleRound round = roundWithId(10L, 100L, 1, "1차");

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleRoundRepository.findOrderedRounds(100L)).willReturn(List.of(round));
        given(settlementTargetRepository.findByScheduleForAssistant(100L, null)).willReturn(List.of());

        SettlementHomeResponse response = settlementService.getSettlementHome(100L);

        assertThat(response.getAccount()).isNull();
    }
}
