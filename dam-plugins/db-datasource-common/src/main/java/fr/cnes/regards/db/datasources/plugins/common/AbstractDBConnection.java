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

package fr.cnes.regards.db.datasources.plugins.common;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import fr.cnes.regards.modules.dam.domain.datasources.Column;
import fr.cnes.regards.modules.dam.domain.datasources.Table;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDBConnectionPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * A class to discover the tables and columns of a SQL Database.</br>
 * This class manage a connection pool to the database.</br>
 * This class used @see http://www.mchange.com/projects/c3p0/index.html.</br>
 *
 * @author Christophe Mertz
 * @since 1.0-SNAPSHOT
 */
public abstract class AbstractDBConnection implements IDBConnectionPlugin {

    /**
     * Class logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(AbstractDBConnection.class);

    private static final String METADATA_TABLE = "TABLE";

    private static final String METADATA_VIEW = "VIEW";

    private static final String TABLE_CAT = "TABLE_CAT";

    private static final String TABLE_SCHEM = "TABLE_SCHEM";

    private static final String TABLE_NAME = "TABLE_NAME";

    private static final String TABLE_TYPE = "TABLE_TYPE";

    private static final String COLUMN_NAME = "COLUMN_NAME";

    private static final String TYPE_NAME = "TYPE_NAME";

    private static final String DATA_TYPE = "DATA_TYPE";

    private static final String REMARKS = "REMARKS";

    /**
     * A {@link HikariDataSource} to used to connect to a data source
     */
    protected HikariDataSource pooledDataSource;

    protected abstract IDBConnectionPlugin getDBConnectionPlugin();

    /**
     * The driver used to connect to the database
     *
     * @return the JDBC driver
     */
    protected abstract String getJdbcDriver();

    /**
     * The SQL request used to test the connection to the database
     *
     * @return the SQL request
     */
    protected abstract String getSqlRequestTestConnection();

    /**
     * The URL used to connect to the database.</br>
     * Generally this URL look likes : jdbc:xxxxx//host:port/databaseName
     *
     * @return the database's URL
     */
    protected abstract String buildUrl();

    /**
     * Test the connection to the database
     *
     * @return true if the connection is active
     */
    @Override
    public boolean testConnection() {
        boolean isConnected = false;
        try (Connection conn = pooledDataSource.getConnection()) {
            try (Statement statement = conn.createStatement()) {
                // Execute a simple SQL request
                try (ResultSet rs = statement.executeQuery(getSqlRequestTestConnection())) {
                    isConnected = true;
                }
            }
        } catch (SQLException | HikariPool.PoolInitializationException e) {
            LOG.error("Unable to connect to the database", e);
        }
        return isConnected;
    }

    /**
     * Initialize the {@link HikariDataSource}
     *
     * @param user        The user to used for the database connection
     * @param password    The user's password to used for the database connection
     * @param maxPoolSize Maximum number of Connections a pool will maintain at any given time.
     * @param minPoolSize Minimum number of Connections a pool will maintain at any given time.
     */
    protected void createPoolConnection(String user, String password, Integer maxPoolSize, Integer minPoolSize) {
        String url = buildUrl();
        LOG.info("Create data source pool (url : {})", url);

        HikariConfig config = new HikariConfig(new Properties());
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        // For maximum performance, HikariCP does not recommend setting this value so minimumIdle = maximumPoolSize
        config.setMinimumIdle(minPoolSize);
        config.setMaximumPoolSize(maxPoolSize);
        config.setIdleTimeout(30000L);
        config.setDriverClassName(getJdbcDriver());
        // Postgres schema configuration
        pooledDataSource = new HikariDataSource(config);
    }

    /**
     * Destroy the {@link HikariDataSource}
     */
    @Override
    public void closeConnection() {
        if (pooledDataSource != null) {
            pooledDataSource.close();
        }
    }

    /**
     * Get a {@link Connection} to the database
     *
     * @return the {@link Connection}
     */
    @Override
    public Connection getConnection() throws SQLException {
        try {
            return pooledDataSource.getConnection();
        } catch (SQLException e) {
            LOG.error(e.getMessage());
            throw e;
        }
    }

    /**
     * Returns all the table from the database.
     *
     * @return a {@link Map} of {@link Table}
     */
    @Override
    public Map<String, Table> getTables(String schemaPattern, String tableNamePattern) {
        Map<String, Table> tables = new HashMap<>();
        ResultSet rs = null;

        // Get a connection
        try (Connection conn = getDBConnectionPlugin().getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            rs = metaData.getTables(conn.getCatalog(),
                                    schemaPattern,
                                    tableNamePattern,
                                    new String[] { METADATA_TABLE, METADATA_VIEW });

            while (rs.next()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[TABLE] --> " + logString(rs, TABLE_NAME) + "] " + logString(rs, TABLE_CAT) + logString(
                        rs,
                        TABLE_SCHEM) + logString(rs, TABLE_TYPE) + logString(rs, REMARKS));
                }
                Table table = new Table(rs.getString(TABLE_NAME), rs.getString(TABLE_CAT), rs.getString(TABLE_SCHEM));
                table.setPKey(getPrimaryKey(metaData,
                                            rs.getString(TABLE_CAT),
                                            rs.getString(TABLE_SCHEM),
                                            rs.getString(TABLE_NAME)));
                tables.put(table.getName(), table);
            }
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }

        return tables;
    }

    /**
     * Get the primary key name of a database's table
     *
     * @param metaData The {@link DatabaseMetaData} of the database
     * @param catalog  The catalog name
     * @param schema   The database's schema
     * @param table    The table name
     * @return the primary key name
     * @throws SQLException an SQL error occurred
     */
    private String getPrimaryKey(DatabaseMetaData metaData, String catalog, String schema, String table)
        throws SQLException {
        String column = "";
        ResultSet rs = metaData.getPrimaryKeys(catalog, schema, table);
        if (rs.next()) {
            column = rs.getString(COLUMN_NAME);
            if (LOG.isDebugEnabled()) {
                LOG.debug("[PKEY] --> " + column + ", " + rs.getString("PK_NAME"));
            }
        }
        return column;
    }

    /**
     * Get the columns of a {@link Table} from the database
     *
     * @param tableNameWithSchema table from database optionnally with dotted schema first (ie
     *                            &lt;schema>.&lt;table_name>)
     * @return a {@link Map} of {@link Column}
     */
    @Override
    public Map<String, Column> getColumns(String tableNameWithSchema) {
        String tableName = tableNameWithSchema.replaceAll("\"(.+)\"", "$1");
        tableName = tableName.substring(tableName.indexOf('.') + 1);
        Map<String, Column> cols = new HashMap<>();

        // Get a connection
        try (Connection conn = getDBConnectionPlugin().getConnection()) {

            DatabaseMetaData metaData = conn.getMetaData();

            try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {

                while (rs.next()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[COLUMN] --> " + logString(rs, COLUMN_NAME) + logString(rs, TYPE_NAME) + logInt(rs,
                                                                                                                   DATA_TYPE));
                    }

                    Column column = new Column(rs.getString(COLUMN_NAME),
                                               rs.getString(TYPE_NAME),
                                               rs.getInt(DATA_TYPE));
                    cols.put(column.getName(), column);
                }
            }
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
        return cols;
    }

    private String logString(ResultSet rs, String paramName) throws SQLException {
        if (rs.getString(paramName) != null) {
            return paramName + "=" + rs.getString(paramName) + ",";
        }
        return "";
    }

    private String logInt(ResultSet rs, String paramName) throws SQLException {
        return paramName + "=" + rs.getInt(paramName) + ",";
    }

}
