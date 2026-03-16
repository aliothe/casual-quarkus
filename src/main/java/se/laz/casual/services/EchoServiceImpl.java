package se.laz.casual.services;

import jakarta.enterprise.context.ApplicationScoped;
import se.laz.casual.api.service.CasualService;
import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;

import java.util.logging.Logger;

/**
 * Echo service implementation that echoes back the received buffer
 */
@ApplicationScoped
public class EchoServiceImpl implements EchoService
{
    private static final Logger log = Logger.getLogger(EchoServiceImpl.class.getName());

    @CasualService(name = "echo", category = "example")
    @Override
    public InboundResponse echo(InboundRequest request)
    {
        log.info(() -> "Echo service called with service name: " + request.getServiceName());

        return InboundResponse.createBuilder()
                .buffer(request.getBuffer())
                .build();
    }
}
