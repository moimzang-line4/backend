package com.moimzang.domain.settlement.dto.request;

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
class AccountSetRequestTest {

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

    private AccountSetRequest.AccountSetRequestBuilder valid() {
        return AccountSetRequest.builder()
                .bankName("국민은행")
                .accountNumber("123-456-789012")
                .accountHolder("김모임");
    }

    @Test
    void 정상_요청은_검증을_통과한다() {
        AccountSetRequest request = valid().build();

        Set<ConstraintViolation<AccountSetRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void bankName이_null이면_NotBlank_위반이다() {
        AccountSetRequest request = valid().bankName(null).build();

        Set<ConstraintViolation<AccountSetRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("bankName");
    }

    @Test
    void bankName이_공백이면_NotBlank_위반이다() {
        AccountSetRequest request = valid().bankName("   ").build();

        Set<ConstraintViolation<AccountSetRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("bankName");
    }

    @Test
    void accountNumber가_null이면_NotBlank_위반이다() {
        AccountSetRequest request = valid().accountNumber(null).build();

        Set<ConstraintViolation<AccountSetRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("accountNumber");
    }

    @Test
    void accountHolder가_공백이면_NotBlank_위반이다() {
        AccountSetRequest request = valid().accountHolder(" ").build();

        Set<ConstraintViolation<AccountSetRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("accountHolder");
    }

    @Test
    void bankName이_50자를_넘으면_Size_위반이다() {
        AccountSetRequest request = valid().bankName("가".repeat(51)).build();

        Set<ConstraintViolation<AccountSetRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("bankName");
    }
}
