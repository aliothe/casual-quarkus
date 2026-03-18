package se.laz.casual.quarkus;

import se.laz.casual.jca.inflow.CasualActivationSpec;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Handler for MessageEndpointHandlerFactory that creates CasualMessageListener instances
 */
public class MessageEndpointHandlerFactory implements InvocationHandler
{
    private final CasualQuarkusResourceAdapter resourceAdapter;
    private final Supplier<Boolean> inboundActive;
    private MessageEndpointHandlerFactory(CasualQuarkusResourceAdapter resourceAdapter, Supplier<Boolean> inboundActive)
    {
        this.resourceAdapter = resourceAdapter;
        this.inboundActive = inboundActive;
    }
    public static MessageEndpointHandlerFactory of(CasualQuarkusResourceAdapter resourceAdapter, Supplier<Boolean> inboundActive)
    {
        Objects.requireNonNull(resourceAdapter, "resource adapter can not be null");
        return new MessageEndpointHandlerFactory(resourceAdapter,inboundActive);
    }
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
            spec.setResourceAdapter(resourceAdapter);
            return spec;
        }
        return null;
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
                MessageEndpointHandler.of(listenerImpl, inboundActive)
        );
    }
}
