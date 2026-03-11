package se.laz.casual.quarkus;

import jakarta.enterprise.context.ApplicationScoped;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

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
    private static final Logger log = Logger.getLogger(CasualQuarkusServiceRegistry.class.getName());

    private static volatile CasualQuarkusServiceRegistry instance;

    private final Map<String, ServiceEntry> services = new ConcurrentHashMap<>();

    public CasualQuarkusServiceRegistry()
    {
        // Set the singleton instance when CDI creates this bean
        instance = this;
        log.info("=== CasualQuarkusServiceRegistry instance created ===");
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
        log.fine("Service registered: " + serviceName + " -> " + beanInstance.getClass().getSimpleName());
    }

    public ServiceEntry getService(String serviceName)
    {
        return services.get(serviceName);
    }

    public boolean hasService(String serviceName)
    {
        boolean has = services.containsKey(serviceName);
        log.info("hasService('" + serviceName + "'): " + has + " (total services: " + services.size() + ")");
        return has;
    }

    public int getServiceCount()
    {
        return services.size();
    }

    public Map<String, ServiceEntry> getAllServices()
    {
        return Map.copyOf(services);
    }

    /**
     * Represents a registered Casual service in Quarkus.
     */
    public static class ServiceEntry
    {
        private final String serviceName;
        private final String category;
        private final Object beanInstance;
        private final Method method;

        public ServiceEntry(String serviceName, String category, Object beanInstance, Method method)
        {
            this.serviceName = serviceName;
            this.category = category;
            this.beanInstance = beanInstance;
            this.method = method;
        }

        public String getServiceName()
        {
            return serviceName;
        }

        public String getCategory()
        {
            return category;
        }

        public Object getBeanInstance()
        {
            return beanInstance;
        }

        public Method getMethod()
        {
            return method;
        }

        @Override
        public String toString()
        {
            return "ServiceEntry{" +
                    "serviceName='" + serviceName + '\'' +
                    ", category='" + category + '\'' +
                    ", bean=" + beanInstance.getClass().getSimpleName() +
                    ", method=" + method.getName() +
                    '}';
        }
    }
}
