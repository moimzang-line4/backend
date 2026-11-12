package com.moimzang.domain.attendance.entity;

import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 차수 entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "schedule_round")
public class ScheduleRound extends BaseEntity {

    // 차수 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 일정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    // 차수 번호
    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    // 차수 표시명(예: 1차, 2차)
    @Column(name = "label", length = 20)
    private String label;

    // 가게명(영수증)
    @Column(name = "store_name", length = 100)
    private String storeName;

    // 거래일시
    @Column(name = "transacted_at")
    private LocalDateTime transactedAt;

    // 영수증 총액
    @Column(name = "total_amount")
    private Integer totalAmount;

    // 회비 지원금
    @Column(name = "club_subsidy_amount")
    private Integer clubSubsidyAmount;

    // 영수증 이미지 URL
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // 입금 안내 계좌의 은행명
    @Column(name = "bank_name", length = 50)
    private String bankName;

    // 입금 안내 계좌번호
    @Column(name = "account_number", length = 50)
    private String accountNumber;

    // 입금 안내 계좌 예금주
    @Column(name = "account_holder", length = 50)
    private String accountHolder;

    // 생성 시점에는 영수증·계좌 정보가 전혀 없는 상태로 시작한다(둘 다 settlement 도메인에서 채운다)
    public static ScheduleRound create(Schedule schedule, Integer roundNo, String label) {
        return ScheduleRound.builder()
                .schedule(schedule)
                .roundNo(roundNo)
                .label(label)
                .build();
    }

    // 영수증 등록 여부 — 전용 플래그 컬럼이 없어 영수증 등록 시 항상 채워지는 totalAmount로 판단한다
    public boolean hasReceipt() {
        return totalAmount != null;
    }
}
