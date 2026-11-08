package com.moimzang.domain.budget.entity;

import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 장부 항목 entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry extends BaseEntity {

    // 장부 항목 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 기수
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_term_id", nullable = false)
    private GroupTerm groupTerm;

    // 자동기록 출처 차수(직접 입력이면 null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_round_id")
    private ScheduleRound scheduleRound;

    // 항목 유형(수입/지출)
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private LedgerType type;

    // 항목명
    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    // 금액
    @Column(name = "amount", nullable = false)
    private Integer amount;

    // 거래 일자
    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;
}
