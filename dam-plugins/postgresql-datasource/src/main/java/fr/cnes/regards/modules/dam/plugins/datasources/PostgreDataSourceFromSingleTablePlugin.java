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

package fr.cnes.regards.modules.dam.plugins.datasources;

import com.nurkiewicz.jdbcrepository.sql.SqlGenerator;
import fr.cnes.regards.db.datasources.plugins.common.AbstractDBDataSourceFromSingleTablePlugin;
import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.dam.domain.datasources.AbstractAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDBConnectionPlugin;
import fr.cnes.regards.modules.dam.plugins.datasources.utils.PostgreSqlGenerator;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

/**
 * Class PostgreDataSourceFromSingleTablePlugin A {@link Plugin} to discover the tables, columns and indexes to a
 * PostgreSQL Database.<br>
 * This {@link Plugin} used a {@link IDBConnectionPlugin} to define to connection to the dataSource.
 *
 * @author Christophe Mertz
 * @since 1.0-SNAPSHOT
 */
@Plugin(id = "postgresql-datasource-single-table",
        version = "2.0-SNAPSHOT",
        description = "Allows introspection and data extraction to a PostgreSql database",
        author = "REGARDS Team",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CSSI",
        url = "https://github.com/RegardsOss")
public class PostgreDataSourceFromSingleTablePlugin extends AbstractDBDataSourceFromSingleTablePlugin {

    private static final Logger LOG = LoggerFactory.getLogger(PostgreDataSourceFromSingleTablePlugin.class);

    @PluginParameter(name = DataSourcePluginConstants.CONNECTION_PARAM, label = "Database connection plugin")
    private IDBConnectionPlugin dbConnection;

    @PluginParameter(name = DataSourcePluginConstants.TABLE_PARAM,
                     label = "Table name",
                     description = "Database table name to be requested")
    private String tableName;

    @PluginParameter(name = DataSourcePluginConstants.MODEL_MAPPING_PARAM,
                     label = "model attributes mapping",
                     description = "Mapping between model and database table (in JSON format)")
    private List<AbstractAttributeMapping> attributesMapping;

    @PluginParameter(name = DataSourcePluginConstants.REFRESH_RATE,
                     defaultValue = "86400",
                     optional = true,
                     label = "refresh rate",
                     description = "Ingestion refresh rate in seconds (minimum delay between two consecutive ingestions)")
    private Integer refreshRate;

    @PluginParameter(name = DataSourcePluginConstants.TAGS,
                     label = "data objects common tags",
                     optional = true,
                     description = "Common tags to be put on all data objects created by the data source")
    private final List<String> commonTags = Collections.emptyList();

    /**
     * Init method
     */
    @PluginInit
    private void initPlugin() throws ModuleException {
        LOG.info("Init method call : {}, connection = {}, table name = {}, model = {}, mapping = {}",
                 this.getClass().getName(),
                 dbConnection.toString(),
                 tableName,
                 modelName,
                 attributesMapping);
        init(modelName, attributesMapping, commonTags);
        // to handle issues with uppercase lets wrap tablename into "
        tableName = tableName.replaceAll("(.*)\\.(.*)", "$1.\"$2\"");
        initializePluginMapping(tableName);
        initDataSourceColumns(getDBConnection());
    }

    @Override
    protected SqlGenerator buildSqlGenerator() {
        return new PostgreSqlGenerator();
    }

    @Override
    protected SqlGenerator buildSqlGenerator(String allColumnsClause) {
        return new PostgreSqlGenerator(allColumnsClause);
    }

    @Override
    public IDBConnectionPlugin getDBConnection() {
        return dbConnection;
    }

    /**
     * @see 'https://jdbc.postgresql.org/documentation/head/8-date-time.html'
     */
    @Override
    protected IProperty<?> buildDateAttribute(ResultSet rs, String attrName, String attrDSName, String colName)
        throws SQLException {
        OffsetDateTime ldt;
        Integer typeDS = getTypeDs(attrDSName);

        if (typeDS == null) {
            ldt = buildOffsetDateTime(rs, colName);
        } else {
            long n;
            Instant instant;

            switch (typeDS) {
                case Types.TIME:
                    n = rs.getTime(colName).getTime();
                    instant = Instant.ofEpochMilli(n);
                    ldt = OffsetDateTime.ofInstant(instant, ZoneId.of("UTC"));
                    break;
                case Types.DATE:
                    n = rs.getDate(colName).getTime();
                    instant = Instant.ofEpochMilli(n);
                    ldt = OffsetDateTime.ofInstant(instant, ZoneId.of("UTC"));
                    break;
                default:
                    ldt = buildOffsetDateTime(rs, colName);
                    break;
            }
        }

        return IProperty.buildDate(attrName, ldt);
    }

    @Override
    protected String getLastUpdateValue(String lastUpdateColumnName, OffsetDateTime date) throws DataSourceException {
        return OffsetDateTimeAdapter.format(date);
    }

    @Override
    public int getRefreshRate() {
        return refreshRate;
    }

}
