package com.moimzang.domain.file.service;

import com.moimzang.domain.file.enums.FileDownloadMode;

// 콘텐츠 서빙 엔드포인트 주소를 만드는 곳을 한 군데로 모은다. 다운로드 URL 발급(서비스)과
// 목록 응답의 썸네일 주소(매퍼)가 각자 포맷을 조립하면 한쪽만 바뀌어도 조용히 어긋난다
public final class FileContentUrl {

    private static final String PATH = "/api/files/%d/content?mode=%s";

    private FileContentUrl() {
    }

    public static String of(Long fileId, FileDownloadMode mode) {
        return PATH.formatted(fileId, mode);
    }

    // 썸네일은 화면에 바로 띄우는 용도라 항상 미리보기(inline)로 연다
    public static String ofThumbnail(Long fileId) {
        return of(fileId, FileDownloadMode.PREVIEW) + "&variant=THUMBNAIL";
    }
}
