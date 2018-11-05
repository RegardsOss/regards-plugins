package fr.cnes.regards.modules.ingest.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.oais.urn.EntityType;
import fr.cnes.regards.framework.oais.urn.UniformResourceName;
import fr.cnes.regards.modules.ingest.domain.SIP;
import fr.cnes.regards.modules.ingest.domain.plugin.IAipGeneration;
import fr.cnes.regards.modules.storage.domain.AIP;
import fr.cnes.regards.modules.storage.domain.AIPBuilder;

/**
 *
 * Base for EnhancedDescriptiveAipGeneration plugins. Allows to add one descriptive information. Always or one out of two generated AIPs
 *
 * @author Sylvain VISSIERE-GUERINET
 */
public abstract class AbstractEnhancedDescriptiveAipGeneration implements IAipGeneration {

    /**
     * Integer counter used to know if AIP should receive the new information
     */
    private static int COUNTER = 0;

    @PluginParameter(label = "Name of descriptive information to add")
    protected String descriptiveInfoName;

    @PluginParameter(label = "Always",
            description = "Is the information added all the time or only one AIP out of two?")
    protected Boolean always;

    @Override
    public List<AIP> generate(SIP sip, UniformResourceName aipId, UniformResourceName sipId, String providerId) {

        AIPBuilder builder = new AIPBuilder(aipId,
                                            Optional.of(sipId),
                                            providerId,
                                            EntityType.DATA,
                                            sip.getProperties().getPdi().getProvenanceInformation().getSession());
        // Propagate BBOX
        if (sip.getBbox().isPresent()) {
            builder.setBbox(sip.getBbox().get(), sip.getCrs().orElse(null));
        }
        // Propagate geometry
        builder.setGeometry(sip.getGeometry());
        // Propagate properties
        AIP aip = builder.build(sip.getProperties());
        builder = new AIPBuilder(aip);
        if (COUNTER == Integer.MAX_VALUE) {
            COUNTER = Integer.MIN_VALUE;
        }
        COUNTER++;
        if (always || COUNTER % 2 == 0) {
            addDescriptiveInformation(builder);
        }
        List<AIP> aips = new ArrayList<>();
        aips.add(aip);
        return aips;
    }

    protected abstract void addDescriptiveInformation(AIPBuilder builder);

}
