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
class LedgerEntryUpdateRequestTest {

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
    void 모든_필드가_null이어도_검증을_통과한다() {
        LedgerEntryUpdateRequest request = LedgerEntryUpdateRequest.builder().build();

        Set<ConstraintViolation<LedgerEntryUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void 보낸_필드만_있어도_검증을_통과한다() {
        LedgerEntryUpdateRequest request = LedgerEntryUpdateRequest.builder()
                .type(LedgerType.INCOME)
                .build();

        Set<ConstraintViolation<LedgerEntryUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void itemName이_공백이면_Pattern_위반이다() {
        LedgerEntryUpdateRequest request = LedgerEntryUpdateRequest.builder().itemName("   ").build();

        Set<ConstraintViolation<LedgerEntryUpdateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("itemName");
    }

    @Test
    void itemName이_빈_문자열이면_Pattern_위반이다() {
        LedgerEntryUpdateRequest request = LedgerEntryUpdateRequest.builder().itemName("").build();

        Set<ConstraintViolation<LedgerEntryUpdateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("itemName");
    }

    @Test
    void itemName이_100자를_넘으면_Size_위반이다() {
        LedgerEntryUpdateRequest request = LedgerEntryUpdateRequest.builder().itemName("가".repeat(101)).build();

        Set<ConstraintViolation<LedgerEntryUpdateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("itemName");
    }

    @Test
    void amount가_0이면_Positive_위반이다() {
        LedgerEntryUpdateRequest request = LedgerEntryUpdateRequest.builder().amount(0).build();

        Set<ConstraintViolation<LedgerEntryUpdateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("amount");
    }

    @Test
    void amount가_음수이면_Positive_위반이다() {
        LedgerEntryUpdateRequest request = LedgerEntryUpdateRequest.builder().amount(-1).build();

        Set<ConstraintViolation<LedgerEntryUpdateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("amount");
    }

    @Test
    void amount가_null이면_통과한다() {
        LedgerEntryUpdateRequest request = LedgerEntryUpdateRequest.builder().amount(null).build();

        Set<ConstraintViolation<LedgerEntryUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void entryDate가_null이어도_통과한다() {
        LedgerEntryUpdateRequest request = LedgerEntryUpdateRequest.builder().entryDate(null).build();

        Set<ConstraintViolation<LedgerEntryUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void entryDate를_보내면_그대로_반영될_값으로_통과한다() {
        LedgerEntryUpdateRequest request = LedgerEntryUpdateRequest.builder()
                .entryDate(LocalDate.of(2026, 11, 20))
                .build();

        Set<ConstraintViolation<LedgerEntryUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
