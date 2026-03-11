package se.laz.casual.quarkus;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TestBean
{
    public String test()
    {
        return "Test works!";
    }
}
