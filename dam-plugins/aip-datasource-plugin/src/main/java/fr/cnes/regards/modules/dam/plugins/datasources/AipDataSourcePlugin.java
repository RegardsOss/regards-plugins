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
package fr.cnes.regards.modules.dam.plugins.datasources;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import fr.cnes.regards.framework.amqp.IInstanceSubscriber;
import fr.cnes.regards.framework.amqp.domain.IHandler;
import fr.cnes.regards.framework.feign.security.FeignSecurityManager;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.oais.dto.ContentInformationDto;
import fr.cnes.regards.framework.oais.dto.OAISDataObjectDto;
import fr.cnes.regards.framework.oais.dto.OAISDataObjectLocationDto;
import fr.cnes.regards.framework.oais.dto.aip.AIPDto;
import fr.cnes.regards.framework.oais.dto.urn.OaisUniformResourceName;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.utils.RsRuntimeException;
import fr.cnes.regards.framework.utils.plugins.PluginUtilsRuntimeException;
import fr.cnes.regards.modules.dam.domain.datasources.CrawlingCursor;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IInternalDataSourcePlugin;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.fileaccess.dto.StorageLocationDto;
import fr.cnes.regards.modules.fileaccess.dto.StorageType;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import fr.cnes.regards.modules.ingest.client.IAIPRestClient;
import fr.cnes.regards.modules.ingest.domain.aip.AIPEntity;
import fr.cnes.regards.modules.ingest.dto.AIPState;
import fr.cnes.regards.modules.ingest.dto.aip.SearchAIPsParameters;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.domain.ModelAttrAssoc;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.model.dto.properties.ObjectProperty;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import fr.cnes.regards.modules.model.service.IModelAttrAssocService;
import fr.cnes.regards.modules.model.service.IModelService;
import fr.cnes.regards.modules.project.client.rest.IProjectsClient;
import fr.cnes.regards.modules.project.domain.Project;
import fr.cnes.regards.modules.project.domain.ProjectUpdateEvent;
import fr.cnes.regards.modules.storage.client.IStorageRestClient;
import org.apache.commons.beanutils.NestedNullException;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.util.UriUtils;

import jakarta.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Plugin to crawl data from OAIS feature manager (ingest microservice).
 * {@link AIPEntity}s are converted into standard {@link DataObjectFeature} to be inserted in Elasticsearch catalog
 *
 * @author Simon MILHAU
 */
@Plugin(id = "aip-storage-datasource",
        version = "1.0-SNAPSHOT",
        description = "Allows data extraction from AIP storage",
        author = "REGARDS Team",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CSSI",
        url = "https://github.com/RegardsOss")
public class AipDataSourcePlugin implements IInternalDataSourcePlugin, IHandler<ProjectUpdateEvent> {

    /**
     * Property in AIP contentInformation.representationInformation.environmentDescription.softwareEnvironment to
     * add custom types on data files.
     */
    static final String AIP_PROPERTY_DATA_FILES_TYPES = "types";

    private static final Logger LOGGER = LoggerFactory.getLogger(AipDataSourcePlugin.class);

    private static final String CATALOG_DOWNLOAD_PATH = "/downloads/{aip_id}/files/{checksum}";

    // -------------------------
    // --- PLUGIN PARAMETERS ---
    // -------------------------

    @PluginParameter(name = DataSourcePluginConstants.TAGS,
                     label = "data objects common tags",
                     optional = true,
                     description = "Common tags to be put on all data objects created by the data source")
    private Set<String> commonTags;

    @PluginParameter(name = DataSourcePluginConstants.MODEL_NAME_PARAM,
                     label = "model name",
                     description = "Associated data source model name")
    protected String modelName;

    @PluginParameter(name = DataSourcePluginConstants.SUBSETTING_TAGS,
                     label = "Subsetting tags",
                     optional = true,
                     description = "The plugin will fetch data storage to find AIPs tagged with these specified tags to obtain an AIP subset. If no tag is specified, plugin will fetch all the available AIPs.")
    private Set<String> subsettingTags;

    @PluginParameter(name = DataSourcePluginConstants.SUBSETTING_CATEGORIES,
                     label = "Subsetting categories",
                     optional = true,
                     description = "The plugin will fetch data storage to find AIPs with the given catories. If no category is specified, plugin will fetch all the available AIPs.")
    private Set<String> categories;

