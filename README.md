# casual-quarkus

**Proof of Concept** for integrating [Quarkus](https://quarkus.io/) with [Casual](https://github.com/casualcore/casual) middleware. This POC is the foundation for a future **quarkus-casual extension**.

## Features

- **Outbound**: Call external Casual services from Quarkus applications
- **Inbound**: Expose Quarkus CDI beans as Casual services
- **Developer-Friendly**: Simple `@CasualService` annotation
- **Cloud-Native**: Fast startup, low memory, GraalVM ready

## Quick Start

### 1. Create a Service

```java
@ApplicationScoped
public class MyService {

    @CasualService(name = "myService", category = "business")
    public InboundResponse handle(InboundRequest request) {
        // Your business logic here
        return InboundResponse.createBuilder()
            .buffer(responseBuffer)
            .build();
    }
}
```

### 2. Run the Application

```bash
CASUAL_CONFIG_FILE=./casual-config.json ./gradlew quarkusDev
```

### 3. Services Auto-Discovered!

```
=== Casual Quarkus Service Discovery: Starting ===
Discovered service: myService in MyService.handle()
Registered service: myService
=== Casual Quarkus Service Discovery: Complete ===
```

That's it! Your service is now callable by external Casual clients.

## Documentation

- **[QUARKUS_POC_SUMMARY.md](QUARKUS_POC_SUMMARY.md)** - **Start here!** POC overview and how it works
- **[EXTENSION_DESIGN.md](EXTENSION_DESIGN.md)** - Future extension architecture and roadmap
- **[INBOUND.md](INBOUND.md)** - Inbound configuration details
- **[TESTING.md](TESTING.md)** - Testing instructions

## Branch

This work is on the `feature/inbound` branch.

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./gradlew quarkusDev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew build -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./gradlew build -Dquarkus.native.enabled=true
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/casual-quarkus-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/gradle-tooling>.

## Related Guides

- REST ([guide](https://quarkus.io/guides/rest)): A Jakarta REST implementation utilizing build time processing and Vert.x. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it.
- IronJacamar (JCA) ([guide](https://docs.quarkiverse.io/quarkus-ironjacamar/dev/)): Run Jakarta Connectors (JCA) adapters in Quarkus

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
