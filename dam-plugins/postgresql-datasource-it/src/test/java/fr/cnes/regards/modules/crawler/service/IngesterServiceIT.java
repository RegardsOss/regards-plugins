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
package fr.cnes.regards.modules.crawler.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.google.gson.JsonElement;

import fr.cnes.regards.framework.modules.plugins.dao.IPluginConfigurationRepository;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.StringPluginParam;
import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.oais.urn.EntityType;
import fr.cnes.regards.framework.test.integration.AbstractRegardsIT;
import fr.cnes.regards.framework.utils.plugins.PluginParameterTransformer;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.modules.crawler.dao.IDatasourceIngestionRepository;
import fr.cnes.regards.modules.crawler.domain.DatasourceIngestion;
import fr.cnes.regards.modules.crawler.domain.IngestionStatus;
import fr.cnes.regards.modules.crawler.service.ds.ExternalData;
import fr.cnes.regards.modules.crawler.service.ds.ExternalData2;
import fr.cnes.regards.modules.crawler.service.ds.ExternalData2Repository;
import fr.cnes.regards.modules.crawler.service.ds.ExternalData3;
import fr.cnes.regards.modules.crawler.service.ds.ExternalData3Repository;
import fr.cnes.regards.modules.crawler.service.ds.ExternalDataRepository;
import fr.cnes.regards.modules.dam.dao.entities.IAbstractEntityRepository;
import fr.cnes.regards.modules.dam.dao.entities.IDatasetRepository;
import fr.cnes.regards.modules.dam.dao.models.IModelAttrAssocRepository;
import fr.cnes.regards.modules.dam.dao.models.IModelRepository;
import fr.cnes.regards.modules.dam.domain.datasources.AbstractAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.StaticAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DBConnectionPluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.feature.EntityFeature;
import fr.cnes.regards.modules.dam.domain.models.Model;
import fr.cnes.regards.modules.dam.domain.models.attributes.AttributeType;
import fr.cnes.regards.modules.dam.gson.entities.MultitenantFlattenedAttributeAdapterFactoryEventHandler;
import fr.cnes.regards.modules.dam.plugins.datasources.AipDataSourcePlugin;
import fr.cnes.regards.modules.dam.plugins.datasources.DefaultPostgreConnectionPlugin;
import fr.cnes.regards.modules.dam.plugins.datasources.PostgreDataSourceFromSingleTablePlugin;
import fr.cnes.regards.modules.dam.service.models.IModelService;
import fr.cnes.regards.modules.indexer.dao.IEsRepository;
import fr.cnes.regards.modules.project.client.rest.IProjectsClient;
import fr.cnes.regards.modules.project.domain.Project;
import fr.cnes.regards.modules.storage.client.IAipClient;

@ActiveProfiles({ "noschedule", "IngesterTest", "test" }) // Disable scheduling, this will activate IngesterService during all tests
@TestPropertySource(properties = { "spring.jpa.properties.hibernate.default_schema=projectdb" })
public class IngesterServiceIT extends AbstractRegardsIT {

    @Autowired
    private MultitenantFlattenedAttributeAdapterFactoryEventHandler gsonAttributeFactoryHandler;

    private static final String T_DATA_1 = "projectdb.t_data";

    private static final String T_DATA_2 = "projectdb.t_data_2";

    private static final String T_DATA_3 = "projectdb.t_data_3";

    @Value("${postgresql.datasource.host}")
    private String dbHost;

    @Value("${postgresql.datasource.port}")
    private String dbPort;

    @Value("${postgresql.datasource.name}")
    private String dbName;

    @Value("${postgresql.datasource.username}")
    private String dbUser;

    @Value("${postgresql.datasource.password}")
    private String dbPpassword;

    @Value("${postgresql.datasource.driver}")
    private String driver;

    @Autowired
    private IIngesterService ingesterService;

    private List<AbstractAttributeMapping> modelAttrMapping;

    private Model dataModel;

    private Model datasetModel;

    private PluginConfiguration dataSourcePluginConf1;

    private PluginConfiguration dataSourcePluginConf2;

    private PluginConfiguration dataSourcePluginConf3;

    private PluginConfiguration dataSourcePluginConf4;

    private PluginConfiguration dBConnectionConf;

    @Autowired
    private ExternalDataRepository extData1Repos;

    @Autowired
    private ExternalData2Repository extData2Repos;

