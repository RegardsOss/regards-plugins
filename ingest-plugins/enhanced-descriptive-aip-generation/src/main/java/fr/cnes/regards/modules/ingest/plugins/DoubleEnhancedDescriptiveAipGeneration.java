package fr.cnes.regards.modules.ingest.plugins;

import java.util.concurrent.ThreadLocalRandom;

import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.modules.ingest.dto.aip.AIP;

/**
 * Allows to add a random Double to generated AIPs
 * @author Sylvain VISSIERE-GUERINET
 */
@Plugin(author = "REGARDS Team", description = "Add random double value to the AIP generated",
        id = "DoubleEnhancedDescriptiveAipGeneration", version = "1.0.0", contact = "regards@c-s.fr", license = "GPLv3",
        owner = "CNES", url = "https://regardsoss.github.io/")
public class DoubleEnhancedDescriptiveAipGeneration extends AbstractEnhancedDescriptiveAipGeneration {

    @Override
    protected void addDescriptiveInformation(AIP aip) {
        aip.withDescriptiveInformation(descriptiveInfoName,
                                       ThreadLocalRandom.current().nextDouble(Double.MIN_VALUE, Double.MAX_VALUE));
    }

}
