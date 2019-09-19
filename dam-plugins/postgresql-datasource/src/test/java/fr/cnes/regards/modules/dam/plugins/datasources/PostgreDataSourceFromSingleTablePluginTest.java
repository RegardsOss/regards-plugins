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
package fr.cnes.regards.modules.dam.plugins.datasources;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.assertj.core.util.Lists;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import com.google.gson.Gson;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.StringPluginParam;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.oais.urn.EntityType;
import fr.cnes.regards.framework.test.integration.AbstractRegardsIT;
import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.framework.test.report.annotation.Requirement;
import fr.cnes.regards.framework.utils.plugins.PluginParameterTransformer;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.dam.domain.datasources.AbstractAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.DynamicAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.StaticAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DBConnectionPluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDBDataSourceFromSingleTablePlugin;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.dam.domain.models.Model;
import fr.cnes.regards.modules.dam.domain.models.attributes.AttributeType;
import fr.cnes.regards.modules.dam.plugins.datasources.utils.DataSourceEntity;
import fr.cnes.regards.modules.dam.plugins.datasources.utils.IDataSourceRepositoryTest;
import fr.cnes.regards.modules.dam.service.models.IModelService;

/**
 * @author Christophe Mertz
 */
@TestPropertySource(locations = { "classpath:datasource-test.properties" },
        properties = { "spring.jpa.properties.hibernate.default_schema=public" })
public class PostgreDataSourceFromSingleTablePluginTest extends AbstractRegardsIT {

    private static final Logger LOG = LoggerFactory.getLogger(PostgreDataSourceFromSingleTablePluginTest.class);

    private static final String HELLO = "hello world from ";

    private static final String NAME_ATTR = "name";

    private static final String TABLE_NAME_TEST = "t_test_plugin_data_source";

    private static final String MODEL_NAME_TEST = "VALIDATION_MODEL_1";

    @Value("${postgresql.datasource.host}")
    private String dbHost;

    @Value("${postgresql.datasource.port}")
    private String dbPort;

    @Value("${postgresql.datasource.name}")
    private String dbName;

    @Value("${postgresql.datasource.username}")
    private String dbUser;

    @Value("${postgresql.datasource.password}")
    private String dbPassword;

    private IDBDataSourceFromSingleTablePlugin plgDBDataSource;

    private List<AbstractAttributeMapping> attributesMapping;

    private static int nbElements;

    private final Map<String, Object> pluginCacheMap = new HashMap<>();

    /**
     * JPA Repository
     */
    @Autowired
    private IDataSourceRepositoryTest repository;

    @Autowired
    private IModelService modelService;

    @Autowired
    private IRuntimeTenantResolver tenantResolver;

    @Autowired
    private Gson gson;

