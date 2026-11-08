package com.moimzang.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * 생성·수정 시각을 모두 갖는 테이블의 공통 조상.
 *
 * 수정 화면이 있는 대부분의 테이블이 이쪽을 상속한다(club_group, schedule, member,
 * member_list, operation_note, meeting, meeting_agenda, settlement_target 등).
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseCreatedEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
