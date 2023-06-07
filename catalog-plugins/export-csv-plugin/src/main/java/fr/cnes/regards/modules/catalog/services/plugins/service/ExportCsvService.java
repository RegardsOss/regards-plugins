/*
 * Copyright 2017-2023 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.services.plugins.service;

import com.google.gson.Gson;
import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.modules.catalog.services.helper.IServiceHelper;
import fr.cnes.regards.modules.catalog.services.plugins.ExportCsvConstants;
import fr.cnes.regards.modules.catalog.services.plugins.domain.CsvHeader;
import fr.cnes.regards.modules.catalog.services.plugins.domain.CsvProcessingException;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import fr.cnes.regards.modules.search.dto.SearchRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service to stream {@link DataObject}s in a csv format.
 *
 * @author Iliana Ghazali
 **/
@Service
@MultitenantTransactional
public class ExportCsvService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportCsvService.class);

    private final IServiceHelper serviceHelper;

    private final Gson gson;

    public ExportCsvService(IServiceHelper serviceHelper, Gson gson) {
        this.serviceHelper = serviceHelper;
        this.gson = gson;
    }

    public Page<DataObject> retrievePageDataObjects(SearchRequest searchRequest, int pageIndex, int size)
        throws ModuleException {
        return serviceHelper.getDataObjects(searchRequest, pageIndex, size);
    }

    /**
     * Write stream containing csv composed of dataObjects.
     *
     * @param searchRequest parameters to search dataObjects
     * @param header        header composed of basic and dynamic properties
     * @param csvFileName   name of the csv to export
     * @throws CsvProcessingException if an error occurred during the csv stream
     */
    public StreamingResponseBody writeCsvPageStream(SearchRequest searchRequest, CsvHeader header, String csvFileName) {
        return outStream -> {
            Page<DataObject> pageDataObjects;
            int pageIndex = 0;
            long start = System.currentTimeMillis();
            LOGGER.debug("Starting to generate csv \"{}\".", csvFileName);
            try (CSVPrinter csvPrinter = new CSVPrinter(new OutputStreamWriter(outStream, StandardCharsets.UTF_8),
                                                        getCsvFormat(header))) {
                do {
                    pageDataObjects = retrievePageDataObjects(searchRequest,
                                                              pageIndex,
                                                              ExportCsvConstants.DEFAULT_DATA_OBJECTS_PAGE_SIZE);
                    LOGGER.trace("Handling csv for {} dataObjects at page index {}.",
                                 pageDataObjects.getNumberOfElements(),
                                 pageIndex);
                    writeCsvBody(header, pageDataObjects.getContent(), csvPrinter);
                    pageIndex++;
                } while (pageDataObjects.hasNext());
                // close stream
                csvPrinter.flush();
                LOGGER.debug("Successfully generated csv \"{}\" from {} dataObjects in {} ms.",
                             csvFileName,
                             pageDataObjects.getTotalElements(),
                             System.currentTimeMillis() - start);
            } catch (ModuleException | IOException e) {
                throw new CsvProcessingException(String.format("Failed to stream csv body. Cause: %s", e.getMessage()),
                                                 e);
            }

        };
    }

    /**
     * Write body of csv with basic and dynamic properties.
     *
     * @param header      name of properties to retrieved from the dataObjects
     * @param dataObjects data to be processed
     * @param csvPrinter  prints values in a CSV format
     * @throws IOException if csvPrinter failed to print values
     */
    private void writeCsvBody(CsvHeader header, List<DataObject> dataObjects, CSVPrinter csvPrinter)
        throws IOException {
        for (DataObject dataObject : dataObjects) {
            DataObjectFeature feature = dataObject.getFeature();
            LOGGER.trace("-----> Handling feature with providerId \"{}\".", feature.getProviderId());
            printBasicProperties(header, csvPrinter, feature);
            printDynamicProperties(header, csvPrinter, feature);
            // End record writing
            csvPrinter.println();
        }
    }

    /**
     * Print basic properties common to all dataObjects.
     */
    private void printBasicProperties(CsvHeader csvHeader, CSVPrinter csvPrinter, DataObjectFeature feature)
        throws IOException {
        LOGGER.trace("--> Printing basic properties.");
        for (String headerName : csvHeader.basicHeader()) {
            switch (headerName) {
                case StaticProperties.FEATURE_ID -> csvPrinter.print(feature.getId());
                case StaticProperties.FEATURE_PROVIDER_ID -> csvPrinter.print(feature.getProviderId());
                case StaticProperties.FEATURE_LABEL -> csvPrinter.print(feature.getLabel());
                case StaticProperties.MODEL_TYPE -> csvPrinter.print(feature.getModel());
                case StaticProperties.FEATURE_FILES -> csvPrinter.print(getBasicFeatureFiles(feature));
                case StaticProperties.FEATURE_TAGS -> csvPrinter.print(feature.getTags());
                default ->
                    throw new IllegalArgumentException(String.format("Header named %s unknown. This case should never "
                                                                     + "happen!", headerName));
            }
        }
    }

    /**
     * Get only required property from feature files
     */
    private List<String> getBasicFeatureFiles(DataObjectFeature feature) {
        return feature.getFiles().entries().stream().map(files -> files.getValue().getFilename()).toList();
    }

    /**
     * Print configurable properties to retrieve from the feature properties.
     */
    private void printDynamicProperties(CsvHeader header, CSVPrinter csvPrinter, DataObjectFeature feature)
        throws IOException {
        List<String> dynamicProperties = header.dynamicHeader();
        LOGGER.trace("--> Printing dynamic properties. List of dynamic properties to search {}.", dynamicProperties);
        for (String headerName : dynamicProperties) {
            IProperty<?> property = feature.getProperty(headerName);
            if (property != null) {
                // print value directly if one of these types to avoid double quotes
                if (property.getType() == PropertyType.STRING) {
                    csvPrinter.print(property.getValue());
                } else if (property.getType() == PropertyType.DATE_ISO8601) {
                    csvPrinter.print(OffsetDateTimeAdapter.format((OffsetDateTime) property.getValue()));
                } else {
                    csvPrinter.print(gson.toJson(property.getValue()));
                }
            } else {
                LOGGER.debug("""
                                 Property value corresponding to path "{}" was not found within the feature properties \
                                 with providerId "{}".
                                 The value will not be filled. Please not that property path with depth greater that 1 \
                                 are not handled for now.""", headerName, feature.getProviderId());
                csvPrinter.print(null); // print blank to shift column
            }
        }
    }

    private CSVFormat getCsvFormat(CsvHeader headers) {
        return CSVFormat.DEFAULT.builder().setHeader(headers.toArray()).build();
    }

    /**
     * Build header of the csv with basic and configurable properties.
     *
     * @param basicPropertiesToExclude    properties to exclude from basic properties {@link ExportCsvConstants#BASIC_HEADER}
     * @param dynamicPropertiesToRetrieve configurable properties to add to the csv in addition to the basic properties
     * @return CsvHeader
     */
    public CsvHeader getHeader(List<String> basicPropertiesToExclude, List<String> dynamicPropertiesToRetrieve) {
        // init basicHeader
        List<String> customBasicHeader;
        if (CollectionUtils.isEmpty(basicPropertiesToExclude)) {
            customBasicHeader = ExportCsvConstants.BASIC_HEADER;
        } else {
            customBasicHeader = ExportCsvConstants.BASIC_HEADER.stream()
                                                               .filter(headerName -> !basicPropertiesToExclude.contains(
                                                                   headerName))
                                                               .toList();
        }
        // init dynamicHeader
        List<String> customDynamicProperties;
        if (CollectionUtils.isEmpty(dynamicPropertiesToRetrieve)) {
            customDynamicProperties = List.of();
        } else {
            customDynamicProperties = dynamicPropertiesToRetrieve;
        }

        return new CsvHeader(customBasicHeader, customDynamicProperties);
    }

    public String getCsvFilename(String filename) {
        return StringUtils.isBlank(filename) ?
            String.format(ExportCsvConstants.CSV_FILENAME_PATTERN, OffsetDateTimeAdapter.format(OffsetDateTime.now())) :
            filename;
    }
}