    @PluginParameter(name = DataSourcePluginConstants.BINDING_MAP,
                     keylabel = "Model property path",
                     label = "AIP property path",
                     description = "Binding map between model and AIP (i.e. Property chain from model and its associated property chain from AIP format")
    private Map<String, String> bindingMap;

    /**
     * Ingestion refresh rate in seconds
     */
    @PluginParameter(name = DataSourcePluginConstants.REFRESH_RATE,
                     defaultValue = "86400",
                     optional = true,
                     label = "refresh rate",
                     description = "Ingestion refresh rate in seconds (minimum delay between two consecutive ingestions)")
    private Integer refreshRate;

    @PluginParameter(name = DataSourcePluginConstants.MODEL_ATTR_FILE_SIZE,
                     optional = true,
                     label = "Attribute model for RAW DATA files size",
                     description = "This parameter is used to define which model attribute is used to map the RAW DATA files sizes")
    private String modelAttrNameFileSize;

    /**
     * Return duration in seconds to retrieve entities by maximum date limit to avoid retrieving entities to close with
     * the current aspiration date.
     */
    @PluginParameter(name = "searchLimitFromNowInSeconds",
                     label = "Overlap",
                     description = "Duration in seconds to retrieve entities by maximum date limit to avoid "
                                   + "retrieving entities to close with the current aspiration date",
                     defaultValue = "60")
    private long searchLimitFromNowInSeconds;

    @Value("${prefix.path}")
    String urlPrefix;

    /**
     * Association table between JSON path property and its type from model
     */
    private final Map<String, PropertyType> modelMappingMap = new HashMap<>();

    /**
     * Map of {@link Project}s by tenant
     */
    private final Map<String, Project> projects = new HashMap<>();

    /**
     * Association table between JSON path property and its mapping values.<br/>
     * In general, single value is mapped.<br/>
     * For interval, two values has to be mapped.
     */
    private final Map<String, List<String>> modelBindingMap = new HashMap<>();

    /**
     * Initialize AIP properties resolver
     */
    private final PropertyUtilsBean propertyUtilsBean = new PropertyUtilsBean();

    private final Multimap<String, String> multimap = HashMultimap.create();

    // -------------------------
    // ------- SERVICES --------
    // -------------------------

    @Autowired
    private IModelService modelService;

    @Autowired
    private IModelAttrAssocService modelAttrAssocService;

    @Autowired
    private IInstanceSubscriber subscriber;

    @Autowired
    private IAIPRestClient aipClient;

    @Autowired
    private IStorageRestClient storageRestClient;

    @Autowired
    private IProjectsClient projectClient;

    private static String encode4Uri(String str) {
        return new String(UriUtils.encode(str, Charset.defaultCharset().name()).getBytes(), StandardCharsets.US_ASCII);
    }

