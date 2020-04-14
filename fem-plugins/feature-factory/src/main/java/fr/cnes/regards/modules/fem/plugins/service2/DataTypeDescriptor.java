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
package fr.cnes.regards.modules.fem.plugins.service2;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.modules.model.dto.properties.IProperty;

/**
 * @author Sébastien Binda
 *
 */
public class DataTypeDescriptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataTypeDescriptor.class);

    @JsonProperty("name")
    private String name;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("document")
    private String document;

    @JsonProperty("example")
    private List<String> example;

    @JsonProperty("granule_type")
    private String granule_type;

    @JsonProperty("tar_content")
    private List<String> tar_content;

    @JsonProperty("mission")
    private String mission;

    @JsonProperty("Nature")
    private String nature;

    @JsonProperty("metadata")
    private List<String> metadata;

    @JsonProperty("FileIdentifier")
    private List<String> fileIdentifier;

    @JsonProperty("APIDnumber")
    private List<Integer> apidNumber;

    @JsonIgnore
    private final Map<String, Integer> nameProperties = new HashMap<>();

    @JsonIgnore
    private String regexp;

    @JsonIgnore
    private String type;

    private static String propertyPattern = "\\{\\{ [a-zA-Z]* \\}\\}";

    private static Pattern pattern = Pattern.compile(propertyPattern);

    /**
     * Retrieve a {@link IProperty} from a metadata value of the fileName
     * @param meta name of the property to retrieve from file name
     * @param fileName
     * @return {@link IProperty}
     * @throws ModuleException
     */
    public Optional<IProperty<?>> getMetaProperty(String meta, String fileName, String dataType)
            throws ModuleException {
        PropertiesEnum prop = PropertiesEnum.get(meta);
        Integer groupIndex = this.nameProperties.get(meta);
        String propertyName = meta;
        String propertyPath = meta;
        PropertyType type = PropertyType.STRING;

        if (prop != null) {
            propertyPath = prop.getPropertyPath();
            if (propertyPath.lastIndexOf(".") > 0) {
                propertyName = propertyPath.substring(propertyPath.lastIndexOf(".") + 1, propertyPath.length());
            } else {
                propertyName = propertyPath;
            }
            type = prop.getType();
        } else {
            LOGGER.warn("[{}] Default format used for property {} in file {}", dataType, meta, fileName);
        }

        if ((groupIndex != null) && this.matches(fileName)) {
            IProperty<?> property;
            Matcher matcher = Pattern.compile(this.regexp).matcher(fileName);
            matcher.matches();
            String metaValue = matcher.group(groupIndex);
            switch (type) {
                case DATE:
                    property = IProperty.buildDate(propertyName, parseDate(metaValue, prop.getFormat()));
                    break;
                case DATE_TIME:
                    property = IProperty.buildDate(propertyName, parseDateTime(metaValue, prop.getFormat()));
                    break;
                case INTEGER:
                    property = IProperty.buildInteger(propertyName, Integer.valueOf(metaValue));
                    break;
                case STRING:
                default:
                    property = IProperty.buildString(propertyName, metaValue);
                    break;
            }
            return Optional.of(buildPropertyFragment(propertyPath, property));
        } else {
            LOGGER.warn("[{}] {} not found in file name", this.getType(), meta);
            return Optional.empty();
        }
    }

    /**
     * @param propertyName
     * @param property
     * @return
     */
    private IProperty<?> buildPropertyFragment(String propertyPath, IProperty<?> property) {
        String[] fragments = propertyPath.split("\\.");
        if (fragments.length > 1) {
            IProperty<?> pp = property;
            for (int i = (fragments.length - 2); i >= 0; i--) {
                pp = IProperty.buildObject(fragments[i], pp);
            }
            return pp;
        } else {
            return property;
        }
    }

    /**
     * Parse a date
     * @param date
     * @return
     * @throws ModuleException
     */
    public OffsetDateTime parseDate(String date, String format) throws ModuleException {
        try {
            return OffsetDateTime.of(LocalDate.parse(date, DateTimeFormatter.ofPattern(format)).atStartOfDay(),
                                     ZoneOffset.UTC);
        } catch (Exception e) {
            throw new ModuleException(String.format("Parse date exception %s", e.getMessage()));
        }
    }

    /**
     * Parse a date
     * @param date
     * @return
     * @throws ModuleException
     */
    public OffsetDateTime parseDateTime(String date, String format) throws ModuleException {
        try {
            return OffsetDateTime.of(LocalDateTime.parse(date, DateTimeFormatter.ofPattern(format)), ZoneOffset.UTC);
        } catch (Exception e) {
            throw new ModuleException(String.format("Parse date exception %s", e.getMessage()));
        }
    }

    /**
     * Validate the current {@link DataTypeDescriptor}
     * @throws ModuleException
     */
    public void validate() throws ModuleException {
        List<String> missingMetas = this.metadata.stream().filter(m -> !this.nameProperties.keySet().contains(m))
                .collect(Collectors.toList());
        if (!missingMetas.isEmpty()) {
            throw new ModuleException(String.format("Meta missing from fileName : %s", missingMetas.toString()));
        }
    }

    /**
     * Check if the current {@link DataTypeDescriptor} matches the given fileName
     * @param fileName
     * @return
     */
    public boolean matches(String fileName) {
        boolean matches = true;
        Matcher matcher = Pattern.compile(this.regexp).matcher(fileName);
        if (matcher.matches()) {
            if ((this.apidNumber != null) && !this.apidNumber.isEmpty()) {
                // Test APIDNumber if any
                Integer groupIndex = this.nameProperties.get(PropertiesEnum.APID_NUMBER.getName());
                Integer apid = Integer.parseInt(matcher.group(groupIndex));
                LOGGER.debug("Checking APIDNumber {} from list {}", apid, this.apidNumber.toString());
                if (!this.apidNumber.contains(apid)) {
                    matches = false;
                }
            }
            if ((this.fileIdentifier != null) && !this.fileIdentifier.isEmpty()) {
                // Test FileIdentifier if any
                Integer groupIndex = this.nameProperties.get(PropertiesEnum.FILE_IDENTIFIER.getName());
                String fileId = matcher.group(groupIndex);
                LOGGER.debug("Checking FileIdentifier {} from list {}", fileId, this.fileIdentifier.toString());
                if (!this.fileIdentifier.contains(fileId)) {
                    matches = false;
                }
            }
        } else {
            matches = false;
        }
        return matches;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.regexp = name;
        Matcher matcher = pattern.matcher(name);

        // Find all matches
        int index = 1;
        while (matcher.find()) {
            String property = matcher.group().replace("{{ ", "").replace(" }}", "");
            nameProperties.put(property, index);
            regexp = regexp.replaceAll(PropertiesEnum.asConfPattern(property), PropertiesEnum.getPattern(property));
            index++;
        }
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public List<String> getExample() {
        return example;
    }

    public void setExample(List<String> example) {
        this.example = example;
    }

    public String getGranule_type() {
        return granule_type;
    }

    public void setGranule_type(String granule_type) {
        this.granule_type = granule_type;
    }

    public List<String> getTar_content() {
        return tar_content;
    }

    public void setTar_content(List<String> tar_content) {
        this.tar_content = tar_content;
    }

    public String getMission() {
        return mission;
    }

    public void setMission(String mission) {
        this.mission = mission;
    }

    public String getNature() {
        return nature;
    }

    public void setNature(String nature) {
        this.nature = nature;
    }

    public List<String> getMetadata() {
        return metadata;
    }

    public void setMetadata(List<String> metadata) {
        this.metadata = metadata;
    }

    public List<Integer> getApidNumber() {
        return apidNumber;
    }

    public void setApidNumber(List<Integer> apidNumber) {
        this.apidNumber = apidNumber;
    }

    public String getRegexp() {
        return this.regexp;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

}
