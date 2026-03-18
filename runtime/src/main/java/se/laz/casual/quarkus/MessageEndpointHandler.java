package se.laz.casual.quarkus;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Handler for MessageEndpoint that delegates to CasualMessageListenerImpl
 */
public class MessageEndpointHandler implements InvocationHandler
{
    private static final Logger log = Logger.getLogger(MessageEndpointHandler.class.getName());
    private final Object listenerImpl;
    private final Supplier<Boolean> inboundActive;

    private MessageEndpointHandler(Object listenerImpl, Supplier<Boolean> inboundActive)
    {
        this.listenerImpl = listenerImpl;
        this.inboundActive = inboundActive;
    }

    public static MessageEndpointHandler of(Object listenerImpl, Supplier<Boolean> inboundActive)
    {
        Objects.requireNonNull(listenerImpl, "listenerImpl cannot be null");
        Objects.requireNonNull(inboundActive, "inboundActive cannot be null");
        return new MessageEndpointHandler(listenerImpl, inboundActive);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        try
        {
            if (inboundActive.get())
            {
                Method implMethod = listenerImpl.getClass().getMethod(method.getName(), method.getParameterTypes());
                return implMethod.invoke(listenerImpl, args);
            }
            // otherwise inbound has already been deactivated - no invocations allowed
            return null;
        }
        catch (Exception e)
        {
            log.severe("Error invoking method " + method.getName() + " on listener: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
