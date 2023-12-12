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
package fr.cnes.regards.modules.dam.plugins.datasources;

import fr.cnes.regards.framework.feign.security.FeignSecurityManager;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.dam.domain.datasources.CrawlingCursor;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IInternalDataSourcePlugin;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.feature.client.IFeatureEntityClient;
import fr.cnes.regards.modules.feature.dto.*;
import fr.cnes.regards.modules.feature.dto.urn.FeatureUniformResourceName;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import fr.cnes.regards.modules.project.client.rest.IProjectsClient;
import fr.cnes.regards.modules.project.domain.Project;
import fr.cnes.regards.modules.storage.client.IStorageRestClient;
import fr.cnes.regards.modules.storage.domain.dto.StorageLocationDTO;
import fr.cnes.regards.modules.storage.domain.plugin.StorageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.util.UriUtils;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin to get data from feature manager
 *
 * @author Kevin Marchois
 * @author Marc SORDI
 */
@Plugin(id = "feature-datasource",
        version = "1.0-SNAPSHOT",
        description = "Plugin to get data from feature manager",
        author = "REGARDS Team",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CSSI",
        url = "https://github.com/RegardsOss")
public class FeatureDatasourcePlugin implements IInternalDataSourcePlugin {

    private static final String URN_PLACEHOLDER = "{urn}";

    private static final String CHECKSUM_PLACEHOLDER = "{checksum}";

    private static final String CATALOG_DOWNLOAD_PATH = "/downloads/"
                                                        + URN_PLACEHOLDER
                                                        + "/files/"
                                                        + CHECKSUM_PLACEHOLDER;

    /**
     * Map of {@link Project}s by tenant
     */
    private final Map<String, Project> projects = new ConcurrentHashMap<>();

    /**
     * Storage cache for online and offline storages. Only used if at least one feature has files.
     */
    private Storages storages;

    // -------------------------
    // --- PLUGIN PARAMETERS ---
    // -------------------------

    @PluginParameter(name = DataSourcePluginConstants.MODEL_NAME_PARAM,
                     label = "Model name",
                     description = "Associated data source model name")
    protected String modelName;

    @Value("${prefix.path}")
    private String urlPrefix;

    /**
     * Refresh rate in SECOND
     */
    @PluginParameter(name = DataSourcePluginConstants.REFRESH_RATE,
                     label = "Refresh rate",
                     description = "Harvesting refresh rate in second (minimum delay between two consecutive harvesting)",
                     defaultValue = "1000")
    private int refreshRate;

    /**
     * Overlap in SECOND
     *
     * <p>
     * Explanations: in Feature Manager, paging can cause data loss when it processes the data being acquired. So,
     * to prevent this, data are harvested since date minus an overlap.
     * So data may be harvested twice!
     */
    @PluginParameter(name = "overlap",
                     label = "Overlap",
                     description = "For active datasource, harvest data since latest harvesting date minus this overlap to prevent data loss",
                     defaultValue = "30")
    private long overlap;

    /**
     * Return duration in seconds to retrieve entities by maximum date limit to avoid retrieving entities to close with
     * the current aspiration date.
     */
    @PluginParameter(name = "searchLimitFromNowInSeconds",
                     label = "Search date limit in seconds",
                     description = "Duration in seconds to retrieve entities by maximum date limit to avoid "
                                   + "retrieving entities to close with the current aspiration date",
                     defaultValue = "600")
    private long searchLimitFromNowInSeconds;

    // -------------------------
    // ------- SERVICES --------
    // -------------------------

    @Autowired
    private IFeatureEntityClient featureClient;

    /**
     * May not be useful if no file is managed!
     */
    @Autowired(required = false)
    private IStorageRestClient storageRestClient;

    /**
     * May not be useful if no file is managed!
     */
    @Autowired(required = false)
    private IProjectsClient projectClient;

    @Override
    public int getRefreshRate() {
        return refreshRate;
    }

