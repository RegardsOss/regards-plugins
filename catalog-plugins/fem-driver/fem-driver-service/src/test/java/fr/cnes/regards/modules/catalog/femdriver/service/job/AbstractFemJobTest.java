/*
 * Copyright 2017-2024 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.femdriver.service.job;

import fr.cnes.regards.framework.amqp.event.ISubscribable;
import fr.cnes.regards.framework.jpa.multitenant.test.AbstractMultitenantServiceIT;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.jobs.dao.IJobInfoRepository;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.accessrights.client.ILicenseClient;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.feature.dto.urn.FeatureIdentifier;
import fr.cnes.regards.modules.feature.dto.urn.FeatureUniformResourceName;
import fr.cnes.regards.modules.indexer.dao.IEsRepository;
import fr.cnes.regards.modules.indexer.service.IIndexerService;
import fr.cnes.regards.modules.indexer.service.IndexAliasResolver;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.model.gson.MultitenantFlattenedAttributeAdapterFactory;
import fr.cnes.regards.modules.model.service.IAttributeModelService;
import fr.cnes.regards.modules.model.service.ModelService;
import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author sbinda
 */
@SuppressWarnings("deprecation")
public abstract class AbstractFemJobTest extends AbstractMultitenantServiceIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFemJobTest.class);

    @Autowired
    protected IEsRepository esRepository;

    @Autowired
    private IJobInfoRepository jobInfoRepo;

    @Autowired
    protected IIndexerService indexerService;

    @Autowired
    protected ModelService modelService;

    @Autowired
    private IRuntimeTenantResolver tenantResolver;

    @Autowired
    protected IndexAliasResolver indexAliasResolver;

    @Autowired
    private IAttributeModelService attributeModelService;

    @Autowired
    protected MultitenantFlattenedAttributeAdapterFactory gsonAttributeFactory;

    @MockBean
    private ILicenseClient licenseClient;

    @Captor
    protected ArgumentCaptor<List<ISubscribable>> recordsCaptor;

    protected final List<DataObject> datas = new ArrayList<>();

    public void doInit() throws ModuleException {
        try {
            tenantResolver.forceTenant(getDefaultTenant());
            initIndex(getDefaultTenant());
            modelService.deleteModel("model_test");
        } catch (ModuleException e) {
            // Silent error
        }
        jobInfoRepo.deleteAll();
        // Init  test datas in catalog
        Model model = modelService.importModel(this.getClass().getResourceAsStream("model.xml"));
        Assert.assertNotNull(model);
        List<AttributeModel> atts = attributeModelService.getAttributes(null, null, null);
        gsonAttributeFactory.refresh(getDefaultTenant(), atts);
        indexerService.saveBulkEntities(getDefaultTenant(), createTestData(model));
        simulateApplicationReadyEvent();
        Mockito.reset(publisher);
    }

    protected List<DataObject> createTestData(Model model) {

        LOGGER.info("[TEST INIT] Creates data in index");

        DataObject data = createEntity(model, "test", "data_one");
        data.addProperty(IProperty.buildString("name", "data_one_test"));
        data.setGroups(null);
        data.setCreationDate(OffsetDateTime.now());
        datas.add(data);

        data = createEntity(model, "test", "data_two");
        data.addProperty(IProperty.buildString("name", "data_two_test"));
        data.setGroups(null);
        data.setCreationDate(OffsetDateTime.now());
        datas.add(data);

        // 1000+
        for (int i = 0; i < 1000; i++) {
            data = createEntity(model, "test" + i, "loop test" + i);
            data.addProperty(IProperty.buildString("name", "plouf"));
            data.setGroups(null);
            data.setCreationDate(OffsetDateTime.now());
            datas.add(data);
        }

        return datas;
    }

    protected DataObject createEntity(Model model, String providerId, String label) {
        return DataObject.wrap(model,
                               new DataObjectFeature(FeatureUniformResourceName.build(FeatureIdentifier.FEATURE,
                                                                                      EntityType.DATA,
                                                                                      getDefaultTenant(),
                                                                                      UUID.randomUUID(),
                                                                                      1), providerId, label),
                               false);
    }

    protected void initIndex(String tenant) {
        String aliasName = indexAliasResolver.resolveAliasName(tenant);

        if (esRepository.indexExists(tenant)) {
            esRepository.deleteIndex(tenant);
        }
        if (esRepository.indexExists(aliasName)) {
            esRepository.deleteIndex(aliasName);
        }
        esRepository.createIndex(tenant);
        esRepository.createAlias(tenant, aliasName);
    }

}
