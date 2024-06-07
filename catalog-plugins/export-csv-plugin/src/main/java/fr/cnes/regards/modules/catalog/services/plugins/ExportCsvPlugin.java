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
package fr.cnes.regards.modules.catalog.services.plugins;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.catalog.services.domain.ServicePluginParameters;
import fr.cnes.regards.modules.catalog.services.domain.ServiceScope;
import fr.cnes.regards.modules.catalog.services.domain.annotations.CatalogServicePlugin;
import fr.cnes.regards.modules.catalog.services.domain.plugins.IEntitiesServicePlugin;
import fr.cnes.regards.modules.catalog.services.helper.CatalogPluginResponseFactory;
import fr.cnes.regards.modules.catalog.services.helper.CatalogPluginResponseFactory.CatalogPluginResponseType;
import fr.cnes.regards.modules.catalog.services.plugins.domain.CsvHeader;
import fr.cnes.regards.modules.catalog.services.plugins.domain.CsvProcessingException;
import fr.cnes.regards.modules.catalog.services.plugins.service.ExportCsvService;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Optional;

/**
 * Plugin to export {@link DataObject}s in a csv.
 *
 * @author Iliana Ghazali
 */
@Plugin(description = "Plugin to export REGARDS products to csv.",
        id = "ExportCsvPlugin",
        version = "1.0.0",
        author = "REGARDS Team",
        contact = "regards@csgroup.eu",
        license = "GPLv3",
        owner = "CSSI",
        url = "https://github.com/RegardsOss",
        markdown = "export-csv-doc-admin.md",
        userMarkdown = "export-csv-doc-user.md")
@CatalogServicePlugin(applicationModes = { ServiceScope.MANY }, entityTypes = { EntityType.DATA })
public class ExportCsvPlugin extends AbstractCatalogServicePlugin implements IEntitiesServicePlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportCsvPlugin.class);

    @PluginParameter(name = ExportCsvConstants.DYNAMIC_CSV_FILENAME, label = "Custom csv filename", description = """
        Name of the exported csv file. If the attribute is absent, the file will be named "
        csv_export_<current_date>.csv by default.""", optional = true)
    private String dynamicCsvFilename;

    @PluginParameter(name = ExportCsvConstants.MAX_DATA_OBJECTS_TO_EXPORT,
                     label = "Maximum number of data to export",
                     description = """
                         Maximum number of exportable products in the csv."
                         The variable must be chosen carefully to avoid system overload.
                         Default 10000.""",
                     defaultValue = "10000")
    private int maxDataObjectsToExport;

    @PluginParameter(name = ExportCsvConstants.BASIC_PROPERTIES, label = """
        Basic properties to exclude from the csv file. By default all properties are included \
        ("id", "providerId", "label", "model", "files", "tags").""", description = """
        Basic properties to exclude from the csv. By default all properties are included.
        List of properties included : "id", "providerId", "label", "model", "files", "tags".
        To exclude one of the property, just add it to the list of properties to exclude.""", optional = true)
    private List<String> basicPropertiesToExclude;

    @PluginParameter(name = ExportCsvConstants.DYNAMIC_PROPERTIES,
                     label = "Optional model properties as json paths",
                     description = """
                         Json paths to retrieve dynamic properties from the selected data. The corresponding values will then be added to the csv export
                         only if they were found.
                         Warning: the property and/or fragments names must correspond exactly to those defined in the data models.
                         Properties cannot be retrieved if the depth is greater than 1.
                         Example:
                         Properties in the json product
                         { ...
                           “properties”: {
                             “property1”: {
                               “nestedProperty”: “value1”
                             }
                           }
                         }
                         Attribute in the plugin: property1.nestedProperty (do not include the prefix properties which is common to all products).
                         """,
                     optional = true)
    private List<String> dynamicPropertiesToRetrieve;

    @Autowired
    private ExportCsvService exportCsvService;

    @Override
    public ResponseEntity<StreamingResponseBody> apply(ServicePluginParameters parameters,
                                                       HttpServletResponse response) {
        ResponseEntity<StreamingResponseBody> streamingResponse;
        try {
            // export csv only if the number of dataObjects returned do not exceed limit allowed
            Page<DataObject> results = exportCsvService.retrievePageDataObjects(parameters.getSearchRequest(), 0, 1);
            if (results.getTotalElements() > this.maxDataObjectsToExport) {
                streamingResponse = createFailureResponse(response,
                                                          String.format(
                                                              "Number of data to export to csv (%d) exceeds the "
                                                              + "maximum number of data allowed (%d). Csv was not "
                                                              + "created.",
                                                              results.getTotalElements(),
                                                              this.maxDataObjectsToExport),
                                                          null);
            } else {
                String csvFileName = exportCsvService.getCsvFilename(this.dynamicCsvFilename);
                CsvHeader csvHeader = exportCsvService.getHeader(basicPropertiesToExclude, dynamicPropertiesToRetrieve);
                streamingResponse = createSuccessResponse(response,
                                                          exportCsvService.writeCsvPageStream(parameters.getSearchRequest(),
                                                                                              csvHeader,
                                                                                              csvFileName),
                                                          csvFileName);
            }
        } catch (CsvProcessingException | ModuleException e) {
            streamingResponse = createFailureResponse(response,
                                                      String.format("Error during creation of csv. Cause: %s.",
                                                                    e.getMessage()),
                                                      e);
        }
        return streamingResponse;

    }

    private ResponseEntity<StreamingResponseBody> createSuccessResponse(HttpServletResponse response,
                                                                        StreamingResponseBody streamingResponseBody,
                                                                        String csvFilename) {
        return CatalogPluginResponseFactory.createStreamSuccessResponse(response,
                                                                        streamingResponseBody,
                                                                        csvFilename,
                                                                        new MediaType("text", "csv"),
                                                                        Optional.empty());
    }

    private ResponseEntity<StreamingResponseBody> createFailureResponse(HttpServletResponse response,
                                                                        String errorMessage,
                                                                        Exception e) {
        LOGGER.error(errorMessage, e);
        // NB: response is sent with success status even in case of failure because frontend does not handle plugin
        // errors properly
        return CatalogPluginResponseFactory.createSuccessResponse(response,
                                                                  CatalogPluginResponseType.JSON,
                                                                  errorMessage);
    }

}
