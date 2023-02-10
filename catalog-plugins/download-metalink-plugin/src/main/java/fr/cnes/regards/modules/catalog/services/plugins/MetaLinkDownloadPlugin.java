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
package fr.cnes.regards.modules.catalog.services.plugins;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.security.utils.jwt.JWTService;
import fr.cnes.regards.framework.security.utils.jwt.exception.JwtException;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.catalog.services.domain.ServicePluginParameters;
import fr.cnes.regards.modules.catalog.services.domain.ServiceScope;
import fr.cnes.regards.modules.catalog.services.domain.annotations.CatalogServicePlugin;
import fr.cnes.regards.modules.catalog.services.domain.plugins.IEntitiesServicePlugin;
import fr.cnes.regards.modules.catalog.services.helper.CatalogPluginResponseFactory;
import fr.cnes.regards.modules.catalog.services.helper.CatalogPluginResponseFactory.CatalogPluginResponseType;
import fr.cnes.regards.modules.catalog.services.helper.IServiceHelper;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import fr.cnes.regards.modules.search.domain.SearchRequest;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeType;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

/**
 * Plugin to allow download metalink for all selected entities
 *
 * @author Sébastien Binda
 */
@Plugin(description = "Plugin to allow download on multiple data selection by creating a metalink file.",
        id = "MetaLinkPlugin",
        version = "1.0.0",
        author = "REGARDS Team",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CSSI",
        url = "https://github.com/RegardsOss",
        markdown = "metalink-download.md")
@CatalogServicePlugin(applicationModes = { ServiceScope.MANY }, entityTypes = { EntityType.DATA })
public class MetaLinkDownloadPlugin extends AbstractCatalogServicePlugin implements IEntitiesServicePlugin {

    /**
     * Class logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogPluginResponseFactory.class);

    @Autowired
    private IServiceHelper serviceHelper;

    @Autowired
    private JWTService jwtService;

    @PluginParameter(label = "Download only image files.",
                     description = "If activated, metalink file will only contains image files of the selected products.",
                     defaultValue = "false")
    private final Boolean onlyImages = Boolean.FALSE;

    private static final String METALINK_FILE_NAME = "regards-download.metalink";

    @Override
    public ResponseEntity<StreamingResponseBody> apply(ServicePluginParameters parameters,
                                                       HttpServletResponse response) {
        Page<DataObject> results;
        try {
            results = serviceHelper.getDataObjects(parameters.getSearchRequest(), 0, 1);
            if (results.getTotalElements() > 10_000) {
                return CatalogPluginResponseFactory.createSuccessResponse(response,
                                                                          CatalogPluginResponseType.JSON,
                                                                          String.format(
                                                                              "Number of files to download %d exceed maximum allowed of %d",
                                                                              results.getTotalElements(),
                                                                              10_000));
            }
        } catch (ModuleException e) {
            String message = String.format("Error applying service. OpenSearchQuery is not a valid query. %s",
                                           e.getMessage());
            LOGGER.error(message, e);
            return CatalogPluginResponseFactory.createSuccessResponse(response,
                                                                      CatalogPluginResponseType.JSON,
                                                                      message);
        }

        return CatalogPluginResponseFactory.createStreamSuccessResponse(response,
                                                                        streamMetalinkXml(parameters.getSearchRequest()),
                                                                        METALINK_FILE_NAME,
                                                                        MediaType.APPLICATION_OCTET_STREAM,
                                                                        Optional.empty());
    }

    private StreamingResponseBody streamMetalinkXml(SearchRequest searchRequest) {
        StreamingResponseBody stream = out -> {
            LOGGER.debug("Start writting metalink ... ");
            try {
                Page<DataObject> results;
                int pageIndex = 0;
                // Create XML metalink object
                XMLOutputFactory xof = XMLOutputFactory.newInstance();
                XMLStreamWriter xtw = xof.createXMLStreamWriter(out);
                xtw.writeStartDocument("utf-8", "1.0");
                xtw.writeStartElement("metalink");
                xtw.writeAttribute("xmlns", "http://www.metalinker.org");
                xtw.writeStartElement("files");
                do {
                    results = serviceHelper.getDataObjects(searchRequest, pageIndex, 1000);
                    LOGGER.debug("Wrtting {} data ... ", results.getSize());
                    LOGGER.debug("Wrtting {} data done. ", results.getSize());
                    downloadOrderMetalink(results.getContent(), xtw);
                    xtw.flush();
                    pageIndex++;
                } while (results.hasNext());
                xtw.writeEndElement();
                xtw.writeEndElement();
                xtw.writeEndDocument();
                xtw.flush();
                xtw.close();
            } catch (XMLStreamException | ModuleException e) {
                LOGGER.error("Error writting response", e);
            } finally {
                out.flush();
            }
            LOGGER.debug("Wrtting data done. ");
        };
        return stream;
    }

    /**
     *
     */
    private void downloadOrderMetalink(List<DataObject> dataObjects, XMLStreamWriter xtw)
        throws XMLStreamException, MalformedURLException {
        // For all data files
        for (DataObject dataObject : dataObjects) {
            for (DataFile file : dataObject.getFiles().values()) {
                if (file.getDataType().equals(DataType.RAWDATA)) {
                    // If only images are to download, to not add the file with a different mimeType
                    if (onlyImages && !file.getMimeType().isCompatibleWith(MimeType.valueOf("image/*"))) {
                        break;
                    }
                    URL dataFileUrl = getDataFileURL(file);
                    String filename = getDataObjectFileNameForDownload(dataObject, file);

                    xtw.writeStartElement("file");
                    xtw.writeAttribute("name", filename);

                    xtw.writeStartElement("identity");
                    xtw.writeCharacters(filename);
                    xtw.writeEndElement();

                    xtw.writeStartElement("mimeType");
                    xtw.writeCharacters(file.getMimeType().toString());
                    xtw.writeEndElement();

                    xtw.writeStartElement("resources");
                    xtw.writeStartElement("url");
                    xtw.writeCharacters(dataFileUrl.toString());
                    xtw.writeEndElement();
                    xtw.writeEndElement();

                    xtw.writeEndElement();

                }
            }
        }

    }

    /**
     * File name for download is : <dataobjectName/dataFileName>. The dataFile name is the name of {@link DataFile} or
     * name of URI if name is null.
     *
     * @param dataobject {@link DataObject}
     * @param datafile   {@link DataFile}
     * @return String fileName
     */
    private String getDataObjectFileNameForDownload(DataObject dataobject, DataFile datafile) {
        String fileName = datafile.getFilename() != null ?
            datafile.getFilename() :
            FilenameUtils.getName(datafile.asUri().getPath());
        String dataObjectName = dataobject.getLabel() != null ? dataobject.getLabel().replaceAll(" ", "") : "files";
        return String.format("%s/%s", dataObjectName, fileName);
    }

    /**
     * Generate URL to download the given {@link DataFile}
     *
     * @param file {@link DataFile} to download
     * @return {@link URL}
     */
    private URL getDataFileURL(DataFile file) throws MalformedURLException {
        URI fileUri = file.asUri();
        try {
            if (file.isReference()) {
                fileUri = new URIBuilder(fileUri).build();
            } else {
                fileUri = new URIBuilder(fileUri).addParameter("token", jwtService.getCurrentToken().getJwt()).build();
            }
            LOGGER.debug(String.format("File url is : %s", fileUri.toString()));
        } catch (JwtException | URISyntaxException e) {
            LOGGER.error("Error generating URI with current security token", e);
        }
        return fileUri.toURL();
    }

}
