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
package fr.cnes.regards.modules.fem.plugins.service;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.feature.dto.gson.FeatureProperties;
import fr.cnes.regards.modules.featureprovider.domain.plugin.FactoryParameters;
import fr.cnes.regards.modules.fem.plugins.dto.DataTypeDescriptor;
import fr.cnes.regards.modules.fem.plugins.dto.PropertiesEnum;
import fr.cnes.regards.modules.fem.plugins.dto.SystemPropertiyEnum;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.model.dto.properties.ObjectProperty;

/**
 * Factory to create {@link Feature}s from a String fileName and an associated  {@link DataTypeDescriptor}
 *
 * @author Sébastien Binda
 *
 */
@Service
public class FeatureFactoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureFactoryService.class);

    /**
     * Location parameter path
     */
    protected static final String LOCATION_MEMBER_NAME = "location";

    /**
     * Geometry parameter path
     */
    protected static final String GEOMETRY_MEMBER_NAME = "geometry";

    /**
     * Name of Feature fragment containing  feature system information
     */
    private static final String SYSTEM_FRAGMENT_NAME = "system";

    /**
     * Name of Feature fragment containing SWOT properties
     */
    private static final String SWOT_FRAGMENT = "swot";

    /**
     * Name of Feature fragment containing data properties
     */
    private static final String DATA_FRAGMENT = "data";

    /**
     * Valid and available {@link DataTypeDescriptor}s
     */
    private final Set<DataTypeDescriptor> descriptors = Sets.newConcurrentHashSet();

    @Autowired
    private FactoryParameters fp;

    /**
     * Reads all {@link DataTypeDescriptor}s from configured directory and initialize associated {@link DataTypeDescriptor}s
     * @param directory directory to parse for yml files
     * @throws IOException
     */
    public void readConfs(Path directory) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try (Stream<Path> directories = Files.list(directory)) {
            directories.filter(f -> f.getFileName().toString().endsWith(".yml")).forEach(filePath -> {
                try {
                    if (Files.isRegularFile(filePath)) {
                        String dataType = filePath.getFileName().toString()
                                .substring(0, filePath.getFileName().toString().indexOf("."));
                        if (!this.descriptors.stream().anyMatch(d -> d.getType().equals(dataType))) {
                            JsonNode mainNode = mapper.readTree(filePath.toFile());
                            JsonNode dataNode = mainNode.get(dataType);
                            DataTypeDescriptor dt = mapper.treeToValue(dataNode, DataTypeDescriptor.class);
                            if (dt != null) {
                                dt.setType(dataType);
                                try {
                                    dt.validate();
                                    descriptors.add(dt);
                                } catch (ModuleException e) {
                                    LOGGER.error("[{}] Invalid data type. {}", dt.getType(), e.getMessage());
                                }
                            } else {
                                LOGGER.warn("Unable to parse conf  file {} for  type ", filePath, dataType);
                            }
                        } else {
                            LOGGER.error("[{}] Invalid data type. A data type configuration already exists for this type.",
                                         dataType);
                        }
                    }
                } catch (IOException e) {
                    LOGGER.error("Error reading configuration file {}. Cause : {}", filePath, e.getMessage());
                }
            });
        }
    }

    /**
     * Retrieve the {@link DataTypeDescriptor} associated to given fileName
     * @param fileName
     * @return {@link DataTypeDescriptor}
     * @throws ModuleException    @Autowired
    Gson gson;
     */
    public DataTypeDescriptor findDataTypeDescriptor(String fileName) throws ModuleException {
        Set<DataTypeDescriptor> types = descriptors.stream().filter(dt -> dt.matches(fileName))
                .collect(Collectors.toSet());
        if (types.size() > 1) {
            String errorMessage = String
                    .format("More than one dataType match for the fileName %s. -> %s", fileName,
                            types.stream().map(DataTypeDescriptor::getType).collect(Collectors.toList()).toString());
            LOGGER.error(errorMessage);
            throw new ModuleException(errorMessage);
        }
        if (types.isEmpty()) {
            String errorMessage = String.format("No dataType match for the fileName %s", fileName);
            LOGGER.error(errorMessage);
            throw new ModuleException(errorMessage);
        }
        DataTypeDescriptor dt = types.iterator().next();
        LOGGER.debug("Desriptor {} found for file {}", dt.getType(), fileName);
        return dt;
    }

    /**
     * Generate a {@link Feature} according to specified parameters and by reading the associated {@link DataTypeDescriptor}
     */
    public Feature getFeature(JsonObject parameters, String model, OffsetDateTime creationDate) throws ModuleException {

        // Retrieve required and optional parameters
        String fileLocation = fp.getParameter(parameters, LOCATION_MEMBER_NAME, String.class);
        Optional<IGeometry> geometry = fp.getOptionalParameter(parameters, GEOMETRY_MEMBER_NAME, IGeometry.class);
        // Prepare properties before parsing
        FeatureProperties.beforeRead(parameters);
        Optional<Set<IProperty<?>>> properties = fp.getOptionalParameter(parameters,
                                                                         FeatureProperties.PROPERTIES_FIELD_NAME,
                                                                         new TypeToken<Set<IProperty<?>>>() {
                                                                         }.getType());

        // Generate feature
        String fileName = Paths.get(fileLocation).getFileName().toString();
        DataTypeDescriptor dataDesc = findDataTypeDescriptor(fileName);
        String id = String.format("%s:%s", dataDesc.getType(),
                                  UUID.nameUUIDFromBytes(fileLocation.getBytes()).toString());
        Feature toCreate = Feature.build(id, null, null, null, EntityType.DATA, model);

        // 0. Apply additional properties from parameters
        if (geometry.isPresent()) {
            toCreate.setGeometry(geometry.get());
        }
        if (properties.isPresent()) {
            toCreate.setProperties(properties.get());
        }

        // 1. Add all dynamic properties read from data descriptor
        addSpecificProperties(toCreate, fileName, dataDesc);

        // 2. Add fixed granule type property
        if ((dataDesc.getGranule_type() != null) && !dataDesc.getGranule_type().isEmpty()) {
            IProperty.mergeProperties(toCreate.getProperties(), Sets.newHashSet(IProperty
                    .buildObject(SWOT_FRAGMENT,
                                 IProperty.buildString(PropertiesEnum.GRANULE_TYPE.getPropertyPath(),
                                                       dataDesc.getGranule_type()))),
                                      fileLocation, this.getClass().getName());
        }
        // 3. Add fixed data type  property
        if ((dataDesc.getType() != null) && !dataDesc.getType().isEmpty()) {
            IProperty.mergeProperties(toCreate.getProperties(),
                                      Sets.newHashSet(IProperty
                                              .buildObject(DATA_FRAGMENT,
                                                           IProperty.buildString(PropertiesEnum.TYPE.getPropertyPath(),
                                                                                 dataDesc.getType()))),
                                      fileLocation, this.getClass().getName());
        }
        // 4. Add fixed system properties
        addSystemProperties(toCreate, fileLocation, creationDate, dataDesc.getType());
        return toCreate;
    }

    /**
     * Add specific {@link IProperty}s read from configuration data type
     *
     * @param toCreate
     * @param fileLocation
     * @param dataDesc
     * @throws ModuleException
     */
    private void addSpecificProperties(Feature toCreate, String fileName, DataTypeDescriptor dataDesc)
            throws ModuleException {
        for (String meta : dataDesc.getMetadata()) {
            Optional<IProperty<?>> property = dataDesc.getMetaProperty(meta, fileName);
            if (property.isPresent()) {
                IProperty.mergeProperties(toCreate.getProperties(), Sets.newHashSet(property.get()), fileName,
                                          this.getClass().getName());
            }
        }

        // Handle missing mandatory date properties
        handleMissingDateProperties(toCreate, fileName, dataDesc);
    }

    /**
     * Check date start/end date property are not missing
     * @param toCreate
     * @param fileLocation
     * @param dataDesc
     */
    private void handleMissingDateProperties(Feature toCreate, String fileName, DataTypeDescriptor dataDesc) {
        Map<String, IProperty<?>> map = IProperty.getPropertyMap(toCreate.getProperties());
        IProperty<?> startDate = map.get("data.start_date");
        IProperty<?> stopDate = map.get("data.end_date");
        IProperty<?> productionDate = map.get("data.production_date");
        IProperty<?> dayDate = map.get("swot.day_date");
        if ((startDate == null) && (stopDate == null)) {
            switch (dataDesc.getType()) {
                case "HISTO_OEF":
                    if (productionDate != null) {
                        OffsetDateTime date = (OffsetDateTime) productionDate.getValue();
                        startDate = IProperty.buildDate("start_date", date);
                        stopDate = IProperty.buildDate("end_date", date.plusYears(10L));
                    }
                    break;
                case "ECLIPSE":
                case "L1_DORIS_RINEX":
                case "L1_DORIS_RINEX_REX":
                case "L1_DORIS_RINEX_INVALID":
                    if (dayDate != null) {
                        OffsetDateTime date = (OffsetDateTime) dayDate.getValue();
                        startDate = IProperty.buildDate("start_date", date);
                        stopDate = IProperty.buildDate("end_date", date.plusHours(1L));
                    }
                    break;
                case "L0A_GPSP_Packet":
                    if (productionDate != null) {
                        OffsetDateTime date = (OffsetDateTime) productionDate.getValue();
                        startDate = IProperty.buildDate("start_date", date);
                        stopDate = IProperty.buildDate("end_date", date);
                    }
                    break;
                default:
                    LOGGER.error("Missing start/end date propertues for file  {} of type {}", fileName,
                                 dataDesc.getType());
                    break;
            }
            if ((startDate != null) && (stopDate != null)) {
                startDate = dataDesc.buildPropertyFragment("data.start_date", startDate);
                stopDate = dataDesc.buildPropertyFragment("data.end_date", stopDate);
                IProperty.mergeProperties(toCreate.getProperties(), Sets.newHashSet(startDate), fileName,
                                          this.getClass().getName());
                IProperty.mergeProperties(toCreate.getProperties(), Sets.newHashSet(stopDate), fileName,
                                          this.getClass().getName());
            }
        }

    }

    /**
     * Add all fixed properties of the system fragment
     * @param feature {@link Feature}
     * @param fileLocation
     * @param creationDate
     */
    private void addSystemProperties(Feature feature, String fileLocation, OffsetDateTime creationDate,
            String dataType) {
        Set<IProperty<?>> properties = Sets.newHashSet();
        String fileName = Paths.get(fileLocation).getFileName().toString();
        String fileExt = null;
        if (fileName.lastIndexOf(".") > 0) {
            fileExt = fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length());
            properties.add(IProperty.buildString(SystemPropertiyEnum.EXTENSION.getPropertyPath(), fileExt));
        }
        try {
            URL url = new URL(fileLocation);
            if (url.getProtocol().equals("file") || url.getProtocol().equals("gpfs")) {
                File file = new File(url.getPath());
                if (Files.exists(Paths.get(file.getPath()))) {
                    properties.add(IProperty.buildLong(SystemPropertiyEnum.FILE_SIZE.getPropertyPath(), file.length()));
                } else {
                    LOGGER.debug("[{}] Unable to calculate file size for file {}. File is not readable.", dataType,
                                 fileLocation);
                }
            } else {
                LOGGER.debug("[{}] Unable to calculate file size for file {}. Protocol can not be handled.", dataType,
                             fileLocation);
            }
        } catch (MalformedURLException e) {
            LOGGER.warn("[{}] Unable to calculate file size for file {}. Invalid url.", dataType, fileLocation);
        }
        properties.add(IProperty.buildDate(SystemPropertiyEnum.INGEST_DATE.getPropertyPath(), creationDate));
        properties.add(IProperty.buildDate(SystemPropertiyEnum.CHANGE_DATE.getPropertyPath(), creationDate));
        properties.add(IProperty.buildString(SystemPropertiyEnum.GPFS_URL.getPropertyPath(), fileLocation));
        properties.add(IProperty.buildString(SystemPropertiyEnum.FILE_NAME.getPropertyPath(), fileName));
        ObjectProperty att = new ObjectProperty();
        att.setName(SYSTEM_FRAGMENT_NAME);
        att.setValue(properties);
        feature.addProperty(att);
    }

    /**
     * Retrieve available {@link DataTypeDescriptor}s
     * @return {@link DataTypeDescriptor}s
     */
    public Set<DataTypeDescriptor> getDescriptors() {
        return this.descriptors;
    }

}
