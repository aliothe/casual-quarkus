package se.laz.casual.quarkus;

import io.quarkus.runtime.annotations.RecordableConstructor;

public record CasualServiceDescriptor(String serviceName, String className, String methodName, String category)
{
    @RecordableConstructor
    public CasualServiceDescriptor
    {}
}
