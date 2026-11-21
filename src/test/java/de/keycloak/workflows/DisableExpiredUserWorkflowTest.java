package de.keycloak.workflows;

import de.keycloak.testsupport.KeycloakEnvironment;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.models.workflow.ResourceType;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.userprofile.config.UPAttribute;
import org.keycloak.representations.userprofile.config.UPAttributePermissions;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.keycloak.representations.workflows.WorkflowRepresentation;
import org.keycloak.representations.workflows.WorkflowStepRepresentation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the step {@code disable-expired-user} as part of a workflow in a real Keycloak.
 * <p>
 * The workflow is activated for single users through the admin API; the test environment runs the workflow steps
 * within that request, so the result can be checked right afterwards.
 */
class DisableExpiredUserWorkflowTest extends KeycloakEnvironment {

	private static final String ATTRIBUTE = "accountExpiry";

	private static String workflowId;

	@BeforeAll
	static void createWorkflow() {
		// declare the attribute in the user profile, editable by administrators only, as recommended in the documentation
		UPConfig userProfile = realm().users().userProfile().getConfiguration();
		UPAttributePermissions permissions = new UPAttributePermissions();
		permissions.setView(Set.of("admin"));
		permissions.setEdit(Set.of("admin"));
		userProfile.addOrReplaceAttribute(new UPAttribute(ATTRIBUTE, permissions));
		realm().users().userProfile().update(userProfile);

		WorkflowRepresentation workflow = WorkflowRepresentation.withName("disable-expired-users-" + UUID.randomUUID())
			.withSteps(WorkflowStepRepresentation.create()
				.of(DisableExpiredUserStepProviderFactory.PROVIDER_ID)
				.withConfig(DisableExpiredUserStepProvider.ATTRIBUTE_KEY, ATTRIBUTE)
				.build())
			.build();
		try (Response response = realm().workflows().create(workflow)) {
			assertEquals(201, response.getStatus(), "workflow not created: " + response.readEntity(String.class));
			String path = response.getLocation().getPath();
			workflowId = path.substring(path.lastIndexOf('/') + 1);
		}
	}

	@Test
	void userWithAnExpiryInThePastIsDisabled() {
		TestUser user = createUserWithExpiry(Instant.now().minus(1, ChronoUnit.HOURS).toString());

		activateWorkflow(user);

		assertFalse(reload(user).isEnabled());
	}

	@Test
	void userWithAnExpiryInTheFutureStaysEnabled() {
		TestUser user = createUserWithExpiry(Instant.now().plus(1, ChronoUnit.DAYS).toString());

		activateWorkflow(user);

		assertTrue(reload(user).isEnabled());
	}

	@Test
	void userWithAnInvalidValueStaysEnabledAndAWarningIsLogged() {
		TestUser user = createUserWithExpiry("31.01.2025");

		activateWorkflow(user);

		assertTrue(reload(user).isEnabled());
		assertTrue(keycloakLogs().contains("Attribute '%s' of user %s is not an ISO date(time)".formatted(ATTRIBUTE, user.id())),
			"warning expected in the log");
	}

	@Test
	void userWithoutTheAttributeStaysEnabled() {
		TestUser user = createUser(true, true, null);

		activateWorkflow(user);

		assertTrue(reload(user).isEnabled());
	}

	private static TestUser createUserWithExpiry(String expiry) {
		TestUser user = createUser(true, true, null);
		UserRepresentation representation = reload(user);
		representation.setAttributes(Map.of(ATTRIBUTE, List.of(expiry)));
		realm().users().get(user.id()).update(representation);
		return user;
	}

	private static void activateWorkflow(TestUser user) {
		realm().workflows().workflow(workflowId).activate(ResourceType.USERS.name(), user.id());
	}
}