    /**
     * Populate the datasource as a legacy catalog
     *
     * @throws SQLException
     * @throws ModuleException
     * @throws MalformedURLException
     * @throws NotAvailablePluginConfigurationException
     */
    @Before
    public void setUp()
            throws SQLException, ModuleException, MalformedURLException, NotAvailablePluginConfigurationException {

        PluginUtils.setup(Lists.newArrayList(), gson);
        tenantResolver.forceTenant(getDefaultTenant());

        try {
            // Remove the model if existing
            modelService.getModelByName(MODEL_NAME_TEST);
            modelService.deleteModel(MODEL_NAME_TEST);
        } catch (ModuleException e) {
            // There is nothing to do - we create the model later
        }
        modelService.createModel(Model.build(MODEL_NAME_TEST, "", EntityType.DATA));

        /*
         * Add data to the data source
         */
        repository.deleteAll();
        repository.save(new DataSourceEntity("azertyuiop", 12345, 1.10203045607080901234568790123456789, 45.5444544454,
                LocalDate.now().minusDays(10), LocalTime.now().minusHours(9), LocalDateTime.now(),
                OffsetDateTime.now().minusMinutes(33), OffsetDateTime.now().minusDays(5).toString(), true,
                new URL("file", "localhost", ""), "one"));
        repository.save(new DataSourceEntity("Toulouse", 110, 3.141592653589793238462643383279, -15.2323654654564654,
                LocalDate.now().minusMonths(1), LocalTime.now().minusMinutes(10), LocalDateTime.now().plusHours(33),
                OffsetDateTime.now().minusSeconds(22), OffsetDateTime.now().minusMinutes(56565).toString(), true,
                new URL("http", "localhost", ""), "two"));
        repository.save(new DataSourceEntity("Paris", 350, -3.141592653589793238462643383279502884197169399375105,
                25.565465465454564654654654, LocalDate.now().minusDays(10), LocalTime.now().minusHours(9),
                LocalDateTime.now().minusMonths(2), OffsetDateTime.now().minusHours(7),
                OffsetDateTime.now().minusMinutes(12132125).toString(), false, new URL("ftp", "localhost", ""),
                "three"));
        nbElements = 3;

        /*
         * Initialize the AbstractAttributeMapping
         */
        buildAttributesMapping();

        /*
         * Instantiate the data source plugin
         */
        List<String> tags = new ArrayList<String>();
        tags.add("TOTO");
        tags.add("TITI");
        Set<IPluginParam> parameters = IPluginParam
                .set(IPluginParam.plugin(DataSourcePluginConstants.CONNECTION_PARAM,
                                         getPostgreConnectionConfiguration().getBusinessId()),
                     IPluginParam.build(DataSourcePluginConstants.TABLE_PARAM, TABLE_NAME_TEST),
                     IPluginParam.build(DataSourcePluginConstants.MODEL_NAME_PARAM, MODEL_NAME_TEST),
                     IPluginParam.build(DataSourcePluginConstants.MODEL_MAPPING_PARAM,
                                        PluginParameterTransformer.toJson(attributesMapping)),
                     IPluginParam.build(DataSourcePluginConstants.REFRESH_RATE, 1800),
                     IPluginParam.build(DataSourcePluginConstants.TAGS, PluginParameterTransformer.toJson(tags)));

        plgDBDataSource = PluginUtils.getPlugin(parameters, PostgreDataSourceFromSingleTablePlugin.class,
                                                pluginCacheMap);

        // Do not launch tests is Database is not available
        Assume.assumeTrue(plgDBDataSource.getDBConnection().testConnection());
    }

    @Test
    @Requirement("REGARDS_DSL_DAM_PLG_210")
    @Requirement("REGARDS_DSL_DAM_SRC_100")
    @Requirement("REGARDS_DSL_DAM_SRC_110")
    @Requirement("REGARDS_DSL_DAM_SRC_140")
    @Purpose("The system has a plugin that enables to define a datasource to a PostreSql database by introspection")
    public void getDataSourceIntrospection() throws SQLException, DataSourceException {
        Assert.assertEquals(nbElements, repository.count());

        // Get first page
        Page<DataObjectFeature> page = plgDBDataSource.findAll(getDefaultTenant(), PageRequest.of(0, 2));
        Assert.assertNotNull(page);
        Assert.assertEquals(2, page.getContent().size());

        page.getContent().get(0).getProperties().forEach(attr -> {
            if (attr.getName().equals("name")) {
                Assert.assertTrue(attr.getValue().toString().contains(HELLO));
            }
        });

        page.getContent().forEach(d -> Assert.assertNotNull(d.getId()));
        page.getContent().forEach(d -> Assert.assertNotNull(d.getProviderId()));
        page.getContent().forEach(d -> Assert.assertTrue(0 < d.getProperties().size()));

        // Get second page
        page = plgDBDataSource.findAll(getDefaultTenant(), PageRequest.of(1, 2));
        Assert.assertNotNull(page);
        Assert.assertEquals(1, page.getContent().size());

        page.getContent().forEach(dataObj -> {
            LOG.info("------------------->");
            dataObj.getProperties().forEach(attr -> {
                LOG.info(attr.getName() + " : " + attr.getValue());
                if (attr.getName().equals(NAME_ATTR)) {
                    Assert.assertTrue(attr.getValue().toString().contains(HELLO));
                }
            });
        });

        page.getContent().forEach(d -> Assert.assertNotNull(d.getId()));
        page.getContent().forEach(d -> Assert.assertNotNull(d.getProviderId()));
        page.getContent().forEach(d -> Assert.assertTrue(0 < d.getProperties().size()));
        page.getContent().forEach(d -> Assert.assertTrue(d.getTags().contains("TOTO")));
        page.getContent().forEach(d -> Assert.assertTrue(d.getTags().contains("TITI")));

        plgDBDataSource.getDBConnection().closeConnection();
    }

    @Test
    @Requirement("REGARDS_DSL_DAM_ARC_140")
    @Purpose("The system allows to define a mapping between the datasource's attributes and an internal model")
    public void getDataSourceIntrospectionFromPastDate() throws SQLException, DataSourceException {
        Assert.assertEquals(nbElements, repository.count());

        OffsetDateTime date = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC).minusMinutes(2);
        Page<DataObjectFeature> ll = plgDBDataSource.findAll(getDefaultTenant(), PageRequest.of(0, 10), date);
        Assert.assertNotNull(ll);
        Assert.assertEquals(1, ll.getContent().size());

