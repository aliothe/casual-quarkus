# Service Handler Registration Fix

## The Problem

Services were discovered but not available for inbound calls:
```
WARNING [se.laz.casual.jca.inflow.work.CasualServiceCallWork] ServiceHandler not available for: echo
```

## Root Cause

Casual's `ServiceHandlerFactory` uses Java SPI (`ServiceLoader`) to discover handlers:

```java
for (ServiceHandler h: ServiceLoader.load(ServiceHandler.class)) {
    handlers.add(h);
}
```

Our `CasualQuarkusServiceHandler` was not registered in the SPI registry, so it was never discovered.

## The Fix

### 1. Implement getPriority()

Added priority to ensure our handler is checked before the default JNDI-based handler:

```java
@Override
public Priority getPriority() {
    return Priority.LEVEL_3;  // Higher than default LEVEL_5
}
```

### 2. Register via Java SPI

Created SPI registration file:

**File:** `src/main/resources/META-INF/services/se.laz.casual.jca.inbound.handler.service.ServiceHandler`

```
se.laz.casual.quarkus.CasualQuarkusServiceHandler
```

## How It Works Now

1. **Service call arrives** at inbound server
2. **ServiceHandlerFactory.getHandler("echo")** is called
3. **ServiceLoader** loads all registered handlers (including ours)
4. **Handlers sorted by priority** (LEVEL_3 before LEVEL_5)
5. **Each handler checked** via `canHandleService("echo")`
6. **CasualQuarkusServiceHandler** returns `true` (service in registry)
7. **Service invoked** on CDI bean instance

## Priority Levels

```
LEVEL_0 = Highest priority
LEVEL_1
LEVEL_2
LEVEL_3 ← CasualQuarkusServiceHandler (Quarkus/CDI)
LEVEL_4
LEVEL_5 ← CasualServiceHandler (Default JNDI)
LEVEL_6
LEVEL_7
LEVEL_8
LEVEL_9 = Lowest priority
```

Our handler has higher priority, so it's checked first. If a service is found in the Quarkus registry, it will be handled by our CDI-based handler instead of the JNDI-based one.

## Testing

Now when you run the application, you should see:

```
=== Casual Quarkus Service Discovery: Starting ===
Discovered service: echo in EchoServiceImpl.echo()
Registered service: echo
Discovered service: reverse in ReverseServiceImpl.reverse()
Registered service: reverse
=== Casual Quarkus Service Discovery: Complete ===
Discovered: 2 services, Registered: 2 services
```

And service calls should succeed:

```
INFO [se.laz.casual.quarkus.CasualQuarkusServiceHandler] Invoking Quarkus service: echo
FINE [se.laz.casual.quarkus.CasualQuarkusServiceHandler] Calling EchoServiceImpl.echo()
INFO [se.laz.casual.quarkus.CasualQuarkusServiceHandler] Service echo completed successfully
```

## For Future Extension

In a proper Quarkus extension, we could:

1. **Auto-register the handler** at build time
2. **No manual SPI file needed** - generated automatically
3. **Optimized priority** based on configuration
4. **Multiple handlers** for different service types

But for the POC, the manual SPI registration works perfectly!
