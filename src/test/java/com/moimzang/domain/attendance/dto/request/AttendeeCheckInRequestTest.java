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
class AttendeeCheckInRequestTest {

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

    @Test
    void isCheckedIn이_있으면_검증을_통과한다() {
        AttendeeCheckInRequest request = AttendeeCheckInRequest.builder().isCheckedIn(true).build();

        Set<ConstraintViolation<AttendeeCheckInRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void isCheckedIn이_없으면_NotNull_위반이다() {
        AttendeeCheckInRequest request = AttendeeCheckInRequest.builder().build();

        Set<ConstraintViolation<AttendeeCheckInRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("isCheckedIn");
    }
}
