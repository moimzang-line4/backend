package com.moimzang.domain.schedule.entity;

import com.moimzang.domain.group.entity.GroupTerm;
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

// 일정 entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "schedule")
public class Schedule extends BaseEntity {

    // 일정 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 기수
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_term_id", nullable = false)
    private GroupTerm groupTerm;

    // 일정 카테고리(미생성 시 null 가능)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_category_id")
    private ScheduleCategory scheduleCategory;

    // 제목
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    // 색상(8종 중 자유 선택, 카테고리와 무관)
    @Column(name = "color", nullable = false, length = 20)
    private String color;

    // 종일 여부
    @Column(name = "is_all_day", nullable = false)
    private Boolean isAllDay;

    // 시작 일시
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    // 종료 일시
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    // 장소
    @Column(name = "location", length = 255)
    private String location;

    // 운영 공간 생성 여부(메모·차수·자료 등 하위 데이터가 이 일정을 직접 참조)
    @Column(name = "has_operation_space", nullable = false)
    private Boolean hasOperationSpace;

    // 메모
    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;
}
