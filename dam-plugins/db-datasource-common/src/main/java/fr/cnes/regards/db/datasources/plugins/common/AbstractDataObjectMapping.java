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

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.oais.urn.OAISIdentifier;
import fr.cnes.regards.framework.oais.urn.OaisUniformResourceName;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.dam.domain.datasources.AbstractAttributeMapping;
import fr.cnes.regards.modules.dam.domain.datasources.CrawlingCursor;
import fr.cnes.regards.modules.dam.domain.datasources.CrawlingCursorMode;
import fr.cnes.regards.modules.dam.domain.datasources.Table;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.model.dto.properties.MarkdownURL;
import fr.cnes.regards.modules.model.service.IModelService;
import org.dom4j.tree.AbstractAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * This class allows to process a SQL request to a SQL Database.</br>
 * For each data reads in the Database, a {@link DataObject} is created. This {@link DataObject} are compliant with a
 * {@link Model}.</br>
 * Some attributes extracts from the Database are specials. For each one, a {@link DataObject} property is set :
 * <li>the primary key of the data
 * <li>the data file of the data
 * <li>the thumbnail of the data
 * <li>the update date of the data
 *
 * @author Christophe Mertz
 */
public abstract class AbstractDataObjectMapping extends AbstractDataSourcePlugin {

    /**
     * The PL/SQL key word AS
     */
    protected static final String AS = "as ";

    /**
     * A pattern used to set a date in the statement
     */
    protected static final String LAST_MODIFICATION_DATE_KEYWORD = "%last_modification_date%";

    /**
     * A pattern used to set id in the statement
     */
    protected static final String LAST_ID_KEYWORD = "%last_id%";

    /**
     * Class logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(AbstractDataObjectMapping.class);

    private static final String BLANK = " ";

    /**
     * A comma used to build the select clause
     */
    private static final String COMMA = ",";

    @Autowired
    private Gson gson;

    /**
     * A default value to indicates that the count request should be execute
     */
    private static final int RESET_COUNT = -1;

    /**
     * The {@link List} of columns used by this {@link Plugin} to requests the database. This columns are in the
     * {@link Table}.
     */
    protected List<String> columns;

    /**
     * The column name used in the ORDER BY clause
     */
    protected String orderByColumn = "";

    /**
     * The {@link Model} identifier
     */
    protected Model model;

    /**
     * The mapping between the attribute of the {@link Model} of the attributes of th data source
     */
    protected List<AbstractAttributeMapping> attributesMapping;

    @Autowired
    private IModelService modelService;

    /**
     * Common tags to be added on all created data objects
     */
    private Collection<String> commonTags;

    /**
     * The result of the count request
     */
    private int nbItems = RESET_COUNT;

    /**
     * The attribute name used for the date comparison
     */
    private String lastUpdateAttributeName = "";

    @Override
    public CrawlingCursorMode getCrawlingCursorMode() {
        if (columnId != null) {
            return CrawlingCursorMode.CRAWL_FROM_LAST_ID;
        }
        if (getLastUpdateAttributeName().isBlank()) {
            return CrawlingCursorMode.CRAWL_EVERYTHING;
        } else {
            return CrawlingCursorMode.CRAWL_SINCE_LAST_UPDATE;
        }
    }

    /**
     * Get {@link fr.cnes.regards.modules.model.dto.properties.DateProperty}.
     *
     * @param rs       the {@link ResultSet}
     * @param attrName the attribute name
     *                 àparam attrDSName the column name in the external data source
     * @param colName  the column name in the {@link ResultSet}
     * @return a new {@link fr.cnes.regards.modules.model.dto.properties.DateProperty}
     * @throws SQLException if an error occurs in the {@link ResultSet}
     */
    protected abstract IProperty<?> buildDateAttribute(ResultSet rs, String attrName, String attrDSName, String colName)
        throws SQLException;

