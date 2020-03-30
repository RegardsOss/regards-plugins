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
package fr.cnes.regards.modules.dam.plugins.datasources;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

import fr.cnes.regards.framework.encryption.exception.EncryptionException;
import fr.cnes.regards.framework.module.rest.exception.EntityInvalidException;
import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.dao.IPluginConfigurationRepository;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.StringPluginParam;
import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.test.integration.AbstractRegardsServiceIT;
import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.framework.test.report.annotation.Requirement;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.utils.plugins.PluginParameterTransformer;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.dam.domain.datasources.AbstractAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.DynamicAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.StaticAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DBConnectionPluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDBDataSourcePlugin;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.dam.plugins.datasources.utils.DataSourceEntity;
import fr.cnes.regards.modules.dam.plugins.datasources.utils.IDataSourceRepositoryTest;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import fr.cnes.regards.modules.model.service.IModelService;

/**
 * @author Christophe Mertz
 */
@TestPropertySource(locations = { "classpath:datasource-test.properties" },
        properties = { "spring.jpa.properties.hibernate.default_schema=public" })
public class PostgreDataSourcePluginTest extends AbstractRegardsServiceIT {

    private static final Logger LOG = LoggerFactory.getLogger(PostgreDataSourcePluginTest.class);

    private static final String HELLO = "hello world from ";

    private static final String NAME_ATTR = "name";

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

    private IDBDataSourcePlugin plgDBDataSource;

    private List<AbstractAttributeMapping> attributesMapping;

    private static int nbElements;

    @Autowired
    private IModelService modelService;

    @Autowired
    private IRuntimeTenantResolver tenantResolver;

    /**
     * JPA Repository
     */
    @Autowired
    private IDataSourceRepositoryTest repository;

    @Autowired
    private Gson gson;

    @Autowired
    private IPluginService pluginService;

