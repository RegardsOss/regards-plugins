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

import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.modules.dam.domain.datasources.CrawlingCursor;
import fr.cnes.regards.modules.dam.domain.datasources.CrawlingCursorMode;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDBConnectionPlugin;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDBDataSourcePlugin;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A {@link Plugin} to retrieve the data elements from a SQL Database.</br>
 * This {@link Plugin} used a {@link IDBConnectionPlugin} to define to connection to the Database.
 *
 * @author Christophe Mertz
 */
public abstract class AbstractDBDataSourcePlugin extends AbstractDataObjectMapping implements IDBDataSourcePlugin {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractDBDataSourcePlugin.class);

    /**
     * By default, the refresh rate is set to 1 day (in ms)
     */
    @Override
    public int getRefreshRate() {
        return 86400;
    }

    protected abstract String getFromClause();

    protected String getSelectRequest(Pageable pageable, CrawlingCursor cursor) {
        String request = RequestHelper.SELECT + buildColumnClause(columns.toArray(new String[0])) + getFromClause();
        if (!getLastUpdateAttributeName().isEmpty() && cursor.getLastEntityDate() != null) {
            String additionalWhereClause = RequestHelper.WHERE
                                           + AbstractDataObjectMapping.LAST_MODIFICATION_DATE_KEYWORD;
            request = RequestHelper.mergeWhereClause(request, additionalWhereClause);
        } else if (cursor.getLastId() != null) {
            String additionalWhereClause = RequestHelper.WHERE + AbstractDataObjectMapping.LAST_ID_KEYWORD;
            request = RequestHelper.mergeWhereClause(request, additionalWhereClause);
        }
        return request + buildLimitPart(pageable);
    }

    protected String getCountRequest(CrawlingCursor cursor) {
        if (!getLastUpdateAttributeName().isEmpty() && cursor.getLastEntityDate() != null) {
            return RequestHelper.mergeWhereClause(RequestHelper.SELECT_COUNT + getFromClause(),
                                                  RequestHelper.WHERE
                                                  + AbstractDataObjectMapping.LAST_MODIFICATION_DATE_KEYWORD);
        } else if (cursor.getLastId() != null) {
            return RequestHelper.mergeWhereClause(RequestHelper.SELECT_COUNT + getFromClause(),
                                                  RequestHelper.WHERE + AbstractDataObjectMapping.LAST_ID_KEYWORD);
        } else {
            return RequestHelper.SELECT_COUNT + getFromClause();
        }

    }

    /**
     * @param sinceDate can be null
     */
    @Override
    public List<DataObjectFeature> findAll(String tenant, CrawlingCursor cursor, OffsetDateTime sinceDate)
        throws DataSourceException {
        final String selectRequest = getSelectRequest(PageRequest.of(cursor.getPosition(), cursor.getSize()), cursor);
        final String countRequest = getCountRequest(cursor);

        LOG.debug("select request: {}", selectRequest);
        LOG.debug("count request: {}", countRequest);

        try (Connection conn = getDBConnection().getConnection()) {
            return findAll(tenant, conn, selectRequest, countRequest, cursor);
        } catch (SQLException e) {
            throw new DataSourceException("Unable to obtain a database connection.", e);
        }
    }

    /**
     * Add to the SQL request the part to fetch only a portion of the results.
     *
     * @param pageable the page of the element to fetch
     * @return the SQL request
     */
    protected String buildLimitPart(Pageable pageable) {
        StringBuilder str = new StringBuilder(" ");
        final int offset = pageable.getPageNumber() * pageable.getPageSize();
        String orderByColumns = buildOrderByColumns();
        final String limit = String.format(RequestHelper.LIMIT_CLAUSE, orderByColumns, pageable.getPageSize(), offset);
        str.append(limit);

        return str.toString();
    }

    private String buildOrderByColumns() {
        String columns = orderByColumn;
        if (getCrawlingCursorMode() == CrawlingCursorMode.CRAWL_FROM_LAST_ID) {
            columns += "," + columnId;
        } else if (!getLastUpdateAttributeName().isBlank()) {
            columns += "," + getLastUpdateAttributeName();
        }
        return columns;
    }
}
