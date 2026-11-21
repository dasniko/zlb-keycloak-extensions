package de.keycloak.authentication.requiredaction;

import de.keycloak.testsupport.Fakes;
import de.keycloak.testsupport.Fakes.FakeUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the time of the last verification, which decides whether the periodic verification is due, without a running Keycloak.
 */
class PeriodicVerifyEmailTimestampTest {

	private static final Map<String, String> ONE_DAY = Map.of("email-verify-period-amount", "1", "email-verify-period-unit", "DAYS");

	private final PeriodicVerifyEmail requiredAction = new PeriodicVerifyEmail();

	@Test
	void verificationWithinThePeriodIsNotDue() {
		// control for the tests below
		FakeUser user = new FakeUser();
		user.emailVerifiedAt = System.currentTimeMillis() - Duration.ofHours(1).toMillis();

		evaluateTriggers(user);

		assertTrue(user.writes.isEmpty());
		assertTrue(user.emailVerified);
	}

	@ParameterizedTest
	@ValueSource(strings = {"2026-01-01", "abc", " 1700000000000"})
	void timestampThatIsNotANumberMakesTheVerificationDue(String value) {
		// e.g. a value entered by an administrator; the verification stores a valid timestamp again
		FakeUser user = new FakeUser();
		user.attributes.put(PeriodicVerifyEmail.EMAIL_VERIFIED_AT, value);

		evaluateTriggers(user);

		assertEquals(List.of("addRequiredAction", "setEmailVerified"), user.writes);
		assertFalse(user.emailVerified);
	}

	@Test
	void missingTimestampAndCreationTimeMakeTheVerificationDue() {
		// e.g. a user of a custom user storage, which does not know the time of creation
		FakeUser user = new FakeUser();
		user.createdTimestamp = null;

		evaluateTriggers(user);

		assertEquals(List.of("addRequiredAction", "setEmailVerified"), user.writes);
		assertFalse(user.emailVerified);
	}

	private void evaluateTriggers(FakeUser user) {
		requiredAction.evaluateTriggers(Fakes.context(Fakes.realmWithUserStorage("no-storage", "WRITABLE"), user.model(), ONE_DAY));
	}
}
