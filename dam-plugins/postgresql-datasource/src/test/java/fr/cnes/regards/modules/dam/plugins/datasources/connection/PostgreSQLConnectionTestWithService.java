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

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.StringPluginParam;
import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.framework.test.integration.AbstractRegardsIT;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DBConnectionPluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDBConnectionPlugin;
import fr.cnes.regards.modules.dam.plugins.datasources.DefaultPostgreConnectionPlugin;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

import java.util.Set;

/**
 * @author Christophe Mertz
 */
@TestPropertySource(locations = { "classpath:datasource-test.properties" },
                    properties = { "spring.jpa.properties.hibernate.default_schema=public" })
public class PostgreSQLConnectionTestWithService extends AbstractRegardsIT {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(PostgreSQLConnectionTestWithService.class);

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

    @Autowired
    private IPluginService pluginService;

    @Test
    public void testPoolConnectionWithGetFirstPluginByType()
        throws ModuleException, NotAvailablePluginConfigurationException {
        // Save a PluginConfiguration
        final PluginConfiguration aPluginConfiguration = getPostGreSqlConnectionConfiguration();
        pluginService.savePluginConfiguration(aPluginConfiguration);

        // Get the first Plugin
        final DefaultPostgreConnectionPlugin aa = pluginService.getFirstPluginByType(IDBConnectionPlugin.class);

        Assert.assertNotNull(aa);
        Assert.assertTrue(aa.testConnection());

        // Get the first Plugin : the same as the previous
        final DefaultPostgreConnectionPlugin bb = pluginService.getFirstPluginByType(IDBConnectionPlugin.class);

        Assert.assertNotNull(bb);
        Assert.assertTrue(bb.testConnection());
        Assert.assertEquals(aa, bb);
    }

    @Test
    public void testPoolConnectionWithGetPlugin() throws ModuleException, NotAvailablePluginConfigurationException {
        // Save a PluginConfiguration
        PluginConfiguration aPluginConfiguration = getPostGreSqlConnectionConfiguration();
        pluginService.savePluginConfiguration(aPluginConfiguration);
        String anId = aPluginConfiguration.getBusinessId();

        // Get a Plugin for a specific configuration
        final DefaultPostgreConnectionPlugin aa = pluginService.getPlugin(anId);

        Assert.assertNotNull(aa);
        Assert.assertTrue(aa.testConnection());

        // Get a Plugin for a specific configuration
        final DefaultPostgreConnectionPlugin bb = pluginService.getPlugin(anId);

        Assert.assertNotNull(bb);
        Assert.assertTrue(bb.testConnection());
        Assert.assertEquals(aa, bb);
    }

    @After
    public void erase() {
        // repository.deleteAll();
    }

    /**
     * Define the {@link PluginConfiguration} for a {@link DefaultPostgreConnectionPlugin} to connect to the PostgreSql
     * database
     *
     * @return the {@link PluginConfiguration}
     */
    private PluginConfiguration getPostGreSqlConnectionConfiguration() {
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

        return PluginConfiguration.build(DefaultPostgreConnectionPlugin.class, null, parameters);

    }

}
