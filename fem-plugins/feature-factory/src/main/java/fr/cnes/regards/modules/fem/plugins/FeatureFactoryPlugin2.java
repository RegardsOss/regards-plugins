/*
 * Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
 *
 * This file is part of REGARDS.
 *
 * REGARDS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * REGARDS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with REGARDS. If not, see <http://www.gnu.org/licenses/>.
 */
package fr.cnes.regards.modules.fem.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Autowired;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.feature.domain.plugin.IFeatureFactoryPlugin;
import fr.cnes.regards.modules.feature.domain.request.FeatureReferenceRequest;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.fem.plugins.service2.FeatureFactoryService;

/**
 * Create a {@link Feature} from a {@link FeatureReferenceRequest}
 * We will use the file name to extract {@link Feature} metadata
 * @author Sébastien Binda
 *
 */
@Plugin(author = "REGARDS Team", description = "Create a feature from a file reference", id = "FeatureFactoryPlugin2",
        version = "1.0.0", contact = "regards@c-s.fr", license = "GPLv3", owner = "CNES",
        url = "https://regardsoss.github.io/")
public class FeatureFactoryPlugin2 implements IFeatureFactoryPlugin {

    @PluginParameter(name = "model", label = "Model to generate features")
    public static final String model = "model";

    @PluginParameter(name = "configDirectory",
            label = "Directory where to parse data types desccription files at yml format (datatype.yml)")
    public static final String configDirectory = "model";

    @Autowired
    private FeatureFactoryService factoryService;

    @PluginInit
    public void init() throws ModuleException {
        // Initialize data type description from configuration directory
        Path confPath = Paths.get(configDirectory);
        if (Files.isDirectory(confPath) && Files.isReadable(confPath)) {
            try {
                factoryService.readConfs(confPath);
            } catch (IOException e) {
                throw new ModuleException(
                        String.format("Error during plugin initialisation. Cause : %s", e.getMessage()));
            }
        } else {
            throw new ModuleException(String.format("Invalid configuration directory at %s", configDirectory));
        }
    }

    @Override
    public Feature createFeature(FeatureReferenceRequest reference) throws ModuleException {
        return factoryService.getFeature(reference.getLocation(), model, OffsetDateTime.now());
    }

}
