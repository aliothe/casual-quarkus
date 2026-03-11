# Casual Inbound Configuration

This document describes the inbound configuration for the Quarkus Casual application.

## Overview

The inbound functionality allows external Casual clients to call services hosted in this Quarkus application. Services are exposed using the `@CasualService` annotation on CDI beans.

## Configuration

### Application Properties (`application.properties`)

Configure the inbound server port via the resource adapter configuration:

```properties
# Configure inbound server port (applies to all RA instances)
quarkus.ironjacamar.casual-one.ra.config.inbound-server-port=7772
```

**Important**: Only ONE inbound server will start, even if multiple resource adapter instances are configured. The port is set on the first RA instance that starts.

### Casual Configuration (`casual-config.json`)

```json
{
  "domain": {
    "name": "quarkus-casual-domain"
  },
  "inbound": {
    "useEpoll": true,
    "startup": {
      "mode": "immediate"
    }
  }
}
```

**Configuration Options:**

- `domain.name`: The name of the Casual domain (used for domain discovery)
- `inbound.useEpoll`: Use epoll for better performance on Linux (default: false)
- `inbound.initialDelay`: Delay in seconds before starting the inbound server (optional)
- `inbound.startup.mode`: When to start the inbound server
  - `immediate`: Start immediately (default)
  - `trigger`: Start when a trigger service is deployed
  - `discover`: Start after specified services are registered
- `inbound.startup.services`: List of service names to wait for (only when mode is `discover`)

## Creating Inbound Services

To create a service that can be called by Casual clients:

1. Create a service interface
2. Create an implementation with `@ApplicationScoped` CDI scope
3. Annotate the service method with `@CasualService`

### Example: Echo Service

```java
@ApplicationScoped
public class EchoServiceImpl implements EchoService {
    @CasualService(name = "echo", category = "example")
    @Override
    public InboundResponse echo(InboundRequest request) {
        return InboundResponse.createBuilder()
                .buffer(request.getBuffer())
                .build();
    }
}
```

### CasualService Annotation Parameters

- `name`: The service name that Casual clients will use to call this service
- `category`: Optional category for service organization and discovery
- `transactionType`: Transaction type (AUTO, ATOMIC, NONE, JOIN) - defaults to AUTO

## Example Services

Two example services are provided:

1. **echo** - Returns the same buffer that was sent
2. **reverse** - Reverses the bytes in the buffer

## Testing Inbound Services

To test the inbound services, you need a Casual client that can connect to port 7772 and call the services.

Example using casual-java outbound client:

```java
try (CasualConnection connection = casualConnectionFactory.getConnection()) {
    OctetBuffer request = OctetBuffer.of("Hello".getBytes());
    ServiceReturn<CasualBuffer> reply = connection.tpcall("echo", request, flags);
    // Process reply
}
```

## Running the Application

Start the application with the Casual configuration:

```bash
CASUAL_CONFIG_FILE=./casual-config.json ./gradlew quarkusDev
```

The inbound server will start on port 7772 and begin accepting connections from Casual clients.

## Architecture

The inbound flow works as follows:

1. External Casual client connects to port 7772
2. Client performs domain discovery to find available services
3. Client makes service call request
4. `CasualMessageListenerImpl` receives the network message
5. Request is dispatched to the appropriate service handler
6. Service handler finds the CDI bean with matching `@CasualService` annotation
7. Service method is invoked with `InboundRequest`
8. Service returns `InboundResponse`
9. Response is sent back to the client

## Dependencies

The following dependencies are required for inbound support:

- `casual-inbound-api`: Inbound API interfaces
- `casual-inbound`: Inbound server implementation with `CasualMessageListenerImpl`
- `casual-inbound-handler-api`: Service handler API
- `casual-inbound-handler-casual-service`: Handler for `@CasualService` annotated services

## Troubleshooting

### Inbound server not starting

- Check that the port (7772) is not already in use
- Verify that `CASUAL_CONFIG_FILE` environment variable is set
- Check application logs for any startup errors

### Services not discovered

- Ensure the service bean is `@ApplicationScoped`
- Verify the `@CasualService` annotation is present
- Check that the casual-inbound-handler-casual-service dependency is included
- Verify Jandex indexing is configured for casual dependencies

### Connection refused

- Confirm the inbound server started successfully
- Check firewall rules allow connections on port 7772
- Verify the address is correctly configured (0.0.0.0 for all interfaces)
