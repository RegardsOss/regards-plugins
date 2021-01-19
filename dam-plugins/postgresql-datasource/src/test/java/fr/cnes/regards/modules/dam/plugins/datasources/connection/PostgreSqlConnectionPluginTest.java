/*
 * Copyright 2017-2021 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

import com.google.common.collect.Maps;

import com.zaxxer.hikari.pool.HikariPool;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.StringPluginParam;
import fr.cnes.regards.framework.test.integration.AbstractRegardsIT;
import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.framework.test.report.annotation.Requirement;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.PluginUtilsRuntimeException;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DBConnectionPluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDBConnectionPlugin;
import fr.cnes.regards.modules.dam.plugins.datasources.DefaultPostgreConnectionPlugin;

/**
 * @author Christophe Mertz
 */
@TestPropertySource(locations = { "classpath:datasource-test.properties" },
        properties = { "spring.jpa.properties.hibernate.default_schema=public" })
public class PostgreSqlConnectionPluginTest extends AbstractRegardsIT {

    private static final Logger LOG = LoggerFactory.getLogger(PostgreSqlConnectionPluginTest.class);

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

    @Before
    public void setUp() throws SQLException, NotAvailablePluginConfigurationException {
        IDBConnectionPlugin plgConn;

        plgConn = PluginUtils.getPlugin(PluginConfiguration.build(DefaultPostgreConnectionPlugin.class, null,
                                                                  getPostGreSqlParameters()),
                                        Maps.newHashMap());

        // Do not launch tests is Database is not available
        Assume.assumeTrue(plgConn.testConnection());
    }

    @Test
    @Requirement("REGARDS_DSL_DAM_ARC_100")
    @Purpose("The system allows to define a connection to a data source")
    public void getPostGreSqlConnection() throws NotAvailablePluginConfigurationException {
        final DefaultPostgreConnectionPlugin sqlConn = PluginUtils.getPlugin(PluginConfiguration
                .build(DefaultPostgreConnectionPlugin.class, null, getPostGreSqlParameters()), Maps.newHashMap());

        Assert.assertNotNull(sqlConn);

        // Do not launch tests is Database is not available
        Assume.assumeTrue(sqlConn.testConnection());
    }

    @Test
    public void getMaxPoolSizeWithClose()
            throws InterruptedException, SQLException, NotAvailablePluginConfigurationException {
        final DefaultPostgreConnectionPlugin sqlConn = PluginUtils.getPlugin(PluginConfiguration
                .build(DefaultPostgreConnectionPlugin.class, null, getPostGreSqlParameters()), Maps.newHashMap());

        Assert.assertNotNull(sqlConn);

        try (Connection conn1 = sqlConn.getConnection()) {
            Assert.assertNotNull(conn1);
        }
        Assert.assertTrue(sqlConn.testConnection());
    }

    @Test
    public void getMaxPoolSizeWithoutClose() throws InterruptedException, NotAvailablePluginConfigurationException {
        final DefaultPostgreConnectionPlugin sqlConn = PluginUtils.getPlugin(PluginConfiguration
                .build(DefaultPostgreConnectionPlugin.class, null, getPostGreSqlParameters()), Maps.newHashMap());

        Assert.assertNotNull(sqlConn);

        try (Connection conn1 = sqlConn.getConnection()) {
        } catch (SQLException e) {
            LOG.error("unable to get a connection", e);
            Assert.fail();
        }

    }

    @Test
    public void getMaxPoolSizeWithCloseByThread()
            throws InterruptedException, SQLException, NotAvailablePluginConfigurationException {

        final DefaultPostgreConnectionPlugin sqlConn = PluginUtils.getPlugin(PluginConfiguration
                .build(DefaultPostgreConnectionPlugin.class, null, getPostGreSqlParameters()), Maps.newHashMap());

        Assert.assertNotNull(sqlConn);

        // Get all the available connections
        final Connection conn1 = sqlConn.getConnection();
        Assert.assertNotNull(conn1);

        // Lambda Runnable
        Future<?> closeConnection = Executors.newSingleThreadExecutor().submit(() -> {
            try {
                conn1.close();
            } catch (SQLException e) {
                LOG.error(e.getMessage());
            }
        });

        // Wait for the thread toi finish
        try {
            closeConnection.get();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        // Test the connection (create a new connection, as previous one is ended, this should be ok)
        Assert.assertTrue(sqlConn.testConnection());
    }

    @Test
    public void getPostGreSqlConnectionError() throws NotAvailablePluginConfigurationException {

        Set<IPluginParam> parameters = IPluginParam
                .set(IPluginParam.build(DBConnectionPluginConstants.USER_PARAM, dbUser),
                     IPluginParam.build(DBConnectionPluginConstants.DB_HOST_PARAM, dbHost),
                     IPluginParam.build(DBConnectionPluginConstants.DB_PORT_PARAM, dbPort),
                     IPluginParam.build(DBConnectionPluginConstants.DB_NAME_PARAM, dbName));
        StringPluginParam passwordParam = IPluginParam.build(DBConnectionPluginConstants.PASSWORD_PARAM, "unknown");
        passwordParam.setDecryptedValue("unknown");
        parameters.add(passwordParam);

        try {
            final DefaultPostgreConnectionPlugin sqlConn = PluginUtils
                    .getPlugin(PluginConfiguration.build(DefaultPostgreConnectionPlugin.class, null, parameters), Maps.newHashMap());
        } catch (PluginUtilsRuntimeException e) {
            // we do not rely on expected attribute from Test annotation because we want to be sure the test is successful
            // because we could not create the pool due to bad credential
            // and not due to other errors that could throw PluginUtilsRuntimeException
            LOG.debug(e.getMessage(), e);
            Assert.assertTrue(e.getCause() instanceof HikariPool.PoolInitializationException);
        }
    }

    private Set<IPluginParam> getPostGreSqlParameters() {
        Set<IPluginParam> parameters = IPluginParam
                .set(IPluginParam.build(DBConnectionPluginConstants.USER_PARAM, dbUser),
                     IPluginParam.build(DBConnectionPluginConstants.DB_HOST_PARAM, dbHost),
                     IPluginParam.build(DBConnectionPluginConstants.DB_PORT_PARAM, dbPort),
                     IPluginParam.build(DBConnectionPluginConstants.DB_NAME_PARAM, dbName));
        StringPluginParam passwordParam = IPluginParam.build(DBConnectionPluginConstants.PASSWORD_PARAM, dbPassword);
        passwordParam.setDecryptedValue(dbPassword);
        parameters.add(passwordParam);
        return parameters;
    }

}
