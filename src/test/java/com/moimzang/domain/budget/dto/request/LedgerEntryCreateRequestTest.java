package com.moimzang.domain.budget.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.budget.enums.LedgerType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// Spring 컨텍스트 없이 jakarta.validation Validator로만 검증한다(Docker 불필요)
class LedgerEntryCreateRequestTest {

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

    private LedgerEntryCreateRequest.LedgerEntryCreateRequestBuilder valid() {
        return LedgerEntryCreateRequest.builder()
                .type(LedgerType.EXPENSE)
                .itemName("간식비")
                .amount(15000)
                .entryDate(LocalDate.of(2026, 11, 15));
    }

    @Test
    void 정상_요청은_검증을_통과한다() {
        LedgerEntryCreateRequest request = valid().build();

        Set<ConstraintViolation<LedgerEntryCreateRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void type이_null이면_NotNull_위반이다() {
        LedgerEntryCreateRequest request = valid().type(null).build();

        Set<ConstraintViolation<LedgerEntryCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("type");
    }

    @Test
    void itemName이_null이면_NotBlank_위반이다() {
        LedgerEntryCreateRequest request = valid().itemName(null).build();

        Set<ConstraintViolation<LedgerEntryCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("itemName");
    }

    @Test
    void itemName이_공백이면_NotBlank_위반이다() {
        LedgerEntryCreateRequest request = valid().itemName("   ").build();

        Set<ConstraintViolation<LedgerEntryCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("itemName");
    }

    @Test
    void itemName이_100자를_넘으면_Size_위반이다() {
        LedgerEntryCreateRequest request = valid().itemName("가".repeat(101)).build();

        Set<ConstraintViolation<LedgerEntryCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("itemName");
    }

    @Test
    void amount가_null이면_NotNull_위반이다() {
        LedgerEntryCreateRequest request = valid().amount(null).build();

        Set<ConstraintViolation<LedgerEntryCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("amount");
    }

    @Test
    void amount가_0이면_Positive_위반이다() {
        LedgerEntryCreateRequest request = valid().amount(0).build();

        Set<ConstraintViolation<LedgerEntryCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("amount");
    }

    @Test
    void amount가_음수이면_Positive_위반이다() {
        LedgerEntryCreateRequest request = valid().amount(-1000).build();

        Set<ConstraintViolation<LedgerEntryCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("amount");
    }

    @Test
    void entryDate가_null이면_NotNull_위반이다() {
        LedgerEntryCreateRequest request = valid().entryDate(null).build();

        Set<ConstraintViolation<LedgerEntryCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("entryDate");
    }
}
