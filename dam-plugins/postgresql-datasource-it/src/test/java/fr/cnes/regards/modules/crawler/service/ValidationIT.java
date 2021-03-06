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
package fr.cnes.regards.modules.crawler.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.dao.IPluginConfigurationRepository;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.crawler.test.ValidationConfiguration;
import fr.cnes.regards.modules.dam.dao.entities.IAbstractEntityRepository;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.feature.EntityFeature;
import fr.cnes.regards.modules.indexer.dao.IEsRepository;
import fr.cnes.regards.modules.model.dao.IAttributeModelRepository;
import fr.cnes.regards.modules.model.dao.IFragmentRepository;
import fr.cnes.regards.modules.model.dao.IModelAttrAssocRepository;
import fr.cnes.regards.modules.model.dao.IModelRepository;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import fr.cnes.regards.modules.model.gson.MultitenantFlattenedAttributeAdapterFactory;
import fr.cnes.regards.modules.model.gson.MultitenantFlattenedAttributeAdapterFactoryEventHandler;
import fr.cnes.regards.modules.model.service.IAttributeModelService;
import fr.cnes.regards.modules.model.service.IModelService;

/**
 * Pseudo IT test used for initialisation of Validation
 *
 * @author oroussel
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { ValidationConfiguration.class })
@ActiveProfiles("noschedule") // Disable scheduling, this will activate IngesterService during all tests
@Ignore
public class ValidationIT {

    @SuppressWarnings("unused")
    private final static Logger LOGGER = LoggerFactory.getLogger(ValidationIT.class);

    @Autowired
    private MultitenantFlattenedAttributeAdapterFactory gsonAttributeFactory;

    @Autowired
    private MultitenantFlattenedAttributeAdapterFactoryEventHandler gsonAttributeFactoryHandler;

    @Value("${regards.tenant}")
    private String tenant;

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
    private IAttributeModelService attributeModelService;

    @Autowired
    private IModelRepository modelRepository;

    @Autowired
    private IAttributeModelRepository attrModelRepo;

    @Autowired
    private IModelAttrAssocRepository modelAttrAssocRepo;

    @Autowired
    private IFragmentRepository fragRepo;

    @Autowired
    private ICrawlerAndIngesterService datasetCrawlerService;

    @Autowired
    private IDatasetCrawlerService crawlerService;

    @Autowired
    private IAbstractEntityRepository<AbstractEntity<EntityFeature>> entityRepos;

    @Autowired
    private IEsRepository esRepos;

    @Autowired
    private IRuntimeTenantResolver tenantResolver;

    @Autowired
    private IPluginConfigurationRepository pluginConfRepo;

    // @Autowired
    // private IRabbitVirtualHostAdmin rabbitVhostAdmin;
    //
    // @Autowired
    // private RegardsAmqpAdmin amqpAdmin;
    //
    // private Model dataModel;
    //
    // private Model datasetModel;

    // private Dataset dataset1;
    //
    // private Dataset dataset2;
    //
    // private Dataset dataset3;

    @Before
    public void setUp() throws Exception {

        // Simulate spring boot ApplicationStarted event to start mapping for each tenants.
        gsonAttributeFactoryHandler.onApplicationEvent(null);

        tenantResolver.forceTenant(tenant);
        if (esRepos.indexExists(tenant)) {
            esRepos.deleteAll(tenant);
        } else {
            esRepos.createIndex(tenant);
        }

        crawlerService.setConsumeOnlyMode(true);
        datasetCrawlerService.setConsumeOnlyMode(true);

        // rabbitVhostAdmin.bind(tenantResolver.getTenant());
        // amqpAdmin.purgeQueue(AbstractEntityEvent.class, false);
        // rabbitVhostAdmin.unbind();

        entityRepos.deleteAll();
        modelAttrAssocRepo.deleteAll();
        pluginConfRepo.deleteAll();
        attrModelRepo.deleteAll();
        modelRepository.deleteAll();
        fragRepo.deleteAll();
        // pluginService.addPluginPackage("fr.cnes.regards.modules.dam.plugins.datasources");

        // Connection PluginConf
        // dBConnectionConf = getPostgresConnectionConfiguration();
        // pluginService.savePluginConfiguration(dBConnectionConf);

        // DefaultPostgreConnectionPlugin dbCtx = pluginService.getPlugin(dBConnectionConf);

        // DataSource PluginConf
        // dataSourcePluginConf = getPostgresDataSource(dBConnectionConf);
        // pluginService.savePluginConfiguration(dataSourcePluginConf);
    }

    // @After
    // public void clean() {
    // entityRepos.deleteAll();
    // modelAttrAssocRepo.deleteAll();
    // pluginConfRepo.deleteAll();
    // attrModelRepo.deleteAll();
    // modelRepository.deleteAll();
    // fragRepo.deleteAll();
    // }

    /**
    private PluginConfiguration getPostgresDataSource(final PluginConfiguration pluginConf) {
        final Set<PluginParameter> parameters = PluginParametersFactory.build()
                .addPluginConfiguration(DataSourcePluginConstants.CONNECTION_PARAM, pluginConf)
                .addParameter(DataSourcePluginConstants.TABLE_PARAM, T_VIEW)
                .addParameter(DataSourcePluginConstants.REFRESH_RATE, "1")
                .addParameter(DataSourcePluginConstants.MODEL_NAME_PARAM, "MODEL_VALIDATION_1")
                .addParameter(DataSourcePluginConstants.MODEL_MAPPING_PARAM, modelAttrMapping).getParameters();

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
    */

    // private void buildModelAttributes() {
    // List<AbstractAttributeMapping> attributes = new ArrayList<AbstractAttributeMapping>();
    //
    // attributes.add(new StaticAttributeMapping(AttributeType.INTEGER, "DATA_OBJECTS_ID",
    // AbstractAttributeMapping.PRIMARY_KEY));
    //
    // attributes.add(new DynamicAttributeMapping("FILE_SIZE", AttributeType.INTEGER, "FILE_SIZE"));
    // attributes.add(new DynamicAttributeMapping("FILE_TYPE", AttributeType.STRING, "FILE_TYPE"));
    // attributes.add(new DynamicAttributeMapping("FILE_NAME_ORIGINE", AttributeType.STRING, "FILE_NAME_ORIGINE"));
    //
    // attributes.add(new DynamicAttributeMapping("DATA_SET_ID", AttributeType.INTEGER, "DATA_SET_ID"));
    // attributes.add(new DynamicAttributeMapping("DATA_TITLE", AttributeType.STRING, "DATA_TITLE"));
    // attributes.add(new DynamicAttributeMapping("DATA_AUTHOR", AttributeType.STRING, "DATA_AUTHOR"));
    // attributes.add(new DynamicAttributeMapping("DATA_AUTHOR_COMPANY", AttributeType.STRING, "DATA_AUTHOR_COMPANY"));
    //
    // attributes.add(new DynamicAttributeMapping("START_DATE", AttributeType.DATE_ISO8601, "START_DATE",
    // Types.DECIMAL));
    // attributes
    // .add(new DynamicAttributeMapping("STOP_DATE", AttributeType.DATE_ISO8601, "STOP_DATE", Types.DECIMAL));
    // attributes.add(new DynamicAttributeMapping("DATA_CREATION_DATE", AttributeType.DATE_ISO8601,
    // "DATA_CREATION_DATE", Types.DECIMAL));
    //
    // attributes.add(new DynamicAttributeMapping("MIN", "LONGITUDE", AttributeType.INTEGER, "MIN_LONGITUDE"));
    // attributes.add(new DynamicAttributeMapping("MAX", "LONGITUDE", AttributeType.INTEGER, "MAX_LONGITUDE"));
    // attributes.add(new DynamicAttributeMapping("MIN", "LATITUDE", AttributeType.INTEGER, "MIN_LATITUDE"));
    // attributes.add(new DynamicAttributeMapping("MAX", "LATITUDE", AttributeType.INTEGER, "MAX_LATITUDE"));
    // attributes.add(new DynamicAttributeMapping("MIN", "ALTITUDE", AttributeType.INTEGER, "MIN_ALTITUDE"));
    // attributes.add(new DynamicAttributeMapping("MAX", "ALTITUDE", AttributeType.INTEGER, "MAX_ALTITUDE"));
    // attributes.add(new DynamicAttributeMapping("ANSA5_REAL", AttributeType.DOUBLE, "ANSA5_REAL"));
    // attributes.add(new DynamicAttributeMapping("ANSR5_REAL", AttributeType.DOUBLE, "ANSR5_REAL"));
    // attributes.add(new DynamicAttributeMapping("ANSE5_REAL", AttributeType.DOUBLE, "ANSE5_REAL"));
    // attributes.add(new DynamicAttributeMapping("ANSE6_STRING", AttributeType.STRING, "ANSE6_STRING"));
    // attributes.add(new DynamicAttributeMapping("ANSL6_2_STRING", AttributeType.STRING, "ANSL6_2_STRING"));
    // attributes.add(new DynamicAttributeMapping("ANSR3_INT", "frag3", AttributeType.INTEGER, "ANSR3_INT"));
    // attributes.add(new DynamicAttributeMapping("ANSL3_1_INT", "frag3", AttributeType.INTEGER, "ANSL3_1_INT"));
    // attributes.add(new DynamicAttributeMapping("ANSL3_2_INT", "frag3", AttributeType.INTEGER, "ANSL3_2_INT"));
    //
    // attributes
    // .add(new StaticAttributeMapping(AttributeType.STRING, "ANSA7_URL", AbstractAttributeMapping.THUMBNAIL));
    // attributes
    // .add(new StaticAttributeMapping(AttributeType.STRING, "ANSE7_URL", AbstractAttributeMapping.RAW_DATA));
    //
    // dataSourceModelMapping = new DataSourceModelMapping(dataModel.getId(), attributes);
    // }

    /**
     *
     * @throws IOException
     * @throws ModuleException
     */
    @Test
    public void validationInserts() throws IOException, ModuleException {
        this.initPluginConfForValidation();
        InputStream input = Files.newInputStream(Paths.get("src", "test", "resources", "validation", "models",
                                                           "validationCollectionModel1.xml"));
        modelService.importModel(input);

        input = Files.newInputStream(Paths.get("src", "test", "resources", "validation", "models",
                                               "validationDatasetModel1.xml"));
        modelService.importModel(input);

        input = Files.newInputStream(Paths.get("src", "test", "resources", "validation", "models",
                                               "validationDataModel1.xml"));
        modelService.importModel(input);

        input = Files.newInputStream(Paths.get("src", "test", "resources", "validation", "models",
                                               "validationDatasetModel2.xml"));
        modelService.importModel(input);

        input = Files.newInputStream(Paths.get("src", "test", "resources", "validation", "models",
                                               "validationDataModel2.xml"));
        modelService.importModel(input);

        final List<AttributeModel> attributes = attributeModelService.getAttributes(null, null, null);
        gsonAttributeFactory.refresh(tenant, attributes);
    }

    private void initPluginConfForValidation() throws ModuleException {
        // pluginService.addPluginPackage(IComputedAttribute.class.getPackage().getName());
        // pluginService.addPluginPackage(CountPlugin.class.getPackage().getName());
        // // conf for "count"
        // final Set<PluginParameter> parameters = PluginParametersFactory.build()
        // .addParameter("resultAttributeName", "count").getParameters();
        //
        // // Emulate plugin annotation (user will create an annotation)
        // final PluginMetaData metadata = new PluginMetaData();
        // metadata.setPluginId("CountPlugin");
        // metadata.setAuthor("O. Rousselot");
        // metadata.setDescription("");
        // metadata.setVersion("1");
        // metadata.getInterfaceNames().add(IComputedAttribute.class.getName());
        // metadata.setPluginClassName(CountPlugin.class.getName());
        //
        // final PluginConfiguration confCount = new PluginConfiguration(metadata, "CountValidationConf");
        // confCount.setParameters(parameters);
        // pluginService.savePluginConfiguration(confCount);
        //
        // // create a pluginConfiguration with a label for start_date
        // final Set<PluginParameter> parametersMin = PluginParametersFactory.build()
        // .addParameter("resultAttributeName", "start_date").getParameters();
        // final PluginMetaData metadataMin = new PluginMetaData();
        // metadataMin.setPluginId("MinDateComputePlugin");
        // metadataMin.setAuthor("O. Rousselot");
        // metadataMin.setDescription("");
        // metadataMin.setVersion("1");
        // metadataMin.getInterfaceNames().add(IComputedAttribute.class.getName());
        // metadataMin.setPluginClassName(MinDateComputePlugin.class.getName());
        // final PluginConfiguration confMin = new PluginConfiguration(metadataMin, "MinDateValidationConf");
        // confMin.setParameters(parametersMin);
        // pluginService.savePluginConfiguration(confMin);
        //
        // // create a pluginConfiguration with a label for end_date
        // final Set<PluginParameter> parametersMax = PluginParametersFactory.build()
        // .addParameter("resultAttributeName", "end_date").getParameters();
        // final PluginMetaData metadataMax = new PluginMetaData();
        // metadataMax.setPluginId("MaxDateComputePlugin");
        // metadataMax.setAuthor("O. Rousselot");
        // metadataMax.setDescription("");
        // metadataMax.setVersion("1");
        // metadataMax.getInterfaceNames().add(IComputedAttribute.class.getName());
        // metadataMax.setPluginClassName(MaxDateComputePlugin.class.getName());
        // final PluginConfiguration confMax = new PluginConfiguration(metadataMax, "MaxDateValidationConf");
        // confMax.setParameters(parametersMax);
        // pluginService.savePluginConfiguration(confMax);
        //
        // // create a pluginConfiguration with a label for value_l1
        // final Set<PluginParameter> parametersInteger = PluginParametersFactory.build()
        // .addParameter("resultAttributeName", "values_l1_sum").getParameters();
        // final PluginMetaData metadataLong = new PluginMetaData();
        // metadataLong.setPluginId("LongSumComputePlugin");
        // metadataLong.setAuthor("O. Rousselot");
        // metadataLong.setDescription("");
        // metadataLong.setVersion("1");
        // metadataLong.getInterfaceNames().add(IComputedAttribute.class.getName());
        // metadataLong.setPluginClassName(LongSumComputePlugin.class.getName());
        // final PluginConfiguration confLong = new PluginConfiguration(metadataLong, "SumLongValidationConf");
        // confLong.setParameters(parametersInteger);
        // pluginService.savePluginConfiguration(confLong);
    }
}
