package se.laz.casual.quarkus;

import jakarta.enterprise.context.ApplicationScoped;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.System.Logger;

/**
 * Quarkus-specific service registry that holds CDI bean instances and their service methods.
 *
 * This is both a CDI bean (for injection into CasualQuarkusServiceDiscovery) and a singleton
 * (for access from ServiceLoader-instantiated CasualQuarkusServiceHandler).
 *
 * In a full Quarkus extension, this would be managed by build-time processing.
 */
@ApplicationScoped
public class CasualQuarkusServiceRegistry
{
    private static final Logger LOG = System.getLogger(CasualQuarkusServiceRegistry.class.getName());

    private static volatile CasualQuarkusServiceRegistry instance;

    private final Map<String, ServiceEntry> services = new ConcurrentHashMap<>();

    public CasualQuarkusServiceRegistry()
    {
        // Set the singleton instance when CDI creates this bean
        instance = this;
        LOG.log(Logger.Level.INFO, () -> "=== CasualQuarkusServiceRegistry instance created ===");
    }

    /**
     * Get the singleton instance (used by ServiceHandler which is not CDI-managed).
     */
    public static CasualQuarkusServiceRegistry getInstance()
    {
        return instance;
    }

    public void registerService(String serviceName, String category, Object beanInstance, Method method)
    {
        ServiceEntry entry = new ServiceEntry(serviceName, category, beanInstance, method);
        services.put(serviceName, entry);
        LOG.log(Logger.Level.INFO, () -> "Service registered: " + serviceName + " -> " + beanInstance.getClass().getSimpleName());
    }

    public ServiceEntry getService(String serviceName)
    {
        return services.get(serviceName);
    }

    public boolean hasService(String serviceName)
    {
        boolean has = services.containsKey(serviceName);
        LOG.log(Logger.Level.INFO, () -> "hasService('" + serviceName + "'): " + has + " (total services: " + services.size() + ")");
        return has;
    }
}
