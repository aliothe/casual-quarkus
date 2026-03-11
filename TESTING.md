# Testing Inbound Implementation

## Configuration

### application.properties
```properties
# Outbound to external Casual domain
quarkus.ironjacamar.casual-one.ra.config.host=10.109.83.94
quarkus.ironjacamar.casual-one.ra.config.port=7771
quarkus.ironjacamar.casual-one.ra.config.inbound-server-port=7772

# Outbound to local inbound server (for testing)
quarkus.ironjacamar.casual-self.ra.config.host=0.0.0.0
quarkus.ironjacamar.casual-self.ra.config.port=7772
```

### casual-config.json
```json
{
  "domain": {
    "name": "quarkus-casual-domain"
  },
  "outbound": {
    "unmanaged": true,
    "useEpoll": true
  },
  "inbound": {
    "useEpoll": true,
    "startup": {
      "mode": "immediate"
    }
  }
}
```

## Running the Application

```bash
CASUAL_CONFIG_FILE=./casual-config.json ./gradlew quarkusDev
```

## Expected Log Output

Look for these log messages indicating successful inbound startup:

```
QuarkusCasualResourceAdapter.start() called
Setting inbound server port to: 7772
Activating inbound endpoint (first RA instance)
=== Starting manual activation of Casual inbound endpoint ===
Inbound server port: 7772
Created activation spec
Created message endpoint factory proxy
Calling delegate.endpointActivation()...
start endpointActivation()
start casual inbound server
Casual inbound server bound to port: 7772
=== Casual inbound endpoint activation completed ===
```

If you see a second RA instance start, you should see:
```
QuarkusCasualResourceAdapter.start() called
Inbound endpoint already activated by another RA instance, skipping
```

## Verify Server is Listening

Check that the server is listening on port 7772:

```bash
netstat -an | grep 7772
# Should show: tcp6  0  0  :::7772  :::*  LISTEN

# or
lsof -i :7772
# Should show the Java process listening on port 7772
```

## Testing via JAX-RS

The application exposes a REST endpoint at `/casual/{serviceName}` that can call inbound services.

### Test Echo Service

```bash
curl -X POST http://localhost:8080/casual/echo \
  -H "Content-Type: application/casual-x-octet" \
  --data-binary "Hello World"
```

Expected response: `Hello World` (same bytes echoed back)

### Test Reverse Service

```bash
curl -X POST http://localhost:8080/casual/reverse \
  -H "Content-Type: application/casual-x-octet" \
  --data-binary "Hello"
```

Expected response: `olleH` (bytes reversed)

## Architecture

```
┌─────────────────────────────────────────────┐
│         Quarkus Application                  │
│                                              │
│  ┌────────────┐        ┌─────────────────┐  │
│  │  CasualOne │────────│ External Casual │  │
│  │  (Outbound)│        │   Domain        │  │
│  │            │        │  10.109.83.94   │  │
│  └────────────┘        │     :7771       │  │
│                        └─────────────────┘  │
│                                              │
│  ┌────────────┐        ┌─────────────────┐  │
│  │ CasualSelf │────┐   │ Inbound Server  │  │
│  │ (Outbound) │    └───│  Listening on   │  │
│  │            │        │    :7772        │  │
│  └────────────┘        │                 │  │
│       ↑                │  ┌───────────┐  │  │
│       │                │  │   echo    │  │  │
│  ┌────┴─────┐          │  │  service  │  │  │
│  │ JAX-RS   │          │  └───────────┘  │  │
│  │ Resource │          │  ┌───────────┐  │  │
│  └──────────┘          │  │  reverse  │  │  │
│                        │  │  service  │  │  │
│                        │  └───────────┘  │  │
│                        └─────────────────┘  │
└─────────────────────────────────────────────┘
```

### Flow for Testing via curl:

1. **curl** → POST to `/casual/echo`
2. **JAX-RS Resource** → Gets `casual-self` connection factory
3. **CasualSelf (Outbound)** → Connects to localhost:7772
4. **Inbound Server** → Receives service call request
5. **CasualMessageListenerImpl** → Dispatches to service handler
6. **Service Handler** → Finds `EchoServiceImpl` via `@CasualService`
7. **EchoServiceImpl** → Processes request and returns response
8. **Response** flows back through the chain to curl

## Troubleshooting

### Server not starting

If you don't see the inbound server log messages:

1. Check that both RA configs have `inbound-server-port` set
2. Check casual-config.json has valid inbound configuration
3. Look for exceptions in the logs during startup
4. Verify port 7772 is not already in use

### Connection refused

If testing via curl fails with connection refused:

1. Verify inbound server is listening: `lsof -i :7772`
2. Check that `casual-self` is configured to connect to the correct port
3. Look for "Casual inbound server bound to port: 7772" in logs

### Services not found

If you get "service not found" errors:

1. Check that `@CasualService` annotation is present
2. Verify the bean is `@ApplicationScoped`
3. Check that casual-inbound-handler-casual-service dependency is included
4. Look for service registration logs during startup

## Expected Test Results

### Successful Echo Test
```bash
$ curl -X POST http://localhost:8080/casual/echo \
    -H "Content-Type: application/casual-x-octet" \
    --data-binary "Hello World"
Hello World
```

### Successful Reverse Test
```bash
$ curl -X POST http://localhost:8080/casual/reverse \
    -H "Content-Type: application/casual-x-octet" \
    --data-binary "Hello"
olleH
```
