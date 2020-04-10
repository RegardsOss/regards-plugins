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
package fr.cnes.regards.modules.fem.plugins.service2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

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
import fr.cnes.regards.modules.model.dto.properties.IProperty;

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
     * Valid and available {@link DataTypeDescriptor}s
     */
    Set<DataTypeDescriptor> descriptors = Sets.newHashSet();

    /**
     * Reads all {@link DataTypeDescriptor}s from configured directory
     * @param directory
     * @throws IOException
     */
    @PostConstruct
    public void readConfs(Path directory) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Files.list(directory).forEach(filePath -> {
            try {
                if (Files.isRegularFile(filePath)) {
                    String dataType = filePath.getFileName().toString()
                            .substring(0, filePath.getFileName().toString().indexOf("."));
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
            throw new ModuleException(String.format("More than one dataType match for the fileName %s", fileName));
        }
        if (types.isEmpty()) {
            throw new ModuleException(String.format("No dataType match for the fileName %s", fileName));
        }
        DataTypeDescriptor dt = types.iterator().next();
        LOGGER.debug("Desriptor {} found for file {}", dt.getType(), fileName);
        return dt;
    }

    /**
     * Get a {@link Feature} for the given fileName by reading the associated {@link DataTypeDescriptor}
     * @param fileName
     * @param model
     * @return {@link Feature}
     * @throws ModuleException
     */
    public Feature getFeature(String fileName, String model) throws ModuleException {
        DataTypeDescriptor dataDesc = findDataTypeDescriptor(fileName);
        Feature toCreate = Feature.build(fileName, null, null, EntityType.DATA, model);
        for (String meta : dataDesc.getMetadata()) {
            dataDesc.getMetaProperty(meta, fileName).ifPresent(toCreate::addProperty);
        }
        if ((dataDesc.getGranule_type() != null) && !dataDesc.getGranule_type().isEmpty()) {
            toCreate.addProperty(IProperty.buildString(PropertiesEnum.GRANULE_TYPE.getName(),
                                                       dataDesc.getGranule_type()));
        }
        return toCreate;

    }

    /**
     * Retrieve available {@link DataTypeDescriptor}s
     * @return {@link DataTypeDescriptor}s
     */
    public Set<DataTypeDescriptor> getDescriptors() {
        return this.descriptors;
    }

}