    @Override
    public List<DataObjectFeature> findAll(String tenant,
                                           CrawlingCursor cursor,
                                           @Nullable OffsetDateTime lastIngestDate,
                                           OffsetDateTime currentIngestionStartDate) throws DataSourceException {
        // 1) Get page of feature entities to process
        PagedModel<EntityModel<FeatureEntityDto>> pageFeatureEntities;

        // To avoid missing updated or new storages, reset cache of storages
        resetStorages();

        try {
            FeignSecurityManager.asSystem();
            pageFeatureEntities = getFeatureEntities(cursor, currentIngestionStartDate);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new DataSourceException(String.format(
                "An error occurred during the searching of feature entities. Cause %s: ",
                e.getMessage()), e);
        } finally {
            FeignSecurityManager.reset();
        }

        // 2) Build dataObjectFeatures from featureEntities
        Collection<EntityModel<FeatureEntityDto>> dtos = pageFeatureEntities.getContent();
        List<DataObjectFeature> dataObjects = new ArrayList<>();
        for (EntityModel<FeatureEntityDto> em : dtos) {
            dataObjects.add(initDataObjectFeature(tenant, em));
        }

        // 3) Update cursor for next iteration
        // determine if there is a next page to search after this one
        cursor.setHasNext(pageFeatureEntities.getNextLink().isPresent());

        // set current last update date with the most recent feature entity update.

        cursor.setCurrentLastEntityDate(dtos.stream()
                                            .map(entityModel -> Objects.requireNonNull(entityModel.getContent())
                                                                       .getLastUpdate())
                                            .max(Comparator.comparing(lastUpdate -> lastUpdate))
                                            .orElse(null));

        return dataObjects;
    }

