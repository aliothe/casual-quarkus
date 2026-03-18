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
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Quarkus iron jacamar creates one RA per outbound pool
 * For inbound we only ever want to start one inbound server
 * Also not that when we receive SIGTERM, stop is being called directly
 * so endpointDeactivation is not called - we have to handle that ourselves
 * for a graceful shutdown
 * This is most likely since endpointActivation is not called either, thus we have to initialize inbound ourselves
 * Why the XATerminator is missing on the work manager that we get in the BootstrapContext is most likely also  due to this
 */
public class CasualQuarkusResourceAdapter implements ResourceAdapter
{
    private static final Logger log = Logger.getLogger(CasualQuarkusResourceAdapter.class.getName());
    private static AtomicBoolean inboundActive = new AtomicBoolean(false);
    private final CasualResourceAdapter delegate = new CasualResourceAdapter();
    private CasualActivationSpec activationSpec;
    private Map<String, String> config;
    private BootstrapContext bootstrapContext;

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
        log.info("QuarkusCasualResourceAdapter.start() called");
        this.bootstrapContext = ctx;

        // Check if XATerminator is available
        try {
            jakarta.resource.spi.XATerminator xaTerm = ctx.getXATerminator();
            log.info("BootstrapContext XATerminator: " + (xaTerm != null ? "NOT NULL" : "NULL"));
            if (xaTerm != null) {
                log.info("XATerminator class: " + xaTerm.getClass().getName());
            }
        } catch (Exception e) {
            log.severe("Error getting XATerminator: " + e.getMessage());
        }

        // Set inbound port from configuration if provided
        if (null != config  && config.containsKey("inbound-server-port"))
        {
            Integer port = Integer.parseInt(config.get("inbound-server-port"));
            log.info("Setting inbound server port to: " + port);
            delegate.setInboundServerPort(port);
        }

        // Wrap the BootstrapContext to ensure WorkManager has XATerminator
        BootstrapContext wrappedContext = createWrappedBootstrapContext(ctx);
        delegate.start(wrappedContext);

        synchronized (CasualQuarkusResourceAdapter.class)
        {
            if (!inboundActive.getAndSet(true))
            {
                try
                {
                    log.info("Activating inbound endpoint (first RA instance)");
                    activateInboundEndpoint();
                }
                catch (ResourceException e)
                {
                    log.severe("Failed to activate inbound endpoint: " + e.getMessage());
                    e.printStackTrace();
                    throw new ResourceAdapterInternalException("Failed to activate inbound endpoint", e);
                }
            }
            else
            {
                log.info("Inbound endpoint already activated by another RA instance, skipping");
            }
         }
    }

    @Override
    public void stop()
    {
        // it seems that quarkus ironjacamar does not call endpointDeactivation on SIGTERM
        // thus we have to do that our self since we still want a graceful shutdown
        if (inboundActive.get())
        {
            delegate.endpointDeactivation(null, activationSpec);
            inboundActive.set(false);
        }
        delegate.stop();
    }

    @Override
    public void endpointActivation(MessageEndpointFactory endpointFactory, ActivationSpec spec)
                throws ResourceException
    {
        delegate.endpointActivation(endpointFactory, spec);
    }

    // this never gets called
    @Override
    public void endpointDeactivation(MessageEndpointFactory endpointFactory, ActivationSpec spec)
    {
        delegate.endpointDeactivation(endpointFactory, spec);
    }

    @Override
    public XAResource[] getXAResources(ActivationSpec[] specs) throws ResourceException
    {
        return delegate.getXAResources(specs);
    }

    /**
     * Create a wrapped BootstrapContext that ensures the WorkManager has access to XATerminator.
     * This fixes the issue where IronJacamar's WorkManagerImpl.getXATerminator() returns null.
     * This is a hack, we should try and figure out a proper solution.
     */
    private BootstrapContext createWrappedBootstrapContext(BootstrapContext original)
    {
        log.info("Creating wrapped BootstrapContext to inject XATerminator into WorkManager");
        try
        {
            XATerminator xaTerm = original.getXATerminator();
            WorkManager workManager = original.getWorkManager();

            log.info("Original WorkManager class: " + workManager.getClass().getName());
            log.info("XATerminator class: " + (xaTerm != null ? xaTerm.getClass().getName() : "null"));

            if (xaTerm != null && workManager != null)
            {
                // The XATerminator from Quarkus is actually org.jboss.jca.core.tx.jbossts.XATerminatorImpl
                // which implements org.jboss.jca.core.spi.transaction.xa.XATerminator
                // We need to cast it and set it on the WorkManager
                try
                {
                    // Use reflection to call setXATerminator on the WorkManager
                    Class<?> xaTermClass = Class.forName("org.jboss.jca.core.spi.transaction.xa.XATerminator");
                    java.lang.reflect.Method setXATerminatorMethod =
                            workManager.getClass().getMethod("setXATerminator", xaTermClass);
                    setXATerminatorMethod.invoke(workManager, xaTerm);
                    log.info("Successfully set XATerminator on WorkManager via reflection");
                    // Verify it was set
                    java.lang.reflect.Method getXATerminatorMethod =
                            workManager.getClass().getMethod("getXATerminator");
                    Object verifyXATerm = getXATerminatorMethod.invoke(workManager);
                    log.info("Verified WorkManager.getXATerminator(): " +
                            (verifyXATerm != null ? "NOT NULL" : "NULL"));
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

    private void activateInboundEndpoint() throws ResourceException
    {
        log.info("=== Starting manual activation of Casual inbound endpoint ===");
        log.info("Inbound server port: " + delegate.getInboundServerPort());

        // Create activation spec
        activationSpec = new CasualActivationSpec();
        activationSpec.setResourceAdapter(this);
        log.info("Created activation spec");

        // Create a proxy MessageEndpointFactory that delegates to the CasualMessageListener
        MessageEndpointFactory endpointFactory = createMessageEndpointFactory();
        log.info("Created message endpoint factory proxy");

        // Activate the endpoint - this should start the inbound server
        log.info("Calling delegate.endpointActivation()...");
        delegate.endpointActivation(endpointFactory, activationSpec);

        log.info("=== Casual inbound endpoint activation completed ===");
    }

    /**
     * Create a MessageEndpointFactory that creates CasualMessageListener instances
     */
    private MessageEndpointFactory createMessageEndpointFactory()
    {
        return (MessageEndpointFactory) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { MessageEndpointFactory.class },
                MessageEndpointHandlerFactory.of(this, () -> inboundActive.get())
        );
    }

}
