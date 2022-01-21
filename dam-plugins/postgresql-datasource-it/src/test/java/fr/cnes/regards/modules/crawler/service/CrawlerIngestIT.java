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
package fr.cnes.regards.modules.crawler.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import fr.cnes.regards.modules.indexer.domain.criterion.StringMatchType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.google.common.collect.Sets;

import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.dao.IPluginConfigurationRepository;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.StringPluginParam;
import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.test.integration.AbstractRegardsIT;
import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.framework.test.report.annotation.Requirement;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.framework.utils.plugins.PluginParameterTransformer;
import fr.cnes.regards.modules.crawler.dao.IDatasourceIngestionRepository;
import fr.cnes.regards.modules.crawler.domain.DatasourceIngestion;
import fr.cnes.regards.modules.crawler.domain.IngestionResult;
import fr.cnes.regards.modules.crawler.service.conf.CrawlerPropertiesConfiguration;
import fr.cnes.regards.modules.crawler.service.ds.ExternalData;
import fr.cnes.regards.modules.crawler.service.ds.ExternalDataRepository;
import fr.cnes.regards.modules.crawler.service.ds.plugin.TestDsPlugin;
import fr.cnes.regards.modules.crawler.service.exception.NotFinishedException;
import fr.cnes.regards.modules.dam.dao.entities.IAbstractEntityRepository;
import fr.cnes.regards.modules.dam.dao.entities.IDatasetRepository;
import fr.cnes.regards.modules.dam.domain.datasources.AbstractAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.StaticAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DBConnectionPluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.Dataset;
import fr.cnes.regards.modules.dam.domain.entities.event.DatasetEvent;
import fr.cnes.regards.modules.dam.domain.entities.event.NotDatasetEntityEvent;
import fr.cnes.regards.modules.dam.domain.entities.feature.EntityFeature;
import fr.cnes.regards.modules.dam.plugins.datasources.DefaultPostgreConnectionPlugin;
import fr.cnes.regards.modules.dam.plugins.datasources.PostgreDataSourceFromSingleTablePlugin;
import fr.cnes.regards.modules.dam.service.entities.IDatasetService;
import fr.cnes.regards.modules.indexer.dao.EsRepository;
import fr.cnes.regards.modules.indexer.domain.SimpleSearchKey;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.service.ISearchService;
import fr.cnes.regards.modules.indexer.service.Searches;
import fr.cnes.regards.modules.model.dao.IModelAttrAssocRepository;
import fr.cnes.regards.modules.model.dao.IModelRepository;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.domain.ModelAttrAssoc;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import fr.cnes.regards.modules.model.domain.attributes.Fragment;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import fr.cnes.regards.modules.model.gson.MultitenantFlattenedAttributeAdapterFactoryEventHandler;
import fr.cnes.regards.modules.model.service.IModelAttrAssocService;
import fr.cnes.regards.modules.model.service.IModelService;

/**
 * Crawler ingestion tests
 */
@ActiveProfiles({ "noschedule", "CrawlerTest", "test", "testAmqp" }) // Disable scheduling, this will activate IngesterService during all tests
@TestPropertySource(properties = { "spring.jpa.properties.hibernate.default_schema=public" })
public class CrawlerIngestIT extends AbstractRegardsIT {

    private static Logger LOGGER = LoggerFactory.getLogger(CrawlerIngestIT.class);

    @Autowired
    private MultitenantFlattenedAttributeAdapterFactoryEventHandler gsonAttributeFactoryHandler;

    private static final String TABLE_NAME_TEST = "t_data";

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
    private IModelService modelService;

    @Autowired
    private IModelRepository modelRepository;

    @Autowired
    private IDatasetService dsService;

    @Autowired
    private ISearchService searchService;

    @Autowired
    private ICrawlerAndIngesterService crawlerService;

    @Autowired
    private IngesterService ingesterService;

    @Autowired
    private IAbstractEntityRepository<AbstractEntity<EntityFeature>> entityRepos;

    @Autowired
    private IDatasetRepository datasetRepos;

    @Autowired
    private IRuntimeTenantResolver tenantResolver;

    private List<AbstractAttributeMapping> modelAttrMapping;

    @Autowired
    private IPluginService pluginService;

    @Autowired
    private IPluginConfigurationRepository pluginConfRepos;

    @Autowired
    private CrawlerPropertiesConfiguration crawlerConf;

    @Autowired
    private IPublisher publisher;

    @Autowired
    private EsRepository esRepos;

    private Model dataModel;

    private Model datasetModel;

    private PluginConfiguration dataSourcePluginConf;

    private Dataset dataset;

    private PluginConfiguration dBConnectionConf;

    private PluginConfiguration dataSourceTestPluginConf;

    @Autowired
    private ExternalDataRepository extDataRepos;

    @Autowired
    private IModelAttrAssocRepository attrAssocRepos;

    @Autowired
    private IDatasourceIngestionRepository dsiRepos;

    @Autowired
    private IModelAttrAssocService modelAttrAssocService;

