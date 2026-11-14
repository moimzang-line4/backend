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

    // 부원을 만든다. 명단방에서 수집하지 않는 항목은 호출하는 쪽에서 null로 넘긴다
    public static Member create(MemberList memberList, String name, String school, String department, String phone,
                                String studentId, String part, String grade, boolean privacyConsent) {
        return Member.builder()
                .memberList(memberList)
                .name(name)
                .school(school)
                .department(department)
                .phone(phone)
                .studentId(studentId)
                .part(part)
                .grade(grade)
                .privacyConsent(privacyConsent)
                .build();
    }

    // 운영진의 부원 정보 수정. 수정 화면에 있는 이름·학교·학과·전화번호만 바꾸고 null인 항목은 그대로 둔다.
    // 학교·전화번호는 선택 항목이라 빈 문자열을 보내면 비운다
    public void update(String name, String school, String department, String phone) {
        if (name != null) {
            this.name = name;
        }
        if (school != null) {
            this.school = school.isBlank() ? null : school;
        }
        if (department != null) {
            this.department = department;
        }
        if (phone != null) {
            this.phone = phone.isBlank() ? null : phone;
        }
    }
}
