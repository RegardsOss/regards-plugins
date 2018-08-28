package fr.cnes.regards.modules.ingest.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.oais.urn.EntityType;
import fr.cnes.regards.framework.oais.urn.UniformResourceName;
import fr.cnes.regards.modules.ingest.domain.SIP;
import fr.cnes.regards.modules.ingest.domain.plugin.IAipGeneration;
import fr.cnes.regards.modules.storage.domain.AIP;
import fr.cnes.regards.modules.storage.domain.AIPBuilder;

/**
 *
 * @author Sylvain VISSIERE-GUERINET
 */
@Plugin(author = "REGARDS Team", description = "Add random double value to the AIP generated",
        id = "DoubleEnhancedDescriptiveAipGeneration", version = "1.0.0", contact = "regards@c-s.fr", licence = "GPLv3",
        owner = "CNES", url = "https://regardsoss.github.io/")
public class DoubleEnhancedDescriptiveAipGeneration implements IAipGeneration {

    /**
     * Integer counter used to know if AIP should receive the new information
     */
    private static int COUNTER = 0;

    @PluginParameter(label = "Name of descriptive information to add")
    private String descriptiveInfoName;

    @PluginParameter(label = "Always",
            description = "Is the information added all the time or only one AIP out of two?")
    private Boolean always;

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
            builder.addDescriptiveInformation("optional",
                                              ThreadLocalRandom.current().nextDouble(Double.MIN_VALUE, Double.MAX_VALUE));
        }
        List<AIP> aips = new ArrayList<>();
        aips.add(aip);
        return aips;
    }

}
