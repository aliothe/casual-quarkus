package se.laz.casual.example;

import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Inject;
import jakarta.resource.ResourceException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.buffer.type.OctetBuffer;
import se.laz.casual.api.flags.AtmiFlags;
import se.laz.casual.api.flags.Flag;
import se.laz.casual.api.flags.ServiceReturnState;
import se.laz.casual.jca.CasualConnection;
import se.laz.casual.jca.CasualConnectionFactory;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

@Path("/casual")
public class CasualResource
{
    @Inject
    @Identifier("casual")
    private CasualConnectionFactory casualOne;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello from Quarkus REST";
    }

    @POST
    @Consumes("application/casual-x-octet")
    @Path("{serviceName}")
    public Response serviceRequest(@PathParam("serviceName") String serviceName, InputStream inputStream)
    {
        try
        {
            byte[] data = IOUtils.toByteArray(inputStream);
            Flag<AtmiFlags> flags = Flag.of(AtmiFlags.NOFLAG);
            OctetBuffer buffer = OctetBuffer.of(data);
            return Response.ok().entity(makeServiceCall(buffer, serviceName, flags).getBytes().get(0)).build();
        }
        catch (Exception e)
        {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            return Response.serverError().entity(sw.toString()).build();
        }
    }

    private CasualBuffer makeServiceCall(CasualBuffer msg, String serviceName, Flag<AtmiFlags> flags)
    {
        try(CasualConnection connection = casualOne.getConnection())
        {
            ServiceReturn<CasualBuffer> reply = connection.tpcall(serviceName, msg, flags);
            if (reply.getServiceReturnState() == ServiceReturnState.TPSUCCESS)
            {
                return reply.getReplyBuffer();
            }
            throw new RuntimeException("tpcall failed: " + reply.getErrorState());
        }
        catch (ResourceException e)
        {
            throw new RuntimeException(e);
        }
    }
}
