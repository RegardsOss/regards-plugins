package fr.cnes.regards.modules.ingest.plugins;

import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.ingest.dto.aip.AIP;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Allows to add String Array value to generated AIPs.
 *
 * @author Sylvain VISSIERE-GUERINET
 */
@Plugin(author = "REGARDS Team",
        description = "Add string array value to generated AIP",
        id = "StringArrayEnhancedDescriptiveAipGeneration",
        version = "1.0.0",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CNES",
        url = "https://regardsoss.github.io/")
public class StringArrayEnhancedDescriptiveAipGeneration extends AbstractEnhancedDescriptiveAipGeneration {

    @PluginParameter(label = "Values of the descriptive information to add")
    private Set<String> values;

    @PluginParameter(label = "Should all values be added or not?")
    private Boolean allValues;

    @Override
    protected void addDescriptiveInformation(AIP aip) {
        if (allValues) {
            aip.withDescriptiveInformation(descriptiveInfoName, values);
        } else {
            Random r = new Random();
            Set<String> toAdd = new HashSet<>();
            for (String value : values) {
                if (r.nextInt() % 2 == 0) {
                    toAdd.add(value);
                }
            }
            aip.withDescriptiveInformation(descriptiveInfoName, toAdd);
        }
    }

}
