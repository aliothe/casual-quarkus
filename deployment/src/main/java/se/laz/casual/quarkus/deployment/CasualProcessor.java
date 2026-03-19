package se.laz.casual.quarkus.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;

import java.util.Collection;

class CasualProcessor
{

    private static final String FEATURE = "casual";
    private static final DotName CASUAL_SERVICE = DotName.createSimple("se.laz.casual.api.service.CasualService");
    private static final DotName IDENTIFIER = DotName.createSimple("io.smallrye.common.annotation.Identifier");
    private static final DotName CASUAL_MESSAGE_ENDPOINT = DotName.createSimple("se.laz.casual.quarkus.CasualMessageEndpoint");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void indexCasualDependencies(BuildProducer<IndexDependencyBuildItem> index)
    {
        index.produce(new IndexDependencyBuildItem("se.laz.casual", "casual-inbound-api"));
        index.produce(new IndexDependencyBuildItem("se.laz.casual", "casual-inbound-handler-api"));
        index.produce(new IndexDependencyBuildItem("se.laz.casual", "casual-inbound-handler-casual-service"));
        index.produce(new IndexDependencyBuildItem("com.google.code.gson", "gson"));
        index.produce(new IndexDependencyBuildItem("se.laz.casual", "casual-json-provider-gson"));
    }

    @BuildStep
    void registerRuntimeBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans)
    {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
            "se.laz.casual.quarkus.CasualQuarkusResourceAdapterFactory"));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
            "se.laz.casual.quarkus.CasualQuarkusServiceDiscovery"));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
            "se.laz.casual.quarkus.CasualQuarkusServiceRegistry"));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
            "se.laz.casual.quarkus.CasualMessageEndpoint"));
    }

    /**
     * If the casual RA is configured with a name (not &lt;default&gt;), IronJacamar's
     * DefaultMessageEndpointFactory looks up the endpoint bean with @Identifier(name).
     * This build step dynamically adds that qualifier to CasualMessageEndpoint.
     */
    @BuildStep
    void addIdentifierToEndpoint(BuildProducer<AnnotationsTransformerBuildItem> transformers)
    {
        String casualIdentifier = findCasualRaIdentifier();
        if (casualIdentifier == null)
        {
            return;
        }
        String identifier = casualIdentifier;
        transformers.produce(new AnnotationsTransformerBuildItem(
                AnnotationTransformation.forClasses()
                        .whenClass(CASUAL_MESSAGE_ENDPOINT)
                        .transform(ctx -> ctx.add(AnnotationInstance.create(IDENTIFIER, null,
                                new AnnotationValue[]{ AnnotationValue.createStringValue("value", identifier) })))
        ));
    }

    /**
     * Scan quarkus.ironjacamar config to find the casual RA identifier.
     * Returns null if the RA uses the default (unnamed) identifier or if no casual RA is configured.
     */
    private static String findCasualRaIdentifier()
    {
        var config = ConfigProvider.getConfig();
        for (String name : config.getPropertyNames())
        {
            if (!name.startsWith("quarkus.ironjacamar.") || !name.endsWith(".ra.kind"))
            {
                continue;
            }
            String value = config.getOptionalValue(name, String.class).orElse(null);
            if (!"casual".equals(value))
            {
                continue;
            }
            // quarkus.ironjacamar.ra.kind -> default (unnamed)
            // quarkus.ironjacamar.IDENTIFIER.ra.kind -> named
            String rest = name.substring("quarkus.ironjacamar.".length());
            if (rest.equals("ra.kind"))
            {
                return null;
            }
            return rest.substring(0, rest.length() - ".ra.kind".length());
        }
        return null;
    }

    @BuildStep
    void discoverCasualServices(CombinedIndexBuildItem combinedIndex,
                                BuildProducer<UnremovableBeanBuildItem> unremovableBeans,
                                BuildProducer<CasualServiceBuildItem> casualServices)
    {
        Collection<AnnotationInstance> annotations = combinedIndex.getIndex()
                                                                  .getAnnotations(CASUAL_SERVICE);
        for (AnnotationInstance annotation : annotations)
        {
            String className = annotation.target().asMethod().declaringClass().name().toString();
            String serviceName = annotation.value("name").asString();
            unremovableBeans.produce(UnremovableBeanBuildItem.beanClassNames(className));
            casualServices.produce(new CasualServiceBuildItem(serviceName, className));
        }
    }
}
