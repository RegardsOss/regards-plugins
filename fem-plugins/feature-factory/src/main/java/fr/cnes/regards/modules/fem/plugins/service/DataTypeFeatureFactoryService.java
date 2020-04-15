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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Sets;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.fem.plugins.dto.DataTypeDescriptor;
import fr.cnes.regards.modules.fem.plugins.dto.PropertiesEnum;
import fr.cnes.regards.modules.fem.plugins.dto.SystemPropertiyEnum;
import fr.cnes.regards.modules.model.dto.properties.IProperty;

/**
 * Factory to create {@link Feature}s from a String fileName and an associated  {@link DataTypeDescriptor}
 *
 * @author Sébastien Binda
 *
 */
@Service
public class DataTypeFeatureFactoryService {

    /**
     * Class logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DataTypeFeatureFactoryService.class);

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
    Set<DataTypeDescriptor> descriptors = Sets.newHashSet();

    /**
     * Reads all {@link DataTypeDescriptor}s from configured directory and initialize associated {@link DataTypeDescriptor}s
     * @param directory directory to parse for yml files
     * @throws IOException
     */
    public void readConfs(Path directory) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Files.list(directory).filter(f -> f.getFileName().toString().endsWith(".yml")).forEach(filePath -> {
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

    /**
     * Retrieve the {@link DataTypeDescriptor} associated to given fileName
     * @param fileName
     * @return {@link DataTypeDescriptor}
     * @throws ModuleException
     */
    public DataTypeDescriptor findDataTypeDescriptor(String fileName) throws ModuleException {
        Set<DataTypeDescriptor> types = descriptors.stream().filter(dt -> dt.matches(fileName))
                .collect(Collectors.toSet());
        if (types.size() > 1) {
            throw new ModuleException(String
                    .format("More than one dataType match for the fileName %s. -> %s", fileName,
                            types.stream().map(DataTypeDescriptor::getType).collect(Collectors.toList()).toString()));
        }
        if (types.isEmpty()) {
            throw new ModuleException(String.format("No dataType match for the fileName %s", fileName));
        }
        DataTypeDescriptor dt = types.iterator().next();
        LOGGER.debug("Desriptor {} found for file {}", dt.getType(), fileName);
        return dt;
    }

    /**
     * Get a {@link Feature} for the given fileLocation by reading the associated {@link DataTypeDescriptor}
     * @param fileLocation
     * @param model
     * @return {@link Feature}
     * @throws ModuleException
     */
    public Feature getFeature(String fileLocation, String model, OffsetDateTime creationDate) throws ModuleException {
        String fileName = Paths.get(fileLocation).getFileName().toString();
        DataTypeDescriptor dataDesc = findDataTypeDescriptor(fileName);
        Feature toCreate = Feature.build(fileLocation, null, null, EntityType.DATA, model);
        // 1. Add all dynamic properties read from data descriptor
        for (String meta : dataDesc.getMetadata()) {
            Optional<IProperty<?>> property = dataDesc.getMetaProperty(meta, fileName, dataDesc.getType());
            if (property.isPresent()) {
                IProperty.mergeProperties(toCreate.getProperties(), Sets.newHashSet(property.get()), fileName);
            }
        }
        // 2. Add fixed granule type property
        if ((dataDesc.getGranule_type() != null) && !dataDesc.getGranule_type().isEmpty()) {
            IProperty.mergeProperties(toCreate.getProperties(), Sets
                    .newHashSet(IProperty.buildObject(SWOT_FRAGMENT, IProperty
                            .buildString(PropertiesEnum.GRANULE_TYPE.getPropertyPath(), dataDesc.getGranule_type()))),
                                      fileLocation);
        }
        // 3. Add fixed data type  property
        if ((dataDesc.getType() != null) && !dataDesc.getType().isEmpty()) {
            IProperty.mergeProperties(toCreate.getProperties(),
                                      Sets.newHashSet(IProperty.buildObject(DATA_FRAGMENT, IProperty
                                              .buildString(PropertiesEnum.TYPE.getPropertyPath(), dataDesc.getType()))),
                                      fileLocation);
        }
        // 4. Add fixed system properties
        addSystemProperties(toCreate, fileLocation, creationDate);
        return toCreate;
    }

    /**
     * Add all fixed properties of the system fragment
     * @param feature {@link Feature}
     * @param fileLocation
     * @param creationDate
     */
    private void addSystemProperties(Feature feature, String fileLocation, OffsetDateTime creationDate) {
        String fileName = Paths.get(fileLocation).getFileName().toString();
        String fileExt = null;
        if (fileName.lastIndexOf(".") > 0) {
            fileExt = fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length());
        }
        feature.addProperty(IProperty
                .buildObject(SYSTEM_FRAGMENT_NAME,
                             IProperty.buildDate(SystemPropertiyEnum.INGEST_DATE.getPropertyPath(), creationDate),
                             IProperty.buildDate(SystemPropertiyEnum.CHANGE_DATE.getPropertyPath(), creationDate),
                             IProperty.buildString(SystemPropertiyEnum.GPFS_URL.getPropertyPath(), fileLocation),
                             IProperty.buildString(SystemPropertiyEnum.FILE_NAME.getPropertyPath(), fileName),
                             IProperty.buildString(SystemPropertiyEnum.EXTENSION.getPropertyPath(), fileExt)));
    }

    /**
     * Retrieve available {@link DataTypeDescriptor}s
     * @return {@link DataTypeDescriptor}s
     */
    public Set<DataTypeDescriptor> getDescriptors() {
        return this.descriptors;
    }

}
