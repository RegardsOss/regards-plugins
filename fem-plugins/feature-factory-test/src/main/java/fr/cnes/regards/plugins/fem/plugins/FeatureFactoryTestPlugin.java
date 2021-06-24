package fr.cnes.regards.plugins.fem.plugins;

import com.google.gson.JsonObject;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.featureprovider.domain.plugin.FactoryParameters;
import fr.cnes.regards.modules.featureprovider.domain.plugin.IFeatureFactoryPlugin;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import org.springframework.beans.factory.annotation.Autowired;

@Plugin(author = "REGARDS Team",
        description = "Creates test features from parameters with id key.",
        id = FeatureFactoryTestPlugin.PLUGIN_ID, version = "1.0.0", contact = "regards@c-s.fr", license = "GPLv3",
        owner = "CNES", url = "https://regardsoss.github.io/")
public class FeatureFactoryTestPlugin implements IFeatureFactoryPlugin {

    public static final String PLUGIN_ID = "FeatureFactoryTestPlugin";

    @Autowired
    private FactoryParameters fp;

    @PluginParameter(name = "model", label = "Model name")
    private String model;

    @Override
    public Feature generateFeature(JsonObject parameters) throws ModuleException {
        String id = fp.getParameter(parameters,"id", String.class);
        Feature toCreate = Feature.build(id, null, null, null, EntityType.DATA, model);
        if (id.contains("error")) {
            throw new ModuleException("Generated error for tests");
        }
        return toCreate;
    }
}
