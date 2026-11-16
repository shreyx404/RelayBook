package com.relaybook.model;

import com.relaybook.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation rules, including the polymorphic minimum-routes messages.
 * The rules are exercised through {@code Builder.build()}, which is the single
 * place that invokes {@link ContactValidator} — an invalid contact can never
 * leave the builder.
 */
class ContactValidatorTest {

    @Test
    void emergencyContactWithoutTwoRoutesIsRejectedWithSpecificMessage() {
        ValidationException ex = org.junit.jupiter.api.Assertions.assertThrows(ValidationException.class,
                () -> Contact.builder(ContactType.EMERGENCY)
                        .fullName("Solo Emergency")
                        .route(Channel.PHONE, "+91 11111 11111")
                        .build());
        assertTrue(ex.getMessage().contains("Emergency contacts need at least two routes"),
                "message should be type-specific but was: " + ex.getMessage());
    }

    @Test
    void personContactWithoutAnyRouteIsRejected() {
        ValidationException ex = org.junit.jupiter.api.Assertions.assertThrows(ValidationException.class,
                () -> Contact.builder(ContactType.PERSON).fullName("No Route").build());
        assertTrue(ex.getMessage().contains("Add at least one phone number or email route"));
    }

    @Test
    void invalidEmailOnAnyEmailRouteIsRejected() {
        ValidationException ex = org.junit.jupiter.api.Assertions.assertThrows(ValidationException.class,
                () -> Contact.builder(ContactType.PERSON).fullName("Bad Email")
                        .route(Channel.PHONE, "+91 11111 11111")
                        .route(Channel.EMAIL, "not-an-email")
                        .build());
        assertTrue(ex.getMessage().contains("valid format"));
    }

    @Test
    void futureVerificationDateIsRejected() {
        ValidationException ex = org.junit.jupiter.api.Assertions.assertThrows(ValidationException.class,
                () -> Contact.builder(ContactType.PERSON).fullName("Future")
                        .route(Channel.PHONE, "+91 11111 11111", java.time.LocalDate.now().plusDays(1))
                        .build());
        assertTrue(ex.getMessage().contains("cannot be in the future"));
    }

    @Test
    void invalidTimeZoneIsRejected() {
        ValidationException ex = org.junit.jupiter.api.Assertions.assertThrows(ValidationException.class,
                () -> Contact.builder(ContactType.PERSON).fullName("Bad Zone")
                        .route(Channel.PHONE, "+91 11111 11111")
                        .timeZone("Mars/Olympus")
                        .build());
        assertTrue(ex.getMessage().contains("valid IANA zone id"));
    }

    @Test
    void validEmergencyContactPasses() {
        Contact valid = Contact.builder(ContactType.EMERGENCY).fullName("Fine Emergency")
                .route(Channel.PHONE, "+91 11111 11111", java.time.LocalDate.now())
                .route(Channel.EMAIL, "fine@example.test", java.time.LocalDate.now())
                .timeZone("Asia/Kolkata").build();
        ContactValidator.validate(valid); // must not throw
        org.junit.jupiter.api.Assertions.assertEquals(2, valid.getRoutes().size());
    }

    @Test
    void notesOverTheLimitAreRejected() {
        StringBuilder longNotes = new StringBuilder();
        for (int i = 0; i < Contact.MAX_NOTE_LENGTH + 1; i++) {
            longNotes.append('x');
        }
        org.junit.jupiter.api.Assertions.assertThrows(ValidationException.class,
                () -> Contact.builder(ContactType.PERSON).fullName("Long Notes")
                        .route(Channel.PHONE, "+91 11111 11111")
                        .notes(longNotes.toString())
                        .build());
    }

    @Test
    void validatorAlsoAcceptsAValidContactDirectly() {
        Contact valid = Contact.builder(ContactType.PERSON).fullName("Direct")
                .route(Channel.PHONE, "+91 11111 11111").build();
        ContactValidator.validate(valid); // must not throw
    }
}
