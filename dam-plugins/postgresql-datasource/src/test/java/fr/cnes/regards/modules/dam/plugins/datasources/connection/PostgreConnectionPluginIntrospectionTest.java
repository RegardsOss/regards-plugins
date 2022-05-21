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
package fr.cnes.regards.modules.dam.plugins.datasources.connection;

import com.google.common.collect.Maps;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.StringPluginParam;
import fr.cnes.regards.framework.test.integration.AbstractRegardsIT;
import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.framework.test.report.annotation.Requirement;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.dam.domain.datasources.Column;
import fr.cnes.regards.modules.dam.domain.datasources.Table;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DBConnectionPluginConstants;
import fr.cnes.regards.modules.dam.plugins.datasources.DefaultPostgreConnectionPlugin;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.Set;

/**
 * @author Christophe Mertz
 */
@TestPropertySource(locations = { "classpath:datasource-test.properties" },
    properties = { "spring.jpa.properties.hibernate.default_schema=public" })
public class PostgreConnectionPluginIntrospectionTest extends AbstractRegardsIT {

    private static final Logger LOG = LoggerFactory.getLogger(PostgreConnectionPluginIntrospectionTest.class);

    private static final String TABLE_NAME_TEST = "t_test_plugin_data_source";

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

    private DefaultPostgreConnectionPlugin postgreDBConn;

    @Before
    public void setUp() throws NotAvailablePluginConfigurationException {
        Set<IPluginParam> parameters = IPluginParam.set(IPluginParam.build(DBConnectionPluginConstants.USER_PARAM,
                                                                           dbUser),
                                                        IPluginParam.build(DBConnectionPluginConstants.DB_HOST_PARAM,
                                                                           dbHost),
                                                        IPluginParam.build(DBConnectionPluginConstants.DB_PORT_PARAM,
                                                                           dbPort),
                                                        IPluginParam.build(DBConnectionPluginConstants.DB_NAME_PARAM,
                                                                           dbName));
        StringPluginParam passwordParam = IPluginParam.build(DBConnectionPluginConstants.PASSWORD_PARAM, dbPassword);
        passwordParam.setDecryptedValue(dbPassword);
        parameters.add(passwordParam);

        postgreDBConn = PluginUtils.getPlugin(PluginConfiguration.build(DefaultPostgreConnectionPlugin.class,
                                                                        null,
                                                                        parameters), Maps.newHashMap());

        // Do not launch tests is Database is not available
        Assume.assumeTrue(postgreDBConn.testConnection());
    }

    @Test
    @Requirement("REGARDS_DSL_DAM_PLG_100")
    @Purpose("The system has a plugin that enables to connect to a PostreSql database")
    public void postgreSqlConnection() {
        Assert.assertTrue(postgreDBConn.testConnection());

        Map<String, Table> tables = postgreDBConn.getTables(null, null);
        Assert.assertNotNull(tables);
        Assert.assertTrue(!tables.isEmpty());
    }

    @Test
    @Requirement("REGARDS_DSL_DAM_SRC_155")
    @Purpose(
        "The system has a plugin that enables for a SGBD to get the list of tables and for a table, the list of columns and their types")
    public void getTablesAndColumns() {
        Assert.assertTrue(postgreDBConn.testConnection());

        Map<String, Table> tables = postgreDBConn.getTables(null, null);
        Assert.assertNotNull(tables);
        Assert.assertTrue(!tables.isEmpty());

        tables.forEach((k, t) -> {
            Assert.assertNotNull(t.getName());
            LOG.info("table={}-{}-{}-{}-{}-{}",
                     t.toString(),
                     t.getPKey(),
                     t.getName(),
                     t.getTableDefinition(),
                     t.getCatalog(),
                     t.getSchema());

        });

        Map<String, Column> columns = postgreDBConn.getColumns(TABLE_NAME_TEST);
        Assert.assertNotNull(columns);

        columns.forEach((k, c) -> {
            Assert.assertNotNull(c.getName());
            LOG.info("column={}-{}", c.getName(), c.getJavaSqlType());
        });
    }

}