    /**
     * Get a {@link LocalDateTime} value from a {@link ResultSet} for a {@link AbstractAttributeMapping}.
     *
     * @param rs      The {@link ResultSet} to read
     * @param colName the column name in the {@link ResultSet}
     * @return the {@link OffsetDateTime}
     * @throws SQLException An error occurred when try to read the {@link ResultSet}
     */
    protected OffsetDateTime buildOffsetDateTime(ResultSet rs, String colName) throws SQLException {
        long n = rs.getTimestamp(colName).getTime();
        Instant instant = Instant.ofEpochMilli(n);
        return OffsetDateTime.ofInstant(instant, ZoneId.of("UTC"));
    }

    /**
     * Returns a page of DataObject from the database defined by the {@link Connection} and corresponding to the SQL. A
     * {@link Date} is apply to filter the {@link DataObject} created or updated after this {@link Date}.
     *
     * @param tenant          the tenant name
     * @param ctx             a {@link Connection} to a database
     * @param inSelectRequest the SQL request
     * @param inCountRequest  the SQL count request
     * @param sinceDate       a {@link Date} used to apply returns the {@link DataObject} update or create after this date
     * @return a page of {@link DataObject}
     */
    protected List<DataObjectFeature> findAll(String tenant,
                                              Connection ctx,
                                              String inSelectRequest,
                                              String inCountRequest,
                                              CrawlingCursor cursor) throws DataSourceException {
        List<DataObjectFeature> features = null;

        try (Statement statement = ctx.createStatement()) {

            String selectRequest = inSelectRequest;
            String countRequest = inCountRequest;

            if (getCrawlingCursorMode() == CrawlingCursorMode.CRAWL_FROM_LAST_ID) {
                selectRequest = buildIdStatement(selectRequest, cursor);
                countRequest = buildIdStatement(countRequest, cursor);
            } else if (cursor.getLastEntityDate() != null) {
                selectRequest = buildDateStatement(selectRequest, cursor.getLastEntityDate());
                countRequest = buildDateStatement(countRequest, cursor.getLastEntityDate());
            }
            LOG.info("select request : {}", selectRequest);
            LOG.info("count request : {}", countRequest);

            // Execute the request to get the elements
            try (ResultSet rs = statement.executeQuery(selectRequest)) {
                features = processResults(rs, tenant, cursor);
            }
            countItems(statement, countRequest);
            // 3) Update cursor for next iteration
            // compute the total number of pages to process and see if there are still requests to be performed
            int totalNumOfPages = nbItems == 0 ? 1 : (int) Math.ceil((double) nbItems / (double) cursor.getSize());
            cursor.setHasNext(cursor.getPosition() + 1 < totalNumOfPages);

        } catch (SQLException e) {
            throw new DataSourceException("Error while retrieving or counting datasource elements", e);
        }
        return features;
    }

    private List<DataObjectFeature> processResults(ResultSet rs, String tenant, CrawlingCursor cursor)
        throws SQLException, DataSourceException {
        List<DataObjectFeature> features = new ArrayList<>();
        switch (getCrawlingCursorMode()) {
            case CRAWL_EVERYTHING -> {
                while (rs.next()) {
                    features.add(processResultSet(rs, tenant));
                }
            }
            case CRAWL_SINCE_LAST_UPDATE -> {
                Optional<AbstractAttributeMapping> column = computeLastUpdateDateColumn();
                if (column.isPresent()) {
                    while (rs.next()) {
                        IProperty<?> attr = buildAttribute(rs, column.get());
                        features.add(processResultSet(rs, tenant));
                        if (attr != null && attr.getValue() != null) {
                            cursor.setCurrentLastEntityDate(OffsetDateTime.parse(attr.getValue().toString()));
                        } else {
                            // this case will never happen because a where clause on specific column will exclude
                            // null values
                            throw new DataSourceException("last update date must not be null");
                        }
                    }
                }
            }
            case CRAWL_FROM_LAST_ID -> {
                while (rs.next()) {
                    features.add(processResultSet(rs, tenant));
                    cursor.setCurrentLastId(rs.getLong(columnId));
                    LOG.info("Read line with id {} and columnId {} ", rs.getLong("id"), rs.getLong(columnId));
                }
            }
        }
        return features;
    }

