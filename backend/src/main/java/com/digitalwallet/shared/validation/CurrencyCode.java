package com.digitalwallet.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bean Validation annotation: the annotated value MUST be a 3-letter ISO 4217 currency
 * code present in {@link java.util.Currency#getAvailableCurrencies()}.
 *
 * <p>{@code null} is rejected (use {@code @Nullable} or wrap in {@code Optional} if the
 * field is genuinely optional). ISO 4217 codes are uppercase by definition, so lowercase
 * input is rejected.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CurrencyCodeValidator.class)
@Documented
public @interface CurrencyCode {

    String message() default "must be a valid ISO 4217 currency code";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
