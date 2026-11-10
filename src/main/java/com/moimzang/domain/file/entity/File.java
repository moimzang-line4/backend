package com.moimzang.domain.file.entity;

import com.moimzang.domain.file.enums.FileCategory;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 자료 entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "file")
public class File extends BaseEntity {

    // 자료 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 기수(기수별 자료실)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_term_id", nullable = false)
    private GroupTerm groupTerm;

    // 첨부된 일정(null이면 자료실 직접등록, 있으면 일정 삭제 시 함께 삭제)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    // 자료명
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // 확장자
    @Column(name = "extension", nullable = false, length = 20)
    private String extension;

    // 파일 크기
    @Column(name = "size", nullable = false)
    private Long size;

    // 자료 분류(활동문서/예산안/활동사진/장부)
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private FileCategory category;

    // 스토리지 객체 키(다운로드·미리보기 URL 발급에 사용)
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    // 소프트 삭제 일시(되돌리기 지원)
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static File upload(GroupTerm groupTerm, Schedule schedule, String name, String extension,
                               long size, FileCategory category, String storageKey) {
        return File.builder()
                .groupTerm(groupTerm)
                .schedule(schedule)
                .name(name)
                .extension(extension)
                .size(size)
                .category(category)
                .storageKey(storageKey)
                .build();
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    // 기간 제한 없이 언제든 되돌릴 수 있다
    public void restore() {
        this.deletedAt = null;
    }
}
