package se.laz.casual.quarkus.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import se.laz.casual.quarkus.CasualServiceDescriptor;
import se.laz.casual.quarkus.CasualServiceRecorder;

import java.lang.System.Logger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

class CasualProcessor
{
    private static final Logger LOG = System.getLogger(CasualProcessor.class.getName());
    private static final String FEATURE = "casual";
    private static final DotName CASUAL_SERVICE = DotName.createSimple("se.laz.casual.api.service.CasualService");
    private static final DotName IDENTIFIER = DotName.createSimple("io.smallrye.common.annotation.Identifier");
    private static final DotName CASUAL_MESSAGE_ENDPOINT = DotName.createSimple("se.laz.casual.quarkus.CasualMessageEndpoint");

    @BuildStep
    FeatureBuildItem feature()
    {
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
            "se.laz.casual.quarkus.CasualQuarkusServiceRegistry"));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
            "se.laz.casual.quarkus.CasualMessageEndpoint"));
    }

    /**
     * CasualMessageEndpoint has a compile-time @Identifier("casual") which satisfies
     * IronJacamar's Jandex check when multiple RAs are configured.
     *
     * For CDI resolution at runtime, the @Identifier value must match the RA identifier
     * that IronJacamar uses for endpoint activation:
     * - Single RA with a name other than "casual": replace the qualifier to match
     * - Single unnamed RA (&lt;default&gt;): remove @Identifier so the plain-type fallback works
     * - Multiple RAs: leave as "casual" — IronJacamar activates only the "casual" container
     */
    @BuildStep
    void adjustEndpointIdentifier(BuildProducer<AnnotationsTransformerBuildItem> transformers)
    {
        List<String> identifiers = findAllCasualRaIdentifiers();
        if (identifiers.size() != 1)
        {
            // Multiple RAs: compile-time @Identifier("casual") is correct — one RA must be named "casual"
            // No RAs: nothing to adjust
            return;
        }
        String identifier = identifiers.get(0);
        if ("casual".equals(identifier))
        {
            // Already matches compile-time annotation
            return;
        }
        if (identifier == null)
        {
            // <default> RA: remove @Identifier so CDI plain-type fallback works
            LOG.log(Logger.Level.INFO, () -> "Removing @Identifier from CasualMessageEndpoint for <default> RA");
            transformers.produce(new AnnotationsTransformerBuildItem(
                    AnnotationTransformation.forClasses()
                            .whenClass(CASUAL_MESSAGE_ENDPOINT)
                            .transform(ctx -> ctx.remove(ann -> ann.name().equals(IDENTIFIER)))
            ));
        }
        else
        {
            // Named RA (e.g. "casual-one"): replace @Identifier value
            LOG.log(Logger.Level.INFO, "Replacing @Identifier on CasualMessageEndpoint: 'casual' -> '%s'", identifier);
            transformers.produce(new AnnotationsTransformerBuildItem(
                    AnnotationTransformation.forClasses()
                            .whenClass(CASUAL_MESSAGE_ENDPOINT)
                            .transform(ctx -> {
                                ctx.remove(ann -> ann.name().equals(IDENTIFIER));
                                ctx.add(AnnotationInstance.create(IDENTIFIER, null,
                                        new AnnotationValue[]{ AnnotationValue.createStringValue("value", identifier) }));
                            })
            ));
        }
    }

    /**
     * Scan quarkus.ironjacamar config to find all casual RA identifiers.
     * Returns null entries for unnamed (&lt;default&gt;) RAs, actual names for named RAs.
     */
    private static List<String> findAllCasualRaIdentifiers()
    {
        List<String> identifiers = new ArrayList<>();
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
            String rest = name.substring("quarkus.ironjacamar.".length());
            if (rest.equals("ra.kind"))
            {
                identifiers.add(null); // <default>
            }
            else
            {
                identifiers.add(rest.substring(0, rest.length() - ".ra.kind".length()));
            }
        }
        return identifiers;
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
            String methodName = annotation.target().asMethod().name();
            String serviceName = annotation.value("name").asString();
            AnnotationValue categoryValue = annotation.value("category");
            String category = categoryValue != null ? categoryValue.asString() : "";
            unremovableBeans.produce(UnremovableBeanBuildItem.beanClassNames(className));
            casualServices.produce(new CasualServiceBuildItem(serviceName, className, methodName, category));
        }
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    void registerCasualServices(CasualServiceRecorder recorder,
                                BeanContainerBuildItem beanContainer,
                                List<CasualServiceBuildItem> casualServices)
    {
        List<CasualServiceDescriptor> descriptors = new ArrayList<>();
        for (CasualServiceBuildItem item : casualServices)
        {
            descriptors.add(new CasualServiceDescriptor(
                    item.getServiceName(),
                    item.getClassName(),
                    item.getMethodName(),
                    item.getCategory()));
        }
        recorder.registerServices(beanContainer.getValue(), descriptors);
    }
}