    @Before
    public void setUp() throws Exception {
        LOGGER.info("********************* setUp CrawlerIngestIT ***********************************");
        // Simulate spring boot ApplicationStarted event to start mapping for each tenants.
        gsonAttributeFactoryHandler.onApplicationEvent(Mockito.mock(ApplicationStartedEvent.class));

        tenantResolver.forceTenant(getDefaultTenant());

        if (esRepos.indexExists(getDefaultTenant())) {
            esRepos.deleteAll(getDefaultTenant());
        } else {
            esRepos.createIndex(getDefaultTenant());
        }

        crawlerService.setConsumeOnlyMode(false);
        ingesterService.setConsumeOnlyMode(true);

        publisher.purgeQueue(DatasetEvent.class);
        publisher.purgeQueue(NotDatasetEntityEvent.class);

        tenantResolver.forceTenant(getDefaultTenant());

        attrAssocRepos.deleteAll();
        datasetRepos.deleteAll();
        entityRepos.deleteAll();
        pluginConfRepos.deleteAll();
        modelRepository.deleteAll();
        extDataRepos.deleteAll();

        // Register model attributes
        dataModel = new Model();
        dataModel.setName("model_1" + System.currentTimeMillis());
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
        dataSourcePluginConf = getPostgresDataSource(dBConnectionConf);
        pluginService.savePluginConfiguration(dataSourcePluginConf);

        // DataSource from Test Plugin Conf
        dataSourceTestPluginConf = this.getTestDsPluginDatasource();
        pluginService.savePluginConfiguration(dataSourceTestPluginConf);
        LOGGER.info("***************************************************************************");
    }

    @After
    public void clean() {
        LOGGER.info("********************* clean CrawlerIngestIT ***********************************");
        tenantResolver.forceTenant(getDefaultTenant());
        attrAssocRepos.deleteAll();
        datasetRepos.deleteAll();
        entityRepos.deleteAll();
        pluginConfRepos.deleteAll();
        modelRepository.deleteAll();
        extDataRepos.deleteAll();
        LOGGER.info("***************************************************************************");
    }

    private PluginConfiguration getPostgresDataSource(final PluginConfiguration pluginConf) {
        Set<IPluginParam> parameters = IPluginParam
                .set(IPluginParam.plugin(DataSourcePluginConstants.CONNECTION_PARAM, pluginConf.getBusinessId()),
                     IPluginParam.build(DataSourcePluginConstants.TABLE_PARAM, TABLE_NAME_TEST),
                     IPluginParam.build(DataSourcePluginConstants.REFRESH_RATE, 1800),
                     IPluginParam.build(DataSourcePluginConstants.MODEL_NAME_PARAM, dataModel.getName()),
                     IPluginParam.build(DataSourcePluginConstants.MODEL_MAPPING_PARAM,
                                        PluginParameterTransformer.toJson(modelAttrMapping)));
        return PluginConfiguration.build(PostgreDataSourceFromSingleTablePlugin.class, null, parameters);
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

        return PluginConfiguration.build(DefaultPostgreConnectionPlugin.class, null, parameters);
    }

    private PluginConfiguration getTestDsPluginDatasource() {
        Set<IPluginParam> parameters = IPluginParam
                .set(IPluginParam.build(DataSourcePluginConstants.MODEL_NAME_PARAM, dataModel.getName()));
        return PluginConfiguration.build(TestDsPlugin.class, null, parameters);
    }

    private void buildModelAttributes() {
        modelAttrMapping = new ArrayList<>();

        modelAttrMapping.add(new StaticAttributeMapping(AbstractAttributeMapping.PRIMARY_KEY, "id"));

        modelAttrMapping.add(new StaticAttributeMapping(AbstractAttributeMapping.LAST_UPDATE, PropertyType.DATE_ISO8601,
                "date"));
    }

