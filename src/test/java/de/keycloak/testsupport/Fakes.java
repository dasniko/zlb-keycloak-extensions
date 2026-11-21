package de.keycloak.testsupport;

import io.opentelemetry.api.trace.Span;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.LDAPConstants;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredActionConfigModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.storage.ReadOnlyException;
import org.keycloak.tracing.TracingProvider;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Minimal fakes of Keycloak models for unit tests without a running Keycloak. Methods that are not faked return the default value of their type.
 */
public final class Fakes {

	public static final String USERNAME = "secret-username";

	private Fakes() {
	}

	/**
	 * A fake user, which records the write calls.
	 */
	public static final class FakeUser {
		public String federationLink;
		public String email = "user@keycloak.test";
		public boolean emailVerified = true;
		public Long emailVerifiedAt;
		public boolean enabled = true;
		public Long createdTimestamp = System.currentTimeMillis();
		/** further attributes of the user */
		public final Map<String, String> attributes = new HashMap<>();
		/** the required actions of the user */
		public final List<String> requiredActions = new ArrayList<>();
		/** if set, all write calls fail like with a read-only user storage */
		public boolean readOnly;
		/** the names of the write methods, which fail like with a read-only user storage, e.g. "setEmailVerified" */
		public final Set<String> readOnlyMethods = new HashSet<>();
		/** the names of the write methods, which have been called successfully */
		public final List<String> writes = new ArrayList<>();

		public UserModel model() {
			return proxy(UserModel.class, (method, args) -> {
				switch (method.getName()) {
					case "getId":
						return "00000000-0000-0000-0000-000000000001";
					case "getUsername":
						return USERNAME;
					case "getFederationLink":
						return federationLink;
					case "getEmail":
						return email;
					case "isEmailVerified":
						return emailVerified;
					case "getCreatedTimestamp":
						return createdTimestamp;
					case "isEnabled":
						return enabled;
					case "getFirstAttribute":
						if ("emailVerifiedAt".equals(args[0]) && emailVerifiedAt != null) {
							return String.valueOf(emailVerifiedAt);
						}
						return attributes.get((String) args[0]);
					case "getRequiredActionsStream":
						return requiredActions.stream();
					case "addRequiredAction", "removeRequiredAction", "setEmailVerified", "setSingleAttribute", "setEnabled":
						if (readOnly || readOnlyMethods.contains(method.getName())) {
							throw new ReadOnlyException("The user is read-only. Not possible to write '" + method.getName() + "' when updating user '" + USERNAME + "'.");
						}
						writes.add(method.getName());
						switch (method.getName()) {
							case "setEnabled" -> enabled = (boolean) args[0];
							case "setEmailVerified" -> emailVerified = (boolean) args[0];
							case "addRequiredAction" -> {
								// like Keycloak, the required actions of a user are a set
								if (!requiredActions.contains(args[0].toString())) {
									requiredActions.add(args[0].toString());
								}
							}
							case "removeRequiredAction" -> requiredActions.remove(args[0].toString());
							default -> {
							}
						}
						return null;
					default:
						return defaultValue(method.getReturnType());
				}
			});
		}
	}

	/**
	 * A realm, which knows one user storage component with the given edit mode.
	 */
	public static RealmModel realmWithUserStorage(String componentId, String editMode) {
		ComponentModel component = new ComponentModel();
		component.setId(componentId);
		MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
		config.putSingle(LDAPConstants.EDIT_MODE, editMode);
		component.setConfig(config);
		return proxy(RealmModel.class, (method, args) -> {
			if (method.getName().equals("getComponent") && componentId.equals(args[0])) {
				return component;
			}
			return defaultValue(method.getReturnType());
		});
	}

	/**
	 * A context with the given realm, user and configuration of the required action.
	 * <p>
	 * Its event builder is a real {@link EventBuilder}, which sends the events to nowhere (no event store, no listeners, no tracing).
	 */
	public static RequiredActionContext context(RealmModel realm, UserModel user, Map<String, String> config) {
		RequiredActionConfigModel configModel = new RequiredActionConfigModel();
		configModel.setConfig(config);
		EventBuilder event = new EventBuilder(realm, session());
		return proxy(RequiredActionContext.class, (method, args) -> switch (method.getName()) {
			case "getRealm" -> realm;
			case "getUser" -> user;
			case "getConfig" -> configModel;
			case "getEvent" -> event;
			default -> defaultValue(method.getReturnType());
		});
	}

	/**
	 * A session with the given realm as the realm of its context, whose user provider knows the given user only.
	 */
	public static KeycloakSession sessionWithUser(RealmModel realm, UserModel user) {
		UserProvider users = proxy(UserProvider.class, (method, args) -> {
			if (method.getName().equals("getUserById") && args[0] == realm && user.getId().equals(args[1])) {
				return user;
			}
			return defaultValue(method.getReturnType());
		});
		KeycloakContext context = proxy(KeycloakContext.class, (method, args) ->
			method.getName().equals("getRealm") ? realm : defaultValue(method.getReturnType()));
		return proxy(KeycloakSession.class, (method, args) -> switch (method.getName()) {
			case "users" -> users;
			case "getContext" -> context;
			default -> defaultValue(method.getReturnType());
		});
	}

	/**
	 * A session, which offers no providers except a tracing provider without recording.
	 */
	private static KeycloakSession session() {
		TracingProvider tracing = proxy(TracingProvider.class, (method, args) ->
			method.getName().equals("getCurrentSpan") ? Span.getInvalid() : defaultValue(method.getReturnType()));
		KeycloakSessionFactory sessionFactory = proxy(KeycloakSessionFactory.class, (method, args) -> defaultValue(method.getReturnType()));
		return proxy(KeycloakSession.class, (method, args) -> switch (method.getName()) {
			case "getProvider" -> args.length == 1 && args[0] == TracingProvider.class ? tracing : null;
			case "getKeycloakSessionFactory" -> sessionFactory;
			default -> defaultValue(method.getReturnType());
		});
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, Handler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> handler.invoke(method, args));
	}

	private static Object defaultValue(Class<?> type) {
		if (type == boolean.class) {
			return false;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == Stream.class) {
			return Stream.empty();
		}
		return null;
	}

	@FunctionalInterface
	private interface Handler {
		Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
	}
}
