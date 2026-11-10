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
    private final LocalDateTime createdAt;
}
