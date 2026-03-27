package se.laz.casual.quarkus;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ActivationSpec;
import jakarta.resource.spi.BootstrapContext;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.ResourceAdapterInternalException;
import jakarta.resource.spi.XATerminator;
import jakarta.resource.spi.endpoint.MessageEndpointFactory;
import jakarta.resource.spi.work.WorkManager;
import se.laz.casual.jca.CasualResourceAdapter;
import se.laz.casual.jca.inflow.CasualActivationSpec;

import javax.transaction.xa.XAResource;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Quarkus IronJacamar creates one RA per outbound pool config.
 * For inbound we only ever want to start one inbound server.
 * The static AtomicBoolean guard ensures only the first RA instance (CasualQuarkusResourceAdapter from ironjacamars point of view)
 * activates the inbound endpoint and only the last deactivation shuts it down.
 *
 * IronJacamar handles endpoint activation/deactivation via @ResourceEndpoint.
 */
public class CasualQuarkusResourceAdapter implements ResourceAdapter
{
    private static final Logger log = Logger.getLogger(CasualQuarkusResourceAdapter.class.getName());
    private static final AtomicInteger inboundActive = new AtomicInteger(0);
    // We only ever want to create one real RA
    // This since it sets up the event server, inbound and reverse inbound
    // All which should be done only once
    // Quarkus Ironjacamar creates one CasualQuarkusResourceAdapter per configured outbound pool
    private static final CasualResourceAdapter delegate = new CasualResourceAdapter();
    private Map<String, String> config;

    public Map<String, String> getConfig()
    {
        return config;
    }

    public void setConfig(Map<String, String> config)
    {
        this.config = config;
    }

    @Override
    public void start(BootstrapContext ctx) throws ResourceAdapterInternalException
    {
        log.info("CasualQuarkusResourceAdapter.start() called");
        // Set inbound port from configuration if provided
        if (null != config && config.containsKey("inbound-server-port"))
        {
            Integer port = Integer.parseInt(config.get("inbound-server-port"));
            log.info("Setting inbound server port to: " + port);
            delegate.setInboundServerPort(port);
        }
        // Wrap the BootstrapContext to ensure WorkManager has XATerminator
        // We do this since for some reason, the work manager in the context that we receive, ctx,
        // is not properly initialized with the XATerminator - also provided in the context.
        BootstrapContext wrappedContext = createWrappedBootstrapContext(ctx);
        delegate.start(wrappedContext);
    }

    @Override
    public void stop()
    {
        // endpointActivation is not called for some reason
        // we really want to do that before stopping the RA
        endpointDeactivation(null, new CasualActivationSpec());
        delegate.stop();
    }

    @Override
    public void endpointActivation(MessageEndpointFactory endpointFactory, ActivationSpec spec)
            throws ResourceException
    {
        if (inboundActive.getAndIncrement() == 0)
        {
            log.info("Activating inbound endpoint (first RA instance)");
            delegate.endpointActivation(endpointFactory, spec);
        }
        else
        {
            log.info("Inbound endpoint already activated by another RA instance, skipping");
        }
    }

    @Override
    public void endpointDeactivation(MessageEndpointFactory endpointFactory, ActivationSpec spec)
    {
        if (inboundActive.decrementAndGet() == 0)
        {
            log.info("Deactivating inbound endpoint");
            delegate.endpointDeactivation(endpointFactory, spec);
        }
        else
        {
            log.info("Inbound endpoint already deactivated, skipping");
        }
    }

    @Override
    public XAResource[] getXAResources(ActivationSpec[] specs) throws ResourceException
    {
        return delegate.getXAResources(specs);
    }

    /**
     * Create a wrapped BootstrapContext that ensures the WorkManager has access to XATerminator.
     * This fixes the issue where IronJacamar's WorkManagerImpl.getXATerminator() returns null.
     */
    private BootstrapContext createWrappedBootstrapContext(BootstrapContext original)
    {
        log.info("Creating wrapped BootstrapContext to inject XATerminator into WorkManager");
        try
        {
            XATerminator xaTerm = original.getXATerminator();
            WorkManager workManager = original.getWorkManager();
            if (xaTerm != null && workManager != null)
            {
                try
                {
                    Class<?> xaTermClass = Class.forName("org.jboss.jca.core.spi.transaction.xa.XATerminator");
                    java.lang.reflect.Method setXATerminatorMethod =
                            workManager.getClass().getMethod("setXATerminator", xaTermClass);
                    setXATerminatorMethod.invoke(workManager, xaTerm);
                    log.info("Successfully set XATerminator on WorkManager via reflection");
                }
                catch (Exception e)
                {
                    log.severe("Failed to set XATerminator on WorkManager: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            else
            {
                log.severe("XATerminator or WorkManager is null, cannot inject");
            }
        }
        catch (Exception e)
        {
            log.severe("Error wrapping BootstrapContext: " + e.getMessage());
            e.printStackTrace();
        }
        return original;
    }
}