    /**
     * Returns the column id (type Long), the last_update column mapped, or null if none indicated
     */
    private Optional<AbstractAttributeMapping> computeLastUpdateDateColumn() {
        return attributesMapping.stream()
                                .filter(mapping -> mapping.getNameDS().equals(getLastUpdateAttributeName()))
                                .findFirst();
    }

    /**
     * Execute a SQL request to count the number of items
     *
     * @param statement    a {@link Statement} used to execute the SQL request
     * @param countRequest the SQL count request
     * @throws SQLException an SQL error occurred
     */
    private void countItems(Statement statement, String countRequest) throws SQLException {
        if ((countRequest != null) && !countRequest.isEmpty()) {
            // Execute the request to count the elements
            try (ResultSet rsCount = statement.executeQuery(countRequest)) {
                if (rsCount.next()) {
                    nbItems = rsCount.getInt(1);
                }
            }
        }
    }

    /**
     * Build a {@link DataObject} for a {@link ResultSet}.
     *
     * @param rset the {@link ResultSet}
     * @return the {@link DataObject} created
     * @throws SQLException An SQL error occurred
     */
    protected DataObjectFeature processResultSet(ResultSet rset, String tenant)
        throws SQLException, DataSourceException {

        DataObjectFeature feature = new DataObjectFeature(OaisUniformResourceName.pseudoRandomUrn(OAISIdentifier.AIP,
                                                                                                  EntityType.DATA,
                                                                                                  tenant,
                                                                                                  1),
                                                          "providerIdPlaceHolder",
                                                          "labelPlaceHolder");

        Set<IProperty<?>> attributes = new HashSet<>();
        Map<String, List<IProperty<?>>> spaceNames = Maps.newHashMap();

        // Loop the attributes in the mapping
        for (AbstractAttributeMapping attrMapping : attributesMapping) {
            IProperty<?> attr = buildAttribute(rset, attrMapping);

            if (attr != null) {
                if (attrMapping.isMappedToStaticProperty()) {
                    // static attribute mapping
                    processStaticAttributes(feature, attr, attrMapping);
                } else {
                    // dynamic attribute mapping
                    if (!Strings.isNullOrEmpty(attrMapping.getNameSpace())) {
                        if (!spaceNames.containsKey(attrMapping.getNameSpace())) {
                            // It is a new name space
                            spaceNames.put(attrMapping.getNameSpace(), new ArrayList<>());
                        }
                        // Add the attribute to the namespace
                        spaceNames.get(attrMapping.getNameSpace()).add(attr);
                    } else {
                        attributes.add(attr);
                    }
                }
            }
        }

        //For each name space, add an ObjectAttribute to the list of attribute
        spaceNames.forEach((name, attrs) -> attributes.add(IProperty.buildObject(name,
                                                                                 attrs.toArray(new IProperty[0]))));

        feature.setProperties(attributes);

        // Add common tags
        if ((commonTags != null) && (!commonTags.isEmpty())) {
            feature.addTags(commonTags.toArray(new String[0]));
        }

        return feature;
    }

    /**
     * Get an attribute define in the mapping in a {@link ResultSet}
     *
     * @param rset        the {@link ResultSet}
     * @param attrMapping the {@link AbstractAttributeMapping}
     * @return a new {@link AbstractAttribute}
     * @throws SQLException if an error occurs in the {@link ResultSet}
     */
    private IProperty<?> buildAttribute(ResultSet rset, AbstractAttributeMapping attrMapping)
        throws SQLException, DataSourceException {
        IProperty<?> attr;
        final String colName = extractColumnName(attrMapping.getNameDS(),
                                                 attrMapping.getName(),
                                                 attrMapping.isPrimaryKey());

        switch (attrMapping.getType()) {
            // lets handle touchy cases by hand
            case URL:
                try {
                    attr = IProperty.buildUrl(attrMapping.getName(), MarkdownURL.build(rset.getString(colName)));
                } catch (MalformedURLException e) {
                    String message = String.format("Given url into database (column %s) could not be processed as a URL",
                                                   colName);
                    throw new DataSourceException(message, e);
                }
                break;
            case DATE_ISO8601:
                attr = buildDateAttribute(rset, attrMapping.getName(), attrMapping.getNameDS(), colName);
                break;
            // if it is not a touchy case, lets use the general way
            default:
                attr = IProperty.forType(attrMapping.getType(), attrMapping.getName(), rset.getObject(colName));
                break;
        }
        // If value was null => no attribute value
        if (rset.wasNull()) {
            return null;
        }

        if (LOG.isDebugEnabled() && (attr != null)) {
            if ((attrMapping.getName() != null) && attrMapping.getName().equals(attrMapping.getNameDS())) {
                LOG.debug("the value for <{}> of type <{}> is :{}",
                          attrMapping.getName(),
                          attrMapping.getType(),
                          attr.getValue());

            } else {
                LOG.debug("the value for <{}|{}> of type <{}> is :{}",
                          attrMapping.getName(),
                          attrMapping.getNameDS(),
                          attrMapping.getType(),
                          attr.getValue());
            }
        }

        return attr;
    }

