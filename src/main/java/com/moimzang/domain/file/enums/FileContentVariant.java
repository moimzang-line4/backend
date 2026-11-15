package com.moimzang.domain.file.enums;

// 콘텐츠 서빙 시 어떤 바이트를 내려줄지 고른다. mode(Content-Disposition)와는 다른 축이다
public enum FileContentVariant {
    ORIGINAL,
    THUMBNAIL
}