    @Autowired
    private ExternalData3Repository extData3Repos;

    @Autowired
    private ICrawlerAndIngesterService crawlerService;

    @Autowired
    private IDatasetCrawlerService datasetCrawlerService;

    @Autowired
    private IRuntimeTenantResolver tenantResolver;

    @Autowired
    private IModelService modelService;

    @Autowired
    private IModelRepository modelRepository;

    @Autowired
    private IModelAttrAssocRepository modelAttrAssocRepos;

    @Autowired
    private IAbstractEntityRepository<AbstractEntity<EntityFeature>> entityRepos;

    @Autowired
    private IDatasetRepository datasetRepos;

    @Autowired
    private IPluginService pluginService;

    @Autowired
    private IPluginConfigurationRepository pluginConfRepos;

    @Autowired
    private IDatasourceIngestionRepository dsIngestionRepos;

    @Autowired
    private IEsRepository esRepository;

    @Autowired
    private IAipClient aipClient;

    @Autowired
    private IProjectsClient projectsClient;

    private PluginConfiguration getPostgresDataSource1(final PluginConfiguration pluginConf) {
        Set<IPluginParam> parameters = IPluginParam
                .set(IPluginParam.build(DataSourcePluginConstants.CONNECTION_PARAM,
                                        PluginParameterTransformer.toJson(pluginConf)),
                     IPluginParam.build(DataSourcePluginConstants.TABLE_PARAM, T_DATA_1),
                     IPluginParam.build(DataSourcePluginConstants.REFRESH_RATE, 1),
                     IPluginParam.build(DataSourcePluginConstants.MODEL_NAME_PARAM, dataModel.getName()),
                     IPluginParam.build(DataSourcePluginConstants.MODEL_MAPPING_PARAM,
                                        PluginParameterTransformer.toJson(modelAttrMapping)));

        return PluginUtils.getPluginConfiguration(parameters, PostgreDataSourceFromSingleTablePlugin.class);
    }

    private PluginConfiguration getPostgresDataSource2(final PluginConfiguration pluginConf) {
        Set<IPluginParam> parameters = IPluginParam
                .set(IPluginParam.build(DataSourcePluginConstants.CONNECTION_PARAM,
                                        PluginParameterTransformer.toJson(pluginConf)),
                     IPluginParam.build(DataSourcePluginConstants.TABLE_PARAM, T_DATA_2),
                     IPluginParam.build(DataSourcePluginConstants.REFRESH_RATE, 1),
                     IPluginParam.build(DataSourcePluginConstants.MODEL_NAME_PARAM, dataModel.getName()),
                     IPluginParam.build(DataSourcePluginConstants.MODEL_MAPPING_PARAM,
                                        PluginParameterTransformer.toJson(modelAttrMapping)));

        return PluginUtils.getPluginConfiguration(parameters, PostgreDataSourceFromSingleTablePlugin.class);
    }

    private PluginConfiguration getPostgresDataSource3(final PluginConfiguration pluginConf) {
        Set<IPluginParam> parameters = IPluginParam
                .set(IPluginParam.build(DataSourcePluginConstants.CONNECTION_PARAM,
                                        PluginParameterTransformer.toJson(pluginConf)),
                     IPluginParam.build(DataSourcePluginConstants.TABLE_PARAM, T_DATA_3),
                     IPluginParam.build(DataSourcePluginConstants.REFRESH_RATE, 1),
                     IPluginParam.build(DataSourcePluginConstants.MODEL_NAME_PARAM, dataModel.getName()),
                     IPluginParam.build(DataSourcePluginConstants.MODEL_MAPPING_PARAM,
                                        PluginParameterTransformer.toJson(modelAttrMapping)));

        return PluginUtils.getPluginConfiguration(parameters, PostgreDataSourceFromSingleTablePlugin.class);
    }

    private PluginConfiguration getAipDataSource() {
        Set<IPluginParam> parameters = IPluginParam
                .set(IPluginParam.build(DataSourcePluginConstants.MODEL_NAME_PARAM, "model_1"),
                     IPluginParam.build(DataSourcePluginConstants.REFRESH_RATE, 10),
                     IPluginParam.build(DataSourcePluginConstants.BINDING_MAP,
                                        PluginParameterTransformer.toJson(createBindingMap())));

        return PluginUtils.getPluginConfiguration(parameters, AipDataSourcePlugin.class);
    }

