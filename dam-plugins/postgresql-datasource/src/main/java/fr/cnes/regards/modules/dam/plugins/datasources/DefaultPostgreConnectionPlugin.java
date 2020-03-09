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

import fr.cnes.regards.db.datasources.plugins.common.AbstractDBConnection;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginDestroy;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DBConnectionPluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDBConnectionPlugin;

/**
 * A default {@link Plugin} of type {@link IDBConnectionPlugin}. For the test of the connection :
 *
 * @see 'http://stackoverflow.com/questions/3668506/efficient-sql-test-query-or-validation-query-that-will-work-across-all-or-most'
 * @author Christophe Mertz
 * @since 1.0-SNAPSHOT
 */
@Plugin(id = "postgresql-db-connection", version = "1.0-SNAPSHOT", description = "Connection to a PostgreSql database",
        author = "REGARDS Team", contact = "regards@c-s.fr", license = "GPLv3", owner = "CSSI",
        url = "https://github.com/RegardsOss")
public class DefaultPostgreConnectionPlugin extends AbstractDBConnection {

    /**
     * The JDBC PostgreSQL driver
     */
    private static final String POSTGRESQL_JDBC_DRIVER = "org.postgresql.Driver";

    /**
     * The user to used for the database connection
     */
    @PluginParameter(name = DBConnectionPluginConstants.USER_PARAM, label = "Databse user")
    private String dbUser;

    /**
     * The user's password to used for the database connection
     */
    @PluginParameter(name = DBConnectionPluginConstants.PASSWORD_PARAM, label = "Database user password",
            sensitive = true)
    private String dbPassword;

    /**
     * The URL to the database's host
     */
    @PluginParameter(name = DBConnectionPluginConstants.DB_HOST_PARAM, label = "Database host")
    private String dbHost;

    /**
     * The PORT to the database's host
     */
    @PluginParameter(name = DBConnectionPluginConstants.DB_PORT_PARAM, label = "Database port")
    private String dbPort;

    /**
     * The NAME of the database
     */
    @PluginParameter(name = DBConnectionPluginConstants.DB_NAME_PARAM, label = "Database name")
    private String dbName;

    /**
     * This class is used to initialize the {@link Plugin}
     */
    @PluginInit
    public void createPoolConnection() {
        createPoolConnection(dbUser, dbPassword, 3, 1);
    }

    @PluginDestroy
    public void destroyPoolConnection() {
        closeConnection();
    }

    @Override
    public String buildUrl() {
        return "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
    }

    @Override
    protected IDBConnectionPlugin getDBConnectionPlugin() {
        return this;
    }

    @Override
    protected String getJdbcDriver() {
        return POSTGRESQL_JDBC_DRIVER;
    }

    @Override
    protected String getSqlRequestTestConnection() {
        return "select 1";
    }

}