    /**
     * Init method
     */
    @PluginInit
    private void initPlugin() throws ModuleException {
        Model model = modelService.getModelByName(modelName);
        if (model == null) {
            throw new ModuleException(String.format("Model '%s' does not exist.", modelName));
        }

        subscriber.subscribeTo(ProjectUpdateEvent.class, this);

        List<ModelAttrAssoc> modelAttrAssocs = modelAttrAssocService.getModelAttrAssocs(modelName);
        // Fill map ["properties.titi.tutu", AttributeType.STRING]
        for (ModelAttrAssoc assoc : modelAttrAssocs) {
            modelMappingMap.put(assoc.getAttribute().getJsonPath(), assoc.getAttribute().getType());
        }

        // Build binding map considering interval double mapping
        for (Map.Entry<String, String> entry : bindingMap.entrySet()) {
            if (entry.getKey().startsWith(DataSourcePluginConstants.PROPERTY_PREFIX)) {
                String doPropertyPath = entry.getKey();
                // Manage dynamic properties
                if (doPropertyPath.endsWith(DataSourcePluginConstants.LOWER_BOUND_SUFFIX)) {
                    // - interval lower bound
                    String modelKey = entry.getKey()
                                           .substring(0,
                                                      doPropertyPath.length()
                                                      - DataSourcePluginConstants.LOWER_BOUND_SUFFIX.length());
                    if (modelBindingMap.containsKey(modelKey)) {
                        // Add lower bound value at index 0
                        modelBindingMap.get(modelKey).add(0, entry.getValue());
                    } else {
                        List<String> values = new ArrayList<>();
                        values.add(entry.getValue());
                        modelBindingMap.put(modelKey, values);
                    }
                } else if (doPropertyPath.endsWith(DataSourcePluginConstants.UPPER_BOUND_SUFFIX)) {
                    // - interval upper bound
                    String modelKey = entry.getKey()
                                           .substring(0,
                                                      doPropertyPath.length()
                                                      - DataSourcePluginConstants.UPPER_BOUND_SUFFIX.length());
                    if (modelBindingMap.containsKey(modelKey)) {
                        // Add upper bound value at index 1
                        modelBindingMap.get(modelKey).add(entry.getValue());
                    } else {
                        List<String> values = new ArrayList<>();
                        values.add(entry.getValue());
                        modelBindingMap.put(modelKey, values);
                    }
                } else {
                    // - others : propagate properties
                    modelBindingMap.put(doPropertyPath, List.of(entry.getValue()));
                }
            } else {
                // Propagate properties
                modelBindingMap.put(entry.getKey(), List.of(entry.getValue()));
            }
        }

        // All bindingMap values should be JSON path properties from model so each of them starting with PROPERTY_PREFIX
        // must exist as a value into modelMappingMap
        Set<String> notInModelProperties = modelBindingMap.keySet()
                                                          .stream()
                                                          .filter(name -> name.startsWith(DataSourcePluginConstants.PROPERTY_PREFIX))
                                                          .filter(name -> !modelMappingMap.containsKey(name))
                                                          .collect(Collectors.toSet());
        if (!notInModelProperties.isEmpty()) {
            throw new ModuleException(String.format("Following properties don't exist into model : %s",
                                                    String.join(", ", notInModelProperties)));
        }
        DataObject forIntrospection = new DataObject();
        PropertyUtilsBean property = new PropertyUtilsBean();
        Set<String> notInModelStaticProperties = modelBindingMap.keySet()
                                                                .stream()
                                                                .filter(name -> !name.startsWith(
                                                                    DataSourcePluginConstants.PROPERTY_PREFIX))
                                                                .filter(name -> !property.isWriteable(forIntrospection,
                                                                                                      name))
                                                                .collect(Collectors.toSet());
        if (!notInModelStaticProperties.isEmpty()) {
            throw new ModuleException("Following static properties don't exist : " + Joiner.on(", ")
                                                                                           .join(notInModelProperties));
        }

        // Check number of values mapped for each type
        for (Map.Entry<String, List<String>> entry : modelBindingMap.entrySet()) {
            if (entry.getKey().startsWith(DataSourcePluginConstants.PROPERTY_PREFIX)) {
                PropertyType attributeType = modelMappingMap.get(entry.getKey());
                if (attributeType.isInterval()) {
                    if (entry.getValue().size() != 2) {
                        throw new ModuleException(String.format("%s properties %s has to be mapped to exactly 2 values",
                                                                attributeType,
                                                                entry.getKey()));
                    }
                } else {
                    if (entry.getValue().size() != 1) {
                        throw new ModuleException(String.format("%s properties %s has to be mapped to a single value",
                                                                attributeType,
                                                                entry.getKey()));
                    }
                }
            }
        }
    }

