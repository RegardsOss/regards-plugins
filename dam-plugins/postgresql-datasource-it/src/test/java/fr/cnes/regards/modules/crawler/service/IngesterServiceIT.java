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
package fr.cnes.regards.modules.crawler.service;

import com.google.gson.Gson;
import fr.cnes.regards.framework.modules.plugins.dao.IPluginConfigurationRepository;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.dto.parameter.parameter.IPluginParam;
import fr.cnes.regards.framework.modules.plugins.dto.parameter.parameter.StringPluginParam;
import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.test.integration.AbstractRegardsIT;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.utils.plugins.PluginParameterTransformer;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.modules.crawler.dao.IDatasourceIngestionRepository;
import fr.cnes.regards.modules.crawler.domain.DatasourceIngestion;
import fr.cnes.regards.modules.crawler.domain.IngestionStatus;
import fr.cnes.regards.modules.crawler.service.ds.*;
import fr.cnes.regards.modules.crawler.service.service.IngesterService;
import fr.cnes.regards.modules.dam.dao.entities.IAbstractEntityRepository;
import fr.cnes.regards.modules.dam.dao.entities.IDatasetRepository;
import fr.cnes.regards.modules.dam.domain.datasources.AbstractAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.StaticAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DBConnectionPluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.feature.EntityFeature;
import fr.cnes.regards.modules.dam.plugins.datasources.DefaultPostgreConnectionPlugin;
import fr.cnes.regards.modules.dam.plugins.datasources.PostgreDataSourceFromSingleTablePlugin;
import fr.cnes.regards.modules.indexer.dao.IEsRepository;
import fr.cnes.regards.modules.model.dao.IModelAttrAssocRepository;
import fr.cnes.regards.modules.model.dao.IModelRepository;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import fr.cnes.regards.modules.model.gson.MultitenantFlattenedAttributeAdapterFactoryEventHandler;
import fr.cnes.regards.modules.model.service.IModelService;
import fr.cnes.regards.modules.project.client.rest.IProjectsClient;
import fr.cnes.regards.modules.project.domain.Project;
import org.assertj.core.util.Lists;
import org.awaitility.Awaitility;
import org.junit.*;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@ActiveProfiles({ "noscheduler", "IngesterTest", "test" })
// Disable scheduling, this will activate IngesterService during all tests
@TestPropertySource(properties = { "spring.jpa.properties.hibernate.default_schema=projectdb" })
public class IngesterServiceIT extends AbstractRegardsIT {

    private static final String PLUGIN_LABEL_1 = "pluginConf1";

    private static final String PLUGIN_LABEL_2 = "pluginConf2";

    private static final String PLUGIN_LABEL_3 = "pluginConf3";

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
    private IngesterService ingesterService;

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
    private IProjectsClient projectsClient;

    @Autowired
    private Gson gson;

    private PluginConfiguration getPostgresDataSource1(final PluginConfiguration pluginConf) {
        Set<IPluginParam> parameters = IPluginParam.set(IPluginParam.plugin(DataSourcePluginConstants.CONNECTION_PARAM,
                                                                            pluginConf.getBusinessId()),
                                                        IPluginParam.build(DataSourcePluginConstants.TABLE_PARAM,
                                                                           T_DATA_1),
                                                        IPluginParam.build(DataSourcePluginConstants.REFRESH_RATE, 1),
                                                        IPluginParam.build(DataSourcePluginConstants.MODEL_NAME_PARAM,
                                                                           dataModel.getName()),
                                                        IPluginParam.build(DataSourcePluginConstants.MODEL_MAPPING_PARAM,
                                                                           PluginParameterTransformer.toJson(
                                                                               modelAttrMapping)));

        PluginConfiguration conf = PluginConfiguration.build(PostgreDataSourceFromSingleTablePlugin.class,
                                                             null,
                                                             parameters);
        conf.setLabel(PLUGIN_LABEL_1);
        conf.setBusinessId(PLUGIN_LABEL_1);
        return conf;
    }

