/*
 * Copyright 2017-2018 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import fr.cnes.regards.framework.geojson.geometry.Polygon;
import fr.cnes.regards.framework.modules.plugins.dao.IPluginConfigurationRepository;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.PluginParameter;
import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.oais.urn.EntityType;
import fr.cnes.regards.framework.utils.plugins.PluginParametersFactory;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.modules.crawler.dao.IDatasourceIngestionRepository;
import fr.cnes.regards.modules.crawler.domain.DatasourceIngestion;
import fr.cnes.regards.modules.crawler.domain.IngestionStatus;
import fr.cnes.regards.modules.dam.dao.entities.IAbstractEntityRepository;
import fr.cnes.regards.modules.dam.dao.entities.IDatasetRepository;
import fr.cnes.regards.modules.dam.dao.models.IModelRepository;
import fr.cnes.regards.modules.dam.domain.datasources.AbstractAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.StaticAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DBConnectionPluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.feature.EntityFeature;
import fr.cnes.regards.modules.dam.domain.models.Model;
import fr.cnes.regards.modules.dam.domain.models.attributes.AttributeType;
import fr.cnes.regards.modules.dam.gson.entities.MultitenantFlattenedAttributeAdapterFactoryEventHandler;
import fr.cnes.regards.modules.dam.plugins.datasources.DefaultPostgreConnectionPlugin;
import fr.cnes.regards.modules.dam.plugins.datasources.PostgreDataSourceFromSingleTablePlugin;
import fr.cnes.regards.modules.dam.service.models.IModelService;
import fr.cnes.regards.modules.indexer.dao.IEsRepository;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.service.Searches;

@Ignore
@ActiveProfiles({ "noschedule", "IngesterGeometryTest", "test" }) // Disable scheduling, this will activate IngesterService during all tests
@TestPropertySource(properties = { "spring.jpa.properties.hibernate.default_schema=projectdb" })
public class IngesterGeometryServiceIT {

    private static final String TENANT = "INGEST_GEO";

    @Autowired
    private MultitenantFlattenedAttributeAdapterFactoryEventHandler gsonAttributeFactoryHandler;

    private static final String T_VIEW = "spire_photo_l2_geo";

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

    private PluginConfiguration dataSourcePluginConf;

    private PluginConfiguration dBConnectionConf;

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

    private PluginConfiguration getPostgresDataSource1(final PluginConfiguration pluginConf) {
        final Set<PluginParameter> parameters = PluginParametersFactory.build()
                .addPluginConfiguration(DataSourcePluginConstants.CONNECTION_PARAM, pluginConf)
                .addParameter(DataSourcePluginConstants.TABLE_PARAM, T_VIEW)
                .addParameter(DataSourcePluginConstants.REFRESH_RATE, 1)
                .addParameter(DataSourcePluginConstants.MODEL_MAPPING_PARAM, modelAttrMapping)
                .addParameter(DataSourcePluginConstants.MODEL_NAME_PARAM, dataModel.getName()).getParameters();

        return PluginUtils.getPluginConfiguration(parameters, PostgreDataSourceFromSingleTablePlugin.class);
    }

    private PluginConfiguration getPostgresConnectionConfiguration() {
        final Set<PluginParameter> parameters = PluginParametersFactory.build()
                .addParameter(DBConnectionPluginConstants.USER_PARAM, dbUser)
                .addParameter(DBConnectionPluginConstants.PASSWORD_PARAM, dbPpassword)
                .addParameter(DBConnectionPluginConstants.DB_HOST_PARAM, dbHost)
                .addParameter(DBConnectionPluginConstants.DB_PORT_PARAM, dbPort)
                .addParameter(DBConnectionPluginConstants.DB_NAME_PARAM, dbName).getParameters();

        return PluginUtils.getPluginConfiguration(parameters, DefaultPostgreConnectionPlugin.class);
    }

    private void buildModelAttributes() {
        modelAttrMapping = new ArrayList<>();
        modelAttrMapping.add(new StaticAttributeMapping(AbstractAttributeMapping.PRIMARY_KEY, AttributeType.INTEGER,
                "line_id"));
        modelAttrMapping.add(new StaticAttributeMapping(AbstractAttributeMapping.GEOMETRY, AttributeType.STRING,
                "polygon_geojson"));
    }

    @Before
    public void setUp() throws Exception {

        // Simulate spring boot ApplicationStarted event to start mapping for each tenants.
        gsonAttributeFactoryHandler.onApplicationEvent(null);

        tenantResolver.forceTenant(TENANT);

        if (esRepository.indexExists(TENANT)) {
            esRepository.deleteAll(TENANT);
        } else {
            esRepository.createIndex(TENANT);
        }
        crawlerService.setConsumeOnlyMode(true);
        datasetCrawlerService.setConsumeOnlyMode(true);
        ingesterService.setConsumeOnlyMode(true);

        dsIngestionRepos.deleteAll();

        datasetRepos.deleteAll();
        entityRepos.deleteAll();
        modelRepository.deleteAll();

        pluginConfRepos.deleteAll();

        dataModel = new Model();
        dataModel.setName("model_geom");
        dataModel.setType(EntityType.DATA);
        dataModel.setVersion("1");
        dataModel.setDescription("Test data object model");
        modelService.createModel(dataModel);

        datasetModel = new Model();
        datasetModel.setName("model_ds_geom");
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
        dataSourcePluginConf = getPostgresDataSource1(dBConnectionConf);
        pluginService.savePluginConfiguration(dataSourcePluginConf);

    }

    @After
    public void clean() {
        if (dataSourcePluginConf != null) {
            Utils.execute(pluginService::deletePluginConfiguration, dataSourcePluginConf.getId());
        }
        if (dBConnectionConf != null) {
            Utils.execute(pluginService::deletePluginConfiguration, dBConnectionConf.getId());
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
        // Ingestion of external data
        ingesterService.manage();

        final List<DatasourceIngestion> dsIngestions = dsIngestionRepos.findAll();
        Assert.assertTrue(dsIngestions.stream().allMatch(dsIngest -> dsIngest.getStatus() == IngestionStatus.FINISHED));
        Assert.assertTrue(dsIngestions.stream().allMatch(dsIngest -> dsIngest.getSavedObjectsCount() == 20_362));
        Assert.assertTrue(dsIngestions.stream().allMatch(dsIngest -> dsIngest.getLastIngestDate() != null));

        final Page<DataObject> page = esRepository.search(Searches.onSingleEntity(EntityType.DATA), 10,
                                                          ICriterion.all());
        final List<DataObject> objects = page.getContent();
        Assert.assertTrue(objects.stream().allMatch(o -> o.getGeometry() != null));
        Assert.assertTrue(objects.stream().allMatch(o -> o.getGeometry() instanceof Polygon));

    }
}