    @Override
    public int getRefreshRate() {
        return refreshRate;
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public void handle(String tenant, ProjectUpdateEvent projectEvent) {
        if (projects.get(projectEvent.getProject().getName()) != null) {
            projects.put(projectEvent.getProject().getName(), projectEvent.getProject());
        }
    }

    @Override
    public List<DataObjectFeature> findAll(String tenant,
                                           CrawlingCursor cursor,
                                           @Nullable OffsetDateTime lastIngestDate,
                                           OffsetDateTime currentIngestionStartDate) throws DataSourceException {
        try {
            FeignSecurityManager.asSystem();
            Storages storages = getStorageLocations();

            // 1) Get all storage locations and aipEntities to process
            PagedModel<EntityModel<AIPEntity>> pageAipEntities = getAipEntities(cursor, currentIngestionStartDate);
            Collection<EntityModel<AIPEntity>> aipEntities = pageAipEntities.getContent();
            if (aipEntities.isEmpty()) {
                cursor.setHasNext(false);
                return Collections.emptyList();
            }
            // 2) Build dataObjectFeatures only from DATA aipEntities
            List<DataObjectFeature> dataObjects = convertAIPEntitiesToDataObjects(tenant, aipEntities, storages);

            // 3) Update cursor for next iteration
            // determine if there is a next page to search after this one
            cursor.setHasNext(pageAipEntities.getNextLink().isPresent());
            // set current last update date with the most recent aip entity update.
            cursor.setCurrentLastEntityDate(aipEntities.stream()
                                                       .map(entityModel -> Objects.requireNonNull(entityModel.getContent())
                                                                                  .getLastUpdate())
                                                       .max(Comparator.comparing(lastUpdate -> lastUpdate))
                                                       .orElse(null));
            return dataObjects;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new DataSourceException(String.format(
                "An error occurred during the processing of aipEntities. Cause %s: ",
                e.getMessage()), e);
        } finally {
            FeignSecurityManager.reset();
        }
    }

    /**
     * Build {@link DataObjectFeature} features from a {@link AIPEntity}s
     *
     * @throws DataSourceException      in case call to {@link #getDownloadUrl(OaisUniformResourceName, String, String)} fails
     * @throws HttpClientErrorException same as above
     * @throws HttpServerErrorException same as above
     */
    private List<DataObjectFeature> convertAIPEntitiesToDataObjects(String tenant,
                                                                    Collection<EntityModel<AIPEntity>> aipEntities,
                                                                    Storages storages) throws DataSourceException {
        List<DataObjectFeature> dataObjects = new ArrayList<>();
        for (EntityModel<AIPEntity> entityModel : aipEntities) {
            AIPEntity aipEntity = entityModel.getContent();
            multimap.put(tenant, Objects.requireNonNull(aipEntity).getAip().getId().toString());
            try {
                dataObjects.add(buildFeature(aipEntity, storages, tenant));
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new PluginUtilsRuntimeException(e);
            }
        }
        return dataObjects;
    }

    /**
     * Search for all storages locations
     *
     * @throws DataSourceException in case locations could not be retrieved
     */
    private Storages getStorageLocations() throws DataSourceException {
        ResponseEntity<List<EntityModel<StorageLocationDto>>> storageResponse = storageRestClient.retrieve();

        if (!storageResponse.getStatusCode().is2xxSuccessful() || !storageResponse.hasBody()) {
            throw new DataSourceException(String.format(
                "Cannot fetch storage locations list. (HTTP STATUS: %s, BODY: %s)",
                storageResponse.getStatusCode(),
                storageResponse.getBody()));
        }
        return Storages.build(Objects.requireNonNull(storageResponse.getBody())
                                     .stream()
                                     .filter(entityModel -> entityModel.getContent() != null)
                                     .map(EntityModel::getContent)
                                     .toList());
    }

    /**
     * Search a page of {@link AIPEntity}s by several criteria
     *
     * @param cursor aipEntities should be retrieved with a lastUpdate >= {@link CrawlingCursor#getLastEntityDate()}
     * @throws DataSourceException in case aipEntities could not be retrieved
     */
    private PagedModel<EntityModel<AIPEntity>> getAipEntities(CrawlingCursor cursor,
                                                              OffsetDateTime currentIngestionStartDate)
        throws DataSourceException {

        // /!\ In order to avoid some missing features from ingest service, search for entities
        OffsetDateTime searchMinDate = cursor.getLastEntityDate();
        OffsetDateTime searchMaxDate = currentIngestionStartDate.minusSeconds(searchLimitFromNowInSeconds);
        if (searchMinDate != null && searchMaxDate.isBefore(searchMinDate)) {
            // Ensure range is valid
            searchMaxDate = searchMinDate;
        }

        // /!\ this sorting is very important as it allows the db to retrieve aipEntities always in the same order, to avoid any entity to be skipped.
        // entities must always be sorted by lastUpdate and id ASC. Handles nulls first, otherwise, these entities will never be processed.
        Sort sorting = Sort.by(new Sort.Order(Sort.Direction.ASC, "lastUpdate", Sort.NullHandling.NULLS_FIRST),
                               new Sort.Order(Sort.Direction.ASC, "id"));
        SearchAIPsParameters filters = new SearchAIPsParameters().withAipIpType(Arrays.asList(EntityType.DATA))
                                                                 .withStatesIncluded(Arrays.asList(AIPState.STORED))
                                                                 .withLastUpdateAfter(searchMinDate)
                                                                 .withLastUpdateBefore(searchMaxDate);

        if (!CollectionUtils.isEmpty(subsettingTags)) {
            filters.withTagsIncluded(subsettingTags);
        }
        if (!CollectionUtils.isEmpty(categories)) {
            filters.withCategoriesIncluded(categories);
        }
        ResponseEntity<PagedModel<EntityModel<AIPEntity>>> aipResponseEntity = aipClient.searchAIPs(filters,
                                                                                                    cursor.getPosition(),
                                                                                                    cursor.getSize(),
                                                                                                    sorting);
        if (!aipResponseEntity.getStatusCode().is2xxSuccessful() || !aipResponseEntity.hasBody()) {
            throw new DataSourceException(String.format("Cannot fetch AIP entities. (HTTP STATUS: %s, BODY: %s)",
                                                        aipResponseEntity.getStatusCode(),
                                                        aipResponseEntity.getBody()));
        }
        return Objects.requireNonNull(aipResponseEntity.getBody());
    }

    /**
     * Build DataObjectFeature from AIPEntity
     */
    private DataObjectFeature buildFeature(AIPEntity aipEntity, Storages storages, String tenant)
        throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, DataSourceException {
        AIPDto aip = aipEntity.getAip();
        // Build feature
        DataObjectFeature feature = new DataObjectFeature(aip.getId(), aip.getProviderId(), "NO_LABEL");

        // Add session information for session monitoring.
        feature.setSessionOwner(aipEntity.getSessionOwner());
        feature.setSession(aipEntity.getSession());

        feature.setLast(aipEntity.isLast());

        // Sum size of all RAW DATA Files
        Long rawDataFilesSize = 0L;

        // Add referenced files from raw AIP
        for (ContentInformationDto ci : aip.getProperties().getContentInformations()) {
            OAISDataObjectDto oaisDo = ci.getDataObject();
            if (oaisDo.getLocations().isEmpty()) {
                LOGGER.warn("No location inside the AIP's content informations {}", aipEntity.getAipId());
            } else {
                boolean online = checkOnline(oaisDo, storages);
                Optional<String> referenceUrl = Optional.empty();
                if (!online) {
                    referenceUrl = checkReference(oaisDo, storages);
                }

                String downloadUrl = referenceUrl.orElse(getDownloadUrl(aip.getId(), oaisDo.getChecksum(), tenant));
                DataFile dataFile = buildDataFile(ci, oaisDo, online, referenceUrl, downloadUrl);
                if ((ci.getRepresentationInformation() != null)
                    && (ci.getRepresentationInformation()
                          .getEnvironmentDescription() != null)
                    && (ci.getRepresentationInformation().getEnvironmentDescription().getSoftwareEnvironment()
                        != null)) {
                    Object types = ci.getRepresentationInformation()
                                     .getEnvironmentDescription()
                                     .getSoftwareEnvironment()
                                     .get(AIP_PROPERTY_DATA_FILES_TYPES);
                    if (types instanceof Collection) {
                        dataFile.getTypes().addAll((Collection<String>) types);
                    }
                }
                // Register file
                feature.getFiles().put(dataFile.getDataType(), dataFile);
                if ((oaisDo.getRegardsDataType() == DataType.RAWDATA) && (oaisDo.getFileSize() != null)) {
                    rawDataFilesSize += oaisDo.getFileSize();
                }
            }
        }

        // Create attribute containing all RAW DATA files size
        if (!Strings.isNullOrEmpty(modelAttrNameFileSize)) {
            //handle fragment
            String[] fragNAttr = modelAttrNameFileSize.split("\\.");
            if (fragNAttr.length > 1) {
                feature.addProperty(IProperty.buildObject(fragNAttr[0],
                                                          IProperty.forType(PropertyType.LONG,
                                                                            fragNAttr[1],
                                                                            rawDataFilesSize)));
            } else {
                feature.addProperty(IProperty.forType(PropertyType.LONG, modelAttrNameFileSize, rawDataFilesSize));
            }
        }

        // Tags
        if (commonTags != null && !commonTags.isEmpty()) {
            feature.addTags(commonTags.toArray(new String[0]));
        }
        if ((aip.getTags() != null) && !(aip.getTags().isEmpty())) {
            feature.addTags(aip.getTags().toArray(new String[0]));
        }
        // BEWARE:
        // Initial geometry needs to be normalized (for Circle search with another than Wgs84 crs)
        // BUT NOT NOW. Crawler needs to use initial one before transforming it to WGS84, not normalized one (in
        // some cases, project transformation destroys normalization). This initial geometry will be normalized by
        // crawler later
        if (aip.getGeometry() != null) {
            feature.setGeometry(aip.getGeometry());
        }

        manageFeatureProperties(aip, feature);

        return feature;
    }

    private void manageFeatureProperties(AIPDto aip, DataObjectFeature feature)
        throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        // Binded properties
        for (Map.Entry<String, List<String>> entry : modelBindingMap.entrySet()) {
            String doPropertyPath = entry.getKey();

            // Does property refers to a dynamic ("properties....") or static property ?
            if (!doPropertyPath.startsWith(DataSourcePluginConstants.PROPERTY_PREFIX)) {
                // Value from AIP
                Object value = getNestedProperty(aip, entry.getValue().get(0));
                // Static, use propertyUtilsBean
                propertyUtilsBean.setNestedProperty(feature, doPropertyPath, value);
            } else { // Dynamic
                String dynamicPropertyPath = doPropertyPath.substring(doPropertyPath.indexOf('.') + 1);
                // Property name in all cases (fragment or not)
                String propName = dynamicPropertyPath.substring(dynamicPropertyPath.indexOf('.') + 1);
                // Retrieve attribute type to manage interval specific value
                PropertyType attributeType = modelMappingMap.get(doPropertyPath);
                IProperty<?> propAtt = null;
                if (attributeType.isInterval()) {
                    Object lowerBound = null;
                    Object upperBound = null;
                    String lowerBoundPropertyPath = null;
                    String upperBoundPropertyPath = null;
                    if (entry.getValue().size() == 2
                        && entry.getValue().get(0) != null
                        && entry.getValue().get(1) != null) {
                        // Values from AIP
                        lowerBoundPropertyPath = entry.getValue().get(0);
                        lowerBound = getNestedProperty(aip, lowerBoundPropertyPath);
                        upperBoundPropertyPath = entry.getValue().get(1);
                        upperBound = getNestedProperty(aip, upperBoundPropertyPath);
                    }
                    if ((lowerBound != null) || (upperBound != null)) {
                        try {
                            propAtt = IProperty.forType(attributeType, propName, lowerBound, upperBound);
                        } catch (ClassCastException e) {
                            throw new RsRuntimeException(String.format("Cannot map %s and %s to %s (values %s and %s)",
                                                                       lowerBoundPropertyPath,
                                                                       upperBoundPropertyPath,
                                                                       propName,
                                                                       lowerBound,
                                                                       upperBound), e);
                        }
                    }
                } else {
                    // Value from AIP
                    String propertyPath = entry.getValue().get(0);
                    Object value = getNestedProperty(aip, propertyPath);
                    if (value != null) {
                        try {
                            propAtt = IProperty.forType(attributeType, propName, value);
                        } catch (ClassCastException e) {
                            throw new RsRuntimeException(String.format("Cannot map %s to %s (value %s)",
                                                                       propertyPath,
                                                                       propName,
                                                                       value), e);
                        }
                    }
                }
                if (propAtt != null) {
                    // If it contains another '.', there is a fragment
                    if (dynamicPropertyPath.contains(".")) {
                        String fragmentName = dynamicPropertyPath.substring(0, dynamicPropertyPath.indexOf('.'));

                        Optional<IProperty<?>> opt = feature.getProperties()
                                                            .stream()
                                                            .filter(p -> p.getName().equals(fragmentName))
                                                            .findAny();
                        ObjectProperty fragmentAtt = (ObjectProperty) opt.orElse(null);
                        if (fragmentAtt == null) {
                            fragmentAtt = IProperty.buildObject(fragmentName, propAtt);
                        } else {
                            fragmentAtt.addProperty(propAtt);
                        }
                        feature.addProperty(fragmentAtt);
                    } else {
                        feature.addProperty(propAtt);
                    }
                }
            }
        }
    }

