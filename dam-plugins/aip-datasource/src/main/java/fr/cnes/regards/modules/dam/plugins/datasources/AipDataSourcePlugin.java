/*
 * Copyright 2017-2019 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import fr.cnes.regards.framework.feign.security.FeignSecurityManager;
import fr.cnes.regards.framework.oais.ContentInformation;
import fr.cnes.regards.framework.oais.OAISDataObject;
import fr.cnes.regards.framework.oais.OAISDataObjectLocation;
import fr.cnes.regards.framework.oais.urn.DataType;
import fr.cnes.regards.framework.oais.urn.EntityType;
import fr.cnes.regards.framework.utils.RsRuntimeException;
import fr.cnes.regards.framework.utils.plugins.PluginUtilsRuntimeException;
import fr.cnes.regards.modules.dam.domain.entities.attribute.AbstractAttribute;
import fr.cnes.regards.modules.dam.domain.entities.attribute.ObjectAttribute;
import fr.cnes.regards.modules.dam.domain.entities.attribute.builder.AttributeBuilder;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import fr.cnes.regards.modules.ingest.client.IAIPRestClient;
import fr.cnes.regards.modules.ingest.domain.aip.AIPEntity;
import fr.cnes.regards.modules.ingest.domain.aip.AIPState;
import fr.cnes.regards.modules.ingest.dto.aip.AIP;
import fr.cnes.regards.modules.storagelight.client.IStorageRestClient;
import fr.cnes.regards.modules.storagelight.domain.dto.StorageLocationDTO;
import fr.cnes.regards.modules.storagelight.domain.plugin.StorageType;
import org.apache.commons.beanutils.NestedNullException;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.apache.commons.lang3.tuple.Pair;
import org.hipparchus.util.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IAipDataSourcePlugin;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.dam.domain.models.Model;
import fr.cnes.regards.modules.dam.domain.models.ModelAttrAssoc;
import fr.cnes.regards.modules.dam.domain.models.attributes.AttributeType;
import fr.cnes.regards.modules.dam.service.models.IModelAttrAssocService;
import fr.cnes.regards.modules.dam.service.models.IModelService;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * @author oroussel
 */
@Plugin(id = "aip-storage-datasource", version = "1.0-SNAPSHOT",
        description = "Allows data extraction from AIP storage", author = "REGARDS Team", contact = "regards@c-s.fr",
        license = "GPLv3", owner = "CSSI", url = "https://github.com/RegardsOss")