    private PluginConfiguration getPostgresDataSource2(final PluginConfiguration pluginConf) {
        Set<IPluginParam> parameters = IPluginParam.set(IPluginParam.plugin(DataSourcePluginConstants.CONNECTION_PARAM,
                                                                            pluginConf.getBusinessId()),
                                                        IPluginParam.build(DataSourcePluginConstants.TABLE_PARAM,
                                                                           T_DATA_2),
                                                        IPluginParam.build(DataSourcePluginConstants.REFRESH_RATE, 1),
                                                        IPluginParam.build(DataSourcePluginConstants.MODEL_NAME_PARAM,
                                                                           dataModel.getName()),
                                                        IPluginParam.build(DataSourcePluginConstants.MODEL_MAPPING_PARAM,
                                                                           PluginParameterTransformer.toJson(
                                                                               modelAttrMapping)));

        PluginConfiguration conf = PluginConfiguration.build(PostgreDataSourceFromSingleTablePlugin.class,
                                                             null,
                                                             parameters);
        conf.setLabel(PLUGIN_LABEL_2);
        conf.setBusinessId(PLUGIN_LABEL_2);
        return conf;
    }

    private PluginConfiguration getPostgresDataSource3(final PluginConfiguration pluginConf) {
        Set<IPluginParam> parameters = IPluginParam.set(IPluginParam.plugin(DataSourcePluginConstants.CONNECTION_PARAM,
                                                                            pluginConf.getBusinessId()),
                                                        IPluginParam.build(DataSourcePluginConstants.TABLE_PARAM,
                                                                           T_DATA_3),
                                                        IPluginParam.build(DataSourcePluginConstants.REFRESH_RATE, 10),
                                                        IPluginParam.build(DataSourcePluginConstants.MODEL_NAME_PARAM,
                                                                           dataModel.getName()),
                                                        IPluginParam.build(DataSourcePluginConstants.MODEL_MAPPING_PARAM,
                                                                           PluginParameterTransformer.toJson(
                                                                               modelAttrMapping)));

        PluginConfiguration conf = PluginConfiguration.build(PostgreDataSourceFromSingleTablePlugin.class,
                                                             null,
                                                             parameters);
        conf.setLabel(PLUGIN_LABEL_3);
        conf.setBusinessId(PLUGIN_LABEL_3);
        return conf;
    }

    private PluginConfiguration getPostgresConnectionConfiguration() {
        Set<IPluginParam> parameters = IPluginParam.set(IPluginParam.build(DBConnectionPluginConstants.USER_PARAM,
                                                                           dbUser),
                                                        IPluginParam.build(DBConnectionPluginConstants.DB_HOST_PARAM,
                                                                           dbHost),
                                                        IPluginParam.build(DBConnectionPluginConstants.DB_PORT_PARAM,
                                                                           dbPort),
                                                        IPluginParam.build(DBConnectionPluginConstants.DB_NAME_PARAM,
                                                                           dbName));
        StringPluginParam passwordParam = IPluginParam.build(DBConnectionPluginConstants.PASSWORD_PARAM, dbPpassword);
        passwordParam.setValue(dbPpassword);
        parameters.add(passwordParam);

        return PluginConfiguration.build(DefaultPostgreConnectionPlugin.class, null, parameters);
    }

    private void buildModelAttributes() {
        modelAttrMapping = new ArrayList<>();
        modelAttrMapping.add(new StaticAttributeMapping(AbstractAttributeMapping.PRIMARY_KEY, PropertyType.LONG, "id"));
        modelAttrMapping.add(new StaticAttributeMapping(AbstractAttributeMapping.LAST_UPDATE,
                                                        PropertyType.DATE_ISO8601,
                                                        "date"));
    }