    @Autowired
    private IPluginConfigurationRepository pluginConfigurationRepository;

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
        pluginConfigurationRepository.deleteAll();
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
                OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC).minusMinutes(33),
                OffsetDateTime.now().minusMinutes(12132125).toString(), true, new URL("file", "localhost", ""),
                "monday"));
        repository.save(new DataSourceEntity("Toulouse", 110, 3.141592653589793238462643383279, -15.2323654654564654,
                LocalDate.now().minusMonths(1), LocalTime.now().minusMinutes(10), LocalDateTime.now().plusHours(33),
                OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC).minusSeconds(22),
                OffsetDateTime.now().minusSeconds(4464654).toString(), true,
                new URL("https", "regardsv2", "html/polder/ADEOS1_POLDER1_L0.html"), "saturday"));
        repository.save(new DataSourceEntity("Paris", 350, -3.141592653589793238462643383279502884197169399375105,
                25.565465465454564654654654, LocalDate.now().minusDays(10), LocalTime.now().minusHours(9),
                LocalDateTime.now().minusMonths(2),
                OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC).minusHours(7),
                OffsetDateTime.now().minusHours(4545).toString(), false, new URL("ftp", "localhost", ""), "sunday"));
        nbElements = 3;

        /*
         * Initialize the AbstractAttributeMapping
         */
        buildModelAttributes();

        /*
         * Instantiate the data source plugin
         */
        Set<IPluginParam> parameters = IPluginParam
                .set(IPluginParam.plugin(DataSourcePluginConstants.CONNECTION_PARAM,
                                         getPostgreConnectionConfiguration().getBusinessId()),
                     IPluginParam.build(DataSourcePluginConstants.MODEL_NAME_PARAM, MODEL_NAME_TEST),
                     IPluginParam.build(DataSourcePluginConstants.MODEL_MAPPING_PARAM,
                                        PluginParameterTransformer.toJson(attributesMapping)),
                     IPluginParam.build(DataSourcePluginConstants.FROM_CLAUSE, "from\n\n\nT_TEST_PLUGIN_DATA_SOURCE"));

        PluginConfiguration dbDataSourceConf = new PluginConfiguration("TEST_PostgreDataSourcePlugin", parameters,
                PostgreDataSourcePlugin.class.getAnnotation(Plugin.class).id());

        dbDataSourceConf = pluginService.savePluginConfiguration(dbDataSourceConf);

        plgDBDataSource = pluginService.getPlugin(dbDataSourceConf.getBusinessId());

        // Do not launch tests is Database is not available
        Assume.assumeTrue(plgDBDataSource.getDBConnection().testConnection());
    }

    @Test
    @Requirement("REGARDS_DSL_DAM_SRC_100")
    @Requirement("REGARDS_DSL_DAM_SRC_110")
    @Requirement("REGARDS_DSL_DAM_SRC_140")
    @Requirement("REGARDS_DSL_DAM_PLG_200")
    @Purpose("The system has a plugin that enables to define a datasource to a PostreSql database by setting a SQL request")
    public void getDataSourceIntrospection() throws SQLException, DataSourceException {
        Assert.assertEquals(nbElements, repository.count());

        Page<DataObjectFeature> ll = plgDBDataSource.findAll(getDefaultTenant(), PageRequest.of(0, 10));
        Assert.assertNotNull(ll);
        Assert.assertEquals(nbElements, ll.getContent().size());

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
    @Requirement("REGARDS_DSL_DAM_ARC_120")
    @Requirement("REGARDS_DSL_DAM_ARC_130")
    @Purpose("The system allows to define a request to a data source to get a subset of the data")
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
     * @throws EncryptionException
     * @throws IOException
     * @throws InvalidAlgorithmParameterException
     * @throws InvalidKeyException
     */
    private PluginConfiguration getPostgreConnectionConfiguration()
            throws EncryptionException, EntityNotFoundException, EntityInvalidException {
        Set<IPluginParam> parameters = IPluginParam
                .set(IPluginParam.build(DBConnectionPluginConstants.USER_PARAM, dbUser),
                     IPluginParam.build(DBConnectionPluginConstants.DB_HOST_PARAM, dbHost),
                     IPluginParam.build(DBConnectionPluginConstants.DB_PORT_PARAM, dbPort),
                     IPluginParam.build(DBConnectionPluginConstants.DB_NAME_PARAM, dbName));
        StringPluginParam passwordParam = IPluginParam.build(DBConnectionPluginConstants.PASSWORD_PARAM, dbPassword);
        passwordParam.setDecryptedValue(dbPassword);
        parameters.add(passwordParam);

        PluginConfiguration plgConf = new PluginConfiguration("TEST_DefaultPostgreConnectionPlugin", parameters,
                DefaultPostgreConnectionPlugin.class.getAnnotation(Plugin.class).id());

        pluginService.savePluginConfiguration(plgConf);
        return plgConf;
    }

    private void buildModelAttributes() {
        attributesMapping = new ArrayList<AbstractAttributeMapping>();

        attributesMapping.add(new DynamicAttributeMapping(NAME_ATTR, PropertyType.STRING,
                "'" + HELLO + "--> '||label as label"));
        attributesMapping
                .add(new DynamicAttributeMapping("alt", "geometry", PropertyType.INTEGER, "altitude AS altitude"));
        attributesMapping.add(new DynamicAttributeMapping("lat", "geometry", PropertyType.DOUBLE, "latitude"));
        attributesMapping.add(new DynamicAttributeMapping("long", "geometry", PropertyType.DOUBLE, "longitude"));
        attributesMapping.add(new DynamicAttributeMapping("creationDate1", "hello", PropertyType.DATE_ISO8601,
                "timeStampWithoutTimeZone"));
        attributesMapping.add(new DynamicAttributeMapping("creationDate2", "hello", PropertyType.DATE_ISO8601,
                "timeStampWithoutTimeZone"));
        attributesMapping.add(new DynamicAttributeMapping("date", "hello", PropertyType.DATE_ISO8601, "date"));
        attributesMapping.add(new StaticAttributeMapping(AbstractAttributeMapping.LAST_UPDATE,
                PropertyType.DATE_ISO8601, "timeStampWithTimeZone"));
        attributesMapping.add(new DynamicAttributeMapping("isUpdate", "hello", PropertyType.BOOLEAN, "update"));
        attributesMapping.add(new DynamicAttributeMapping("date_string", PropertyType.DATE_ISO8601,
                "to_timestamp(dateStr, 'YYYY-MM-DD HH24:MI:SS:US')"));
        attributesMapping
                .add(new StaticAttributeMapping(AbstractAttributeMapping.PRIMARY_KEY, PropertyType.LONG, "id"));
        attributesMapping.add(new DynamicAttributeMapping("select", PropertyType.STRING, "descr"));
        attributesMapping.add(new DynamicAttributeMapping("link", PropertyType.URL, "url"));
    }

}
