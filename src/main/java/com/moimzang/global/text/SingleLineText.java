package com.moimzang.global.text;

import java.util.regex.Pattern;

/**
 * 한 줄 입력란에 들어온 값을 한 줄로 정규화한다.
 *
 * 붙여넣기로 줄바꿈이 섞여 들어와도 거부하지 않고 조용히 펴 준다 — 거부는 이미 한 번
 * 되돌린 방향이고, 막아서 얻는 것보다 사용자가 입력을 다시 다듬게 만드는 비용이 크다.
 *
 * 펴 주지 않으면 LIKE 검색이 조용히 깨진다. "가을 정기공연\n운영진 회의"는 사용자가
 * 자연스럽게 치는 "가을 정기공연 운영진 회의"로 검색되지 않는다.
 *
 * 여러 줄이 정상인 본문(안건 내용, 논의 내용, 채팅 질문)에는 쓰지 않는다.
 */
public final class SingleLineText {

    // 자바 기본 \s는 [ \t\n\x0B\f\r]뿐이라 붙여넣기로 실제로 딸려 오는 구분자를 놓친다.
    // 특히 U+2028(LINE SEPARATOR)은 진짜 줄바꿈인데 \s로는 안 잡혀 검색이 그대로 어긋난다.
    // \p{Z}로 NBSP·이데오그래픽 공백까지 덮고, 줄 구분자 세 개를 따로 더한다
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\p{Z}\\u0085\\u2028\\u2029]+");

    private SingleLineText() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        // 줄바꿈만 공백으로 바꾸면 "a\n b"가 "a  b"로 남는다 — 연속 공백까지 한 칸으로 접는다
        return SEPARATORS.matcher(raw).replaceAll(" ").trim();
    }
}