public class AipDataSourcePlugin implements IAipDataSourcePlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(AipDataSourcePlugin.class);

    @Autowired
    private IAIPRestClient aipClient;

    @Autowired
    private IStorageRestClient storageRestClient;

    @PluginParameter(name = DataSourcePluginConstants.TAGS, label = "data objects common tags", optional = true,
            description = "Common tags to be put on all data objects created by the data source")
    private final Set<String> commonTags = Sets.newHashSet();

    /**
     * Association table between JSON path property and its type from model
     */
    private final Map<String, AttributeType> modelMappingMap = new HashMap<>();

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

    @PluginParameter(name = DataSourcePluginConstants.MODEL_NAME_PARAM, label = "model name",
            description = "Associated data source model name")
    protected String modelName;

    @PluginParameter(name = DataSourcePluginConstants.SUBSETTING_TAGS, label = "Subsetting tags", optional = true,
            description = "The plugin will fetch data storage to find AIPs tagged with these specified tags to obtain an AIP subset. If no tag is specified, plugin will fetch all the available AIPs.")
    private Set<String> subsettingTags;

    @PluginParameter(name = DataSourcePluginConstants.BINDING_MAP, keylabel = "Model property path",
            label = "AIP property path",
            description = "Binding map between model and AIP (i.e. Property chain from model and its associated property chain from AIP format")
    private Map<String, String> bindingMap;

    @Autowired
    private IModelService modelService;

    @Autowired
    private IModelAttrAssocService modelAttrAssocService;

    private Model model;

    /**
     * Ingestion refresh rate in seconds
     */
    @PluginParameter(name = DataSourcePluginConstants.REFRESH_RATE, defaultValue = "86400", optional = true,
            label = "refresh rate",
            description = "Ingestion refresh rate in seconds (minimum delay between two consecutive ingestions)")
    private Integer refreshRate;

    @PluginParameter(name = DataSourcePluginConstants.MODEL_ATTR_FILE_SIZE, optional = true,
            label = "Attribute model for RAW DATA files size",
            description = "This parameter is used to define which model attribute is used to map the RAW DATA files sizes")
    private String modelAttrNameFileSize;

    /**
     * Init method
     */
    @PluginInit
    private void initPlugin() throws ModuleException {
        this.model = modelService.getModelByName(modelName);
        if (this.model == null) {
            throw new ModuleException(String.format("Model '%s' does not exist.", modelName));
        }

        List<ModelAttrAssoc> modelAttrAssocs = modelAttrAssocService.getModelAttrAssocs(modelName);
        // Fill map { "properties.titi.tutu", AttributeType.STRING }
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
                                       doPropertyPath.length() - DataSourcePluginConstants.LOWER_BOUND_SUFFIX.length());
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
                                       doPropertyPath.length() - DataSourcePluginConstants.UPPER_BOUND_SUFFIX.length());
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
                    modelBindingMap.put(doPropertyPath, Arrays.asList(entry.getValue()));
                }
            } else {
                // Propagate properties
                modelBindingMap.put(entry.getKey(), Arrays.asList(entry.getValue()));
            }
        }

        // All bindingMap values should be JSON path properties from model so each of them starting with PROPERTY_PREFIX
        // must exist as a value into modelMappingMap
        Set<String> notInModelProperties = modelBindingMap.keySet().stream()
                .filter(name -> name.startsWith(DataSourcePluginConstants.PROPERTY_PREFIX))
                .filter(name -> !modelMappingMap.containsKey(name)).collect(Collectors.toSet());
        if (!notInModelProperties.isEmpty()) {
            throw new ModuleException(
                    "Following properties don't exist into model : " + Joiner.on(", ").join(notInModelProperties));
        }
        DataObject forIntrospection = new DataObject();
        PropertyUtilsBean propertyUtilsBean = new PropertyUtilsBean();
        Set<String> notInModelStaticProperties = modelBindingMap.keySet().stream()
                .filter(name -> !name.startsWith(DataSourcePluginConstants.PROPERTY_PREFIX))
                .filter(name -> !propertyUtilsBean.isWriteable(forIntrospection, name)).collect(Collectors.toSet());
        if (!notInModelStaticProperties.isEmpty()) {
            throw new ModuleException(
                    "Following static properties don't exist : " + Joiner.on(", ").join(notInModelProperties));
        }

        // Check number of values mapped for each type
        for (Map.Entry<String, List<String>> entry : modelBindingMap.entrySet()) {
            if (entry.getKey().startsWith(DataSourcePluginConstants.PROPERTY_PREFIX)) {
                AttributeType attributeType = modelMappingMap.get(entry.getKey());
                if (attributeType.isInterval()) {
                    if (entry.getValue().size() != 2) {
                        throw new ModuleException(attributeType + " properties " + entry.getKey()
                                + " has to be mapped to exactly 2 values");
                    }
                } else {
                    if (entry.getValue().size() != 1) {
                        throw new ModuleException(attributeType + " properties " + entry.getKey()
                                + " has to be mapped to a single value");
                    }
                }
            }
        }
    }

    @Override
    public int getRefreshRate() {
        return refreshRate;
    }

    private final Multimap<String, String> multimap = HashMultimap.create();

    @Override
    public Page<DataObjectFeature> findAll(String tenant, Pageable pageable, OffsetDateTime date)
            throws DataSourceException {
        try {
            ResponseEntity<List<Resource<StorageLocationDTO>>> storageResponseEntity = storageRestClient.retrieve();
            List<StorageLocationDTO> storageLocationDTOList = storageResponseEntity.getBody().stream().map(n -> n.getContent()).collect(Collectors.toList());
            ResponseEntity<PagedResources<Resource<AIPEntity>>> aipResponseEntity = aipClient
                    .searchAIPs(AIPState.STORED, subsettingTags, date, pageable.getPageNumber(),
                                          pageable.getPageSize());
            Storages storages = new Storages(storageLocationDTOList);
            FeignSecurityManager.asSystem();
            FeignSecurityManager.reset();
            if (aipResponseEntity.getStatusCode() == HttpStatus.OK) {
                List<DataObjectFeature> list = new ArrayList<>();
                for (Resource<AIPEntity> aipDataFiles : aipResponseEntity.getBody().getContent()) {
                    // rs-storage stores all kinds of entity, we only want data here.
                    AIPEntity aipEntity = aipDataFiles.getContent();
                    if (aipEntity.getAip().getIpType() == EntityType.DATA) {
                        multimap.put(tenant, aipEntity.getAip().getId().toString());
                        try {
                            list.add(buildFeature(aipEntity, storages));
                        } catch (URISyntaxException e) {
                            throw new DataSourceException("AIP dataObject url cannot be transformed in URI", e);
                        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                            throw new PluginUtilsRuntimeException(e);
                        }
                    }
                }

                PagedResources.PageMetadata responsePageMeta = aipResponseEntity.getBody().getMetadata();
                int pageSize = (int) responsePageMeta.getSize();
                return new PageImpl<>(list,
                        PageRequest.of((int) responsePageMeta.getNumber(), pageSize == 0 ? 1 : pageSize),
                        responsePageMeta.getTotalElements());
            } else {
                throw new DataSourceException(
                        "Error while calling storage client (HTTP STATUS : " + aipResponseEntity.getStatusCode());
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new DataSourceException("Cannot fetch storage locations list because: " + e.getMessage());
        }
    }

    /**
     * Build DataObjectFeature from AIPEntity
     * @param aipEntity
     * @param storages
     */
    private DataObjectFeature buildFeature(AIPEntity aipEntity, Storages storages)
            throws URISyntaxException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        AIP aip = aipEntity.getAip();
        // Build feature
        DataObjectFeature feature = new DataObjectFeature(aip.getId(), aip.getProviderId(), aipEntity.getAipId());
        // FIXME feature.setLabel(dataFileDto.getName());
    
        // Sum size of all RAW DATA Files
        Long rawDataFilesSize = 0L;
    
        // Add referenced files from raw AIP
        for (ContentInformation ci : aip.getProperties().getContentInformations()) {
            OAISDataObject oaisDo = ci.getDataObject();
            if (oaisDo.getLocations().isEmpty()) {
                LOGGER.warn("No location inside the AIP's content informations {}", aipEntity.getAipId());
            } else {
                Boolean online = checkOnline(oaisDo, storages);
                Boolean reference = false;
                if (!online) {
                    reference = checkReference(oaisDo, storages);
                }
                DataFile dataFile = DataFile.build(
                        oaisDo.getRegardsDataType(),
                        oaisDo.getFilename(),
                        oaisDo.getLocations().iterator().next().getStorage(),
                        ci.getRepresentationInformation().getSyntax().getMimeType(),
                        online,
                        reference);
                feature.getFiles().put(dataFile.getDataType(), dataFile);
                dataFile.setFilesize(oaisDo.getFileSize());
                dataFile.setDigestAlgorithm(oaisDo.getAlgorithm());
                dataFile.setChecksum(oaisDo.getChecksum());
                dataFile.setImageHeight(ci.getRepresentationInformation().getSyntax().getHeight());
                dataFile.setImageWidth(ci.getRepresentationInformation().getSyntax().getWidth());
                // Register file
                feature.getFiles().put(dataFile.getDataType(), dataFile);
                if ((oaisDo.getRegardsDataType() == DataType.RAWDATA) && (oaisDo.getFileSize() != null)) {
                    rawDataFilesSize += oaisDo.getFileSize();
                }
            }
        }

        // Create attribute containing all RAW DATA files size
        if (!Strings.isNullOrEmpty(modelAttrNameFileSize)) {
            feature.addProperty(AttributeBuilder.forType(AttributeType.LONG, modelAttrNameFileSize, rawDataFilesSize));
        }

        // Tags
        if ((commonTags != null) && (commonTags.size() > 0)) {
            feature.addTags(commonTags.toArray(new String[commonTags.size()]));
        }
        if ((aip.getTags() != null) && (aip.getTags().size() > 0)) {
            feature.addTags(aip.getTags().toArray(new String[aip.getTags().size()]));
        }
        // BEWARE:
        // Initial geometry needs to be normalized (for Circle search with another than Wgs84 crs)
        // BUT NOT NOW. Crawler needs to use initial one before transforming it to WGS84, not normalized one (in
        // some cases, project transformation destroys normalization). This initial geometry will be normalized by
        // crawler later
        if (aip.getGeometry() != null) {
            feature.setGeometry(aip.getGeometry());
        }

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
                AttributeType attributeType = modelMappingMap.get(doPropertyPath);
                AbstractAttribute<?> propAtt = null;
                if (attributeType.isInterval()) {
                    // Values from AIP
                    String lowerBoundPropertyPath = entry.getValue().get(0);
                    Object lowerBound = getNestedProperty(aip, lowerBoundPropertyPath);
                    String upperBoundPropertyPath = entry.getValue().get(1);
                    Object upperBound = getNestedProperty(aip, upperBoundPropertyPath);
                    if ((lowerBound != null) || (upperBound != null)) {
                        try {
                            propAtt = AttributeBuilder.forType(attributeType, propName, lowerBound, upperBound);
                        } catch (ClassCastException e) {
                            String msg = String.format("Cannot map %s and to %s (values %s and %s)",
                                                       lowerBoundPropertyPath, upperBoundPropertyPath, propName,
                                                       lowerBound, upperBound);
                            throw new RsRuntimeException(msg, e);
                        }
                    }
                } else {
                    // Value from AIP
                    String propertyPath = entry.getValue().get(0);
                    Object value = getNestedProperty(aip, propertyPath);
                    if (value != null) {
                        try {
                            propAtt = AttributeBuilder.forType(attributeType, propName, value);
                        } catch (ClassCastException e) {
                            String msg = String.format("Cannot map %s to %s (value %s)", propertyPath, propName, value);
                            throw new RsRuntimeException(msg, e);
                        }
                    }
                }
                if (propAtt != null) {
                    // If it contains another '.', there is a fragment
                    if (dynamicPropertyPath.contains(".")) {
                        String fragmentName = dynamicPropertyPath.substring(0, dynamicPropertyPath.indexOf('.'));

                        Optional<AbstractAttribute<?>> opt = feature.getProperties().stream()
                                .filter(p -> p.getName().equals(fragmentName)).findAny();
                        ObjectAttribute fragmentAtt = opt.isPresent() ? (ObjectAttribute) opt.get() : null;
                        if (fragmentAtt == null) {
                            fragmentAtt = AttributeBuilder.buildObject(fragmentName, propAtt);
                        } else {
                            fragmentAtt.getValue().add(propAtt);
                        }
                        feature.addProperty(fragmentAtt);
                    } else {
                        feature.addProperty(propAtt);
                    }
                }
            }
        }
    
        return feature;
    }

    private Boolean checkReference(OAISDataObject oaisDo, Storages storages) {
        Boolean isReference = false;
        for (OAISDataObjectLocation oaisDataObjectLocation : oaisDo.getLocations()) {
            Boolean isOffline = storages.getOfflines().contains(oaisDataObjectLocation.getStorage()) || !storages.getAll().contains(oaisDataObjectLocation.getStorage());
            if (isOffline && oaisDataObjectLocation.getUrl().startsWith("http")) {
                isReference = true;
                break;
            }
        }
        return isReference;
    }

    private Boolean checkOnline(OAISDataObject oaisDo, Storages storageLocationDTOList) {
        Boolean isOnline = false;
        for (OAISDataObjectLocation oaisDataObjectLocation : oaisDo.getLocations()) {
            isOnline = storageLocationDTOList.getOnlines().contains(oaisDataObjectLocation.getStorage());
            if (isOnline) {
                break;
            }
        }
        return isOnline;
    }

    /**
     * Get nested property managing null value
      *
     */
    private Object getNestedProperty(AIP aip, String propertyJsonPath)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Object value = null;
        try {
            value = propertyUtilsBean.getNestedProperty(aip, propertyJsonPath.trim());
        } catch (NestedNullException e) {
            LOGGER.debug("Property \"{}\" not found in AIP \"{}\"", propertyJsonPath, aip.getId());
        }
        return value;
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    private class Storages {
        private List<String> all;
        private List<String> onlines;
        private List<String> offlines;

        Storages(List<StorageLocationDTO> storageLocationDTOList) {
            this.all = storageLocationDTOList.stream().map(n -> n.getName()).collect(Collectors.toList());
            this.onlines = storageLocationDTOList.stream().filter(s -> s.getConfiguration() != null && s.getConfiguration().getStorageType() == StorageType.ONLINE).map(n -> n.getName()).collect(Collectors.toList());
            this.offlines = storageLocationDTOList.stream().filter(s -> s.getConfiguration() == null || s.getConfiguration().getStorageType() == StorageType.OFFLINE).map(n -> n.getName()).collect(Collectors.toList());
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
