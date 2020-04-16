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
package fr.cnes.regards.modules.fem.plugins.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.validation.Errors;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;

import fr.cnes.regards.framework.jpa.multitenant.test.AbstractMultitenantServiceTest;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.feature.service.FeatureValidationService;
import fr.cnes.regards.modules.fem.plugins.dto.DataTypeDescriptor;
import fr.cnes.regards.modules.model.client.IModelAttrAssocClient;
import fr.cnes.regards.modules.model.client.IModelClient;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.domain.ModelAttrAssoc;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import fr.cnes.regards.modules.model.gson.MultitenantFlattenedAttributeAdapterFactory;
import fr.cnes.regards.modules.model.service.exception.ImportException;
import fr.cnes.regards.modules.model.service.validation.ValidationMode;
import fr.cnes.regards.modules.model.service.xml.IComputationPluginService;
import fr.cnes.regards.modules.model.service.xml.XmlImportHelper;

/**
 * Test Class
 *
 * @author Sébastien  Binda
 */
@TestPropertySource(properties = { "regards.gson.prettyPrint=true", "spring.application.name=fatureFactory" })
public class DataTypeFeatureFactoryServiceTest extends AbstractMultitenantServiceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataTypeFeatureFactoryServiceTest.class);

    @Autowired
    Gson gson;

    @Autowired
    DataTypeFeatureFactoryService featureFactory;

    @Autowired
    IRuntimeTenantResolver resovlver;

    @Autowired
    protected MultitenantFlattenedAttributeAdapterFactory factory;

    @Autowired
    private FeatureValidationService validationService;

    @Autowired
    protected IModelClient modelClientMock;

    @Autowired
    protected IModelAttrAssocClient modelAttrAssocClientMock;

    private final static String RESOURCE_PATH = "src/test/resources/conf/models";

    private static final String tenant = "DEFAULT";

    @Before
    public void init() {
        try {
            FileUtils.deleteDirectory(Paths.get("target/features").toFile());
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
    }

    public String importModel(String filename) {
        try (InputStream input = new FileInputStream(Paths.get(RESOURCE_PATH, filename).toFile())) {
            // Import model
            Iterable<ModelAttrAssoc> assocs = XmlImportHelper
                    .importModel(input, Mockito.mock(IComputationPluginService.class));

            // Translate to resources and attribute models and extract model name
            String modelName = null;
            List<AttributeModel> atts = new ArrayList<>();
            List<EntityModel<ModelAttrAssoc>> resources = new ArrayList<>();
            for (ModelAttrAssoc assoc : assocs) {
                atts.add(assoc.getAttribute());
                resources.add(new EntityModel<ModelAttrAssoc>(assoc));
                if (modelName == null) {
                    modelName = assoc.getModel().getName();
                }
            }

            // Property factory registration
            factory.registerAttributes(tenant, atts);

            // Mock client
            List<EntityModel<Model>> models = new ArrayList<EntityModel<Model>>();
            Model mockModel = Mockito.mock(Model.class);
            Mockito.when(mockModel.getName()).thenReturn(modelName);
            models.add(new EntityModel<Model>(mockModel));
            Mockito.when(modelClientMock.getModels(null)).thenReturn(ResponseEntity.ok(models));
            Mockito.when(modelAttrAssocClientMock.getModelAttrAssocs(modelName))
                    .thenReturn(ResponseEntity.ok(resources));

            return modelName;
        } catch (IOException | ImportException e) {
            String errorMessage = "Cannot import model";
            LOGGER.error(e.getMessage(), e);
            throw new AssertionError(errorMessage);
        }
    }

    /**
     * Test to parse all data types and  log errors for each one if any.
     * @throws JsonParseException
     * @throws JsonMappingException
     * @throws IOException
     */
    @Test
    public void testAllDataTypes() throws JsonParseException, JsonMappingException, IOException {
        resovlver.forceTenant(tenant);
        String modelName = this.importModel("model_geode.xml");
        featureFactory.readConfs(Paths.get("src/test/resources/conf/"));
        featureFactory.readConfs(Paths.get("src/test/resources/conf/daux"));
        // FIXME : correct invalids data  types
        // featureFactory.readConfs(Paths.get("src/test/resources/conf/invalids"));
        OffsetDateTime creationDate = OffsetDateTime.of(2020, 4, 10, 12, 0, 0, 0, ZoneOffset.UTC);
        for (DataTypeDescriptor d : featureFactory.getDescriptors()) {
            if ((d.getExample() != null) && !d.getExample().isEmpty()) {
                try {
                    Feature feature = featureFactory.getFeature("file://somewhere/test/" + d.getExample().get(0),
                                                                modelName, creationDate);
                    LOGGER.debug(feature.getProperties().toString());
                    Errors errors = validationService.validate(feature, ValidationMode.CREATION);
                    if (errors.hasErrors()) {
                        errors.getAllErrors().forEach(e -> LOGGER.error(" ----> {}", e.getDefaultMessage().toString()));
                        String message = String.format("[%s] %s validation errors", d.getType(),
                                                       errors.getErrorCount());
                        LOGGER.error(message);
                        Assert.fail(message);
                    }

                    File result = writeToFile(feature, d.getType());
                    Assert.assertTrue(String.format("Expected generated feature for product %s does not match",
                                                    d.getType()),
                                      com.google.common.io.Files.equal(result, Paths
                                              .get("src/test/resources/features", d.getType() + ".json").toFile()));
                } catch (ModuleException e) {
                    LOGGER.error("[{}] Invalid data descriptor cause : {}", d.getType(), e.getMessage());
                    Assert.fail(String.format("[%s] Invalid data descriptor cause : %s", d.getType(), e.getMessage()));
                }
            }
        }
        // FIXME : One  each data type is well defined add test all 127 data types.
        Assert.assertEquals(103, featureFactory.getDescriptors().size());
    }

    private File writeToFile(Feature feature, String dataType) {
        File file = Paths.get("target/features", dataType + ".json").toFile();
        try {
            Files.createDirectories(Paths.get("target/features"));
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(feature, writer);
                writer.flush();
            }
        } catch (JsonIOException | IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return file;
    }

}
