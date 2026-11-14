package com.moimzang.domain.budget.entity;

import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.budget.enums.LedgerAutoCategory;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.schedule.entity.Schedule;
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
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "ledger_entry", uniqueConstraints = @UniqueConstraint(
        name = "uk_ledger_entry_schedule_auto_category",
        columnNames = {"schedule_id", "auto_category"}))
public class LedgerEntry extends BaseEntity {

    // 장부 항목 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 기수
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_term_id", nullable = false)
    private GroupTerm groupTerm;

    // 자동기록 출처 일정(직접 입력이면 null). 자동기록은 차수 단위가 아니라 일정 단위로 합산해
    // 생성하므로, 이 참조가 그 일정 내 모든 차수를 대표하는 유일한 FK다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    // (레거시 컬럼) 과거 "차수 단위 자동기록" 설계에서 쓰던 차수 참조. 일정 단위로 바뀐 뒤로는 새
    // 자동기록이 이 값을 채우지 않는다 — 자동기록은 schedule로만 연결된다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_round_id")
    private ScheduleRound scheduleRound;

    // 항목 유형(수입/지출)
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private LedgerType type;

    // 자동기록 종류(같은 일정에 생기는 최대 3종 지출 + 1종 수입을 구분, 직접 입력이면 null)
    @Enumerated(EnumType.STRING)
    @Column(name = "auto_category", length = 30)
    private LedgerAutoCategory autoCategory;

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
