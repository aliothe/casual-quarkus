package se.laz.casual.services;

import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;

/**
 * Interface for the echo service
 */
public interface EchoService
{
    /**
     * Echo back the request buffer
     * @param request the inbound request
     * @return the inbound response with the same buffer
     */
    InboundResponse echo(InboundRequest request);
}
