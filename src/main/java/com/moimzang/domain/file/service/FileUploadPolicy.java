package com.moimzang.domain.file.service;

import java.util.Set;

// 업로드 허용 범위 — API-062 응답과 업로드 검증이 이 상수 하나를 같이 참조한다
public final class FileUploadPolicy {

    public static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;

    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "hwp", "hwpx",
            "jpg", "jpeg", "png", "gif", "webp",
            "zip");

    private FileUploadPolicy() {
    }

    public static boolean isAllowed(String extension) {
        return ALLOWED_EXTENSIONS.contains(extension);
    }
}
