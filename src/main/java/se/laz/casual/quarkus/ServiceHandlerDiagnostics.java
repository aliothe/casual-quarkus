package se.laz.casual.quarkus;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import se.laz.casual.jca.inbound.handler.service.ServiceHandler;
import se.laz.casual.jca.inbound.handler.service.ServiceHandlerFactory;

import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Logger;

/**
 * Diagnostic tool to verify that our ServiceHandler is being found by ServiceLoader.
 */
@ApplicationScoped
public class ServiceHandlerDiagnostics
{
    private static final Logger log = Logger.getLogger(ServiceHandlerDiagnostics.class.getName());

    void checkServiceHandlers(@Observes StartupEvent event)
    {
        log.info("=== ServiceHandler Diagnostics ===");

        // Check what ServiceLoader finds
        log.info("Checking ServiceLoader.load(ServiceHandler.class):");
        int count = 0;
        for (ServiceHandler handler : ServiceLoader.load(ServiceHandler.class))
        {
            count++;
            log.info("  [" + count + "] " + handler.getClass().getName() + " (priority: " + handler.getPriority() + ")");
        }
        log.info("ServiceLoader found " + count + " handler(s)");

        // Check what ServiceHandlerFactory finds
        log.info("Checking ServiceHandlerFactory.getHandlers():");
        List<ServiceHandler> handlers = ServiceHandlerFactory.getHandlers();
        log.info("ServiceHandlerFactory found " + handlers.size() + " handler(s):");
        for (int i = 0; i < handlers.size(); i++)
        {
            ServiceHandler handler = handlers.get(i);
            log.info("  [" + (i + 1) + "] " + handler.getClass().getName() + " (priority: " + handler.getPriority() + ")");
        }

        // Check if our handler is present
        boolean foundOurHandler = handlers.stream()
            .anyMatch(h -> h instanceof CasualQuarkusServiceHandler);

        if (foundOurHandler)
        {
            log.info("✓ CasualQuarkusServiceHandler IS registered!");
        }
        else
        {
            log.severe("✗ CasualQuarkusServiceHandler NOT FOUND!");
            log.severe("This means ServiceLoader is not finding our handler.");
            log.severe("Check META-INF/services file and classloading.");
        }

        log.info("=== End ServiceHandler Diagnostics ===");
    }
}