    @Requirement("REGARDS_DSL_DAM_CAT_310")
    @Purpose("Le système doit permettre d’ajouter un AIP de données dans un jeu de données à partir de son IP_ID "
            + "(ajout d'un tag sur l'AIP de données).")
    @Test
    @Ignore("Don't reactivate this test, it is nearly impossible de manage a multi-thread tests with all this mess")
    public void test() throws ModuleException, IOException, InterruptedException, ExecutionException,
            DataSourceException, NotFinishedException {
        LOGGER.info("********************* test CrawlerIngestIT ***********************************");
        final String tenant = tenantResolver.getTenant();
        // First delete index if it already exists
        // indexerService.deleteIndex(tenant);

        // Fill the DB with an object from 2000/01/01
        extDataRepos.saveAndFlush(new ExternalData(LocalDate.of(2000, Month.JANUARY, 1)));

        // Ingest from scratch
        DatasourceIngestion dsi = new DatasourceIngestion(dataSourcePluginConf.getBusinessId());
        IngestionResult summary = crawlerService.ingest(dsi.getId()).orElseThrow(()-> new RuntimeException("There was some issue while ingesting test datasource"));
        Assert.assertEquals(1, summary.getSavedObjectsCount());

        crawlerService.startWork();
        // Dataset on all objects
        dataset = new Dataset(datasetModel, tenant, "DS1", "dataset label 1");
        dataset.setDataModel(dataModel.getName());
        dataset.setSubsettingClause(ICriterion.all());
        dataset.setLicence("licence");
        dataset.setDataSource(dataSourcePluginConf);
        dataset.setTags(Sets.newHashSet("empty_tag"));
        dataset.setGroups(Sets.newHashSet("group0", "group11"));
        LOGGER.info("Creating dataset....");
        dsService.create(dataset);
        LOGGER.info("Dataset created in DB....");

        LOGGER.info("Waiting for end of crawler work");
        crawlerService.waitForEndOfWork();
        LOGGER.info("Sleeping 10 s....");
        Thread.sleep(10_000);
        LOGGER.info("...Waking");

        // Retrieve dataset1 from ES
        final UniformResourceName ipId = dataset.getIpId();
        dataset = searchService.get(ipId);
        if (dataset == null) {
            Thread.sleep(10_000L);
            esRepos.refresh(tenant);
            dataset = searchService.get(ipId);
        }

        final SimpleSearchKey<DataObject> objectSearchKey = Searches.onSingleEntity(EntityType.DATA);
        // Search for DataObjects tagging dataset1
        LOGGER.info("searchService : " + searchService);
        LOGGER.info("dataset : " + dataset);
        LOGGER.info("dataset.getIpId() : " + dataset.getIpId());

        Page<DataObject> objectsPage = searchService.search(objectSearchKey, crawlerConf.getMaxBulkSize(),
                                                            ICriterion.eq("tags", dataset.getIpId().toString(), StringMatchType.KEYWORD));
        Assert.assertEquals(1L, objectsPage.getTotalElements());

        // Fill the Db with an object dated 2001/01/01
        extDataRepos.save(new ExternalData(LocalDate.of(2001, Month.JANUARY, 1)));

        // Ingest from 2000/01/01 (strictly after)
        DatasourceIngestion dsi2 = new DatasourceIngestion(dataSourcePluginConf.getBusinessId());
        dsi.setLastIngestDate(OffsetDateTime.of(2000, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC));
        summary = crawlerService.ingest(dsi2.getId()).orElseThrow(()-> new RuntimeException("There was some issues while ingesting dsi2"));
        Assert.assertEquals(1, summary.getSavedObjectsCount());

        // Search for DataObjects tagging dataset1
        objectsPage = searchService.search(objectSearchKey, crawlerConf.getMaxBulkSize(),
                                           ICriterion.eq("tags", dataset.getIpId().toString(), StringMatchType.KEYWORD));
        Assert.assertEquals(2L, objectsPage.getTotalElements());
        Assert.assertEquals(1, objectsPage.getContent().stream()
                .filter(data -> data.getLastUpdate().equals(data.getCreationDate())).count());
        Assert.assertEquals(1, objectsPage.getContent().stream()
                .filter(data -> data.getLastUpdate().isAfter(data.getCreationDate())).count());
        LOGGER.info("***************************************************************************");
    }

    public static Model model = new Model();

    @Ignore
    @Test
    public void testDsIngestionWithValidation() throws InterruptedException, ExecutionException, DataSourceException,
            ModuleException, NotFinishedException {
        DatasourceIngestion dsi = new DatasourceIngestion(dataSourceTestPluginConf.getBusinessId());
        dsiRepos.save(dsi);
        // First ingestion with a "nude" model
        try {
            crawlerService.ingest(dsi.getId()).orElseThrow(()-> new RuntimeException("There was some issues while ingesting dsi"));
            Assert.fail("Test should have failed on \"Model identifier must be specified.\"");
        } catch (ExecutionException ee) {
            Assert.assertTrue(ee.getCause() instanceof IllegalArgumentException);
            Assert.assertEquals("Model identifier must be specified.", ee.getCause().getMessage());
        }

        model.setId(15000L);
        List<ModelAttrAssoc> modelAttrAssocs = new ArrayList<>();
        AttributeModel attTutuToto = new AttributeModel();
        Fragment fragmentTutu = new Fragment();
        fragmentTutu.setName("tutu");
        attTutuToto.setFragment(fragmentTutu);
        attTutuToto.setName("toto");
        attTutuToto.setType(PropertyType.STRING);
        attTutuToto.setOptional(false);
        ModelAttrAssoc attrAssocTutuToto = new ModelAttrAssoc(attTutuToto, model);
        modelAttrAssocs.add(attrAssocTutuToto);
        Mockito.when(modelAttrAssocService.getModelAttrAssocs(Mockito.anyString())).thenReturn(modelAttrAssocs);
        IngestionResult summary = crawlerService.ingest(dsi.getId()).orElseThrow(()-> new RuntimeException("There was some issues while ingesting dsi"));
        // 2 validation errors so nothing saved
        Assert.assertEquals(0, summary.getSavedObjectsCount());
    }
}
