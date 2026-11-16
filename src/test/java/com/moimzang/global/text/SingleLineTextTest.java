package com.moimzang.global.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SingleLineTextTest {

    @Test
    void normalize_줄바꿈을_공백으로_바꾼다() {
        assertThat(SingleLineText.normalize("가을 정기공연\n운영진 회의"))
                .isEqualTo("가을 정기공연 운영진 회의");
    }

    @Test
    void normalize_윈도우_줄바꿈도_한_칸으로_접는다() {
        // \r\n을 각각 공백으로 바꾸면 두 칸이 된다
        assertThat(SingleLineText.normalize("가을 정기공연\r\n운영진 회의"))
                .isEqualTo("가을 정기공연 운영진 회의");
    }

    @Test
    void normalize_탭도_공백으로_바꾼다() {
        assertThat(SingleLineText.normalize("1차\t회의")).isEqualTo("1차 회의");
    }

    @Test
    void normalize_자바_기본_공백이_아닌_유니코드_구분자도_편다() {
        // 붙여넣기로 실제로 딸려 오는 것들이다. 자바 기본 \s로는 하나도 안 잡힌다
        assertThat(SingleLineText.normalize("가을\u00A0공연")).isEqualTo("가을 공연");   // NBSP
        assertThat(SingleLineText.normalize("가을\u3000공연")).isEqualTo("가을 공연");   // 이데오그래픽 공백
        assertThat(SingleLineText.normalize("가을\u0085공연")).isEqualTo("가을 공연");   // NEL
    }

    @Test
    void normalize_유니코드_줄_구분자도_편다() {
        // U+2028은 진짜 줄바꿈이다 — 이게 남으면 decisions #84가 막으려던 검색 누락이 그대로 난다
        assertThat(SingleLineText.normalize("가을 정기공연\u2028운영진 회의"))
                .isEqualTo("가을 정기공연 운영진 회의");
        assertThat(SingleLineText.normalize("가을 정기공연\u2029운영진 회의"))
                .isEqualTo("가을 정기공연 운영진 회의");
    }

    @Test
    void normalize_연속_공백을_한_칸으로_접는다() {
        assertThat(SingleLineText.normalize("가을    정기공연")).isEqualTo("가을 정기공연");
    }

    @Test
    void normalize_앞뒤_공백을_없앤다() {
        assertThat(SingleLineText.normalize("  운영진 회의  ")).isEqualTo("운영진 회의");
        assertThat(SingleLineText.normalize("\n운영진 회의\n")).isEqualTo("운영진 회의");
    }

    @Test
    void normalize_이미_한_줄이면_그대로_둔다() {
        assertThat(SingleLineText.normalize("가을 정기공연 운영진 회의"))
                .isEqualTo("가을 정기공연 운영진 회의");
    }

    @Test
    void normalize_null은_null로_돌려준다() {
        // 선택 필드가 많아 null을 그대로 흘려보내야 "보낸 필드만 수정" 로직이 깨지지 않는다
        assertThat(SingleLineText.normalize(null)).isNull();
    }

    @Test
    void normalize_공백뿐이면_빈_문자열이_된다() {
        // 빈 값 판정은 @NotBlank·@Pattern이 검증 단계에서 이미 끝낸다
        assertThat(SingleLineText.normalize("   ")).isEmpty();
        assertThat(SingleLineText.normalize("\n\n")).isEmpty();
        assertThat(SingleLineText.normalize("")).isEmpty();
    }

    @Test
    void normalize_결과가_원본보다_길어지지_않는다() {
        // @Size 검증을 통과한 뒤에 정규화해도 안전하다는 성질
        String raw = "가을 정기공연\r\n\t운영진    회의  ";
        assertThat(SingleLineText.normalize(raw).length()).isLessThanOrEqualTo(raw.length());
    }
}
