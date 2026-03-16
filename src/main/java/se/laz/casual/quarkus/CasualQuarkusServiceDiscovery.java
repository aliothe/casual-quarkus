package se.laz.casual.quarkus;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import se.laz.casual.api.service.CasualService;
import se.laz.casual.services.EchoServiceImpl;
import se.laz.casual.services.ReverseServiceImpl;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Discovers and registers Quarkus CDI beans with @CasualService annotations.
 * This is the core of the future Quarkus extension - it bridges CDI and Casual's service registry.
 */
@ApplicationScoped
public class CasualQuarkusServiceDiscovery
{
    private static final Logger log = Logger.getLogger(CasualQuarkusServiceDiscovery.class.getName());

    @Inject
    BeanManager beanManager;

    @Inject
    CasualQuarkusServiceRegistry serviceRegistry;

    // Inject services to prevent Quarkus from eliminating them as "unused"
    // Note: This is only for the POC - will not be needed for the extension
    @Inject
    EchoServiceImpl echoService;

    @Inject
    ReverseServiceImpl reverseService;

    void discoverServices(@Observes StartupEvent event)
    {
        log.info("=== Casual Quarkus Service Discovery: Starting ===");
        try
        {
            // Get all CDI beans
            Set<Bean<?>> allBeans = beanManager.getBeans(Object.class);
            log.info("Total CDI beans found: " + allBeans.size());

            // FIRST: Log ALL beans to see what we have
            log.info("=== LISTING ALL BEANS ===");
            int beanNum = 0;
            for (Bean<?> bean : allBeans)
            {
                beanNum++;
                Class<?> beanClass = bean.getBeanClass();
                log.info("  Bean #" + beanNum + ": " + beanClass.getName() + " (types: " + bean.getTypes() + ")");
            }
            log.info("=== END ALL BEANS LIST ===");

            int discovered = 0;
            int registered = 0;
            int scanned = 0;

            for (Bean<?> bean : allBeans)
            {
                Class<?> beanClass = bean.getBeanClass();

                // Skip if this is a built-in Quarkus/CDI class
                if (beanClass.getName().startsWith("io.quarkus") ||
                        beanClass.getName().startsWith("jakarta.") ||
                        beanClass.getName().startsWith("java."))
                {
                    continue;
                }

                scanned++;
                int currentCount = scanned;

                // LOG EVERY BEAN WE SCAN
                log.info("Scanning bean #" + currentCount + ": " + beanClass.getName());

                // Get ALL methods including inherited ones
                Method[] methods = beanClass.getMethods();
                log.fine(() -> "  Found " + methods.length + " methods on " + beanClass.getSimpleName());

                for (Method method : methods)
                {
                    CasualService annotation = method.getAnnotation(CasualService.class);
                    if (annotation != null)
                    {
                        discovered++;
                        log.info("Discovered service: " + annotation.name()
                                + " in " + beanClass.getSimpleName() + "." + method.getName() + "()"
                                + " [class: " + beanClass.getName() + "]");

                        try
                        {
                            // Get a CDI reference to the bean
                            Object beanInstance = beanManager.getReference(
                                    bean,
                                    beanClass,
                                    beanManager.createCreationalContext(bean)
                            );

                            log.info("Got bean instance: " + beanInstance.getClass().getName());

                            // Register with our Quarkus-specific registry
                            serviceRegistry.registerService(
                                    annotation.name(),
                                    annotation.category(),
                                    beanInstance,
                                    method
                            );

                            registered++;
                            log.info("Successfully registered service: " + annotation.name());
                        }
                        catch (Exception e)
                        {
                            log.severe("Failed to register service " + annotation.name() + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
            log.info("=== Casual Quarkus Service Discovery: Complete ===");
            log.info("Beans scanned: " + scanned + ", Discovered: " + discovered + " services, Registered: " + registered + " services");
        }
        catch (Exception e)
        {
            log.severe("=== Casual Quarkus Service Discovery: FAILED ===");
            log.severe("Error during service discovery: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