    @Before
    public void setUp() throws Exception {

        PluginUtils.setup(Lists.newArrayList(), gson);

        // Simulate spring boot ApplicationStarted event to start mapping for each tenants.
        gsonAttributeFactoryHandler.onApplicationEvent(null);

        tenantResolver.forceTenant(getDefaultTenant());

        if (esRepository.indexExists(getDefaultTenant())) {
            esRepository.deleteAll(getDefaultTenant());
        } else {
            esRepository.createIndex(getDefaultTenant());
        }

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

        final DefaultPostgreConnectionPlugin dbCtx = pluginService.getPlugin(dBConnectionConf.getBusinessId());
        Assume.assumeTrue(dbCtx.testConnection());

        // DataSource PluginConf
        dataSourcePluginConf1 = getPostgresDataSource1(dBConnectionConf);
        pluginService.savePluginConfiguration(dataSourcePluginConf1);

        dataSourcePluginConf2 = getPostgresDataSource2(dBConnectionConf);
        pluginService.savePluginConfiguration(dataSourcePluginConf2);

        dataSourcePluginConf3 = getPostgresDataSource3(dBConnectionConf);
        pluginService.savePluginConfiguration(dataSourcePluginConf3);

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
        Project project = new Project("Desc", "Icon", true, "Name");
        Mockito.when(projectsClient.retrieveProject(tenantResolver.getTenant()))
               .thenReturn(ResponseEntity.ok(EntityModel.of(project)));
        // GIVEN Initial Ingestion with no value from datasources
        // WHEN crawl
        ingesterService.manageCrawlingForAllTenants();
        waitForCrawlingTerminationAndReturnTimeElasped(10);

        // THEN
        List<DatasourceIngestion> dsIngestions = dsIngestionRepos.findAll();
        for (DatasourceIngestion dsi : dsIngestions) {
            System.out.print(dsi.getStackTrace());
            Assert.assertEquals(IngestionStatus.FINISHED, dsi.getStatus());
            Assert.assertEquals(Integer.valueOf(0), dsi.getSavedObjectsCount());
            Assert.assertNotNull("Datasource ingest last ingest date should not be null", dsi.getLastIngestDate());
        }

        // GIVEN Add a ExternalData
        final LocalDate today = LocalDate.now(ZoneOffset.UTC);
        final ExternalData data1_0 = new ExternalData(today);
        extData1Repos.save(data1_0);

        // ExternalData is from a datasource that has a refresh rate of 1 s
        Thread.sleep(1_000);

        // WHEN crawl
        ingesterService.manageCrawlingForAllTenants();
        waitForCrawlingTerminationAndReturnTimeElasped(10);
        // THEN
        dsIngestions = dsIngestionRepos.findAll();
        for (DatasourceIngestion dsIngestion : dsIngestions) {
            switch (dsIngestion.getLabel()) {
                case PLUGIN_LABEL_1 -> Assertions.assertEquals(1, dsIngestion.getSavedObjectsCount());
                case PLUGIN_LABEL_2 -> Assertions.assertEquals(0, dsIngestion.getSavedObjectsCount());
                case PLUGIN_LABEL_3 -> Assertions.assertEquals(0, dsIngestion.getSavedObjectsCount());
            }
        }

        // GIVEN add ExternalData1 and 2
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        final ExternalData2 data2_0 = new ExternalData2(now);
        extData2Repos.save(data2_0);
        final ExternalData3 data3_0 = new ExternalData3(now);
        extData3Repos.save(data3_0);

        // ExternalData2 is from a datasource that has a refresh rate of 1 s
        // ExternalData3 is from a datasource that has a refresh rate of 10 s (so does AipDataSourcePlugin)
        Thread.sleep(1_000);
        // WHEN crawl
        ingesterService.manageCrawlingForAllTenants();
        waitForCrawlingTerminationAndReturnTimeElasped(1000);
        // THEN
        dsIngestions = dsIngestionRepos.findAll();
        // because of refresh rates, only ExternalData2 datasource must be ingested, we should wait 9 more
        // seconds for ExternalData3 one
        for (DatasourceIngestion dsIngestion : dsIngestions) {
            switch (dsIngestion.getLabel()) {
                case PLUGIN_LABEL_1 -> Assertions.assertEquals(0, dsIngestion.getSavedObjectsCount());
                case PLUGIN_LABEL_2 -> Assertions.assertEquals(1, dsIngestion.getSavedObjectsCount());
                case PLUGIN_LABEL_3 -> Assertions.assertEquals(0, dsIngestion.getSavedObjectsCount());
            }
        }

        // GIVEN wait 9 sec more for crawler 3 to run
        Thread.sleep(9_000);
        // WHEN crawl
        ingesterService.manageCrawlingForAllTenants();
        waitForCrawlingTerminationAndReturnTimeElasped(10);
        // THEN
        dsIngestions = dsIngestionRepos.findAll();
        // because of refresh rates, only ExternalData2 datasource must be ingested, we should wait at least 9 more
        // seconds for ExternalData3 one
        for (DatasourceIngestion dsIngestion : dsIngestions) {
            switch (dsIngestion.getLabel()) {
                case PLUGIN_LABEL_1 -> Assertions.assertEquals(0, dsIngestion.getSavedObjectsCount());
                case PLUGIN_LABEL_2 -> Assertions.assertEquals(1, dsIngestion.getSavedObjectsCount());
                case PLUGIN_LABEL_3 -> Assertions.assertEquals(1, dsIngestion.getSavedObjectsCount());
            }
        }

        // GIVEN wait 10 sec more
        Thread.sleep(10_000);
        // WHEN crawl
        ingesterService.manageCrawlingForAllTenants();
        waitForCrawlingTerminationAndReturnTimeElasped(10);
        // THEN
        dsIngestions = dsIngestionRepos.findAll();
        Assert.assertTrue(dsIngestions.stream().allMatch(dsIngest -> dsIngest.getStatus() == IngestionStatus.FINISHED));
        for (DatasourceIngestion dsIngestion : dsIngestions) {
            switch (dsIngestion.getLabel()) {
                case PLUGIN_LABEL_1 -> Assertions.assertEquals(0, dsIngestion.getSavedObjectsCount());
                case PLUGIN_LABEL_2 -> Assertions.assertEquals(0, dsIngestion.getSavedObjectsCount());
                case PLUGIN_LABEL_3 -> Assertions.assertEquals(1, dsIngestion.getSavedObjectsCount());
            }
        }
        Assert.assertTrue(dsIngestions.stream().allMatch(dsIngest -> dsIngest.getLastIngestDate() != null));

        // GIVEN wait 10 sec more
        Thread.sleep(10_000);
        // WHEN crawl
        ingesterService.manageCrawlingForAllTenants();
        waitForCrawlingTerminationAndReturnTimeElasped(10);
        // THEN
        dsIngestions = dsIngestionRepos.findAll();
        Assert.assertTrue(dsIngestions.stream().allMatch(dsIngest -> dsIngest.getStatus() == IngestionStatus.FINISHED));
        for (DatasourceIngestion dsIngestion : dsIngestions) {
            switch (dsIngestion.getLabel()) {
                case PLUGIN_LABEL_1 -> Assertions.assertEquals(0, dsIngestion.getSavedObjectsCount());
                case PLUGIN_LABEL_2 -> Assertions.assertEquals(0, dsIngestion.getSavedObjectsCount());
                case PLUGIN_LABEL_3 -> Assertions.assertEquals(0, dsIngestion.getSavedObjectsCount());
            }
        }
    }

    private int waitForCrawlingTerminationAndReturnTimeElasped(int atMostSeconds) {
        // previous ingestion may not be started yet, so we wait a little bit
        long beforeAwait = System.currentTimeMillis();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Awaitility.await().atMost(atMostSeconds, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
            tenantResolver.forceTenant(getDefaultTenant());
            return dsIngestionRepos.findAll();
        }, dsList -> {
            if (!dsList.isEmpty()) {
                if (dsList.size() == 3) {
                    return dsList.stream().map(DatasourceIngestion::getStatus).allMatch(IngestionStatus::isFinal);
                } else {
                    // We expect 3 datasource ingestions
                    return false;
                }
            } else {
                return false;
            }
        });
        return (int) ((System.currentTimeMillis() - beforeAwait));
    }
}
