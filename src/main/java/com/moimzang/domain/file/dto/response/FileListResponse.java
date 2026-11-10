package com.moimzang.domain.file.dto.response;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FileListResponse {

    private final List<FileSummaryResponse> files;
    private final String nextCursor;
    private final Long totalCount;
}
