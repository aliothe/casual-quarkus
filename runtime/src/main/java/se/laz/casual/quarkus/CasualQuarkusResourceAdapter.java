package se.laz.casual.quarkus;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ActivationSpec;
import jakarta.resource.spi.BootstrapContext;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.ResourceAdapterInternalException;
import jakarta.resource.spi.endpoint.MessageEndpointFactory;
import se.laz.casual.jca.CasualResourceAdapter;

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
    private ActivationSpec activationSpec;
    private MessageEndpointFactory endpointFactory;

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
        if (null != config && config.containsKey("inbound-server-port"))
        {
            Integer port = Integer.parseInt(config.get("inbound-server-port"));
            log.info("Setting inbound server port to: " + port);
            delegate.setInboundServerPort(port);
        }
        delegate.start(ctx);
    }

    @Override
    public void stop()
    {
        // endpointActivation is not called for some reason
        // we really want to do that before stopping the RA
        endpointDeactivation(endpointFactory, activationSpec);
        delegate.stop();
    }

    @Override
    public void endpointActivation(MessageEndpointFactory endpointFactory, ActivationSpec spec)
            throws ResourceException
    {
        if (inboundActive.getAndIncrement() == 0)
        {
            log.info("Activating inbound endpoint (first RA instance)");
            activationSpec = spec;
            this.endpointFactory = endpointFactory;
            delegate.endpointActivation(endpointFactory, spec);
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
    }

    @Override
    public XAResource[] getXAResources(ActivationSpec[] specs) throws ResourceException
    {
        return delegate.getXAResources(specs);
    }

}
