package de.keycloak.workflows;

import de.keycloak.testsupport.Fakes;
import de.keycloak.testsupport.Fakes.FakeUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.workflow.WorkflowEvent;
import org.keycloak.models.workflow.WorkflowExecutionContext;
import org.keycloak.models.workflow.WorkflowStep;

import java.lang.reflect.Proxy;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the step {@code disable-expired-user}, without a running Keycloak.
 */
class DisableExpiredUserStepProviderTest {

	private static final String ATTRIBUTE = "accountExpiry";

	private final RealmModel realm = (RealmModel) Proxy.newProxyInstance(RealmModel.class.getClassLoader(), new Class<?>[]{RealmModel.class},
		(proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);

	// --- parsing of the values ---

	@Test
	void plainDateIsTheStartOfTheDayInTheTimeZoneOfTheServer() {
		assertEquals(LocalDate.of(2025, 1, 31).atStartOfDay(ZoneId.systemDefault()).toInstant(),
			DisableExpiredUserStepProvider.parse("2025-01-31"));
	}

	@Test
	void localDateTimeIsInTheTimeZoneOfTheServer() {
		assertEquals(LocalDateTime.of(2025, 1, 31, 10, 15).atZone(ZoneId.systemDefault()).toInstant(),
			DisableExpiredUserStepProvider.parse("2025-01-31T10:15"));
		assertEquals(LocalDateTime.of(2025, 1, 31, 10, 15, 30).atZone(ZoneId.systemDefault()).toInstant(),
			DisableExpiredUserStepProvider.parse("2025-01-31T10:15:30"));
	}

	@Test
	void dateTimeWithOffsetDoesNotDependOnTheTimeZoneOfTheServer() {
		assertEquals(Instant.parse("2025-01-31T08:15:30Z"), DisableExpiredUserStepProvider.parse("2025-01-31T10:15:30+02:00"));
		assertEquals(Instant.parse("2025-01-31T10:15:30Z"), DisableExpiredUserStepProvider.parse("2025-01-31T10:15:30Z"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"tomorrow", "31.01.2025", "2025-13-01", "2025-01-31T25:00", "2025-01-31 10:15", "1738317330000"})
	void valueThatIsNotAnIsoDateOrDateTimeIsRejected(String value) {
		assertThrows(DateTimeException.class, () -> DisableExpiredUserStepProvider.parse(value));
	}

	// --- running the step ---

	@Test
	void userWithAnExpiryInThePastIsDisabled() {
		FakeUser user = userWithExpiry(Instant.now().minus(1, ChronoUnit.MINUTES).toString());

		run(user, ATTRIBUTE);

		assertFalse(user.enabled);
		assertEquals(List.of("setEnabled"), user.writes);
	}

	@Test
	void userWithAPlainDateInThePastIsDisabled() {
		FakeUser user = userWithExpiry(LocalDate.now(ZoneId.systemDefault()).minusDays(1).toString());

		run(user, ATTRIBUTE);

		assertFalse(user.enabled);
	}

	@Test
	void userWithAnExpiryInTheFutureIsLeftUntouched() {
		FakeUser user = userWithExpiry(Instant.now().plus(1, ChronoUnit.MINUTES).toString());

		run(user, ATTRIBUTE);

		assertTrue(user.enabled);
		assertTrue(user.writes.isEmpty());
	}

	@Test
	void valueIsTrimmed() {
		FakeUser user = userWithExpiry("  " + Instant.now().minus(1, ChronoUnit.MINUTES) + "  ");

		run(user, ATTRIBUTE);

		assertFalse(user.enabled);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   ", "not-a-date"})
	void userWithAnEmptyOrInvalidValueIsLeftUntouched(String value) {
		FakeUser user = userWithExpiry(value);

		run(user, ATTRIBUTE);

		assertTrue(user.enabled);
		assertTrue(user.writes.isEmpty());
	}

	@Test
	void userWithoutTheAttributeIsLeftUntouched() {
		FakeUser user = new FakeUser();

		run(user, ATTRIBUTE);

		assertTrue(user.enabled);
		assertTrue(user.writes.isEmpty());
	}

	@Test
	void userWhoIsAlreadyDisabledIsLeftUntouched() {
		FakeUser user = userWithExpiry(Instant.now().minus(1, ChronoUnit.MINUTES).toString());
		user.enabled = false;

		run(user, ATTRIBUTE);

		assertTrue(user.writes.isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "  "})
	void stepWithoutTheAttributeSettingDoesNothing(String attribute) {
		FakeUser user = userWithExpiry(Instant.now().minus(1, ChronoUnit.MINUTES).toString());

		run(user, attribute);

		assertTrue(user.enabled);
		assertTrue(user.writes.isEmpty());
	}

	@Test
	void stepWithoutAnySettingDoesNothing() {
		FakeUser user = userWithExpiry(Instant.now().minus(1, ChronoUnit.MINUTES).toString());

		run(user, null);

		assertTrue(user.enabled);
		assertTrue(user.writes.isEmpty());
	}

	@Test
	void unknownUserIsIgnored() {
		FakeUser user = userWithExpiry(Instant.now().minus(1, ChronoUnit.MINUTES).toString());
		ComponentModel step = stepModel(ATTRIBUTE);

		new DisableExpiredUserStepProvider(Fakes.sessionWithUser(realm, user.model()), step).run(context("another-user-id"));

		assertTrue(user.writes.isEmpty());
	}

	private static FakeUser userWithExpiry(String value) {
		FakeUser user = new FakeUser();
		user.attributes.put(ATTRIBUTE, value);
		return user;
	}

	/**
	 * Runs the step for the user.
	 *
	 * @param attribute the value of the step setting {@code attribute}, or {@code null}, if the setting is missing
	 */
	private void run(FakeUser user, String attribute) {
		UserModel model = user.model();
		new DisableExpiredUserStepProvider(Fakes.sessionWithUser(realm, model), stepModel(attribute)).run(context(model.getId()));
	}

	private static ComponentModel stepModel(String attribute) {
		ComponentModel model = new ComponentModel();
		MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
		if (attribute != null) {
			config.putSingle(DisableExpiredUserStepProvider.ATTRIBUTE_KEY, attribute);
		}
		model.setConfig(config);
		return model;
	}

	private static WorkflowExecutionContext context(String resourceId) {
		return new WorkflowExecutionContext() {
			@Override
			public String getResourceId() {
				return resourceId;
			}

			@Override
			public WorkflowEvent getEvent() {
				return null;
			}

			@Override
			public WorkflowStep getNextStep() {
				return null;
			}
		};
	}
}
