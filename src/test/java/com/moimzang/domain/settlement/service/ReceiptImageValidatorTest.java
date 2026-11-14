package com.moimzang.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ReceiptImageValidatorTest {

    private final ReceiptImageValidator validator = new ReceiptImageValidator();

    @Test
    void validate_jpg_이미지면_확장자를_돌려준다() {
        MockMultipartFile image = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", new byte[] {1});
        assertThat(validator.validate(image)).isEqualTo("jpg");
    }

    @Test
    void validate_대문자_확장자도_소문자로_정규화해서_허용한다() {
        MockMultipartFile image = new MockMultipartFile("image", "receipt.PNG", "image/png", new byte[] {1});
        assertThat(validator.validate(image)).isEqualTo("png");
    }

    @Test
    void validate_webp_확장자도_허용한다() {
        MockMultipartFile image = new MockMultipartFile("image", "receipt.webp", "image/webp", new byte[] {1});
        assertThat(validator.validate(image)).isEqualTo("webp");
    }

    @Test
    void validate_이미지가_null이면_COMMON_400을_던진다() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void validate_빈_파일이면_COMMON_400을_던진다() {
        MockMultipartFile image = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> validator.validate(image))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void validate_허용되지_않는_확장자면_COMMON_400을_던진다() {
        MockMultipartFile image = new MockMultipartFile("image", "receipt.gif", "image/gif", new byte[] {1});
        assertThatThrownBy(() -> validator.validate(image))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void validate_확장자가_없으면_COMMON_400을_던진다() {
        MockMultipartFile image = new MockMultipartFile("image", "receipt", "image/jpeg", new byte[] {1});
        assertThatThrownBy(() -> validator.validate(image))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void validate_파일명이_null이면_COMMON_400을_던진다() {
        MockMultipartFile image = new MockMultipartFile("image", null, "image/jpeg", new byte[] {1});
        assertThatThrownBy(() -> validator.validate(image))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }
}
