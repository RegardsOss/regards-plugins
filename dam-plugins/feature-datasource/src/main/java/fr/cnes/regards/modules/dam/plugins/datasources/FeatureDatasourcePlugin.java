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
package fr.cnes.regards.modules.dam.plugins.datasources;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.util.UriUtils;

import fr.cnes.regards.framework.feign.security.FeignSecurityManager;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDataSourcePlugin;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.feature.client.IFeatureEntityClient;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.feature.dto.FeatureEntityDto;
import fr.cnes.regards.modules.feature.dto.FeatureFile;
import fr.cnes.regards.modules.feature.dto.FeatureFileAttributes;
import fr.cnes.regards.modules.feature.dto.FeatureFileLocation;
import fr.cnes.regards.modules.feature.dto.urn.FeatureUniformResourceName;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import fr.cnes.regards.modules.project.client.rest.IProjectsClient;
import fr.cnes.regards.modules.project.domain.Project;
import fr.cnes.regards.modules.storage.client.IStorageRestClient;
import fr.cnes.regards.modules.storage.domain.dto.StorageLocationDTO;
import fr.cnes.regards.modules.storage.domain.plugin.StorageType;

/**
 * Plugin to get data from feature manager
 *
 * @author Kevin Marchois
 * @author Marc SORDI
 */
@Plugin(id = "feature-datasource", version = "1.0-SNAPSHOT", description = "Plugin to get data from feature manager",
        author = "REGARDS Team", contact = "regards@c-s.fr", license = "GPLv3", owner = "CSSI",
        url = "https://github.com/RegardsOss")
public class FeatureDatasourcePlugin implements IDataSourcePlugin {

    private static final String URN_PLACEHOLDER = "{urn}";

    private static final String CHECKSUM_PLACEHOLDER = "{urn}";

    private static final String CATALOG_DOWNLOAD_PATH = "/downloads/" + URN_PLACEHOLDER + "/files/"
            + CHECKSUM_PLACEHOLDER;

    @Value("${geode.plugin.refreshRate:1000}")
    private int refreshRate;

    @PluginParameter(name = DataSourcePluginConstants.MODEL_NAME_PARAM, label = "Model name",
            description = "Associated data source model name")
    protected String modelName;

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

    @Value("${zuul.prefix}")
    private String urlPrefix;

    /**
     * Map of {@link Project}s by tenant
     */
    private final Map<String, Project> projects = new ConcurrentHashMap<>();

    /**
     * Storage cache for online and offline storages. Only used if at least one feature has files.
     */
    private Storages storages;

    @Override
    public int getRefreshRate() {
        return refreshRate;
    }

    @Override
    public Page<DataObjectFeature> findAll(String tenant, Pageable pageable, OffsetDateTime date)
            throws DataSourceException {

        ResponseEntity<PagedModel<EntityModel<FeatureEntityDto>>> response;
        try {
            FeignSecurityManager.asSystem();
            // Do remote request to FEATURE MANAGER
            response = featureClient.findAll(modelName, date, pageable.getPageNumber(), pageable.getPageSize());
        } finally {
            FeignSecurityManager.reset();
        }

        // Manage request error
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new DataSourceException(
                    "Error while calling FEATURE MANAGER client (HTTP STATUS : " + response.getStatusCode());
        }

        // Process current page
        PagedModel<EntityModel<FeatureEntityDto>> page = response.getBody();
        Collection<EntityModel<FeatureEntityDto>> dtos = page.getContent();

        List<DataObjectFeature> result = new ArrayList<>();
        for (EntityModel<FeatureEntityDto> em : dtos) {
            result.add(initDataObjectFeature(tenant, em));
        }
        return new PageImpl<DataObjectFeature>(result,
                PageRequest.of(Long.valueOf(page.getMetadata().getNumber()).intValue(),
                               Long.valueOf(page.getMetadata().getSize()).intValue()),
                page.getMetadata().getTotalElements());
    }

    private DataObjectFeature initDataObjectFeature(String tenant, EntityModel<FeatureEntityDto> entity)
            throws DataSourceException {

        FeatureEntityDto featureDto = entity.getContent();
        Feature feature = featureDto.getFeature();

        // Initialize catalog feature
        DataObjectFeature dataObject = new DataObjectFeature(feature.getUrn(), feature.getId(), feature.getId(),
                featureDto.getSessionOwner(), featureDto.getSession(), feature.getModel());
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

                Boolean online = checkOnline(locations, getStorages());
                Optional<String> referenceUrl = Optional.empty();
                if (!online) {
                    referenceUrl = checkReference(locations, getStorages());
                }
                String downloadUrl = referenceUrl.orElse(getDownloadUrl(feature.getUrn(), atts.getChecksum(), tenant));
                DataFile dataFile = DataFile.build(atts.getDataType(), atts.getFilename(), downloadUrl,
                                                   atts.getMimeType(), online, referenceUrl.isPresent());
                dataFile.setFilesize(atts.getFilesize());
                dataFile.setDigestAlgorithm(atts.getAlgorithm());
                dataFile.setChecksum(atts.getChecksum());
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
            Boolean isOffline = storages.getOfflines().contains(location.getStorage())
                    || !storages.getAll().contains(location.getStorage());
            if (isOffline && location.getUrl().startsWith("http")) {
                referenceUrl = Optional.of(location.getUrl());
                break;
            }
        }
        return referenceUrl;
    }

    private Boolean checkOnline(Set<FeatureFileLocation> locations, Storages storages) {
        Boolean isOnline = false;
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
                    throw new DataSourceException(
                            "Error while calling STORAGE client (HTTP STATUS : " + response.getStatusCode());
                }

                List<StorageLocationDTO> storageLocationDTOList = response.getBody().stream().map(n -> n.getContent())
                        .collect(Collectors.toList());
                storages = Storages.build(storageLocationDTOList);
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                throw new DataSourceException("Cannot fetch storage locations list because: " + e.getMessage(), e);
            } finally {
                FeignSecurityManager.reset();
            }
        }
        return storages;
    }

    private static class Storages {

        private List<String> all;

        private List<String> onlines;

        private List<String> offlines;

        public static Storages build(List<StorageLocationDTO> storageLocationDTOList) {
            Storages storages = new Storages();
            storages.all = storageLocationDTOList.stream().map(n -> n.getName()).collect(Collectors.toList());
            storages.onlines = storageLocationDTOList.stream()
                    .filter(s -> (s.getConfiguration() != null)
                            && (s.getConfiguration().getStorageType() == StorageType.ONLINE))
                    .map(n -> n.getName()).collect(Collectors.toList());
            storages.offlines = storageLocationDTOList.stream()
                    .filter(s -> (s.getConfiguration() == null)
                            || (s.getConfiguration().getStorageType() == StorageType.OFFLINE))
                    .map(n -> n.getName()).collect(Collectors.toList());
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
                    throw new DataSourceException(
                            "Error while calling PROJECT client (HTTP STATUS : " + response.getStatusCode());
                }

                project = response.getBody().getContent();
                projects.put(tenant, project);
            } finally {
                FeignSecurityManager.reset();
            }
        }
        return project.getHost() + urlPrefix + "/" + encode4Uri("rs-catalog") + CATALOG_DOWNLOAD_PATH
                .replace(URN_PLACEHOLDER, urn.toString()).replace(CHECKSUM_PLACEHOLDER, checksum);
    }

    private static String encode4Uri(String str) {
        return new String(UriUtils.encode(str, Charset.defaultCharset().name()).getBytes(), StandardCharsets.US_ASCII);
    }

}
