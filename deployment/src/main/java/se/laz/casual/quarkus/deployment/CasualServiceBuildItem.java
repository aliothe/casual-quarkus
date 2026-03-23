package se.laz.casual.quarkus.deployment;

import io.quarkus.builder.item.MultiBuildItem;

final class CasualServiceBuildItem extends MultiBuildItem
{
    private final String serviceName;
    private final String className;

    CasualServiceBuildItem(String serviceName, String className)
    {
        this.serviceName = serviceName;
        this.className = className;
    }

    public String getServiceName() { return serviceName; }
    public String getClassName() { return className; }
}
