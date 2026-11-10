package com.moimzang.domain.file.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// 목록 cursor 인코딩 — base64(정렬 기준 값) + "." + id. 정렬 기준 값은 base64라 "."을 포함할 수
// 없으므로 마지막 "."로 안전하게 분리할 수 있다
public record FileCursor(String sortValue, Long id) {

    public static String encode(String sortValue, Long id) {
        String encodedSortValue = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sortValue.getBytes(StandardCharsets.UTF_8));
        return encodedSortValue + "." + id;
    }

    public static FileCursor decode(String cursor) {
        int dot = cursor.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException("잘못된 cursor 형식이다: " + cursor);
        }
        String sortValue = new String(
                Base64.getUrlDecoder().decode(cursor.substring(0, dot)), StandardCharsets.UTF_8);
        Long id = Long.parseLong(cursor.substring(dot + 1));
        return new FileCursor(sortValue, id);
    }
}
