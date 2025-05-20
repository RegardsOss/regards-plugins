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
package fr.cnes.regards.modules.catalog.stac.rest.extension.download;

import fr.cnes.regards.framework.feign.security.FeignSecurityManager;
import fr.cnes.regards.framework.security.annotation.ResourceAccess;
import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol.DownloadPreparationResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol.FiltersByCollection;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacException;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType;
import fr.cnes.regards.modules.catalog.stac.rest.link.LinkCreatorService;
import fr.cnes.regards.modules.catalog.stac.rest.utils.HeaderUtils;
import fr.cnes.regards.modules.catalog.stac.rest.utils.TryToResponseEntity;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.CollectionDownloadService;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.CollectionSearchService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.vavr.control.Try;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static fr.cnes.regards.modules.catalog.stac.rest.utils.StacApiConstants.*;

/**
 * Download collection items API.
 *
 * @author Marc SORDI
 */
@RestController
@RequestMapping(path = STAC_DOWNLOAD_BY_COLLECTION_PATH)
public class CollectionDownloadController implements TryToResponseEntity {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionDownloadController.class);

    private final CollectionSearchService collectionSearchService;

    private final CollectionDownloadService collectionDownloadService;

    private final LinkCreatorService linkCreatorService;

    private final FeignSecurityManager feignSecurityManager;

    private final ConfigurationAccessorFactory configFactory;

    public CollectionDownloadController(CollectionSearchService collectionSearchService,
                                        CollectionDownloadService collectionDownloadService,
                                        LinkCreatorService linkCreatorService,
                                        FeignSecurityManager feignSecurityManager,
                                        ConfigurationAccessorFactory configFactory) {
        this.collectionSearchService = collectionSearchService;
        this.collectionDownloadService = collectionDownloadService;
        this.linkCreatorService = linkCreatorService;
        this.feignSecurityManager = feignSecurityManager;
        this.configFactory = configFactory;
    }

    public static final String CONTENT_DISPOSITION_ATTACHMENT_FORMAT = "attachment; filename=%s";

    @Operation(summary = "Compute information for downloading a set of collections as zip at once or one by one",
               description =
                   "For each collection and its item query parameters, a download link, the forecast download size and item number are given plus a"
                   + " link to download all at once")
    @ApiResponses(value = { @ApiResponse(responseCode = "200",
                                         description = "Zip download information prepared",
                                         content = { @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                              schema = @Schema(implementation = DownloadPreparationResponse.class)) }) })
    @ResourceAccess(description = "Prepare information for collection download as zip", role = DefaultRole.PUBLIC)
    @PostMapping(value = STAC_DOWNLOAD_AS_ZIP_PREPARE_PATH_SUFFIX, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DownloadPreparationResponse> prepareZipDownload(
        @RequestBody FiltersByCollection filtersByCollection) {
        return toResponseEntity(collectionSearchService.prepareZipDownload(filtersByCollection,
                                                                           linkCreatorService.makeDownloadLinkCreator(
                                                                               feignSecurityManager,
                                                                               Optional.ofNullable(filtersByCollection.getAppendAuthParameters())
                                                                                       .orElse(true))));
    }

    @Operation(summary = "Endpoint to download all collections as zip at once",
               description = "(Stream) Prepare NGINX mod_zip descriptor file to download all items of all collections at once")
    @ApiResponses(value = { @ApiResponse(responseCode = "200",
                                         description = "NGINX mod_zip descriptor built for all collections") })
    @ResourceAccess(description = "Download all collections at once", role = DefaultRole.PUBLIC)
    @GetMapping(value = STAC_DOWNLOAD_ALL_COLLECTIONS_AS_ZIPSTREAM_SUFFIX, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadAllCollectionsAsZipStream(final HttpServletResponse response,
                                                                                   @RequestParam(name = "tinyurl")
                                                                                   String tinyurl,
                                                                                   @RequestParam(name = "filename",
                                                                                                 defaultValue = "regards.zip")
                                                                                   String filename) {
        return toResponseEntity(delegateDownloadToNginxAsStream(response, Optional.empty(), tinyurl, filename));
    }

    @Operation(summary = "Endpoint to download all collections as zip at once",
               description = "Prepare NGINX mod_zip descriptor file to download all items of all collections at once")
    @ApiResponses(value = { @ApiResponse(responseCode = "200",
                                         description = "NGINX mod_zip descriptor built for all collections") })
    @ResourceAccess(description = "Download all collections at once", role = DefaultRole.PUBLIC)
    @GetMapping(value = STAC_DOWNLOAD_ALL_COLLECTIONS_AS_ZIP_SUFFIX, produces = MediaType.TEXT_PLAIN_VALUE)
    public void downloadAllCollectionsAsZip(final HttpServletResponse response,
                                            @RequestParam(name = "tinyurl") String tinyurl,
                                            @RequestParam(name = "filename", defaultValue = "regards.zip")
                                            String filename) {
        delegateDownloadToNginx(response, Optional.empty(), tinyurl, filename);
    }

    @Operation(summary = "Endpoint to download script for downloading all collection items")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Script file stream") })
    @ResourceAccess(description = "Download all script", role = DefaultRole.PUBLIC)
    @GetMapping(value = STAC_DOWNLOAD_ALL_COLLECTIONS_SCRIPT_SUFFIX)
    public void getDownloadAllScript(final HttpServletResponse response,
                                     @RequestParam(name = "tinyurl") String tinyurl,
                                     @RequestParam(name = "filename", defaultValue = "regards.py") String filename,
                                     @RequestHeader Map<String, String> headers) {
        boolean appendAuthParam = !configFactory.makeConfigurationAccessor().isDisableauthParam();
        generateAndStreamDownloadScript(linkCreatorService.makeSearchPageLinkCreator(1,
                                                                                     ItemSearchBody.builder().build(),
                                                                                     appendAuthParam,
                                                                                     headers),
                                        response,
                                        Optional.empty(),
                                        tinyurl,
                                        filename);
    }

    @Operation(summary = "Endpoint to download a single collection as zip",
               description = "(Stream) Prepare NGINX mod_zip descriptor file to download all items of a single collection")
    @ApiResponses(value = { @ApiResponse(responseCode = "200",
                                         description = "NGINX mod_zip descriptor built for current collection") })
    @ResourceAccess(description = "(Stream) Download by collection", role = DefaultRole.PUBLIC)
    @GetMapping(value = STAC_DOWNLOAD_BY_COLLECTION_AS_ZIP_STREAM_SUFFIX, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadSingeCollectionAsZipStream(final HttpServletResponse response,
                                                                                    @PathVariable(name = "collectionId")
                                                                                    String collectionId,
                                                                                    @RequestParam(name = "tinyurl")
                                                                                    String tinyurl,
                                                                                    @RequestParam(name = "filename",
                                                                                                  defaultValue = "regards.zip")
                                                                                    String filename) {
        return toResponseEntity(delegateDownloadToNginxAsStream(response,
                                                                Optional.of(collectionId),
                                                                tinyurl,
                                                                filename));
    }

    @Operation(summary = "Endpoint to download a collection sample as zip",
               description = "(Stream) Prepare NGINX mod_zip descriptor file to download first item of the collection")
    @ApiResponses(value = { @ApiResponse(responseCode = "200",
                                         description = "NGINX mod_zip descriptor built for current collection") })
    @ResourceAccess(description = "(Stream) Download sample by collection", role = DefaultRole.PUBLIC)
    @GetMapping(value = STAC_DOWNLOAD_SAMPLE_BY_COLLECTION_AS_ZIP_STREAM_SUFFIX, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadSingeCollectionSampleAsZipStream(final HttpServletResponse response,
                                                                                          @PathVariable(name = "collectionId")
                                                                                          String collectionId,
                                                                                          @RequestParam(name = "tinyurl")
                                                                                          String tinyurl,
                                                                                          @RequestParam(name = "filename",
                                                                                                        defaultValue = "regards.zip")
                                                                                          String filename) {
        return toResponseEntity(delegateDownloadToNginxAsStream(response,
                                                                Optional.of(collectionId),
                                                                tinyurl,
                                                                filename,
                                                                Boolean.TRUE));
    }

    @Operation(summary = "Endpoint to download a single collection as zip",
               description = "Prepare NGINX mod_zip descriptor file to download all items of a single collection")
    @ApiResponses(value = { @ApiResponse(responseCode = "200",
                                         description = "NGINX mod_zip descriptor built for current collection") })
    @ResourceAccess(description = "Download by collection", role = DefaultRole.PUBLIC)
    @GetMapping(value = STAC_DOWNLOAD_BY_COLLECTION_AS_ZIP_SUFFIX, produces = MediaType.TEXT_PLAIN_VALUE)
    public void downloadSingeCollectionAsZip(final HttpServletResponse response,
                                             @PathVariable(name = "collectionId") String collectionId,
                                             @RequestParam(name = "tinyurl") String tinyurl,
                                             @RequestParam(name = "filename", defaultValue = "regards.zip")
                                             String filename) {
        delegateDownloadToNginx(response, Optional.of(collectionId), tinyurl, filename);
    }

    @Operation(summary = "Endpoint to download script for downloading all items of a single collection")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Script file stream") })
    @ResourceAccess(description = "Download script for a single collection", role = DefaultRole.PUBLIC)
    @GetMapping(value = STAC_DOWNLOAD_BY_COLLECTION_SCRIPT_SUFFIX)
    public void getDownloadScript(final HttpServletResponse response,
                                  @PathVariable(name = "collectionId") String collectionId,
                                  @RequestParam(name = "tinyurl") String tinyurl,
                                  @RequestParam(name = "filename", defaultValue = "regards.py") String filename,
                                  @RequestHeader Map<String, String> headers) {
        boolean appendAuthParam = !configFactory.makeConfigurationAccessor().isDisableauthParam();
        generateAndStreamDownloadScript(linkCreatorService.makeSearchPageLinkCreator(1,
                                                                                     ItemSearchBody.builder().build(),
                                                                                     appendAuthParam,
                                                                                     HeaderUtils.getStacHeaders(headers)),
                                        response,
                                        Optional.of(collectionId),
                                        tinyurl,
                                        filename);
    }

    @Operation(summary = "Endpoint to download a collection sample as zip",
               description = "Prepare NGINX mod_zip descriptor file to download first item of the collection")
    @ApiResponses(value = { @ApiResponse(responseCode = "200",
                                         description = "NGINX mod_zip descriptor built for current collection") })
    @ResourceAccess(description = "Download sample by collection", role = DefaultRole.PUBLIC)
    @GetMapping(value = STAC_DOWNLOAD_SAMPLE_BY_COLLECTION_AS_ZIP_SUFFIX, produces = MediaType.TEXT_PLAIN_VALUE)
    public void downloadSingeCollectionSampleAsZip(final HttpServletResponse response,
                                                   @PathVariable(name = "collectionId") String collectionId,
                                                   @RequestParam(name = "tinyurl") String tinyurl,
                                                   @RequestParam(name = "filename", defaultValue = "regards.zip")
                                                   String filename) {
        delegateDownloadToNginx(response, Optional.of(collectionId), tinyurl, filename, Boolean.TRUE);
    }

    private Try<StreamingResponseBody> delegateDownloadToNginxAsStream(final HttpServletResponse response,
                                                                       final Optional<String> collectionId,
                                                                       final String tinyurl,
                                                                       final String filename) {
        return delegateDownloadToNginxAsStream(response, collectionId, tinyurl, filename, Boolean.FALSE);
    }

    private Try<StreamingResponseBody> delegateDownloadToNginxAsStream(final HttpServletResponse response,
                                                                       final Optional<String> collectionId,
                                                                       final String tinyurl,
                                                                       final String filename,
                                                                       final boolean onlySample) {
        LOGGER.debug("(stream) Preparing mod_zip descriptor...");
        // Activate mod_zip on NGINX
        response.addHeader("X-Archive-Files", "zip");
        // Define zip output file name
        response.addHeader(HttpHeaders.CONTENT_DISPOSITION,
                           String.format(CONTENT_DISPOSITION_ATTACHMENT_FORMAT, filename));
        // Prepare mod_zip descriptor file
        return collectionDownloadService.prepareDescriptorAsStream(collectionId,
                                                                   tinyurl,
                                                                   linkCreatorService.makeDownloadLinkCreator(
                                                                       feignSecurityManager,
                                                                       true),
                                                                   onlySample);
    }

    private void delegateDownloadToNginx(final HttpServletResponse response,
                                         final Optional<String> collectionId,
                                         final String tinyurl,
                                         final String filename) {
        delegateDownloadToNginx(response, collectionId, tinyurl, filename, Boolean.FALSE);
    }

    private void delegateDownloadToNginx(final HttpServletResponse response,
                                         final Optional<String> collectionId,
                                         final String tinyurl,
                                         final String filename,
                                         final boolean onlySample) {
        LOGGER.debug("Preparing mod_zip descriptor...");
        // Activate mod_zip on NGINX
        response.addHeader("X-Archive-Files", "zip");
        // Define zip output file name
        response.addHeader(HttpHeaders.CONTENT_DISPOSITION,
                           String.format(CONTENT_DISPOSITION_ATTACHMENT_FORMAT, filename));
        // Prepare mod_zip descriptor file
        try {
            collectionDownloadService.prepareDescriptor(response.getOutputStream(),
                                                        collectionId,
                                                        tinyurl,
                                                        linkCreatorService.makeDownloadLinkCreator(feignSecurityManager,
                                                                                                   true),
                                                        onlySample);
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new StacException("Cannot open output stream", e, StacFailureType.DOWNLOAD_IO_EXCEPTION);
        }
    }

    private void generateAndStreamDownloadScript(final SearchPageLinkCreator searchPageLinkCreator,
                                                 final HttpServletResponse response,
                                                 final Optional<String> collectionId,
                                                 final String tinyurl,
                                                 final String filename) {
        LOGGER.debug("Generating download script...");
        response.addHeader(HttpHeaders.CONTENT_DISPOSITION,
                           String.format(CONTENT_DISPOSITION_ATTACHMENT_FORMAT, filename));
        try {
            collectionDownloadService.generateEOdagScript(searchPageLinkCreator,
                                                          response.getOutputStream(),
                                                          collectionId,
                                                          tinyurl,
                                                          filename);
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new StacException("Cannot open output stream", e, StacFailureType.DOWNLOAD_IO_EXCEPTION);
        }
    }
}
