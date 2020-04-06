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
package fr.cnes.regards.modules.catalog.femdriver.service.job;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.google.common.collect.Sets;

import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.amqp.event.ISubscribable;
import fr.cnes.regards.framework.jpa.multitenant.test.AbstractMultitenantServiceTest;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.jobs.dao.IJobInfoRepository;
import fr.cnes.regards.framework.modules.jobs.domain.JobStatus;
import fr.cnes.regards.framework.modules.jobs.service.IJobInfoService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.catalog.femdriver.dto.FeatureUpdateRequest;
import fr.cnes.regards.modules.catalog.femdriver.service.FemDriverService;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.feature.dto.event.in.FeatureUpdateRequestEvent;
import fr.cnes.regards.modules.feature.dto.urn.FeatureIdentifier;
import fr.cnes.regards.modules.feature.dto.urn.FeatureUniformResourceName;
import fr.cnes.regards.modules.indexer.dao.IEsRepository;
import fr.cnes.regards.modules.indexer.service.IIndexerService;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.model.gson.MultitenantFlattenedAttributeAdapterFactory;
import fr.cnes.regards.modules.model.service.IAttributeModelService;
import fr.cnes.regards.modules.model.service.ModelService;
import fr.cnes.regards.modules.search.domain.SearchRequest;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineMappings;

/**
 * Test class
 *
 * @author Sébastien Binda
 *
 */
@TestPropertySource(locations = { "classpath:test.properties", "classpath:local.properties" },
        properties = { "spring.jpa.properties.hibernate.default_schema=fem_job" })
public class FemUpdateJobTest extends AbstractMultitenantServiceTest {

    @Autowired
    protected IEsRepository esRepository;

    @Autowired
    private IJobInfoRepository jobInfoRepo;

    @Autowired
    protected IIndexerService indexerService;

    @Autowired
    protected ModelService modelService;

    @Autowired
    private IJobInfoService jobInfoService;

    @Autowired
    private FemDriverService femDriverService;

    @Autowired
    private IRuntimeTenantResolver tenantResolver;

    @Autowired
    private IAttributeModelService attributeModelService;

    @Autowired
    protected MultitenantFlattenedAttributeAdapterFactory gsonAttributeFactory;

    @SpyBean
    private IPublisher publisher;

    @Captor
    private ArgumentCaptor<ISubscribable> recordsCaptor;

    private final List<DataObject> datas = new ArrayList<>();

    @Before
    public void init() throws ModuleException {
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
        createTestData(model);
        indexerService.saveBulkEntities(getDefaultTenant(), createTestData(model));
        simulateApplicationReadyEvent();
    }

    protected List<DataObject> createTestData(Model model) {

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

        return datas;
    }

    protected DataObject createEntity(Model model, String providerId, String label) {
        return DataObject.wrap(model,
                               new DataObjectFeature(
                                       FeatureUniformResourceName.build(FeatureIdentifier.FEATURE, EntityType.DATA,
                                                                        getDefaultTenant(), UUID.randomUUID(), 1),
                                       providerId, label),
                               false);
    }

    protected void initIndex(String index) {
        if (esRepository.indexExists(index)) {
            esRepository.deleteIndex(index);
        }
        esRepository.createIndex(index);
    }

    @Test
    public void testUpdateJob() throws ModuleException, InterruptedException, ExecutionException {
        tenantResolver.forceTenant(getDefaultTenant());
        Mockito.verify(publisher, Mockito.times(0)).publish(recordsCaptor.capture());
        MultiValueMap<String, String> searchParameters = new LinkedMultiValueMap<String, String>();
        SearchRequest searchRequest = new SearchRequest(SearchEngineMappings.LEGACY_PLUGIN_ID, null, searchParameters,
                null, null, null);
        Map<String, IProperty<?>> propertyMap = new HashMap<String, IProperty<?>>();
        propertyMap.put("name", IProperty.buildString("name", "plop"));
        femDriverService.scheduleUpdate(FeatureUpdateRequest.build(searchRequest, propertyMap));
        int loop = 0;
        while ((jobInfoService.retrieveJobs(JobStatus.SUCCEEDED).size() == 0) && (loop < 2000)) {
            loop++;
            Thread.sleep(100);
        }
        Mockito.verify(publisher, Mockito.atLeastOnce()).publish(recordsCaptor.capture());
        long nbEvents = recordsCaptor.getAllValues().stream().filter(v -> v instanceof FeatureUpdateRequestEvent)
                .peek(v -> {
                    if (v instanceof FeatureUpdateRequestEvent) {
                        FeatureUpdateRequestEvent event = (FeatureUpdateRequestEvent) v;
                        Assert.assertNotNull("Invalid null event", event.getFeature());
                        Assert.assertEquals("model_test", event.getFeature().getModel());
                        Assert.assertNotNull("EntityType is mandatory", event.getFeature().getEntityType());
                        Assert.assertNotNull("Feature id is mandatory", event.getFeature().getId());
                        Assert.assertNotNull("Feature urn is mandatory", event.getFeature().getUrn());
                    }
                }).count();
        Assert.assertEquals(2L, nbEvents);

    }

    @Test
    public void testUpdateJobWithCrit() throws ModuleException, InterruptedException, ExecutionException {
        tenantResolver.forceTenant(getDefaultTenant());
        Mockito.verify(publisher, Mockito.times(0)).publish(recordsCaptor.capture());
        MultiValueMap<String, String> searchParameters = new LinkedMultiValueMap<String, String>();
        SearchRequest searchRequest = new SearchRequest(SearchEngineMappings.LEGACY_PLUGIN_ID, null, searchParameters,
                Sets.newHashSet(datas.get(0).getIpId().toString()), null, null);
        Map<String, IProperty<?>> propertyMap = new HashMap<String, IProperty<?>>();
        propertyMap.put("name", IProperty.buildString("name", "plop"));
        femDriverService.scheduleUpdate(FeatureUpdateRequest.build(searchRequest, propertyMap));
        int loop = 0;
        while ((jobInfoService.retrieveJobs(JobStatus.SUCCEEDED).size() == 0) && (loop < 2000)) {
            loop++;
            Thread.sleep(100);
        }
        Mockito.verify(publisher, Mockito.atLeastOnce()).publish(recordsCaptor.capture());
        long nbEvents = recordsCaptor.getAllValues().stream().filter(v -> v instanceof FeatureUpdateRequestEvent)
                .peek(v -> {
                    if (v instanceof FeatureUpdateRequestEvent) {
                        FeatureUpdateRequestEvent event = (FeatureUpdateRequestEvent) v;
                        Assert.assertNotNull("Invalid null event", event.getFeature());
                        Assert.assertEquals("model_test", event.getFeature().getModel());
                        Assert.assertNotNull("EntityType is mandatory", event.getFeature().getEntityType());
                        Assert.assertNotNull("Feature id is mandatory", event.getFeature().getId());
                        Assert.assertNotNull("Feature urn is mandatory", event.getFeature().getUrn());
                    }
                }).count();
        Assert.assertEquals(1L, nbEvents);
    }

}
