package se.laz.casual.services;

import se.laz.casual.jca.inbound.handler.InboundRequest;
import se.laz.casual.jca.inbound.handler.InboundResponse;

/**
 * Interface for the reverse service
 */
public interface ReverseService
{
    /**
     * Reverse the bytes in the request buffer
     * @param request the inbound request
     * @return the inbound response with reversed buffer
     */
    InboundResponse reverse(InboundRequest request);
}
