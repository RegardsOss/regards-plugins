package fr.cnes.regards.modules.ingest.plugins;

import com.google.common.base.Strings;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.storage.domain.AIPBuilder;

/**
 * Allows to add two given String to generated AIPs.
 * @author Sylvain VISSIERE-GUERINET
 */
@Plugin(author = "REGARDS Team", description = "Add two given string value to generated AIP",
        id = "StringEnhancedDescriptiveAipGeneration", version = "1.0.0", contact = "regards@c-s.fr", licence = "GPLv3",
        owner = "CNES", url = "https://regardsoss.github.io/")
public class StringEnhancedDescriptiveAipGeneration extends AbstractEnhancedDescriptiveAipGeneration {

    @PluginParameter(label = "Second descriptive information", optional = true)
    private SecondDescriptiveInformation secondDescriptiveInfo;

    @PluginParameter(label = "Value of the descriptive information to add")
    private String value;

    @Override
    protected void addDescriptiveInformation(AIPBuilder builder) {
        builder.addDescriptiveInformation(descriptiveInfoName, value);
        if(secondDescriptiveInfo != null && secondDescriptiveInfo.isInitialized()) {
            builder.addDescriptiveInformation(secondDescriptiveInfo.getSecondDescriptiveInfoName(),
                                              secondDescriptiveInfo.getSecondValue());
        }
    }

    private class SecondDescriptiveInformation {

        @PluginParameter(label = "Second descriptive information to add")
        private String secondDescriptiveInfoName;

        @PluginParameter(label = "Second value of the second descriptive information to add")
        private String secondValue;

        public SecondDescriptiveInformation() {
        }

        public String getSecondDescriptiveInfoName() {
            return secondDescriptiveInfoName;
        }

        public void setSecondDescriptiveInfoName(String secondDescriptiveInfoName) {
            this.secondDescriptiveInfoName = secondDescriptiveInfoName;
        }

        public String getSecondValue() {
            return secondValue;
        }

        public void setSecondValue(String secondValue) {
            this.secondValue = secondValue;
        }

        public boolean isInitialized() {
            return !Strings.isNullOrEmpty(secondDescriptiveInfoName) && secondValue != null;
        }
    }
}