    /**
     * Extracts a column name from a PL/SQL expression.</br>
     * The column label can be placed after the word 'AS'.
     * If 'AS' is not present the column name is the internal attribute name.
     *
     * @param attrDataSourceName The PL/SQL expression to analyze
     * @param attrName           The attribute name
     * @return the column label extracted from the PL/SQL
     */
    protected String extractColumnName(String attrDataSourceName, String attrName, boolean isPrimaryKey) {
        String colName;

        int pos = attrDataSourceName.toLowerCase().lastIndexOf(AS);

        if (pos > 0) {
            String str = attrDataSourceName.substring(pos + AS.length()).trim();
            LOG.debug("the extracted column name is : <{}>", str);
            colName = str;
        } else {
            LOG.debug("the extracted column name is : <{}>", attrName);
            if (isPrimaryKey) {
                colName = attrDataSourceName;
            } else {
                colName = attrName + "_";
            }
        }

        return colName;
    }

    /**
     * This class extracts data information from an attribute and sets this informations into the
     * {@link DataObject}.</br>
     * The REGARDS internal attributes's that are analyzed :
     * <li>primary key
     * <li>raw data
     * <li>thumbnail
     * <li>label
     * <li>last flag
     * <li>last update date
     * <li>geometry
     *
     * @param dataObject  the current {@link DataObject} to build
     * @param attr        the current {@link IProperty} to analyze
     * @param attrMapping the {@link AbstractAttributeMapping} for the current attribute
     */
    private void processStaticAttributes(DataObjectFeature dataObject,
                                         IProperty<?> attr,
                                         AbstractAttributeMapping attrMapping) {
        if (attrMapping.isPrimaryKey()) {
            String val = attr.getValue().toString();
            dataObject.setProviderId(val);
            // providerId being the primary key, we cannot have multiple entities with the same providerId
            // so we cannot have multiple versions of the same entity
            dataObject.setLast(true);
        }

        // Manage files
        if (attrMapping.isRawData() || attrMapping.isThumbnail()) {
            String str = (String) attr.getValue();

            try {
                // Check if attribute is a valid URL
                new URL(str);
                // Compute data type
                DataType type = attrMapping.isRawData() ? DataType.RAWDATA : DataType.THUMBNAIL;
                // Compute mime type
                MimeType mimeType;
                if (attrMapping.isRawData()) {
                    mimeType = MediaType.APPLICATION_OCTET_STREAM;
                } else {
                    // Detect mime type according to extension for THUMBNAIL
                    if (str.endsWith(".PNG") || str.endsWith(".png")) {
                        mimeType = MediaType.IMAGE_PNG;
                    } else if (str.endsWith(".GIF") || str.endsWith(".gif")) {
                        mimeType = MediaType.IMAGE_GIF;
                    } else if (str.endsWith(".JPG") || str.endsWith(".jpg") || str.endsWith(".JPEG") || str.endsWith(
                        ".jpeg")) {
                        mimeType = MediaType.IMAGE_JPEG;
                    } else {
                        throw new IllegalArgumentException("Unsupported image extension for " + str);
                    }
                }
                String filename = str.contains("/") ? str.substring(str.lastIndexOf('/') + 1) : str;
                DataFile dataFile = DataFile.build(type, filename, str, mimeType, Boolean.TRUE, Boolean.TRUE);
                dataObject.getFiles().put(type, dataFile);
            } catch (MalformedURLException e) {
                LOG.warn(String.format("Invalid URL mapped from database for dataobject %s. Value=%s",
                                       dataObject.getProviderId(),
                                       str), e);
            }
        }

        if (attrMapping.isLabel()) {
            dataObject.setLabel((String) attr.getValue());
        }
        if (attrMapping.isGeometry()) {
            String str = (String) attr.getValue();
            dataObject.setGeometry(gson.fromJson(str, IGeometry.class));
        }
    }

