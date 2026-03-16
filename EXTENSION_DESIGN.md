# Casual Quarkus Extension Design

This POC is the foundation for a future **quarkus-casual** extension that will make it trivial for developers to expose Casual inbound services.

## Developer Experience

### Ideal Usage (Future Extension)

```java
@ApplicationScoped
public class MyService {

    @CasualService(name = "myService", category = "business")
    public InboundResponse handleRequest(InboundRequest request) {
        // Process request
        return InboundResponse.createBuilder()
            .buffer(responseBuffer)
            .build();
    }
}
```

That's it! The extension handles:
- ✅ Service discovery at build time
- ✅ Automatic registration with Casual inbound server
- ✅ CDI integration
- ✅ Transaction management
- ✅ Error handling

### Current POC Implementation

The POC uses:
1. **Runtime discovery** - `CasualQuarkusServiceDiscovery` scans CDI beans at startup
2. **Quarkus registry** - `CasualQuarkusServiceRegistry` holds service references
3. **Custom handler** - `CasualQuarkusServiceHandler` invokes CDI beans directly

This proves the concept works! The extension will move discovery to build-time for better performance.

## Extension Architecture

### Build Time (Future)

```
Developer Code
    ↓
@CasualService annotation
    ↓
Quarkus Build Step (Jandex scanning)
    ↓
Generate ServiceRegistry initialization code
    ↓
Quarkus Recorder (bytecode generation)
```

### Runtime (Current POC)

```
Application Startup
    ↓
CasualQuarkusServiceDiscovery observes @StartupEvent
    ↓
Scans all CDI beans for @CasualService
    ↓
Registers services in CasualQuarkusServiceRegistry
    ↓
CasualQuarkusServiceHandler handles inbound calls
```

## Components

### 1. Service Discovery (POC: Runtime, Extension: Build-time)

**Current POC:**
- `CasualQuarkusServiceDiscovery` - Scans beans at startup
- Uses `BeanManager` to find annotated methods
- Registers with `CasualQuarkusServiceRegistry`

**Future Extension:**
- Build step processor finds `@CasualService` at compile time
- Generates optimized registry initialization code
- No runtime scanning overhead

### 2. Service Registry

**Current POC:**
- `CasualQuarkusServiceRegistry` - Simple map-based registry
- Holds bean instances and methods

**Future Extension:**
- Generated at build time
- Integrated with Casual's `ServiceHandlerFactory`
- Support for multiple handlers (priority-based)

### 3. Service Handler

**Current POC:**
- `CasualQuarkusServiceHandler` - CDI-based handler
- Directly invokes methods on CDI beans
- Returns `InboundResponse`

**Future Extension:**
- Registered via SPI
- Support for interceptors
- Transaction integration
- Metrics/observability hooks

## Configuration (Future Extension)

### application.properties

```properties
# Inbound server configuration
quarkus.casual.inbound.enabled=true
quarkus.casual.inbound.port=7772
quarkus.casual.inbound.use-epoll=true

# Domain configuration
quarkus.casual.domain.name=my-quarkus-domain

# Outbound connections
quarkus.casual.outbound.connections.external.host=10.109.83.94
quarkus.casual.outbound.connections.external.port=7771
```

### Programmatic Configuration

```java
@ApplicationScoped
public class CasualConfig {

    @Produces
    @CasualInboundConfig
    public InboundConfiguration inboundConfig() {
        return InboundConfiguration.builder()
            .port(7772)
            .domain("my-domain")
            .startupMode(StartupMode.IMMEDIATE)
            .build();
    }
}
```

## Annotations

### @CasualService (Reuse from casual-java)

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CasualService {
    String name();
    String category() default "";
    TransactionType transactionType() default TransactionType.AUTOMATIC;
}
```

### @CasualQuarkusService (Optional Alternative)

If we need Quarkus-specific features:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CasualQuarkusService {
    String name();
    String category() default "";
    boolean transactional() default true;
}
```

## Extension Module Structure (Future)

```
quarkus-casual/
├── deployment/
│   ├── CasualBuildStep.java           # Build-time processing
│   ├── CasualServiceBuildItem.java    # Build items
│   └── CasualProcessor.java           # Main processor
├── runtime/
│   ├── CasualRecorder.java            # Runtime initialization
│   ├── CasualServiceRegistry.java     # Service registry
│   ├── CasualServiceHandler.java      # Service handler
│   └── CasualConfiguration.java       # Runtime config
└── integration-tests/
    └── src/test/java/...              # Integration tests
```

## Migration Path

### Phase 1: POC (Current)
- ✅ Runtime service discovery
- ✅ CDI integration
- ✅ Basic service handling
- ✅ Prove concept works

### Phase 2: Extension MVP
- Build-time service discovery
- Quarkus configuration integration
- IronJacamar integration
- Dev mode support

### Phase 3: Production Ready
- Transaction management
- Security integration
- Observability (metrics, tracing)
- Native image support
- Documentation

### Phase 4: Advanced Features
- Service mesh integration
- Cloud-native features
- Hot reload
- Testing utilities

## Developer Benefits

### Simple API
```java
// Just annotate your method!
@CasualService(name = "myService")
public InboundResponse handle(InboundRequest request) { ... }
```

### Zero Configuration
Extension provides sensible defaults, works out of the box.

### Quarkus Native
- Fast startup
- Low memory
- GraalVM native image support
- Dev mode with live reload

### Type Safe
- Compile-time service discovery
- IDE autocomplete support
- Build-time validation

## Testing Support (Future)

```java
@QuarkusTest
public class MyServiceTest {

    @InjectCasualService("myService")
    CasualServiceClient client;

    @Test
    public void testService() {
        InboundResponse response = client.call(request);
        assertEquals(ErrorState.OK, response.getErrorState());
    }
}
```

## Current POC Status

✅ **Working:**
- Inbound server starts
- Services discovered via @CasualService
- CDI beans registered
- Service invocation works

🚧 **To Do:**
- Integration with casual-java's ServiceHandlerFactory
- Transaction support
- Buffer handling improvements
- Error handling improvements

## Next Steps

1. **Test the POC** - Verify services are being discovered and invoked
2. **Refine API** - Get feedback on developer experience
3. **Create Extension** - Move to proper Quarkus extension structure
4. **Add Tests** - Comprehensive integration tests
5. **Documentation** - Developer guide and examples
6. **Publish** - Make available via Quarkus Platform

This POC proves the concept! Once validated, we can create a proper Quarkus extension that developers will love to use.
