package com.moimzang.domain.memberlist.entity;

import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.memberlist.enums.MemberListStatus;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 명단방 entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "member_list")
public class MemberList extends BaseEntity {

    // 명단방 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 기수
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_term_id", nullable = false)
    private GroupTerm groupTerm;

    // 명단방 이름
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // 등록 마감일시
    @Column(name = "register_deadline")
    private LocalDateTime registerDeadline;

    // 학교 수집 여부
    @Column(name = "collect_school", nullable = false)
    private Boolean collectSchool;

    // 학과 수집 여부
    @Column(name = "collect_department", nullable = false)
    private Boolean collectDepartment;

    // 전화번호 수집 여부
    @Column(name = "collect_phone", nullable = false)
    private Boolean collectPhone;

    // 학번 수집 여부
    @Column(name = "collect_student_id", nullable = false)
    private Boolean collectStudentId;

    // 파트 수집 여부
    @Column(name = "collect_part", nullable = false)
    private Boolean collectPart;

    // 학년 수집 여부
    @Column(name = "collect_grade", nullable = false)
    private Boolean collectGrade;

    // 명단방 상태(등록중/등록완료)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberListStatus status;

    // 등록 링크 토큰(고유값)
    @Column(name = "join_token", nullable = false, unique = true, length = 64)
    private String joinToken;

    // 등록 링크 닫힘 여부
    @Column(name = "is_link_closed", nullable = false)
    private Boolean isLinkClosed;
}
