package com.moimzang.domain.file.dto.response;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FilePolicyResponse {

    private final List<String> allowedExtensions;
    private final Long maxSizeBytes;
}
