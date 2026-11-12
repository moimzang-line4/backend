package com.moimzang.domain.attendance.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// Spring 컨텍스트 없이 jakarta.validation Validator로만 검증한다(Docker 불필요)
class OnSiteAttendeeAddRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    private OnSiteAttendeeAddRequest.OnSiteAttendeeAddRequestBuilder valid() {
        return OnSiteAttendeeAddRequest.builder()
                .name("김모임")
                .affiliation("컴퓨터공학과")
                .phoneLast4("1234")
                .allowSameName(false);
    }

    @Test
    void 정상_요청은_검증을_통과한다() {
        OnSiteAttendeeAddRequest request = valid().build();

        Set<ConstraintViolation<OnSiteAttendeeAddRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void name이_공백이면_NotBlank_위반이다() {
        OnSiteAttendeeAddRequest request = valid().name("   ").build();

        Set<ConstraintViolation<OnSiteAttendeeAddRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("name");
    }

    @Test
    void name이_null이면_NotBlank_위반이다() {
        OnSiteAttendeeAddRequest request = valid().name(null).build();

        Set<ConstraintViolation<OnSiteAttendeeAddRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("name");
    }

    @Test
    void affiliation이_공백이면_NotBlank_위반이다() {
        OnSiteAttendeeAddRequest request = valid().affiliation(" ").build();

        Set<ConstraintViolation<OnSiteAttendeeAddRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("affiliation");
    }

    @Test
    void phoneLast4가_공백이면_NotBlank_위반만_발생한다() {
        OnSiteAttendeeAddRequest request = valid().phoneLast4(" ").build();

        Set<ConstraintViolation<OnSiteAttendeeAddRequest>> violations = validator.validate(request);

        // 공백은 NotBlank에 걸리고, Pattern(^\d{4}$)에도 안 맞아 둘 다 위반될 수 있다 —
        // 적어도 phoneLast4 하나는 위반이어야 한다는 것만 확인한다
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .allMatch("phoneLast4"::equals);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void phoneLast4가_3자리면_Pattern_위반이다() {
        OnSiteAttendeeAddRequest request = valid().phoneLast4("123").build();

        Set<ConstraintViolation<OnSiteAttendeeAddRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("phoneLast4");
    }

    @Test
    void phoneLast4에_숫자가_아닌_문자가_있으면_Pattern_위반이다() {
        OnSiteAttendeeAddRequest request = valid().phoneLast4("12a4").build();

        Set<ConstraintViolation<OnSiteAttendeeAddRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("phoneLast4");
    }

    @Test
    void name이_50자를_넘으면_Size_위반이다() {
        OnSiteAttendeeAddRequest request = valid().name("가".repeat(51)).build();

        Set<ConstraintViolation<OnSiteAttendeeAddRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("name");
    }
}
