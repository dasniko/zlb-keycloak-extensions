# Workflow Steps

Custom steps for the [workflows](https://www.keycloak.org/docs/latest/server_admin/#_managing_workflows) of Keycloak.
Workflows automate administrative tasks on realm resources, e.g. users, triggered by events or by a schedule.

The workflows feature is enabled by default since Keycloak 26.6, no feature flag is required.
Workflows are managed per realm in the Admin Console (_Workflows_), where they are defined in YAML.
A custom step is used like a built-in one, by its id in the `uses` setting of a step.

## Step `disable-expired-user`

Disables a user if the date or date-time stored in a user attribute lies in the past.

| `with` setting | Required | Description |
|---|---|---|
| `attribute` | yes | Name of the user attribute holding the expiry date or date-time. |

The step leaves the user untouched, if

- the user is already disabled,
- the attribute is not set or empty,
- the value is not an ISO-8601 date or date-time (logged as a warning),
- the user storage of the user is read-only, e.g. an LDAP federation without import (logged as a warning).

If the setting `attribute` is missing, the step does nothing and logs a warning.

### Values of the Attribute

Accepted values (ISO-8601):

| Value | Meaning |
|---|---|
| `2025-01-31` | start of the day, in the time zone of the server |
| `2025-01-31T10:15`, `2025-01-31T10:15:30` | local date-time, in the time zone of the server |
| `2025-01-31T10:15:30+02:00`, `2025-01-31T10:15:30Z` | date-time with offset |

The time zone of the server is the default time zone of the JVM running Keycloak.
Use values with an offset, if the expiry must not depend on the configuration of the server.

### Protecting the Attribute

Users must not be able to change the attribute themselves, otherwise they could postpone the expiry of their account.
Declare it in the user profile of the realm with permissions for administrators only,
the same way as described for `emailVerifiedAt` in [Periodic Email Verification](periodic-email-verification.md#user-profile-configuration).

### Example

A workflow, which checks all users of the realm once a day (`accountExpiry` is an example name of the attribute):

```yaml
name: Disable expired users
schedule:
  after: 1d
  batch-size: 100
steps:
  - uses: disable-expired-user
    with:
      attribute: accountExpiry
```

`after` is the interval between the runs, `batch-size` the maximum number of users processed per run.
See [Scheduling workflows](https://www.keycloak.org/docs/latest/server_admin/#_scheduling_workflows_) for the details,
and [Managing workflows](https://www.keycloak.org/docs/latest/server_admin/#_managing_workflows) for other triggers (`on`) and conditions (`if`).
