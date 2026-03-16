# Quarkus Service Discovery for Casual Inbound

## The Problem

In traditional application servers (WebLogic, WildFly), Casual uses:

1. **CasualServiceDiscovery** (CDI Extension) - Discovers `@CasualService` annotated methods and registers metadata
2. **JndiSearchTimerEjbSingleton** (EJB Timer) - Periodically searches JNDI to resolve actual EJB proxies
3. **CasualServiceHandler** - Looks up beans via JNDI and invokes service methods

In Quarkus:
- ✅ Step 1 works (CDI portable extension)
- ❌ Step 2 doesn't work (no EJB timers, different JNDI model)
- ❌ Step 3 doesn't work (JNDI lookup fails for CDI beans)

## Solution Approach

For Quarkus, we need to replace the JNDI-based discovery with CDI-based discovery. Here are the options:

### Option 1: Contribute to casual-java (Recommended)

Add Quarkus support directly to casual-java by:

1. Creating a `casual-service-discovery-quarkus` module
2. Using `@Observes AfterDeploymentValidation` to resolve services after CDI initialization
3. Using `BeanManager` to look up CDI beans instead of JNDI
4. Creating a Quarkus-specific service handler that doesn't rely on JNDI

This would be the cleanest long-term solution.

### Option 2: Workaround with JNDI Provider

Create a custom Initial Context that bridges CDI to JNDI:

1. Implement `InitialContextFactory` that returns CDI bean instances
2. Register it via system property or jndi.properties
3. The existing `CasualServiceHandler` will work without modification

### Option 3: Manual Service Registration (Quick Fix)

Create a startup bean that manually registers services:

```java
@ApplicationScoped
public class CasualServiceRegistrar {

    @Inject
    BeanManager beanManager;

    @Inject
    EchoServiceImpl echoService;  // Inject each service manually

    @Inject
    ReverseServiceImpl reverseService;

    void onStart(@Observes StartupEvent event) {
        // Manually register each service with CasualServiceRegistry
        registerService("echo", echoService, EchoServiceImpl.class);
        registerService("reverse", reverseService, ReverseServiceImpl.class);
    }

    private void registerService(String name, Object bean, Class<?> beanClass) {
        // Create JNDI entry in a custom context
        // or store bean reference somewhere the handler can find it
    }
}
```

## Current Status

The inbound server is **running** and can **receive service calls**, but service discovery is not working because:

- Services are not being registered in `CasualServiceRegistry`
- The `JndiSearchTimerEjbSingleton` EJB timer doesn't exist in Quarkus
- JNDI lookups for CDI beans don't work the same way

## Next Steps

Recommend discussing with the casual-java team about adding Quarkus support. In the meantime, Option 3 (manual registration) is the quickest workaround to get things working.

## Files to Check

- `CasualServiceRegistry` - Check if metadata is being registered
- Log output from `CasualServiceDiscovery` - Should show "Initializing service Discovery" and "Services found: X"
- `JndiSearchTimerEjbSingleton` - This won't be deployed in Quarkus (no @Singleton EJB support)

## Testing Service Discovery

Add logging to see what's happening:

```bash
# Check if CDI extension is discovering services
grep "processAnnotatedType" logs

# Check registry status
# Add this to a startup bean:
log.info("Service metadata size: " + CasualServiceRegistry.getInstance().serviceMetaDataSize());
log.info("Service entries size: " + CasualServiceRegistry.getInstance().serviceEntrySize());
```

If metadata size > 0 but entries size == 0, then Phase 1 (discovery) works but Phase 2 (resolution) doesn't.
