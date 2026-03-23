package se.laz.casual.quarkus;

import io.netty.channel.Channel;
import io.quarkiverse.ironjacamar.ResourceEndpoint;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.resource.spi.XATerminator;
import jakarta.resource.spi.work.WorkManager;
import se.laz.casual.api.network.protocol.messages.CasualNWMessage;
import se.laz.casual.jca.inflow.CasualInboundTransactionRegistry;
import se.laz.casual.jca.inflow.CasualMessageListener;
import se.laz.casual.jca.inflow.CasualMessageListenerImpl;
import se.laz.casual.network.protocol.messages.domain.CasualDomainConnectRequestMessage;
import se.laz.casual.network.protocol.messages.domain.CasualDomainDiscoveryRequestMessage;
import se.laz.casual.network.protocol.messages.domain.DomainDisconnectReplyMessage;
import se.laz.casual.network.protocol.messages.service.CasualServiceCallRequestMessage;
import se.laz.casual.network.protocol.messages.transaction.CasualTransactionResourceCommitRequestMessage;
import se.laz.casual.network.protocol.messages.transaction.CasualTransactionResourcePrepareRequestMessage;
import se.laz.casual.network.protocol.messages.transaction.CasualTransactionResourceRollbackRequestMessage;

@ApplicationScoped
@ResourceEndpoint
@Identifier("casual")
public class CasualMessageEndpoint implements CasualMessageListener
{
    private final CasualMessageListenerImpl delegate = new CasualMessageListenerImpl();

    @Override
    public void domainConnectRequest(CasualNWMessage<CasualDomainConnectRequestMessage> message, Channel channel)
    {
        delegate.domainConnectRequest(message, channel);
    }

    @Override
    public void domainDisconnectReply(CasualNWMessage<DomainDisconnectReplyMessage> message)
    {
        delegate.domainDisconnectReply(message);
    }

    @Override
    public void domainDiscoveryRequest(CasualNWMessage<CasualDomainDiscoveryRequestMessage> message, Channel channel)
    {
        delegate.domainDiscoveryRequest(message, channel);
    }

    @Override
    public void serviceCallRequest(CasualNWMessage<CasualServiceCallRequestMessage> message, Channel channel, WorkManager workManager, CasualInboundTransactionRegistry inboundTransactionRegistry)
    {
        delegate.serviceCallRequest(message, channel, workManager, inboundTransactionRegistry);
    }

    @Override
    public void prepareRequest(CasualNWMessage<CasualTransactionResourcePrepareRequestMessage> message, Channel channel, XATerminator xaTerminator, CasualInboundTransactionRegistry inboundTransactionRegistry)
    {
        delegate.prepareRequest(message, channel, xaTerminator, inboundTransactionRegistry);
    }

    @Override
    public void commitRequest(CasualNWMessage<CasualTransactionResourceCommitRequestMessage> message, Channel channel, XATerminator xaTerminator, CasualInboundTransactionRegistry inboundTransactionRegistry)
    {
        delegate.commitRequest(message, channel, xaTerminator, inboundTransactionRegistry);
    }

    @Override
    public void requestRollback(CasualNWMessage<CasualTransactionResourceRollbackRequestMessage> message, Channel channel, XATerminator xaTerminator, CasualInboundTransactionRegistry inboundTransactionRegistry)
    {
        delegate.requestRollback(message, channel, xaTerminator, inboundTransactionRegistry);
    }
}
