package com.moimzang.domain.file.dto.response;

import com.moimzang.domain.file.enums.FileCategory;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FileSummaryResponse {

    private final Long fileId;
    private final String name;
    private final String extension;
    private final Long size;
    private final FileCategory category;
    private final Long scheduleId;
    // 활동사진 그리드에 띄울 썸네일 주소. 이미지가 아니거나 생성에 실패한 자료는 null이다
    private final String thumbnailUrl;
    private final LocalDateTime createdAt;
}
