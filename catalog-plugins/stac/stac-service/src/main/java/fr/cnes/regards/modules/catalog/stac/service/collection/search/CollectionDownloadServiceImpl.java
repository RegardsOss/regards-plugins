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
package fr.cnes.regards.modules.catalog.stac.service.collection.search;

import com.google.gson.reflect.TypeToken;
import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.framework.modules.tinyurl.domain.TinyUrl;
import fr.cnes.regards.framework.modules.tinyurl.service.TinyUrlService;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacException;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagGenerator;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagInformation;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagParameters;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.link.DownloadLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.opensearch.service.exception.OpenSearchUnknownParameter;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.IBusinessSearchService;
import fr.cnes.regards.modules.search.service.SearchException;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;

/**
 * Implementation of {@link CollectionDownloadService}
 *
 * @author Marc SORDI
 */
@Service
@MultitenantTransactional
public class CollectionDownloadServiceImpl implements CollectionDownloadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionDownloadServiceImpl.class);

    private static final String MOD_ZIP_LINE_FORMAT = "%s %d %s %s";

    private static final String UNKNOWN_CRC32 = "-";

    private static final int MAX_PAGE_SIZE = 1000;

    private static final String STORAGE_DOWNLOAD_FILE_PATH = "/%s/resources/%s/download?token=%s";

    /**
     * Only used to split URI for {@link DownloadSource#CATALOG}
     */
    @Value("${spring.application.name}")
    private String microserviceName;

    /**
     * Download endpoint selector
     */
    @Value("${regards.zip.source:STORAGE}")
    private DownloadSource downloadSource;

    /**
     * NGINX prefix for NGINX configuration and request routing
     */
    @Value("${regards.zip.source-nginx-prefix:regards}")
    private String nginxPrefix;

    @Autowired
    private TinyUrlService tinyUrlService;

    @Autowired
    private IBusinessSearchService businessSearchService;

    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    @Autowired
    private ConfigurationAccessorFactory configFactory;

    @Override
    public void prepareDescriptor(OutputStream outputStream, Optional<String> collectionId, String tinyurl,
            DownloadLinkCreator downloadLinkCreator, boolean onlySample) {
        try (PrintWriter writer = new PrintWriter(outputStream)) {
            if (onlySample) {
                addSampleFilesToDescriptor(downloadLinkCreator, writer, collectionId, tinyurl);
            } else {
                addFilesToDescriptor(downloadLinkCreator, writer, collectionId, tinyurl);
            }
        }
    }

    @Override
    public Try<StreamingResponseBody> prepareDescriptorAsStream(Optional<String> collectionId, final String tinyurl,
            DownloadLinkCreator downloadLinkCreator, boolean onlySample) {
        return trying(() -> (StreamingResponseBody) outputStream -> {
            try (PrintWriter writer = new PrintWriter(outputStream)) {
                if (onlySample) {
                    addSampleFilesToDescriptor(downloadLinkCreator, writer, collectionId, tinyurl);
                } else {
                    addFilesToDescriptor(downloadLinkCreator, writer, collectionId, tinyurl);
                }
            }
        }).mapFailure(StacFailureType.MOD_ZIP_DESC_BUILD, () -> String.format("Download preparation failure for %s",
                                                                                  collectionId.orElse("all collections")));
    }

    @Override
    public void generateEOdagScript(SearchPageLinkCreator searchPageLinkCreator, OutputStream outputStream, Optional<String> collectionId, String tinyurl) {
        ConfigurationAccessor config = configFactory.makeConfigurationAccessor();

        // Retrieve context from tinyurl
        TinyUrl tiny = getTinyUrl(tinyurl);

        // Prepare information context
        EODagInformation information = new EODagInformation();
        information.setProjectName(runtimeTenantResolver.getTenant());
        information.setPortalName(config.getEODAGPortalName());
        information.setProvider(config.getEODAGProvider());
        information.setStacSearchApi(searchPageLinkCreator.searchAll().map(uri -> uri.toString()).getOrElse("Unknown STAC API URI"));
        information.setBaseUri(searchPageLinkCreator.searchAll().map(uri -> getBaseUri(uri)).getOrElse("Unknown host URI"));

        // Route according to multi or single collection
        try {
            if (EODagParameters.class.isAssignableFrom(Class.forName(tiny.getClassOfContext()))) {
                EODagParameters parameters = tinyUrlService.loadContext(tiny, EODagParameters.class);
                // Single collection
                try (PrintWriter writer = new PrintWriter(outputStream)) {
                        EODagGenerator.generateFromTemplate(writer, information, parameters);
                }
            } else {
                // Retrieve list of tiny url id
                Type type = new TypeToken<Set<String>>() {

                }.getType();
                java.util.Set<String> tinyUrlUuids = tinyUrlService.loadContext(tiny, type);
                // Multi collection
                List<EODagParameters> parameterList = new ArrayList<>();
                tinyUrlUuids.forEach(t -> {
                    TinyUrl tu = getTinyUrl(t);
                    try {
                        if (EODagParameters.class.isAssignableFrom(Class.forName(tu.getClassOfContext()))) {
                            parameterList.add(tinyUrlService.loadContext(tu, EODagParameters.class));
                        } else {
                            throw new StacException(String.format("Unexpected class %s", tu.getClassOfContext()), null,
                                                    StacFailureType.DOWNLOAD_UNKNOWN_CLASS_OF_TINYURL);
                        }
                    } catch (ClassNotFoundException e) {
                        throw new StacException(String.format("Unknown class %s", tu.getClassOfContext()), e,
                                                StacFailureType.DOWNLOAD_UNKNOWN_CLASS_OF_TINYURL);
                    }
                });
                try (PrintWriter writer = new PrintWriter(outputStream)) {
                    EODagGenerator.generateFromTemplate(writer, information, parameterList);
                }
            }
        } catch (ClassNotFoundException e) {
            throw new StacException(String.format("Unknown class %s", tiny.getClassOfContext()), e,
                                    StacFailureType.DOWNLOAD_UNKNOWN_CLASS_OF_TINYURL);
        }
    }

    private String getBaseUri(URI uri) {
        StringBuilder sb = new StringBuilder();
        if (uri.getScheme() != null) {
            sb.append(uri.getScheme());
            sb.append("://");
        }
        sb.append(uri.getHost());
        if (uri.getPort() != -1) {
            sb.append(':');
            sb.append(uri.getPort());
        }
        return sb.toString();
    }

    private void addSampleFilesToDescriptor(DownloadLinkCreator downloadLinkCreator, PrintWriter writer,
            Optional<String> collectionId, final String tinyurl) {

        if (collectionId.isPresent()) {
            LOGGER.info("Handling sample for tinyurl {} for collection {}", tinyurl, collectionId.get());
        } else {
            throw new StacException("Sample can only be downloaded for a specified collection", null,
                                    StacFailureType.DOWNLOAD_SAMPLE_ONLY_FOR_SINGLE_COLLECTION);
        }

        // Retrieve context from tinyurl
        TinyUrl tiny = getTinyUrl(tinyurl);

        // Load criteria
        ICriterion itemCriteria = tinyUrlService.loadContext(tiny, ICriterion.class);

        try {
            // Extract file from first matching item
            FacetPage<DataObjectFeature> facetPage = businessSearchService.search(itemCriteria, SearchType.DATAOBJECTS,
                                                                                  null, PageRequest.of(0, 1));
            // Extract file from it
            facetPage.forEach(feature -> printFeatureReferences(downloadLinkCreator, writer, feature));

        } catch (SearchException | OpenSearchUnknownParameter e) {
            throw new StacException(String.format("Cannot retrieve sample files to download for collection %s",
                                                  collectionId.orElse("unknown collection")), e,
                                    StacFailureType.DOWNLOAD_RETRIEVE_SAMPLE_FILES);
        }
    }

    private void addFilesToDescriptor(DownloadLinkCreator downloadLinkCreator, PrintWriter writer,
            Optional<String> collectionId, final String tinyurl) {

        if (collectionId.isPresent()) {
            LOGGER.info("Handling tinyurl {} for collection {}", tinyurl, collectionId.get());
        } else {
            LOGGER.info("Handling tinyurl {}", tinyurl);
        }

        // Retrieve context from tinyurl
        TinyUrl tiny = getTinyUrl(tinyurl);

        // Criterion or list of tiny url id
        try {
            if (ICriterion.class.isAssignableFrom(Class.forName(tiny.getClassOfContext()))) {
                ICriterion itemCriteria = tinyUrlService.loadContext(tiny, ICriterion.class);
                extractFilesFromPage(downloadLinkCreator, writer, PageRequest.of(0, MAX_PAGE_SIZE), itemCriteria);
            } else {
                // Retrieve list of tiny url id
                Type type = new TypeToken<Set<String>>() {

                }.getType();
                java.util.Set<String> tinyUrlUuids = tinyUrlService.loadContext(tiny, type);
                tinyUrlUuids.forEach(t -> addFilesToDescriptor(downloadLinkCreator, writer, Optional.empty(), t));
            }
        } catch (ClassNotFoundException e) {
            throw new StacException(String.format("Unknown class %s", tiny.getClassOfContext()), e,
                                    StacFailureType.DOWNLOAD_UNKNOWN_CLASS_OF_TINYURL);
        }
    }

    private TinyUrl getTinyUrl(final String tinyurl) {
        return tinyUrlService.get(tinyurl).orElseThrow(
                () -> new StacException(String.format("Tiny URL not found with this identifier : %s", tinyurl), null,
                                        StacFailureType.DOWNLOAD_UNKNOWN_TINYURL));
    }

    private void extractFilesFromPage(DownloadLinkCreator downloadLinkCreator, PrintWriter writer, Pageable pageable,
            ICriterion itemCriteria) {
        try {
            // Get a page of feature
            FacetPage<DataObjectFeature> facetPage = businessSearchService.search(itemCriteria, SearchType.DATAOBJECTS,
                                                                                  null, pageable);
            // Extract file from each one
            facetPage.forEach(feature -> printFeatureReferences(downloadLinkCreator, writer, feature));
            // Handle next page if necessary
            if (facetPage.hasNext()) {
                extractFilesFromPage(downloadLinkCreator, writer, facetPage.getPageable().next(), itemCriteria);
            }
        } catch (SearchException | OpenSearchUnknownParameter e) {
            throw new StacException(
                    String.format("Cannot retrieve files to download at page %d with size %d", pageable.getPageNumber(),
                                  pageable.getPageSize()), e, StacFailureType.DOWNLOAD_RETRIEVE_FILES);
        }
    }

    private void printFeatureReferences(DownloadLinkCreator downloadLinkCreator, PrintWriter writer,
            DataObjectFeature feature) {
        Collection<DataFile> dataFiles = feature.getFiles().get(DataType.RAWDATA);
        Path dir = getFeatureDirectory(feature);
        if (dataFiles != null) {
            dataFiles.forEach(
                    // Download
                    file -> printFileReference(writer, Optional.ofNullable(file.getCrc32()), file.getFilesize(),
                                               DownloadSource.STORAGE.equals(downloadSource) ?
                                                       getStorageLocation(file.getChecksum(), downloadLinkCreator) :
                                                       getCatalogLocation(file.getUri(), downloadLinkCreator),
                                               Optional.of(dir), file.getFilename()));
        }
    }

    private Path getFeatureDirectory(DataObjectFeature feature) {
        return Paths.get("/", feature.getProviderId().replaceAll("[\\W]", "_"));
    }

    /**
     * Add a line to mod_zip descriptor
     *
     * @param writer   output stream writer
     * @param crc32    optional CRC-32 checksum for current file allowing to support "Range" header.
     * @param size     file size in bytes
     * @param location URL-encoded location that corresponds to a location in nginx.conf
     * @param dir      optional path in zip to tidy up current file
     * @param filename file name only
     */
    private void printFileReference(PrintWriter writer, Optional<String> crc32, Long size, String location,
            Optional<Path> dir, String filename) {
        String targetFilename = dir.map(path -> path.resolve(filename).toString()).orElse(filename);
        String line = String.format(MOD_ZIP_LINE_FORMAT, crc32.orElse(UNKNOWN_CRC32), size, location,
                                    targetFilename);
        LOGGER.debug("Mod_zip line : {}", line);
        writer.println(line);
    }

    /**
     * Download from CATALOG transforming REGARDS location to NGINX one using configured NGINX prefixes
     */
    private String getCatalogLocation(String location, DownloadLinkCreator downloadLinkCreator) {
        // Use microservice name to split URI
        int index = location.indexOf(microserviceName);
        if (index > 0) {
            String part = location.substring(index + microserviceName.length());
            String uri = "/" + nginxPrefix + (part.startsWith("/") ? part : "/" + part);
            return downloadLinkCreator.appendAuthParamsForNginx(uri);
        }

        String message = String.format("Cannot map location to NGINX one : %s", location);
        LOGGER.error(message);
        throw new StacException(message, null, StacFailureType.DONWLOAD_BAD_FILE_LOCATION);
    }

    /**
     * Download directly from STORAGE bypassing catalog with access control previously done here
     *
     * @param checksum file checksum
     *                 <p>
     *                                                                                                                 TODO : check access rights are well respected while searching data before building mod_zip descriptor
     */
    private String getStorageLocation(String checksum, DownloadLinkCreator downloadLinkCreator) {
        return String.format(STORAGE_DOWNLOAD_FILE_PATH, nginxPrefix, checksum, downloadLinkCreator.getSystemToken());
    }

    enum DownloadSource {
        CATALOG, STORAGE
    }
}