    private PluginConfiguration getPostgresConnectionConfiguration() {
        Set<IPluginParam> parameters = IPluginParam
                .set(IPluginParam.build(DBConnectionPluginConstants.USER_PARAM, dbUser),
                     IPluginParam.build(DBConnectionPluginConstants.DB_HOST_PARAM, dbHost),
                     IPluginParam.build(DBConnectionPluginConstants.DB_PORT_PARAM, dbPort),
                     IPluginParam.build(DBConnectionPluginConstants.DB_NAME_PARAM, dbName));
        StringPluginParam passwordParam = IPluginParam.build(DBConnectionPluginConstants.PASSWORD_PARAM, dbPpassword);
        passwordParam.setDecryptedValue(dbPpassword);
        parameters.add(passwordParam);

        return PluginUtils.getPluginConfiguration(parameters, DefaultPostgreConnectionPlugin.class);
    }

    private void buildModelAttributes() {
        modelAttrMapping = new ArrayList<>();
        modelAttrMapping
                .add(new StaticAttributeMapping(AbstractAttributeMapping.PRIMARY_KEY, AttributeType.LONG, "id"));
        modelAttrMapping.add(new StaticAttributeMapping(AbstractAttributeMapping.LAST_UPDATE,
                AttributeType.DATE_ISO8601, "date"));
    }

    /**
     * No binding with dynamic values, only mandatory ones
     */
    private Map<String, JsonElement> createBindingMap() {
        Map<String, JsonElement> map = new HashMap<>();
        return map;
    }

    @Before
    public void setUp() throws Exception {

        // Simulate spring boot ApplicationStarted event to start mapping for each tenants.
        gsonAttributeFactoryHandler.onApplicationEvent(null);

        tenantResolver.forceTenant(getDefaultTenant());

        if (esRepository.indexExists(getDefaultTenant())) {
            esRepository.deleteAll(getDefaultTenant());
        } else {
            esRepository.createIndex(getDefaultTenant());
        }

        crawlerService.setConsumeOnlyMode(true);
        datasetCrawlerService.setConsumeOnlyMode(true);
        ingesterService.setConsumeOnlyMode(true);

        dsIngestionRepos.deleteAll();
        extData1Repos.deleteAll();
        extData2Repos.deleteAll();
        extData3Repos.deleteAll();

        datasetRepos.deleteAll();
        entityRepos.deleteAll();
        modelAttrAssocRepos.deleteAll();
        modelRepository.deleteAll();

        pluginConfRepos.deleteAll();

        dataModel = new Model();
        dataModel.setName("model_1");
        dataModel.setType(EntityType.DATA);
        dataModel.setVersion("1");
        dataModel.setDescription("Test data object model");
        modelService.createModel(dataModel);

        datasetModel = new Model();
        datasetModel.setName("model_ds_1" + System.currentTimeMillis());
        datasetModel.setType(EntityType.DATASET);
        datasetModel.setVersion("1");
        datasetModel.setDescription("Test dataset model");
        modelService.createModel(datasetModel);

        // Initialize the AbstractAttributeMapping
        buildModelAttributes();

        // Connection PluginConf
        dBConnectionConf = getPostgresConnectionConfiguration();
        pluginService.savePluginConfiguration(dBConnectionConf);

        final DefaultPostgreConnectionPlugin dbCtx = pluginService.getPlugin(dBConnectionConf.getId());
        Assume.assumeTrue(dbCtx.testConnection());

        // DataSource PluginConf
        dataSourcePluginConf1 = getPostgresDataSource1(dBConnectionConf);
        pluginService.savePluginConfiguration(dataSourcePluginConf1);

        dataSourcePluginConf2 = getPostgresDataSource2(dBConnectionConf);
        pluginService.savePluginConfiguration(dataSourcePluginConf2);

        dataSourcePluginConf3 = getPostgresDataSource3(dBConnectionConf);
        pluginService.savePluginConfiguration(dataSourcePluginConf3);

        dataSourcePluginConf4 = getAipDataSource();
        pluginService.savePluginConfiguration(dataSourcePluginConf4);

    }