    /**
     * Search a page of {@link FeatureEntityDto}s by several criteria
     *
     * @param cursor featureEntities should be retrieved from the {@link CrawlingCursor#getLastEntityDate()}
     * @throws DataSourceException in case featureEntities could not be retrieved
     */
    private PagedModel<EntityModel<FeatureEntityDto>> getFeatureEntities(CrawlingCursor cursor,
                                                                         OffsetDateTime currentIngestionStartDate)
        throws DataSourceException {

        // /!\ In order to avoid some missing features from fem service, search for entities
        OffsetDateTime searchMinDate = cursor.getLastEntityDate();
        OffsetDateTime searchMaxDate = currentIngestionStartDate.minusSeconds(searchLimitFromNowInSeconds);
        if (searchMinDate != null && searchMaxDate.isBefore(searchMinDate)) {
            // Ensure range is valid
            searchMaxDate = searchMinDate;
        }

        // /!\ this sorting is very important as it allows the db to retrieve features always in the same order, to avoid any feature to be skipped.
        // Features must always be sorted by lastUpdate and id ASC. Handles nulls first, otherwise, these entities will never be processed.
        Sort sorting = Sort.by(new Sort.Order(Sort.Direction.ASC, "lastUpdate", Sort.NullHandling.NULLS_FIRST),
                               new Sort.Order(Sort.Direction.ASC, "id"));
        ResponseEntity<PagedModel<EntityModel<FeatureEntityDto>>> response = featureClient.findAll(modelName,
                                                                                                   searchMinDate,
                                                                                                   searchMaxDate,
                                                                                                   cursor.getPosition(),
                                                                                                   cursor.getSize(),
                                                                                                   sorting);
        // Manage request error
        if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
            throw new DataSourceException(String.format(
                "Error while calling FEATURE MANAGER client, HTTP STATUS : %s, BODY: %s",
                response.getStatusCode(),
                response.getBody()));
        }
        return Objects.requireNonNull(response.getBody());
    }

    private DataObjectFeature initDataObjectFeature(String tenant, EntityModel<FeatureEntityDto> entity)
        throws DataSourceException {

        FeatureEntityDto featureDto = entity.getContent();
        Feature feature = Objects.requireNonNull(featureDto).getFeature();

        // Initialize catalog feature
        DataObjectFeature dataObject = new DataObjectFeature(feature.getUrn(),
                                                             feature.getId(),
                                                             feature.getId(),
                                                             featureDto.getSource(),
                                                             featureDto.getSession(),
                                                             feature.getModel());
        dataObject.setLast(feature.isLast());
        // Propagate geometry
        dataObject.setGeometry(feature.getGeometry());
        // Propagate properties
        dataObject.setProperties(feature.getProperties());
        // Propagate files if any
        if (feature.hasFiles()) {
            for (FeatureFile file : feature.getFiles()) {
                FeatureFileAttributes atts = file.getAttributes();
                Set<FeatureFileLocation> locations = file.getLocations();

                boolean online = checkOnline(locations, getStorages());
                Optional<String> referenceUrl = Optional.empty();
                if (!online) {
                    referenceUrl = checkReference(locations, getStorages());
                }
                String downloadUrl = referenceUrl.orElse(getDownloadUrl(feature.getUrn(), atts.getChecksum(), tenant));
                DataFile dataFile = DataFile.build(atts.getDataType(),
                                                   atts.getFilename(),
                                                   downloadUrl,
                                                   atts.getMimeType(),
                                                   online,
                                                   referenceUrl.isPresent());
                dataFile.setFilesize(atts.getFilesize());
                dataFile.setDigestAlgorithm(atts.getAlgorithm());
                dataFile.setChecksum(atts.getChecksum());
                dataFile.setCrc32(atts.getCrc32());
                dataObject.getFiles().put(dataFile.getDataType(), dataFile);
            }
        }
        return dataObject;
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    private Optional<String> checkReference(Set<FeatureFileLocation> locations, Storages storages) {
        Optional<String> referenceUrl = Optional.empty();
        for (FeatureFileLocation location : locations) {
            boolean isOffline = storages.getOfflines().contains(location.getStorage()) || !storages.getAll()
                                                                                                   .contains(location.getStorage());
            if (isOffline && location.getUrl().startsWith("http")) {
                referenceUrl = Optional.of(location.getUrl());
                break;
            }
        }
        return referenceUrl;
    }

    private boolean checkOnline(Set<FeatureFileLocation> locations, Storages storages) {
        boolean isOnline = false;
        for (FeatureFileLocation location : locations) {
            isOnline = storages.getOnlines().contains(location.getStorage());
            if (isOnline) {
                break;
            }
        }
        return isOnline;
    }

    private Storages getStorages() throws DataSourceException {
        if (storages == null) {
            // Remote request to STORAGE
            try {
                FeignSecurityManager.asSystem();
                ResponseEntity<List<EntityModel<StorageLocationDTO>>> response = storageRestClient.retrieve();

                // Manage request error
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new DataSourceException("Error while calling STORAGE client, HTTP STATUS : "
                                                  + response.getStatusCode());
                }

                List<StorageLocationDTO> storageLocationDTOList = Objects.requireNonNull(response.getBody())
                                                                         .stream()
                                                                         .map(EntityModel::getContent)
                                                                         .toList();
                storages = Storages.build(storageLocationDTOList);
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                throw new DataSourceException("Cannot fetch storage locations list because: " + e.getMessage(), e);
            } finally {
                FeignSecurityManager.reset();
            }
        }
        return storages;
    }

    private void resetStorages() {
        storages = null;
    }

    private static class Storages {

        private List<String> all;

        private List<String> onlines;

        private List<String> offlines;

        public static Storages build(List<StorageLocationDTO> storageLocationDTOList) {
            Storages storages = new Storages();
            storages.all = storageLocationDTOList.stream().map(StorageLocationDTO::getName).toList();
            storages.onlines = storageLocationDTOList.stream()
                                                     .filter(s -> (s.getConfiguration() != null)
                                                                  && (s.getConfiguration().getStorageType()
                                                                      == StorageType.ONLINE))
                                                     .map(StorageLocationDTO::getName)
                                                     .toList();
            storages.offlines = storageLocationDTOList.stream()
                                                      .filter(s -> (s.getConfiguration() == null)
                                                                   || (s.getConfiguration().getStorageType()
                                                                       == StorageType.OFFLINE))
                                                      .map(StorageLocationDTO::getName)
                                                      .toList();
            return storages;
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

    private String getDownloadUrl(FeatureUniformResourceName urn, String checksum, String tenant)
        throws DataSourceException {
        Project project = projects.get(tenant);
        if (project == null) {
            try {
                FeignSecurityManager.asSystem();
                ResponseEntity<EntityModel<Project>> response = projectClient.retrieveProject(tenant);

                // Manage request error
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new DataSourceException("Error while calling PROJECT client, HTTP STATUS : "
                                                  + response.getStatusCode());
                }

                project = Objects.requireNonNull(response.getBody()).getContent();
                projects.put(tenant, project);
            } finally {
                FeignSecurityManager.reset();
            }
        }
        return Objects.requireNonNull(project).getHost()
               + urlPrefix
               + "/"
               + encode4Uri("rs-catalog")
               + CATALOG_DOWNLOAD_PATH.replace(URN_PLACEHOLDER, urn.toString()).replace(CHECKSUM_PLACEHOLDER, checksum);
    }

    private static String encode4Uri(String uriToEncode) {
        return new String(UriUtils.encode(uriToEncode, Charset.defaultCharset().name()).getBytes(),
                          StandardCharsets.US_ASCII);
    }

    @Override
    public long getOverlap() {
        return overlap;
    }
}
