package com.moimzang.domain.memberlist.entity;

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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 부원 entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "member")
public class Member extends BaseEntity {

    // 부원 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 명단방
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_list_id", nullable = false)
    private MemberList memberList;

    // 이름(토글과 무관하게 항상 필수)
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    // 학교(명단방의 학교 수집이 켜져 있을 때만 수집)
    @Column(name = "school", length = 100)
    private String school;

    // 학과(이름과 함께 항상 필수)
    @Column(name = "department", nullable = false, length = 100)
    private String department;

    // 전화번호(명단방의 전화번호 수집이 켜져 있을 때만 수집)
    @Column(name = "phone", length = 20)
    private String phone;

    // 학번(명단방의 학번 수집이 켜져 있을 때만 수집)
    @Column(name = "student_id", length = 30)
    private String studentId;

    // 파트(명단방의 파트 수집이 켜져 있을 때만 수집)
    @Column(name = "part", length = 50)
    private String part;

    // 학년(명단방의 학년 수집이 켜져 있을 때만 수집)
    @Column(name = "grade", length = 20)
    private String grade;

    // 개인정보 수집 동의 여부
    @Column(name = "privacy_consent", nullable = false)
    private Boolean privacyConsent;
}
