package com.moimzang.global.cursor;

// 모든 도메인의 cursor 기반 목록 조회가 공유하는 페이지 크기 기본값·상한
public final class CursorPageConstants {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private CursorPageConstants() {
    }

    public static int resolveSize(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(requested, MAX_SIZE);
    }
}