    @After
    public void clean() {
        if (dataSourcePluginConf1 != null) {
            Utils.execute(pluginService::deletePluginConfiguration, dataSourcePluginConf1.getBusinessId());
        }
        if (dataSourcePluginConf2 != null) {
            Utils.execute(pluginService::deletePluginConfiguration, dataSourcePluginConf2.getBusinessId());
        }
        if (dataSourcePluginConf3 != null) {
            Utils.execute(pluginService::deletePluginConfiguration, dataSourcePluginConf3.getBusinessId());
        }
        if (dBConnectionConf != null) {
            Utils.execute(pluginService::deletePluginConfiguration, dBConnectionConf.getBusinessId());
        }
        if (dataSourcePluginConf4 != null) {
            Utils.execute(pluginService::deletePluginConfiguration, dataSourcePluginConf4.getBusinessId());
        }

        if (datasetModel != null) {
            Utils.execute(modelService::deleteModel, datasetModel.getName());
        }
        if (dataModel != null) {
            Utils.execute(modelService::deleteModel, dataModel.getName());
        }

    }

    @Test
    public void test() throws InterruptedException {
        Mockito.when(aipClient.retrieveAipDataFiles(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(),
                                                    Mockito.anyInt()))
                .thenReturn(ResponseEntity.ok(new PagedResources<>(Collections.emptyList(),
                        new PagedResources.PageMetadata(0, 0, 0, 1))));
        Project project = new Project("Desc", "Icon", true, "Name");
        Mockito.when(projectsClient.retrieveProject(tenantResolver.getTenant()))
                .thenReturn(ResponseEntity.ok(new Resource<>(project)));
        // Initial Ingestion with no value from datasources
        ingesterService.manage();

        List<DatasourceIngestion> dsIngestions = dsIngestionRepos.findAll();
        for (DatasourceIngestion dsi : dsIngestions) {
            System.out.print(dsi.getStackTrace());
            Assert.assertEquals(IngestionStatus.FINISHED, dsi.getStatus());
            Assert.assertEquals(new Integer(0), dsi.getSavedObjectsCount());
            Assert.assertNotNull("Datasource ingest last ingest date should not be null", dsi.getLastIngestDate());
        }

        // Add a ExternalData
        final LocalDate today = LocalDate.now();
        final ExternalData data1_0 = new ExternalData(today);
        extData1Repos.save(data1_0);

        // ExternalData is from a datasource that has a refresh rate of 1 s
        Thread.sleep(1_000);

        ingesterService.manage();
        dsIngestions = dsIngestionRepos.findAll();
        // ExternalData has a Date not a DateTime so its creation date will be available tomorrow, not today
        Assert.assertTrue(dsIngestions.stream().allMatch(dsIngest -> dsIngest.getSavedObjectsCount() == 0));

        final OffsetDateTime now = OffsetDateTime.now();
        final ExternalData2 data2_0 = new ExternalData2(now);
        extData2Repos.save(data2_0);
        final ExternalData3 data3_0 = new ExternalData3(now);
        extData3Repos.save(data3_0);

        // ExternalData2 is from a datasource that has a refresh rate of 1 s
        // ExternalData3 is from a datasource that has a refresh rate of 10 s (so does AipDataSourcePlugin)
        Thread.sleep(1_000);
        ingesterService.manage();
        dsIngestions = dsIngestionRepos.findAll();
        // because of refresh rates, only ExternalData2 datasource must be ingested, we should wait 9 more
        // seconds for ExternalData3 one
        Assert.assertEquals(1, dsIngestions.stream().filter(dsIngest -> dsIngest.getSavedObjectsCount() == 1).count());

        Thread.sleep(9_000);
        ingesterService.manage();
        dsIngestions = dsIngestionRepos.findAll();
        // because of refresh rates, only ExternalData2 datasource must be ingested, we should wait at least 9 more
        // seconds for ExternalData3 one
        Assert.assertEquals(1, dsIngestions.stream().filter(dsIngest -> dsIngest.getSavedObjectsCount() == 1).count());

        Thread.sleep(10_000);
        ingesterService.manage();
        dsIngestions = dsIngestionRepos.findAll();
        Assert.assertTrue(dsIngestions.stream().allMatch(dsIngest -> dsIngest.getStatus() == IngestionStatus.FINISHED));
        Assert.assertTrue(dsIngestions.stream().allMatch(dsIngest -> dsIngest.getSavedObjectsCount() == 0));
        Assert.assertTrue(dsIngestions.stream().allMatch(dsIngest -> dsIngest.getLastIngestDate() != null));
    }
}
