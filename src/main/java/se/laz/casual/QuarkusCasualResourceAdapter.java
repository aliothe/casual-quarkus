package se.laz.casual;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ActivationSpec;
import jakarta.resource.spi.BootstrapContext;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.ResourceAdapterInternalException;
import jakarta.resource.spi.endpoint.MessageEndpointFactory;
import se.laz.casual.jca.CasualResourceAdapter;
import se.laz.casual.jca.inflow.CasualActivationSpec;

import javax.transaction.xa.XAResource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.logging.Logger;

/**
 * So that we can have the properties available
 */
public class QuarkusCasualResourceAdapter implements ResourceAdapter
{
    private static final Logger log = Logger.getLogger(QuarkusCasualResourceAdapter.class.getName());
    private static boolean inboundActivated = false; // Ensure only one inbound server starts
    private final CasualResourceAdapter delegate = new CasualResourceAdapter();
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

        // Set inbound port from configuration if provided
        if (config != null && config.containsKey("inbound-server-port"))
        {
            Integer port = Integer.parseInt(config.get("inbound-server-port"));
            log.info("Setting inbound server port to: " + port);
            delegate.setInboundServerPort(port);
        }

        delegate.start(ctx);

        // Manually activate inbound endpoint since IronJacamar Quarkus doesn't do it automatically
        // Only activate ONCE, even if multiple RA instances exist
        synchronized (QuarkusCasualResourceAdapter.class)
        {
            if (!inboundActivated)
            {
                try
                {
                    log.info("Activating inbound endpoint (first RA instance)");
                    activateInboundEndpoint();
                    inboundActivated = true;
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

    /**
     * Manually activate the inbound endpoint
     */
    private void activateInboundEndpoint() throws ResourceException
    {
        log.info("=== Starting manual activation of Casual inbound endpoint ===");
        log.info("Inbound server port: " + delegate.getInboundServerPort());

        // Create activation spec
        CasualActivationSpec activationSpec = new CasualActivationSpec();
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
                new MessageEndpointFactoryHandler()
        );
    }

    /**
     * Handler for MessageEndpointFactory that creates CasualMessageListener instances
     */
    private class MessageEndpointFactoryHandler implements InvocationHandler
    {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
        {
            String methodName = method.getName();

            if ("createEndpoint".equals(methodName))
            {
                // Create the actual listener implementation
                Object listenerImpl = Class.forName("se.laz.casual.jca.inflow.CasualMessageListenerImpl")
                        .getDeclaredConstructor()
                        .newInstance();

                // Wrap it in a MessageEndpoint proxy
                return createMessageEndpoint(listenerImpl);
            }
            else if ("isDeliveryTransacted".equals(methodName))
            {
                // Return true to indicate transacted delivery
                return Boolean.TRUE;
            }
            else if ("getActivationSpec".equals(methodName))
            {
                CasualActivationSpec spec = new CasualActivationSpec();
                spec.setResourceAdapter(QuarkusCasualResourceAdapter.this);
                return spec;
            }

            return null;
        }
    }

    /**
     * Create a MessageEndpoint proxy that wraps the CasualMessageListener implementation
     */
    private Object createMessageEndpoint(Object listenerImpl)
    {
        return Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {
                        jakarta.resource.spi.endpoint.MessageEndpoint.class,
                        se.laz.casual.jca.inflow.CasualMessageListener.class
                },
                new MessageEndpointHandler(listenerImpl)
        );
    }

    /**
     * Handler for MessageEndpoint that delegates to CasualMessageListenerImpl
     */
    private class MessageEndpointHandler implements InvocationHandler
    {
        private final Object listenerImpl;

        public MessageEndpointHandler(Object listenerImpl)
        {
            this.listenerImpl = listenerImpl;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
        {
            String methodName = method.getName();

            // Handle MessageEndpoint lifecycle methods
            if ("beforeDelivery".equals(methodName))
            {
                // Called before message delivery
                log.finest("beforeDelivery called");
                return null;
            }
            else if ("afterDelivery".equals(methodName))
            {
                // Called after message delivery
                log.finest("afterDelivery called");
                return null;
            }
            else if ("release".equals(methodName))
            {
                // Called to release the endpoint
                log.finest("release called");
                return null;
            }

            // Delegate all other methods (CasualMessageListener methods) to the actual implementation
            try
            {
                Method implMethod = listenerImpl.getClass().getMethod(method.getName(), method.getParameterTypes());
                return implMethod.invoke(listenerImpl, args);
            }
            catch (Exception e)
            {
                log.severe("Error invoking method " + methodName + " on listener: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        }
    }

    @Override
    public void stop()
    {
        // Deactivate endpoint before stopping
        try
        {
            CasualActivationSpec activationSpec = new CasualActivationSpec();
            activationSpec.setResourceAdapter(this);
            MessageEndpointFactory endpointFactory = createMessageEndpointFactory();
            delegate.endpointDeactivation(endpointFactory, activationSpec);
        }
        catch (Exception e)
        {
            log.warning("Error deactivating endpoint: " + e.getMessage());
        }

        delegate.stop();
    }

    @Override
    public void endpointActivation(MessageEndpointFactory endpointFactory, ActivationSpec spec)
            throws ResourceException
    {
        delegate.endpointActivation(endpointFactory, spec);
    }

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
}

