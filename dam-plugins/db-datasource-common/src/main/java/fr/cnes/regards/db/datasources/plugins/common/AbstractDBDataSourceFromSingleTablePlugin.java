/*
 * Copyright 2017-2024 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import com.google.common.base.Strings;
import cz.jirutka.spring.data.jdbc.TableDescription;
import cz.jirutka.spring.data.jdbc.sql.SqlGenerator;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.modules.dam.domain.datasources.Column;
import fr.cnes.regards.modules.dam.domain.datasources.CrawlingCursor;
import fr.cnes.regards.modules.dam.domain.datasources.CrawlingCursorMode;
import fr.cnes.regards.modules.dam.domain.datasources.Table;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDBConnectionPlugin;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDBDataSourceFromSingleTablePlugin;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import jakarta.annotation.Nullable;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * A {@link Plugin} to discover the tables and columns of a SQL Database and to retrieve the data elements of a specific
 * table.<br>
 * This {@link Plugin} used a {@link IDBConnectionPlugin} to define the connection to the Database.
 *
 * @author Christophe Mertz
 */
public abstract class AbstractDBDataSourceFromSingleTablePlugin extends AbstractDataObjectMapping
    implements IDBDataSourceFromSingleTablePlugin {

    /**
     * Class logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(AbstractDBDataSourceFromSingleTablePlugin.class);

    protected static final String AND = " AND ";

    protected static final String LIMIT = "LIMIT";

    protected static final String ORDER_BY = "ORDER";

    protected static final String SPACE = " ";

    protected static final String WHERE = " WHERE ";

    /**
     * The description of the {@link Table} used by this {@link Plugin} to requests the database.
     */
    protected TableDescription tableDescription;

    /**
     * Map { column name -> Column}
     */
    private Map<String, Column> columnTypeMap;

    protected SqlGenerator sqlGenerator;

    protected abstract SqlGenerator buildSqlGenerator();

    /**
     * This method initialize the {@link SqlGenerator} used to request the database.<br>
     *
     * @param table the table used to requests the database
     */
    @Override
    public void initializePluginMapping(String table) {
        // reset the number of data element hosted by the datasource
        this.reset();

        if (!columns.isEmpty()) {
            if (Strings.isNullOrEmpty(orderByColumn)) {
                orderByColumn = columns.get(0);
            }
            tableDescription = new TableDescription(table,
                                                    buildColumnClause(columns.toArray(new String[0])),
                                                    orderByColumn);
        } else {
            tableDescription = new TableDescription(table, orderByColumn);
        }
        sqlGenerator = buildSqlGenerator();
    }

    protected void initDataSourceColumns(IDBConnectionPlugin dbConnection) {
        // Retrieve all data types from DatabaseMetaData
        columnTypeMap = dbConnection.getColumns(tableDescription.getTableName());
    }

    protected Integer getTypeDs(String colName) {
        String extractColumnName = extractDataSourceColumnName(colName);

        if (LOG.isDebugEnabled()) {
            LOG.debug("******************************************************************");
            LOG.debug("Retrieving type for {}", colName);
            LOG.debug("Extracted type is {}", extractColumnName);
            if (extractColumnName != null) {
                Column col = columnTypeMap.get(extractColumnName);
                if (col != null) {
                    LOG.debug("Column name {} mapped to {} / JAVA {} / SQL {}",
                              extractColumnName,
                              col.getName(),
                              col.getJavaSqlType(),
                              col.getSqlType());
                } else {
                    LOG.debug("No column mapped to {}", extractColumnName);
                }
            }
            LOG.debug("******************************************************************");
        }

        return columnTypeMap.get(extractColumnName).getSqlType();
    }

    protected String extractDataSourceColumnName(String attrDataSourceName) {
        int pos = attrDataSourceName.toLowerCase().lastIndexOf(AS);

        if (pos > 0) {
            String str = attrDataSourceName.substring(pos + AS.length()).trim();
            if (LOG.isDebugEnabled()) {
                LOG.debug("the extracted column name is : <{}>", str);
            }
            return str;
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("the extracted column name is : <{}>", attrDataSourceName);
            }
            return attrDataSourceName;
        }
    }

    /**
     * Build the SELECT request.</br>
     * Compute keyword from crawling cursor mode
     *
     * @return the SELECT request
     */
    protected String getSelectRequest(CrawlingCursor cursor) {
        PageRequest pageable = getPageRequest(cursor);
        String selectRequest = sqlGenerator.selectAll(tableDescription, pageable);
        selectRequest = switch (getCrawlingCursorMode()) {
            case CRAWL_SINCE_LAST_UPDATE -> getSelectRequestWithCrawlerByLastUpdate(cursor, selectRequest);
            case CRAWL_FROM_LAST_ID -> getSelectRequestWithCrawlerById(selectRequest);
            default -> selectRequest;
        };

        return selectRequest;
    }

    private String getSelectRequestWithCrawlerById(String selectRequest) {
        return getSelectRequestWithWhereKeyword(selectRequest, LAST_ID_KEYWORD);
    }

    /**
     * Build the SELECT request.</br>
     * Add the key word in the WHERE clause.
     *
     * @return the SELECT request
     */
    private String getSelectRequestWithWhereKeyword(String selectRequest, String keyword) {
        // select based on id column
        if (selectRequest.contains(WHERE)) {
            // Add at the beginning of the where clause
            int pos = selectRequest.indexOf(WHERE);
            selectRequest = selectRequest.substring(0, pos) + WHERE + keyword + AND + selectRequest.substring(pos
                                                                                                              + WHERE.length());
        } else if (selectRequest.contains(ORDER_BY)) {
            // Add before the order by clause
            int pos = selectRequest.indexOf(ORDER_BY);
            selectRequest = selectRequest.substring(0, pos) + WHERE + keyword + SPACE + selectRequest.substring(pos);
        } else if (selectRequest.contains(LIMIT)) {
            // Add before the limit clause
            int pos = selectRequest.indexOf(LIMIT);
            selectRequest = selectRequest.substring(0, pos) + WHERE + keyword + SPACE + selectRequest.substring(pos);
        } else {
            // Add at the end of the request
            selectRequest = selectRequest + WHERE + keyword;
        }
        return selectRequest;
    }

    private String getSelectRequestWithCrawlerByLastUpdate(CrawlingCursor cursor, String selectRequest) {
        if ((cursor.getLastEntityDate() != null) && !getLastUpdateAttributeName().isEmpty()) {
            selectRequest = getSelectRequestWithWhereKeyword(selectRequest,
                                                             AbstractDataObjectMapping.LAST_MODIFICATION_DATE_KEYWORD);
        }
        return selectRequest;
    }

    private PageRequest getPageRequest(CrawlingCursor cursor) {
        Sort.Order defaultOrderSorting = new Sort.Order(Sort.Direction.ASC, orderByColumn);
        String customColumnSorting = null;
        if (getCrawlingCursorMode() == CrawlingCursorMode.CRAWL_FROM_LAST_ID) {
            customColumnSorting = columnId;
        } else if (!getLastUpdateAttributeName().isBlank()) {
            customColumnSorting = getLastUpdateAttributeName();
        }
        if (customColumnSorting != null && !customColumnSorting.equals(orderByColumn)) {
            return PageRequest.of(cursor.getPosition(),
                                  cursor.getSize(),
                                  Sort.by(new Sort.Order(Sort.Direction.ASC,
                                                         customColumnSorting,
                                                         Sort.NullHandling.NULLS_FIRST), defaultOrderSorting));
        } else {
            return PageRequest.of(cursor.getPosition(), cursor.getSize(), Sort.by(defaultOrderSorting));
        }
    }

    protected String getCountRequest(OffsetDateTime date) {
        if ((date == null) || getLastUpdateAttributeName().isEmpty()) {
            return sqlGenerator.count(tableDescription);
        } else {
            return sqlGenerator.count(tableDescription)
                   + WHERE
                   + AbstractDataObjectMapping.LAST_MODIFICATION_DATE_KEYWORD;
        }
    }

    protected String getCountRequest(Long id) {
        if (id == null) {
            return sqlGenerator.count(tableDescription);
        } else {
            return sqlGenerator.count(tableDescription) + WHERE + AbstractDataObjectMapping.LAST_ID_KEYWORD;
        }
    }

    @Override
    public List<DataObjectFeature> findAll(String tenant,
                                           CrawlingCursor cursor,
                                           @Nullable OffsetDateTime lastIngestionDate,
                                           OffsetDateTime currentIngestionStartDate) throws DataSourceException {
        if (sqlGenerator == null) {
            throw new DataSourceException("sqlGenerator is null");
        }
        final String selectRequest = getSelectRequest(cursor);
        final String countRequest = getCountRequest(cursor);

        try (Connection conn = getDBConnection().getConnection()) {

            return findAll(tenant, conn, selectRequest, countRequest, cursor);
        } catch (SQLException e) {
            // This exception can only be thrown from getDBConnection(), others have already been transformed into
            // DataSourceException
            throw new DataSourceException("Unable to obtain a database connection.", e);
        }
    }

    private String getCountRequest(CrawlingCursor cursor) {
        if (getCrawlingCursorMode() == CrawlingCursorMode.CRAWL_FROM_LAST_ID) {
            return getCountRequest(cursor.getLastId());
        } else {
            return getCountRequest(cursor.getLastEntityDate());
        }
    }
}
