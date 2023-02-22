/*
 * Copyright 2017-2022 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.stac.service.collection;

import com.google.common.collect.Sets;
import fr.cnes.regards.framework.jpa.multitenant.test.AbstractMultitenantServiceIT;
import fr.cnes.regards.framework.oais.urn.OAISIdentifier;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.dam.domain.entities.Dataset;
import fr.cnes.regards.modules.indexer.dao.EsRepository;
import fr.cnes.regards.modules.indexer.dao.spatial.ProjectGeoSettings;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

@ActiveProfiles({ "test", "feign" })
@TestPropertySource(locations = { "classpath:test.properties" },
                    properties = { "spring.jpa.properties.hibernate.default_schema=public" })
public class IdMappingServiceTest extends AbstractMultitenantServiceIT {

    public static final String ITEMS_TENANT = "PROJECT";

    @Autowired
    private EsRepository repository;

    @MockBean
    private ProjectGeoSettings projectGeoSettings;

    @Mock
    private ICatalogSearchService catalogSearchService;

    @Autowired
    private IdMappingService idMappingService;

    @Before
    public void initMethod() {

        try {
            repository.deleteIndex(ITEMS_TENANT);
        } catch (Exception e) {

        }

        Assert.assertTrue(repository.createIndex(ITEMS_TENANT));

        // Create one collection

        UniformResourceName urnParentCollection = UniformResourceName.build(OAISIdentifier.AIP.name(),
                                                                            EntityType.COLLECTION,
                                                                            ITEMS_TENANT,
                                                                            UUID.fromString(
                                                                                "74f2c965-0136-47f0-93e1-4fd098db5680"),
                                                                            1,
                                                                            null,
                                                                            null);

        Model model1 = new Model();
        Model collectionModel = new Model();
        collectionModel.setName("model_1" + System.currentTimeMillis());
        collectionModel.setType(EntityType.COLLECTION);
        collectionModel.setVersion("1");
        collectionModel.setDescription("Test collection model");
        model1.setType(EntityType.COLLECTION);
        fr.cnes.regards.modules.dam.domain.entities.Collection collection = new fr.cnes.regards.modules.dam.domain.entities.Collection(
            collectionModel,
            ITEMS_TENANT,
            "COL",
            "collection");

        collection.setId(1L);
        collection.setTags(Sets.newHashSet("TEST collection", urnParentCollection.toString()));
        collection.setLabel("toto");
        UniformResourceName collectionUniformResourceName = UniformResourceName.build(OAISIdentifier.AIP.name(),
                                                                                      EntityType.COLLECTION,
                                                                                      ITEMS_TENANT,
                                                                                      UUID.fromString(
                                                                                          "80282ac5-1b01-4e9d-a356-123456789012"),
                                                                                      1,
                                                                                      null,
                                                                                      null);
        collection.setIpId(collectionUniformResourceName);

        // Create one dataset

        UniformResourceName urnParentDataset = UniformResourceName.build(OAISIdentifier.AIP.name(),
                                                                         EntityType.DATASET,
                                                                         ITEMS_TENANT,
                                                                         UUID.fromString(
                                                                             "99f2c965-0136-47f0-93e1-4fd098db569"),
                                                                         1,
                                                                         null,
                                                                         null);

        Model model2 = new Model();
        Model datasetModel = new Model();
        datasetModel.setName("model_2" + System.currentTimeMillis());
        datasetModel.setType(EntityType.DATASET);
        datasetModel.setVersion("1");
        datasetModel.setDescription("Test dataset model");
        model2.setType(EntityType.DATASET);
        Dataset dataset = new Dataset(datasetModel, ITEMS_TENANT, "DAT", "dataset");

        dataset.setId(9L);
        dataset.setTags(Sets.newHashSet("TEST dataset", urnParentDataset.toString()));
        dataset.setLabel("tata");
        UniformResourceName datasetUniformResourceName = UniformResourceName.build(OAISIdentifier.AIP.name(),
                                                                                   EntityType.DATASET,
                                                                                   ITEMS_TENANT,
                                                                                   UUID.fromString(
                                                                                       "88282ac5-1b01-4e9d-a356-12345678909"),
                                                                                   1,
                                                                                   null,
                                                                                   null);
        dataset.setIpId(datasetUniformResourceName);

        // Save one collection and one dataset
        repository.save(ITEMS_TENANT, collection);
        repository.save(ITEMS_TENANT, dataset);
        repository.refresh(ITEMS_TENANT);
    }

    @After
    public void cleanUp() {
        repository.deleteIndex(ITEMS_TENANT);
    }

    @Test
    public void nominal_test() {

        // GIVEN
        idMappingService.initOrUpdateCache();

        // WHEN
        String urnCollection = idMappingService.getUrnByStacId("COL");
        String stacIdDataset = idMappingService.getStacIdByUrn(
            "URN:AIP:DATASET:PROJECT:88282ac5-1b01-4e9d-a356-012345678909:V1");
        String unknownStacId = idMappingService.getStacIdByUrn("URN");
        String unknownUrn = idMappingService.getUrnByStacId("COL_");

        // THEN
        Assert.assertEquals("URN:AIP:COLLECTION:PROJECT:80282ac5-1b01-4e9d-a356-123456789012:V1", urnCollection);
        Assert.assertEquals("DAT", stacIdDataset);
        Assert.assertTrue(StringUtils.isBlank(unknownStacId));
        Assert.assertTrue(StringUtils.isBlank(unknownUrn));
    }
}
