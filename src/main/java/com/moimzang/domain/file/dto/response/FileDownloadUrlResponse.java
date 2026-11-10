package com.moimzang.domain.file.dto.response;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FileDownloadUrlResponse {

    private final String url;
    private final LocalDateTime expiresAt;
}
