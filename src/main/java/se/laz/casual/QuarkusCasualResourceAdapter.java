package se.laz.casual;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ActivationSpec;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.BootstrapContext;
import jakarta.resource.spi.ResourceAdapterInternalException;
import jakarta.resource.spi.endpoint.MessageEndpointFactory;
import se.laz.casual.jca.CasualResourceAdapter;

import javax.transaction.xa.XAResource;
import java.util.Map;

/**
 * So that we can have the properties available
 */
public class QuarkusCasualResourceAdapter implements ResourceAdapter
{
    private final CasualResourceAdapter delegate = new CasualResourceAdapter();
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
        delegate.start(ctx);
    }

    @Override
    public void stop()
    {
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

