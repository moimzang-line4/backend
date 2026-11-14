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

    // 학과는 이름과 함께 항상 받는 항목이라 토글과 무관하게 수집으로 고정한다
    public static MemberList create(GroupTerm groupTerm, String name, LocalDateTime registerDeadline,
                                    boolean collectSchool, boolean collectStudentId, boolean collectPart,
                                    boolean collectGrade, boolean collectPhone, String joinToken) {
        return MemberList.builder()
                .groupTerm(groupTerm)
                .name(name)
                .registerDeadline(registerDeadline)
                .collectSchool(collectSchool)
                .collectDepartment(Boolean.TRUE)
                .collectPhone(collectPhone)
                .collectStudentId(collectStudentId)
                .collectPart(collectPart)
                .collectGrade(collectGrade)
                .status(MemberListStatus.RECRUITING)
                .joinToken(joinToken)
                .isLinkClosed(Boolean.FALSE)
                .build();
    }

    // 명단방 설정을 부분 수정한다 — 인자가 null인 항목은 바꾸지 않는다.
    // 수집 토글을 꺼도 이미 받은 부원 정보는 그대로 두고, 이후 등록부터 적용된다
    public void update(String name, LocalDateTime registerDeadline, Boolean collectSchool, Boolean collectStudentId,
                       Boolean collectPart, Boolean collectGrade, Boolean collectPhone) {
        if (name != null) {
            this.name = name;
        }
        if (registerDeadline != null) {
            this.registerDeadline = registerDeadline;
        }
        if (collectSchool != null) {
            this.collectSchool = collectSchool;
        }
        if (collectStudentId != null) {
            this.collectStudentId = collectStudentId;
        }
        if (collectPart != null) {
            this.collectPart = collectPart;
        }
        if (collectGrade != null) {
            this.collectGrade = collectGrade;
        }
        if (collectPhone != null) {
            this.collectPhone = collectPhone;
        }
    }

    // 등록 폼에서 받는 항목인지 확인한다. 이름·학과는 항상 받으므로 토글 항목만 묻는다
    public boolean collectsSchool() {
        return Boolean.TRUE.equals(collectSchool);
    }

    public boolean collectsStudentId() {
        return Boolean.TRUE.equals(collectStudentId);
    }

    public boolean collectsPart() {
        return Boolean.TRUE.equals(collectPart);
    }

    public boolean collectsGrade() {
        return Boolean.TRUE.equals(collectGrade);
    }

    public boolean collectsPhone() {
        return Boolean.TRUE.equals(collectPhone);
    }

    // 링크를 닫았거나 마감일이 지났으면 더 이상 등록을 받지 않는다
    public MemberListStatus effectiveStatus(LocalDateTime now) {
        boolean closed = Boolean.TRUE.equals(isLinkClosed)
                || (registerDeadline != null && now.isAfter(registerDeadline));
        return closed ? MemberListStatus.COMPLETED : MemberListStatus.RECRUITING;
    }
}
