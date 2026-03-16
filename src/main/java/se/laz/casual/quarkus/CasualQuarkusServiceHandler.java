package se.laz.casual.quarkus;

import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.flags.TransactionState;
import se.laz.casual.api.service.ServiceInfo;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;
import se.laz.casual.jca.inbound.handler.service.ServiceHandler;
import se.laz.casual.network.messages.domain.TransactionType;
import se.laz.casual.spi.Priority;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Quarkus-specific service handler that uses CDI beans instead of JNDI lookups.
 * This handler integrates with Casual's inbound infrastructure while using Quarkus's CDI.
 *
 * Note: This class is instantiated by ServiceLoader (SPI), so it cannot use CDI injection.
 * It accesses the registry via a static reference.
 *
 * In the future Quarkus extension, this would be automatically registered.
 */
public class CasualQuarkusServiceHandler implements ServiceHandler
{
    private static final Logger log = Logger.getLogger(CasualQuarkusServiceHandler.class.getName());

    /**
     * No-arg constructor required by ServiceLoader.
     */
    public CasualQuarkusServiceHandler()
    {
        Thread currentThread = Thread.currentThread();
        log.info("=== CasualQuarkusServiceHandler CONSTRUCTOR called ===");
        log.info("  Thread: " + currentThread.getName());
        log.info("  ClassLoader: " + currentThread.getContextClassLoader());
        log.info("  This class loaded by: " + this.getClass().getClassLoader());

        try {
            // Check if we can see the registry class
            Class.forName("se.laz.casual.quarkus.CasualQuarkusServiceRegistry");
            log.info("  ✓ Can see CasualQuarkusServiceRegistry class");
        } catch (ClassNotFoundException e) {
            log.severe("  ✗ CANNOT see CasualQuarkusServiceRegistry class!");
        }
    }

    /**
     * Higher priority than default CasualServiceHandler (LEVEL_5)
     * so that Quarkus services are preferred over JNDI-based services.
     */
    @Override
    public Priority getPriority()
    {
        return Priority.LEVEL_3;
    }

    @Override
    public boolean canHandleService(String serviceName)
    {
        log.info(">>> CasualQuarkusServiceHandler.canHandleService() called for: " + serviceName);
        CasualQuarkusServiceRegistry registry = CasualQuarkusServiceRegistry.getInstance();
        log.info(">>> Registry instance: " + (registry != null ? "NOT NULL" : "NULL"));

        if (registry != null) {
            log.info(">>> Registry has " + registry.getServiceCount() + " services");
            log.info(">>> All services: " + registry.getAllServices().keySet());
            boolean hasService = registry.hasService(serviceName);
            log.info(">>> Has service '" + serviceName + "': " + hasService);
            return hasService;
        }

        log.severe(">>> Registry is NULL! Cannot handle service: " + serviceName);
        return false;
    }

    @Override
    public boolean isServiceAvailable(String serviceName)
    {
        CasualQuarkusServiceRegistry registry = CasualQuarkusServiceRegistry.getInstance();
        boolean available = registry != null && registry.hasService(serviceName);
        log.finest(() -> "isServiceAvailable(" + serviceName + "): " + available);
        return available;
    }

    @Override
    public InboundResponse invokeService(InboundRequest request)
    {
        String serviceName = request.getServiceName();
        log.info(() -> "Invoking Quarkus service: " + serviceName);

        CasualQuarkusServiceRegistry registry = CasualQuarkusServiceRegistry.getInstance();
        if (registry == null)
        {
            log.severe("CasualQuarkusServiceRegistry not initialized!");
            return InboundResponse.createBuilder()
                    .errorState(ErrorState.TPESYSTEM)
                    .transactionState(TransactionState.ROLLBACK_ONLY)
                    .build();
        }

        ServiceEntry serviceEntry = registry.getService(serviceName);

        if (serviceEntry == null)
        {
            log.warning("Service not found: " + serviceName);
            return InboundResponse.createBuilder()
                    .errorState(ErrorState.TPENOENT)
                    .transactionState(TransactionState.ROLLBACK_ONLY)
                    .build();
        }

        try
        {
            // Invoke the service method on the CDI bean instance
            Object beanInstance = serviceEntry.beanInstance();
            Method method = serviceEntry.method();

            log.fine(() -> "Calling " + beanInstance.getClass().getSimpleName()
                + "." + method.getName() + "()");

            // Invoke the method with the InboundRequest parameter
            Object result = method.invoke(beanInstance, request);

            // The service should return an InboundResponse
            if (result instanceof InboundResponse)
            {
                log.info(() -> "Service " + serviceName + " completed successfully");
                return (InboundResponse) result;
            }
            else
            {
                log.warning("Service " + serviceName + " did not return InboundResponse, got: "
                    + (result != null ? result.getClass() : "null"));
                return InboundResponse.createBuilder()
                        .errorState(ErrorState.TPESVCERR)
                        .transactionState(TransactionState.ROLLBACK_ONLY)
                        .build();
            }
        }
        catch (Exception e)
        {
            log.log(Level.SEVERE, "Error invoking service " + serviceName, e);
            return InboundResponse.createBuilder()
                    .errorState(ErrorState.TPESVCERR)
                    .transactionState(TransactionState.ROLLBACK_ONLY)
                    .build();
        }
    }

    @Override
    public ServiceInfo getServiceInfo(String serviceName)
    {
        CasualQuarkusServiceRegistry registry = CasualQuarkusServiceRegistry.getInstance();
        if (registry == null)
        {
            throw new IllegalStateException("CasualQuarkusServiceRegistry not initialized");
        }

        ServiceEntry serviceEntry = registry.getService(serviceName);

        if (serviceEntry == null)
        {
            throw new IllegalArgumentException("Service not found: " + serviceName);
        }

        // for now, default to AUTO transaction type
        // in the future, this could be determined from annotations
        return ServiceInfo.of(
            serviceEntry.serviceName(),
            serviceEntry.category(),
            TransactionType.AUTOMATIC
        );
    }
}