    /**
     * Build the select clause with the {@link List} of columns used for the mapping.
     *
     * @param columns the columns used for the mapping
     * @return a {@link String} with the columns separated by a comma
     */
    protected String buildColumnClause(String... columns) {
        StringBuilder clauseStr = new StringBuilder();
        for (String col : columns) {
            clauseStr.append(col).append(COMMA);
        }
        if (columnId != null) {
            clauseStr.append(columnId).append(COMMA);
        }
        return clauseStr.substring(0, clauseStr.length() - 1) + BLANK;
    }

    /**
     * Replace the key word '%last_modification_date%' in the request to get the data from a date
     *
     * @param request the SQL request
     * @param date    the date to be used for building date filter
     * @return the SQL request with a from clause to filter the result since a date
     */
    private String buildDateStatement(String request, OffsetDateTime date) throws DataSourceException {
        // Any attribute is defined in the mapping for compare the date, return
        if (getLastUpdateAttributeName().isEmpty()) {
            return request;
        }
        return request.replaceAll(LAST_MODIFICATION_DATE_KEYWORD,
                                  getLastUpdateAttributeName()
                                  + " > '"
                                  + getLastUpdateValue(getLastUpdateAttributeName(), date)
                                  + "'");
    }

    /**
     * Replace the key word '%last_id%' in the request to get the data from a date
     */
    private String buildIdStatement(String request, CrawlingCursor cursor) throws DataSourceException {
        if (cursor.getLastId() != null) {
            String sign = uniqueId ? " > " : " >= ";
            return request.replaceAll(LAST_ID_KEYWORD, columnId + sign + cursor.getLastId());
        } else {
            return request.replaceAll(LAST_ID_KEYWORD, columnId + " > " + Long.MIN_VALUE);
        }
    }

    protected abstract String getLastUpdateValue(String lastUpdateColumnName, OffsetDateTime date)
        throws DataSourceException;

    /**
     * This method reset the number of data element from the database.<br>
     */
    protected void reset() {
        nbItems = RESET_COUNT;
    }

    /**
     * Init with parameters given directly on plugins with @PluginParameter annotation
     */
    protected void init(String modelName,
                        List<AbstractAttributeMapping> attributesMapping,
                        Collection<String> commonTags) throws ModuleException {
        this.model = modelService.getModelByName(modelName);
        this.attributesMapping = attributesMapping;
        this.commonTags = commonTags;

        extractColumnsFromMapping();
    }

    protected String getLastUpdateAttributeName() {
        if (!lastUpdateAttributeName.isEmpty()) {
            return lastUpdateAttributeName;
        }

        for (AbstractAttributeMapping attMapping : attributesMapping) {
            if (attMapping.isLastUpdate()) {
                lastUpdateAttributeName = attMapping.getNameDS();
                LOG.debug("Attribute for date comparison found: {}", lastUpdateAttributeName);
                break;
            }
        }
        return lastUpdateAttributeName;
    }

    /**
     * This method extracts the {@link List} of columns from the data source mapping.
     */
    private void extractColumnsFromMapping() {
        if (columns == null) {
            columns = new ArrayList<>();
        }

        attributesMapping.forEach(d -> {
            if ((0 > d.getNameDS().toLowerCase().lastIndexOf(AS)) && !d.isPrimaryKey()) {
                columns.add(d.getNameDS() + BLANK + AS + d.getName() + "_");
            } else {
                columns.add(d.getNameDS());
            }

            if (d.isPrimaryKey()) {
                orderByColumn = d.getNameDS();
            }
        });
    }
}
