package com.moimzang.domain.file.service;

// 생성된 썸네일 바이트와 그것을 서빙할 때 필요한 정보. 확장자가 원본과 다를 수 있어
// (gif 원본 → png 썸네일) 함께 들고 다닌다
public record FileThumbnail(byte[] content, String extension, String contentType) {
}