    private DataFile buildDataFile(ContentInformationDto ci,
                                   OAISDataObjectDto oaisDo,
                                   boolean online,
                                   Optional<String> referenceUrl,
                                   String downloadUrl) {
        DataFile dataFile = DataFile.build(oaisDo.getRegardsDataType(),
                                           oaisDo.getFilename(),
                                           downloadUrl,
                                           ci.getRepresentationInformation().getSyntax().getMimeType(),
                                           online,
                                           referenceUrl.isPresent());
        dataFile.setFilesize(oaisDo.getFileSize());
        dataFile.setDigestAlgorithm(oaisDo.getAlgorithm());
        dataFile.setChecksum(oaisDo.getChecksum());
        dataFile.setImageHeight(ci.getRepresentationInformation().getSyntax().getHeight());
        dataFile.setImageWidth(ci.getRepresentationInformation().getSyntax().getWidth());

        return dataFile;
    }

    /**
     * Generate URL to access file from REGARDS system thanks to is checksum
     */
    private String getDownloadUrl(OaisUniformResourceName uniformResourceName, String checksum, String tenant)
        throws DataSourceException {
        Project project = projects.get(tenant);
        if (project == null) {
            FeignSecurityManager.asSystem();
            ResponseEntity<EntityModel<Project>> response = projectClient.retrieveProject(tenant);
            if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
                throw new DataSourceException(String.format(
                    "Download url could not be retrieved from tenant %s. (HTTP STATUS %s, BODY: %s)",
                    tenant,
                    response.getStatusCode(),
                    response.getBody()));
            }
            project = Objects.requireNonNull(response.getBody()).getContent();
            projects.put(tenant, project);
            FeignSecurityManager.reset();
        }

