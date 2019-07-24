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

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.PluginParameter;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.oais.urn.EntityType;
import fr.cnes.regards.framework.test.integration.AbstractRegardsIT;
import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.framework.test.report.annotation.Requirement;
import fr.cnes.regards.framework.utils.plugins.PluginParametersFactory;
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
public class PostgreDataSourceFromSingleTablePluginWithoutLastUpdateDateTest extends AbstractRegardsIT {

    private static final Logger LOG = LoggerFactory
            .getLogger(PostgreDataSourceFromSingleTablePluginWithoutLastUpdateDateTest.class);

    private static final String TENANT = "PGDB_TENANT";

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

    private final Map<Long, Object> pluginCacheMap = new HashMap<>();

    /**
     * JPA Repository
     */
    @Autowired
    IDataSourceRepositoryTest repository;

    @Autowired
    private IModelService modelService;

    @Autowired
    private IRuntimeTenantResolver tenantResolver;

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
                OffsetDateTime.now().minusMinutes(33), OffsetDateTime.now().minusMinutes(12132125).toString(), true,
                new URL("file", "localhost", ""), "one"));
        repository.save(new DataSourceEntity("Toulouse", 110, 3.141592653589793238462643383279, -15.2323654654564654,
                LocalDate.now().minusMonths(1), LocalTime.now().minusMinutes(10), LocalDateTime.now().plusHours(33),
                OffsetDateTime.now().minusSeconds(22), OffsetDateTime.now().minusMinutes(12132125).toString(), true,
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
        Set<PluginParameter> parameters;
        parameters = PluginParametersFactory.build()
                .addPluginConfiguration(DataSourcePluginConstants.CONNECTION_PARAM, getPostgreConnectionConfiguration())
                .addParameter(DataSourcePluginConstants.TABLE_PARAM, TABLE_NAME_TEST)
                .addParameter(DataSourcePluginConstants.MODEL_NAME_PARAM, MODEL_NAME_TEST)
                .addParameter(DataSourcePluginConstants.MODEL_MAPPING_PARAM, attributesMapping)
                .addParameter(DataSourcePluginConstants.REFRESH_RATE, 1800).getParameters();

        plgDBDataSource = PluginUtils.getPlugin(parameters, PostgreDataSourceFromSingleTablePlugin.class,
                                                pluginCacheMap);

        // Do not launch tests is Database is not available
        Assume.assumeTrue(plgDBDataSource.getDBConnection().testConnection());
    }

    @Test
    @Requirement("REGARDS_DSL_DAM_ARC_140")
    @Purpose("The system allows to define a mapping between the datasource's attributes and an internal model")
    public void getDataSourceIntrospectionFromPastDate() throws SQLException, DataSourceException {
        Assert.assertEquals(nbElements, repository.count());

        OffsetDateTime date = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC).minusMinutes(2);
        Page<DataObjectFeature> ll = plgDBDataSource.findAll(TENANT, PageRequest.of(0, 10), date);
        Assert.assertNotNull(ll);
        Assert.assertEquals(3, ll.getContent().size());

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

    /**
     * Define the {@link PluginConfiguration} for a {@link DefaultPostgreConnectionPlugin} to connect to the PostgreSql
     * database
     *
     * @return the {@link PluginConfiguration} @
     */
    private PluginConfiguration getPostgreConnectionConfiguration() {
        final Set<PluginParameter> parameters = PluginParametersFactory.build()
                .addParameter(DBConnectionPluginConstants.USER_PARAM, dbUser)
                .addSensitiveParameter(DBConnectionPluginConstants.PASSWORD_PARAM, dbPassword)
                .addParameter(DBConnectionPluginConstants.DB_HOST_PARAM, dbHost)
                .addParameter(DBConnectionPluginConstants.DB_PORT_PARAM, dbPort)
                .addParameter(DBConnectionPluginConstants.DB_NAME_PARAM, dbName).getParameters();

        PluginConfiguration plgConf = PluginUtils.getPluginConfiguration(parameters,
                                                                         DefaultPostgreConnectionPlugin.class);
        pluginCacheMap.put(plgConf.getId(),
                           PluginUtils.getPlugin(plgConf, plgConf.getPluginClassName(), pluginCacheMap));
        return plgConf;
    }

    private void buildAttributesMapping() {
        this.attributesMapping = new ArrayList<>();

        this.attributesMapping
                .add(new StaticAttributeMapping(AbstractAttributeMapping.LABEL, "'" + HELLO + "- '||label as label"));
        this.attributesMapping
                .add(new DynamicAttributeMapping("alt", "geometry", AttributeType.INTEGER, "altitude AS altitude"));
        this.attributesMapping.add(new DynamicAttributeMapping("lat", "geometry", AttributeType.DOUBLE, "latitude"));
        this.attributesMapping.add(new DynamicAttributeMapping("long", "geometry", AttributeType.DOUBLE, "longitude"));
        this.attributesMapping.add(new DynamicAttributeMapping("creationDate1", "hello", AttributeType.DATE_ISO8601,
                "timestampwithouttimezone"));
        this.attributesMapping
                .add(new StaticAttributeMapping(AbstractAttributeMapping.PRIMARY_KEY, AttributeType.LONG, "id"));
        this.attributesMapping.add(new DynamicAttributeMapping("creationDate2", "hello", AttributeType.DATE_ISO8601,
                "timestampwithouttimezone"));
        this.attributesMapping.add(new DynamicAttributeMapping("date", "hello", AttributeType.DATE_ISO8601, "date"));
        this.attributesMapping.add(new DynamicAttributeMapping("isUpdate", "hello", AttributeType.BOOLEAN, "update"));
    }

}
