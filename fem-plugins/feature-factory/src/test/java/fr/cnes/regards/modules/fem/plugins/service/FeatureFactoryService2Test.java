/*
 * Copyright 2017-2019 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.fem.plugins.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.fem.plugins.service2.FeatureFactoryService;

/**
 * @author sbinda
 *
 *
 * L2_HR_RiverAvg : Nom a ccorriger
 */
public class FeatureFactoryService2Test {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureFactoryService2Test.class);

    @Test
    public void testAllWithoutFail() throws JsonParseException, JsonMappingException, IOException {
        FeatureFactoryService factory = new FeatureFactoryService();
        factory.readConfs(Paths.get("src/test/resources/conf/"));
        factory.readConfs(Paths.get("src/test/resources/conf/daux"));
        factory.getDescriptors().forEach(d -> {
            if ((d.getExample() != null) && !d.getExample().isEmpty()) {
                try {
                    Feature feature = factory.getFeature(d.getExample().get(0), "model");
                    LOGGER.debug(feature.getProperties().toString());
                } catch (ModuleException e) {
                    LOGGER.error("[{}] Invalid data descriptor cause : {}", d.getType(), e.getMessage());
                }
            }
        });
    }

    @Test
    @Ignore
    public void testAllWithFails() throws JsonParseException, JsonMappingException, IOException {
        FeatureFactoryService factory = new FeatureFactoryService();
        factory.readConfs(Paths.get("src/test/resources/conf/"));
        long count = Files.list(Paths.get("src/test/resources/conf/")).count();
        factory.readConfs(Paths.get("src/test/resources/conf/daux"));
        count += Files.list(Paths.get("src/test/resources/conf/daux")).count();
        Assert.assertEquals("All data type descriptors should be valid", count, factory.getDescriptors().size());
        factory.getDescriptors().forEach(d -> {
            if ((d.getExample() != null) && !d.getExample().isEmpty()) {
                try {
                    Feature feature = factory.getFeature(d.getExample().get(0), "model");
                    LOGGER.debug(feature.getProperties().toString());
                } catch (ModuleException e) {
                    Assert.fail(String.format("[%s] Invalid data descriptor cause : %s", d.getType(), e.getMessage()));
                }
            }
        });
    }

}
