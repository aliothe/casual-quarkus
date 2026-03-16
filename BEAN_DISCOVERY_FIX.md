# Bean Discovery Fix

## Problem

Service beans with `@ApplicationScoped` were not being discovered by Quarkus Arc, resulting in:
```
Beans scanned: 24, Discovered: 0 services, Registered: 0 services
```

## Root Cause

**Quarkus performs dead code elimination at build time.** Beans that are not referenced/injected anywhere are eliminated from the final application, even if they have proper CDI annotations like `@ApplicationScoped`.

## Investigation Journey

1. **Initial symptoms**: `EchoServiceImpl` and `ReverseServiceImpl` had `@ApplicationScoped` but weren't appearing in `BeanManager.getBeans()`

2. **Things we tried that didn't help**:
   - Added `META-INF/beans.xml` with `bean-discovery-mode="all"` (Quarkus ignores this, uses annotation-based discovery)
   - Moved classes from `se.laz.casual.service` to `se.laz.casual` package
   - Clean builds, cache clearing

3. **The discovery**:
   - Classes were compiled and in the JAR: ✓
   - Had correct `@ApplicationScoped` annotation: ✓
   - But Arc didn't generate `*_Bean.class` proxies: ✗
   - Created `TestBean` - also not discovered
   - Noticed: beans that WERE discovered (`CasualQuarkusServiceDiscovery`, `CasualQuarkusServiceRegistry`) were all injected somewhere

## Solution

**Make beans "reachable" by injecting them** into a bean that IS used by the application.

In `CasualQuarkusServiceDiscovery.java`:

```java
@ApplicationScoped
public class CasualQuarkusServiceDiscovery
{
    @Inject
    BeanManager beanManager;

    @Inject
    CasualQuarkusServiceRegistry serviceRegistry;

    // Inject services to prevent Quarkus from eliminating them as "unused"
    @Inject
    se.laz.casual.services.EchoServiceImpl echoService;

    @Inject
    se.laz.casual.services.ReverseServiceImpl reverseService;

    // ...
}
```

## Result

After adding the injections and rebuilding:
```
Arc generated proxies:
- se/laz/casual/EchoServiceImpl_Bean.class ✓
- se/laz/casual/EchoServiceImpl_ClientProxy.class ✓
- se/laz/casual/ReverseServiceImpl_Bean.class ✓
- se/laz/casual/ReverseServiceImpl_ClientProxy.class ✓

Runtime discovery:
Beans scanned: 26, Discovered: 2 services, Registered: 2 services
- reverse in ReverseServiceImpl.reverse() ✓
- echo in EchoServiceImpl.echo() ✓
```

## For the Future Quarkus Extension

In a proper Quarkus extension, this problem would be solved differently:

1. **Build-time processing**: Use Quarkus build steps to scan for `@CasualService` annotations
2. **Automatic registration**: Register services in the Casual registry during build
3. **Bean inclusion**: Use `AdditionalBeanBuildItem` to ensure service beans are included
4. **No manual injection needed**: The extension would handle making beans reachable

Example build step:
```java
@BuildStep
AdditionalBeanBuildItem discoverCasualServices() {
    return AdditionalBeanBuildItem.builder()
        .addBeanClasses(/* classes with @CasualService methods */)
        .setUnremovable()
        .build();
}
```

## Key Takeaway

In Quarkus, **beans must be reachable from the application entry points** to survive build-time dead code elimination. Simply having `@ApplicationScoped` is not enough - the bean must be:
1. Injected somewhere, OR
2. Explicitly marked as unremovable in a build step (extension), OR
3. Referenced by the application in some way