        ll.getContent().forEach(dataObj -> {
            LOG.info("------------------->");
            dataObj.getProperties().forEach(attr -> {
                LOG.info(attr.getName() + " : " + attr.getValue());
                if (attr.getName().equals(NAME_ATTR)) {
                    Assert.assertTrue(attr.getValue().toString().contains(HELLO));
                }
            });
        });

        ll.getContent().forEach(d -> Assert.assertNotNull(d.getId()));
        ll.getContent().forEach(d -> Assert.assertNotNull(d.getProviderId()));
        ll.getContent().forEach(d -> Assert.assertTrue(0 < d.getProperties().size()));
    }

    @Test
    public void getDataSourceIntrospectionFromFutureDate() throws SQLException, DataSourceException {
        Assert.assertEquals(nbElements, repository.count());

        OffsetDateTime ldt = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC).plusSeconds(10);
        Page<DataObjectFeature> ll = plgDBDataSource.findAll(getDefaultTenant(), PageRequest.of(0, 10), ldt);
        Assert.assertNotNull(ll);
        Assert.assertEquals(0, ll.getContent().size());
    }

    @After
    public void erase() {
        // repository.deleteAll();
    }

    /**
     * Define the {@link PluginConfiguration} for a {@link DefaultPostgreConnectionPlugin} to connect to the PostgreSql
     * database
     *
     * @return the {@link PluginConfiguration} @
     */
    private PluginConfiguration getPostgreConnectionConfiguration() {
        Set<IPluginParam> parameters = IPluginParam
                .set(IPluginParam.build(DBConnectionPluginConstants.USER_PARAM, dbUser),
                     IPluginParam.build(DBConnectionPluginConstants.DB_HOST_PARAM, dbHost),
                     IPluginParam.build(DBConnectionPluginConstants.DB_PORT_PARAM, dbPort),
                     IPluginParam.build(DBConnectionPluginConstants.DB_NAME_PARAM, dbName));
        StringPluginParam passwordParam = IPluginParam.build(DBConnectionPluginConstants.PASSWORD_PARAM, dbPassword);
        passwordParam.setDecryptedValue(dbPassword);
        parameters.add(passwordParam);

        PluginConfiguration plgConf = PluginUtils.getPluginConfiguration(parameters,
                                                                         DefaultPostgreConnectionPlugin.class);
        pluginCacheMap.put(plgConf.getBusinessId(),
                           PluginUtils.getPlugin(plgConf, plgConf.getPluginClassName(), pluginCacheMap));
        return plgConf;
    }

    private void buildAttributesMapping() {
        this.attributesMapping = new ArrayList<>();

        this.attributesMapping
                .add(new StaticAttributeMapping(AbstractAttributeMapping.PRIMARY_KEY, AttributeType.LONG, "id"));

        this.attributesMapping
                .add(new StaticAttributeMapping(AbstractAttributeMapping.LABEL, "'" + HELLO + "- 'as label"));
        this.attributesMapping
                .add(new DynamicAttributeMapping("alt", "geometry", AttributeType.INTEGER, "altitude AS altitude"));
        this.attributesMapping.add(new DynamicAttributeMapping("lat", "geometry", AttributeType.DOUBLE, "latitude"));
        this.attributesMapping.add(new DynamicAttributeMapping("long", "geometry", AttributeType.DOUBLE, "longitude"));
        this.attributesMapping.add(new DynamicAttributeMapping("creationDate1", "hello", AttributeType.DATE_ISO8601,
                "timestampwithouttimezone"));
        this.attributesMapping.add(new DynamicAttributeMapping("creationDate2", "hello", AttributeType.DATE_ISO8601,
                "timestampwithouttimezone"));
        this.attributesMapping.add(new DynamicAttributeMapping("date", "hello", AttributeType.DATE_ISO8601, "date"));
        this.attributesMapping.add(new StaticAttributeMapping(AbstractAttributeMapping.LAST_UPDATE,
                AttributeType.DATE_ISO8601, "timestampwithtimezone"));
        this.attributesMapping.add(new DynamicAttributeMapping("isUpdate", "hello", AttributeType.BOOLEAN, "update"));
        this.attributesMapping.add(new DynamicAttributeMapping("url", "", AttributeType.URL, "url"));
    }

}
