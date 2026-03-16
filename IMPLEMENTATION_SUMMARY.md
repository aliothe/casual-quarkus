# Inbound Implementation Summary

## Overview

Inbound functionality has been implemented for the Quarkus Casual application. This allows external Casual clients to connect to this application and call services hosted within it.

## Changes Made

### 1. Dependencies Added (`versions.gradle` and `build.gradle`)

Added the following Casual inbound dependencies:
- `casual-inbound`: Core inbound server implementation
- `casual-inbound-handler-api`: Inbound service handler API
- `casual-inbound-handler-casual-service`: Handler for `@CasualService` annotated services

### 2. Configuration Files

#### `application.properties`
- Added Jandex indexing for inbound dependencies to enable CDI discovery

#### `casual-config.json`
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

**Note**: The inbound port is configured in `application.properties`, not in `casual-config.json`.

### 3. Resource Adapter Updates

#### `QuarkusCasualResourceAdapter.java`
- Modified to manually activate the inbound endpoint when the resource adapter starts
- Creates a proxy `MessageEndpointFactory` that provides `CasualMessageListenerImpl` instances
- Properly handles cleanup on shutdown

The manual activation is necessary because the IronJacamar Quarkus extension (v1.7.1) doesn't automatically trigger `endpointActivation()` for Message-Driven Bean style activation specs.

#### `CasualResourceAdapterFactory.java`
- Updated `createActivationSpec()` to properly configure the port from configuration

### 4. Example Inbound Services

Created two example services to demonstrate inbound functionality:

#### `EchoService` / `EchoServiceImpl`
- Service name: `echo`
- Category: `example`
- Returns the same buffer that was sent

#### `ReverseService` / `ReverseServiceImpl`
- Service name: `reverse`
- Category: `example`
- Reverses the bytes in the received buffer

Both services use:
- CDI `@ApplicationScoped` scope
- `@CasualService` annotation for service discovery
- `InboundRequest` / `InboundResponse` for request/response handling

## How It Works

1. **Startup**: When the application starts, `QuarkusCasualResourceAdapter.start()` is called
2. **Endpoint Activation**: The adapter manually calls `delegate.endpointActivation()` with a proxy `MessageEndpointFactory`
3. **Inbound Server**: The `CasualResourceAdapter` starts the inbound Netty server on port 7772
4. **Service Discovery**: When external clients connect, they can discover available services
5. **Service Invocation**:
   - Client makes service call
   - `CasualMessageListenerImpl` receives the network message
   - Message is dispatched to appropriate service handler
   - Handler finds CDI bean with matching `@CasualService` annotation
   - Service method is invoked
   - Response is sent back to client

## Testing

### Running the Application

```bash
CASUAL_CONFIG_FILE=./casual-config.json ./gradlew quarkusDev
```

The inbound server will start on port 7772 (configurable in `casual-config.json`).

### Verifying Inbound is Running

Check the logs for:
```
Manually activating Casual inbound endpoint
start casual inbound server
Casual inbound server bound to port: 7772
Casual inbound endpoint activated
```

### Calling Inbound Services

From a Casual client (e.g., using casual-java outbound):

```java
try (CasualConnection connection = casualConnectionFactory.getConnection()) {
    // Call echo service
    OctetBuffer request = OctetBuffer.of("Hello World".getBytes());
    Flag<AtmiFlags> flags = Flag.of(AtmiFlags.NOFLAG);
    ServiceReturn<CasualBuffer> reply = connection.tpcall("echo", request, flags);

    if (reply.getServiceReturnState() == ServiceReturnState.TPSUCCESS) {
        byte[] responseBytes = reply.getReplyBuffer().getBytes().get(0);
        System.out.println("Response: " + new String(responseBytes));
    }
}
```

### Network Testing

You can verify the port is listening:
```bash
netstat -an | grep 7772
# or
lsof -i :7772
```

## Architecture Notes

### MessageEndpointFactory Proxy

Since Quarkus doesn't support traditional EJB Message-Driven Beans, we create a proxy `MessageEndpointFactory` that:
- Returns new instances of `CasualMessageListenerImpl` when `createEndpoint()` is called
- Indicates transacted delivery support via `isDeliveryTransacted()`
- Provides the activation spec when requested

### Service Discovery

The `casual-inbound-handler-casual-service` module provides:
- CDI observer for `@CasualService` annotated methods
- Service registry that maps service names to CDI beans
- Automatic service registration during application startup

## Known Limitations

1. **IronJacamar Quarkus Support**: Version 1.7.1 doesn't fully support inbound activation configuration, requiring manual activation
2. **Split Packages**: Warning about split packages in casual-java modules (doesn't affect functionality)
3. **Reverse Inbound**: Not yet implemented (will be added after basic inbound is verified)

## Next Steps

1. Test with actual Casual clients
2. Add more example services demonstrating transactions
3. Implement reverse inbound when needed
4. Consider contributing upstream fix to IronJacamar Quarkus extension for automatic activation

## Documentation

See [INBOUND.md](INBOUND.md) for detailed configuration options and usage examples.