        return Objects.requireNonNull(project).getHost()
               + urlPrefix
               + "/"
               + encode4Uri("rs-catalog")
               + CATALOG_DOWNLOAD_PATH.replace("{aip_id}", uniformResourceName.toString())
                                      .replace("{checksum}", checksum);
    }

    private Optional<String> checkReference(OAISDataObjectDto oaisDo, Storages storages) {
        Optional<String> referenceUrl = Optional.empty();
        for (OAISDataObjectLocationDto oaisDataObjectLocation : oaisDo.getLocations()) {
            boolean isOffline = storages.getOfflines().contains(oaisDataObjectLocation.getStorage())
                                || !storages.getAll().contains(oaisDataObjectLocation.getStorage());
            if (isOffline && oaisDataObjectLocation.getUrl().startsWith("http")) {
                referenceUrl = Optional.of(oaisDataObjectLocation.getUrl());
                break;
            }
        }
        return referenceUrl;
    }

    private boolean checkOnline(OAISDataObjectDto oaisDo, Storages storageLocationDTOList) {
        boolean isOnline = false;
        for (OAISDataObjectLocationDto oaisDataObjectLocation : oaisDo.getLocations()) {
            isOnline = storageLocationDTOList.getOnlines().contains(oaisDataObjectLocation.getStorage());
            if (isOnline) {
                break;
            }
        }
        return isOnline;
    }

    /**
     * Get nested property managing null value
     */
    private Object getNestedProperty(AIPDto aip, String propertyJsonPath)
        throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Object value = null;
        try {
            value = propertyUtilsBean.getNestedProperty(aip, propertyJsonPath.trim());
        } catch (NestedNullException e) {
            LOGGER.debug(String.format("Property \"%s\" not found in AIP \"%s\"", propertyJsonPath, aip.getId()), e);
        }
        return value;
    }

    private static class Storages {

        private List<String> all;

        private List<String> onlines;

        private List<String> offlines;

        public static Storages build(List<StorageLocationDto> storageLocationDTOList) {
            Storages storages = new Storages();
            storages.all = storageLocationDTOList.stream().map(StorageLocationDto::getName).toList();
            storages.onlines = storageLocationDTOList.stream()
                                                     .filter(s -> (s.getConfiguration() != null)
                                                                  && (s.getConfiguration().getStorageType()
                                                                      == StorageType.ONLINE))
                                                     .map(StorageLocationDto::getName)
                                                     .toList();
            storages.offlines = storageLocationDTOList.stream()
                                                      .filter(s -> (s.getConfiguration() == null)
                                                                   || (s.getConfiguration().getStorageType()
                                                                       == StorageType.OFFLINE))
                                                      .map(StorageLocationDto::getName)
                                                      .toList();
            return storages;
        }

        public List<String> getAll() {
            return all;
        }

        public List<String> getOnlines() {
            return onlines;
        }

        public List<String> getOfflines() {
            return offlines;
        }
    }
}
