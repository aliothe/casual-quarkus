package se.laz.casual.quarkus;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import se.laz.casual.api.service.CasualService;

import java.lang.reflect.Method;
import java.util.Set;
import java.lang.System.Logger;

/**
 * Discovers and registers Quarkus CDI beans with @CasualService annotations.
 * This bridges CDI and Casual's service registry.
 *
 * The deployment module marks @CasualService beans as unremovable so they are
 * available for discovery at runtime.
 */
@ApplicationScoped
public class CasualQuarkusServiceDiscovery
{
    private static final Logger LOG = System.getLogger(CasualQuarkusServiceDiscovery.class.getName());

    @Inject
    BeanManager beanManager;

    @Inject
    CasualQuarkusServiceRegistry serviceRegistry;

    void discoverServices(@Observes StartupEvent event)
    {
        LOG.log(Logger.Level.INFO, () -> "=== Casual Quarkus Service Discovery: Starting ===");
        try
        {
            Set<Bean<?>> allBeans = beanManager.getBeans(Object.class);

            int discovered = 0;
            int registered = 0;

            for (Bean<?> bean : allBeans)
            {
                Class<?> beanClass = bean.getBeanClass();

                // Skip built-in Quarkus/CDI/Java classes
                if (beanClass.getName().startsWith("io.quarkus") ||
                        beanClass.getName().startsWith("jakarta.") ||
                        beanClass.getName().startsWith("java."))
                {
                    continue;
                }

                Method[] methods = beanClass.getMethods();

                for (Method method : methods)
                {
                    CasualService annotation = method.getAnnotation(CasualService.class);
                    if (annotation != null)
                    {
                        discovered++;
                        LOG.log(Logger.Level.INFO, () -> "Discovered service: " + annotation.name()
                                + " in " + beanClass.getSimpleName() + "." + method.getName() + "()");

                        try
                        {
                            Object beanInstance = beanManager.getReference(
                                    bean,
                                    beanClass,
                                    beanManager.createCreationalContext(bean)
                            );

                            serviceRegistry.registerService(
                                    annotation.name(),
                                    annotation.category(),
                                    beanInstance,
                                    method
                            );

                            registered++;
                            LOG.log(Logger.Level.INFO, () -> "Successfully registered service: " + annotation.name());
                        }
                        catch (Exception e)
                        {
                            LOG.log(Logger.Level.ERROR, () -> "Failed to register service " + annotation.name() + ": " + e.getMessage(), e);
                            e.printStackTrace();
                        }
                    }
                }
            }
            LOG.log(Logger.Level.INFO, () -> "=== Casual Quarkus Service Discovery: Complete ===");
            LOG.log(Logger.Level.INFO, "Discovered: " + discovered + " services, Registered: " + registered + " services");
        }
        catch (Exception e)
        {
            LOG.log(Logger.Level.ERROR, "=== Casual Quarkus Service Discovery: FAILED ===", e);
            LOG.log(Logger.Level.ERROR, "Error during service discovery: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
