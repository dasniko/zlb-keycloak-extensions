# ZLB Keycloak Extensions

Custom [Keycloak](https://www.keycloak.org) extensions for the ZLB (Stiftung Zentral- und Landesbibliothek Berlin).

![](https://img.shields.io/badge/Keycloak-26.8-blue)
![](https://img.shields.io/badge/Java-21-f89820)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE.md)

## Extensions

| Extension | Description |
|---|---|
| [Periodic Email Verification](doc/periodic-email-verification.md) | Makes users re-verify their email address periodically |
| [Workflow Steps](doc/workflow-steps.md) | Custom steps for Keycloak workflows, e.g. disabling users whose account has expired |

All extensions are built into one provider JAR.

## Building the Binaries

You can build the binaries if you have a local installation of JDK, with

    $ ./mvnw clean package

If you don't have local JDK installation, but Docker is available, you can use the helper script

    $ ./build-with-docker.sh

Both approaches will build the binary `target/zlb-keycloak-extensions-<version>.jar`
and additionally copy it to the directory `target/providers/`.

`./mvnw clean package` also runs the tests, which require a running Docker environment (see [Running the Tests](#running-the-tests)).
Without Docker, skip them with `./mvnw clean package -DskipTests`.
The helper script `build-with-docker.sh` builds with `-DskipTests`.

## Running the Tests

The integration tests are based on [Testcontainers](https://testcontainers.com/) and run the extensions in a real Keycloak container.
A Mailpit container catches the emails sent by Keycloak.

**Prerequisite:** a running Docker environment, which is accessible for the user running Maven.
The images `quay.io/keycloak/keycloak:<keycloak.version from pom.xml>` and `axllent/mailpit:latest` are pulled, if not yet available locally.

    $ ./mvnw test

The Keycloak and Mailpit containers are started for each test class, so a test run takes some time.

## Updating Keycloak

The extensions are built and tested against the Keycloak version set by the property `keycloak.version` in the `pom.xml`.
It defines the version of the Keycloak dependencies (`keycloak-spi-bom`), of the Keycloak image used by the tests,
and of the upstream code the copied code is compared with.

Some parts of the code mirror logic of Keycloak and are marked with the `@CopiedFromKeycloak` annotation.
The `copied-code-maven-plugin` compares them with the code of the Keycloak version in use.

To update to a new Keycloak version:

1. Set `keycloak.version` in the `pom.xml` to the new version.
2. Run the build including the tests and the check of the copied code:

       $ ./mvnw clean verify

3. Check the result of the copied code check in the build output (`Copied code: {...}`):
   - `UNCHANGED`: the upstream code is unchanged, nothing to do.
   - `CHANGED`: the upstream code has changed, and the build fails.
     The log shows the diff of the upstream code, it is also stored in `target/copied-code/`.
     Review whether the local code has to follow the change, adapt it if necessary,
     and set `version` of the `@CopiedFromKeycloak` annotation to the new Keycloak version.
4. For a new minor or major version, also update the Keycloak badge in this README and the image in `docker-compose.yml`.

## Local Development with Docker Compose

The `docker-compose.yml` starts Keycloak (with the test realm imported) and a Mailpit instance (UI: http://localhost:8025).
The directory `target/providers/` is mounted into the Keycloak container as `/opt/keycloak/providers`,
so any JAR built there is picked up on container start.

    $ ./deploy.sh

which is the same as `mvn clean package -DskipTests && docker compose up`.

Keycloak loads providers on startup only, so restart the container after each rebuild.
Note that `mvn clean` removes `target/providers/`, so rebuild while the container is stopped.

## Download the Binaries

Alternatively to building the binaries yourself, you can download the provider JAR (`zlb-keycloak-extensions-<version>.jar`)
from the [Releases](https://github.com/dasniko/zlb-keycloak-extensions/releases).

### Keycloak Compatibility

The release notes of each release state the Keycloak minor version the release is compatible with,
e.g. _Compatible with Keycloak 26.8.x_, and the exact Keycloak version it was built and tested against.
Releases with a suffix in the version, e.g. `v1.0.0-RC1`, are marked as pre-releases.

## Deployment

Deploy the built JAR file into the `$KEYCLOAK_HOME/providers` folder of your Keycloak server (or copy it to your Docker image).

When running Keycloak in `optimized` mode, you'll have to run a `build` after deploying the JAR.
If you start Keycloak regularly, an implicit build will be performed.
See [Keycloak Docs](https://www.keycloak.org/docs) and [Keycloak Guides](https://www.keycloak.org/guides) for more info.

Check the documentation of each extension for the configuration it needs.

## License

Licensed under the [Apache License 2.0](LICENSE.md); parts of the code marked with `@CopiedFromKeycloak` mirror logic of [Keycloak](https://github.com/keycloak/keycloak), which is licensed under the same license.
