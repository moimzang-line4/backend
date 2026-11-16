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
