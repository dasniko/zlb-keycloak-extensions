# ZLB Keycloak Extensions

Custom [Keycloak](https://www.keycloak.org) extensions for the ZLB (Stiftung Zentral- und Landesbibliothek Berlin).

![](https://img.shields.io/badge/Keycloak-26.8-blue)
![](https://img.shields.io/badge/Java-21-f89820)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE.md)

## Extensions

| Extension | Description |
|---|---|
| [Periodic Email Verification](doc/periodic-email-verification.md) | Makes users re-verify their email address periodically |

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

## Local Development with Docker Compose

The `docker-compose.yml` starts Keycloak (with the test realm imported) and a Mailpit instance (UI: http://localhost:8025).
The directory `target/providers/` is mounted into the Keycloak container as `/opt/keycloak/providers`,
so any JAR built there is picked up on container start.

    $ ./deploy.sh

which is the same as `mvn clean package -DskipTests && docker compose up`.

Keycloak loads providers on startup only, so restart the container after each rebuild.
Note that `mvn clean` removes `target/providers/`, so rebuild while the container is stopped.

## Download the Binaries

Alternatively to building the binaries yourself, you can also download them from GitHub actions.

Go to [Actions](https://github.com/dasniko/zlb-keycloak-extensions/actions/workflows/maven.yml) and click on the most recent workflow run.

In the details of this execution, you can find the section `Artifacts` with the artifact named `<repository-name>-<short-commit-sha>` listed.
It contains the provider JAR (`zlb-keycloak-extensions-<version>.jar`).
Here you can download the artifact up until 90 days after the last build time.

## Deployment

Deploy the built JAR file into the `$KEYCLOAK_HOME/providers` folder of your Keycloak server (or copy it to your Docker image).

When running Keycloak in `optimized` mode, you'll have to run a `build` after deploying the JAR.
If you start Keycloak regularly, an implicit build will be performed.
See [Keycloak Docs](https://www.keycloak.org/docs) and [Keycloak Guides](https://www.keycloak.org/guides) for more info.

Check the documentation of each extension for the configuration it needs.

## License

Licensed under the [Apache License 2.0](LICENSE.md); parts of the code marked with `@CopiedFromKeycloak` mirror logic of [Keycloak](https://github.com/keycloak/keycloak), which is licensed under the same license.
