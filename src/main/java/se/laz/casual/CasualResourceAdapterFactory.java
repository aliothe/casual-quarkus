package se.laz.casual;

import io.quarkiverse.ironjacamar.ResourceAdapterFactory;
import io.quarkiverse.ironjacamar.ResourceAdapterKind;
import io.quarkiverse.ironjacamar.ResourceAdapterTypes;
import jakarta.resource.ResourceException;
import jakarta.resource.spi.ActivationSpec;
import jakarta.resource.spi.ManagedConnectionFactory;
import jakarta.resource.spi.ResourceAdapter;
import se.laz.casual.jca.CasualConnectionFactory;
import se.laz.casual.jca.CasualManagedConnectionFactory;
import se.laz.casual.jca.inflow.CasualActivationSpec;

import java.util.Map;

@ResourceAdapterKind("casual")
@ResourceAdapterTypes(connectionFactoryTypes = { CasualConnectionFactory.class })
public class CasualResourceAdapterFactory implements ResourceAdapterFactory
{
    @Override
    public ResourceAdapter createResourceAdapter(String id, Map<String, String> config) throws ResourceException
    {
        QuarkusCasualResourceAdapter ra = new QuarkusCasualResourceAdapter();
        ra.setConfig(config);
        return ra;
    }

    @Override
    public ManagedConnectionFactory createManagedConnectionFactory(String id, ResourceAdapter adapter) throws ResourceException
    {
        QuarkusCasualResourceAdapter quarkusAdapter = (QuarkusCasualResourceAdapter) adapter;
        CasualManagedConnectionFactory mcf = new CasualManagedConnectionFactory();
        Map<String, String> config = quarkusAdapter.getConfig();
        mcf.setHostName(config.get("host"));
        mcf.setPortNumber(Integer.parseInt(config.get("port")));
        if (config.containsKey("network-connection-pool-size")) {
            mcf.setNetworkConnectionPoolSize(Integer.parseInt(config.get("network-connection-pool-size")));
        }
        if (config.containsKey("network-connection-pool-name")) {
            mcf.setNetworkConnectionPoolName(config.get("network-connection-pool-name"));
        }
        mcf.setResourceAdapter(adapter);
        return mcf;
    }

    @Override
    public ActivationSpec createActivationSpec(String id, ResourceAdapter adapter, Class<?> type, Map<String, String> config) throws ResourceException
    {
        return new CasualActivationSpec();
    }
}
