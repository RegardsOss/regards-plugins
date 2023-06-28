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
package fr.cnes.regards.modules.storage.s3.common;

import com.google.common.collect.Maps;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.s3.S3StorageConfiguration;
import fr.cnes.regards.framework.s3.client.S3HighLevelReactiveClient;
import fr.cnes.regards.framework.s3.domain.*;
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.modules.storage.domain.database.FileReference;
import fr.cnes.regards.modules.storage.domain.database.request.FileCacheRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileStorageRequest;
import fr.cnes.regards.modules.storage.domain.plugin.*;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author Thibaud Michaudel
 **/
@Component
public abstract class AbstractS3Storage implements IStorageLocation {

    public static final String S3_SERVER_ENDPOINT_PARAM_NAME = "S3_Server_Endpoint";

    public static final String S3_SERVER_REGION_PARAM_NAME = "S3_Server_Region";

    public static final String S3_SERVER_KEY_PARAM_NAME = "S3_Server_Key";

    public static final String S3_SERVER_SECRET_PARAM_NAME = "S3_Server_Secret";

    public static final String S3_SERVER_BUCKET_PARAM_NAME = "S3_Server_Bucket";

    public static final String S3_SERVER_ROOT_PATH_PARAM_NAME = "Root_Path";

    public static final String UPLOAD_WITH_MULTIPART_THRESHOLD_IN_MB_PARAM_NAME = "Upload_With_Multipart_Threshold_In_Mb";

    public static final String MULTIPART_PARALLEL_PARAM_NAME = "Upload_With_Multipart_Parallel_Part_Number";

    /**
     * Plugin parameter name of the can delete attribute
     */
    public static final String S3_ALLOW_DELETION = "S3_Allow_Deletion";

    private static final Logger LOGGER = getLogger(AbstractS3Storage.class);

    @PluginParameter(name = S3_SERVER_ENDPOINT_PARAM_NAME,
                     description = "Endpoint of the S3 server (format: http://{ip or server name}:{port})",
                     label = "S3 server endpoint")
    protected String endpoint;

    @PluginParameter(name = S3_SERVER_REGION_PARAM_NAME,
                     description = "Region of the S3 server",
                     label = "S3 server region")
    protected String region;

    @PluginParameter(name = S3_SERVER_KEY_PARAM_NAME, description = "Key of the S3 server", label = "S3 server key")
    protected String key;

    @PluginParameter(name = S3_SERVER_SECRET_PARAM_NAME,
                     description = "Secret of the S3 server",
                     label = "S3 server secret",
                     sensitive = true)
    protected String secret;

    @PluginParameter(name = S3_SERVER_BUCKET_PARAM_NAME,
                     description = "Bucket of the S3 server",
                     label = "S3 server bucket")
    protected String bucket;

    /**
     * Parameter used for URL validation. Only URL starting with {endpoint}/{bucket}/{root_path} is valid.
     * As a file can be accessible at the root of the bucket, this parameter is optional.
     */
    @PluginParameter(name = S3_SERVER_ROOT_PATH_PARAM_NAME,
                     description = "Root path of this storage in the S3 server",
                     label = "Storage root path",
                     optional = true)
    private String rawRootPath;

    protected String rootPath;

    @PluginParameter(name = UPLOAD_WITH_MULTIPART_THRESHOLD_IN_MB_PARAM_NAME,
                     description = "Number of Mb for a file size over which multipart upload is used",
                     label = "Multipart threshold in Mb",
                     defaultValue = "5")
    protected int multipartThresholdMb;

    @PluginParameter(name = MULTIPART_PARALLEL_PARAM_NAME,
                     description = "Number of parallel parts to upload",
                     label = "Number of parallel parts during multipart upload",
                     defaultValue = "5")
    protected int nbParallelPartsUpload;

    @PluginParameter(name = S3_ALLOW_DELETION,
                     label = "Enable effective deletion of files",
                     description = "If deletion is allowed, files are physically deleted else files are only removed from references",
                     defaultValue = "false")
    protected Boolean allowPhysicalDeletion;

    /**
     * Cache for the client S3
     * (Do not use this field, use the createS3Client)
     */
    private S3HighLevelReactiveClient clientCache;

    /**
     * Configuration of S3 server
     */
    public StorageConfig storageConfiguration;

    @Autowired
    protected IRuntimeTenantResolver runtimeTenantResolver;

    /**
     * Settings for the configuration of available S3 server
     */
    @Autowired
    protected S3StorageConfiguration s3StorageSettings;

    /**
     * Initialize the storage configuration of S3 server
     */
    @PluginInit
    public void init() {
        if (rawRootPath == null) {
            rootPath = "";
        } else {
            rootPath = rawRootPath;
            if (rootPath.startsWith("/")) {
                rootPath = rootPath.substring(1);
            }
            if (rootPath.endsWith("/")) {
                rootPath = rootPath.substring(0, rootPath.length() - 1);
            }
        }
        storageConfiguration = StorageConfig.builder(endpoint, region, key, secret)
                                            .bucket(bucket)
                                            .rootPath(rootPath)
                                            .build();
    }

    /**
     * Create the client S3 in order to communicate with S3 server
     *
     * @return the client S3
     */
    protected S3HighLevelReactiveClient createS3Client() {
        if (clientCache == null) {
            Scheduler scheduler = Schedulers.newParallel("s3-reactive-client", 10);
            int maxBytesPerPart = multipartThresholdMb * 1024 * 1024;
            clientCache = new S3HighLevelReactiveClient(scheduler, maxBytesPerPart, nbParallelPartsUpload);
        }
        return clientCache;
    }

    private Mono<InputStream> toInputStream(StorageCommandResult.ReadingPipe pipe) {
        DataBufferFactory dbf = new DefaultDataBufferFactory();
        return pipe.getEntry()
                   .flatMap(entry -> DataBufferUtils.join(entry.getData().map(dbf::wrap)))
                   .map(DataBuffer::asInputStream);
    }

    protected void handleDeleteRequest(FileDeletionRequest request, IDeletionProgressManager progressManager) {
        String tenant = runtimeTenantResolver.getTenant();
        LOGGER.info("Start deleting {} with location {}",
                    request.getFileReference().getMetaInfo().getFileName(),
                    request.getFileReference().getLocation().getUrl());
        StorageCommandID cmdId = new StorageCommandID(request.getJobId(), UUID.randomUUID());

        StorageCommand.Delete deleteCmd = new StorageCommand.Delete.Impl(storageConfiguration,
                                                                         cmdId,
                                                                         getEntryKey(request.getFileReference()));
        createS3Client().delete(deleteCmd)
                        .flatMap(deleteResult -> deleteResult.matchDeleteResult(Mono::just,
                                                                                unreachable -> Mono.error(new RuntimeException(
                                                                                    "Unreachable endpoint")),
                                                                                failure -> Mono.error(new RuntimeException(
                                                                                    "Delete failure in S3 storage"))))
                        .doOnError(t -> {
                            try {
                                runtimeTenantResolver.forceTenant(tenant);
                                LOGGER.error("End deleting {} with location {}",
                                             request.getFileReference().getMetaInfo().getFileName(),
                                             request.getFileReference().getLocation().getUrl(),
                                             t);
                                progressManager.deletionFailed(request, "Delete failure in S3 storage");
                            } finally {
                                runtimeTenantResolver.clearTenant();
                            }
                        })
                        .doOnSuccess(success -> {
                            try {
                                runtimeTenantResolver.forceTenant(tenant);
                                LOGGER.info("End deleting {} with location {}",
                                            request.getFileReference().getMetaInfo().getFileName(),
                                            request.getFileReference().getLocation().getUrl());
                                progressManager.deletionSucceed(request);
                            } finally {
                                runtimeTenantResolver.clearTenant();
                            }
                        })
                        .block();
    }

    protected void handleStoreRequest(FileStorageRequest request, IStorageProgressManager progressManager) {
        try {
            URL sourceUrl = new URL(request.getOriginUrl());
            // Download the file from url (File system, S3 server)
            Flux<ByteBuffer> buffers = DataBufferUtils.readInputStream(() -> DownloadUtils.getInputStream(sourceUrl,
                                                                                                          s3StorageSettings.getStorages()),
                                                                       new DefaultDataBufferFactory(),
                                                                       multipartThresholdMb * 1024 * 1024)
                                                      .map(DataBuffer::asByteBuffer);

            request.getMetaInfo().setFileSize(getFileSize(sourceUrl));

            String entryKey = storageConfiguration.entryKey(getEntryKey(request));

            StorageEntry storageEntry = buildStorageEntry(request, entryKey, buffers);

            StorageCommand.Write writeCmd = new StorageCommand.Write.Impl(storageConfiguration,
                                                                          createStorageCommandID(request.getJobId()),
                                                                          entryKey,
                                                                          storageEntry);

            StorageCommandResult.WriteResult result = createS3Client().write(writeCmd)
                                                                      .flatMap(writeResult -> writeResult.matchWriteResult(
                                                                          Mono::just,
                                                                          unreachable -> Mono.error(new RuntimeException(
                                                                              "Unreachable endpoint")),
                                                                          failure -> Mono.error(new RuntimeException(
                                                                              "Write failure in S3 storage"))))
                                                                      .doOnError(t -> {
                                                                          LOGGER.error("[{}] End storing {}",
                                                                                       request.getJobId(),
                                                                                       request.getOriginUrl(),
                                                                                       t);
                                                                          progressManager.storageFailed(request,
                                                                                                        "Write failure in S3 storage");

                                                                      })
                                                                      .doOnSuccess(success -> {
                                                                          LOGGER.info("[{}] End storing {}",
                                                                                      request.getJobId(),
                                                                                      request.getOriginUrl());
                                                                      })
                                                                      .block();
            long storedFileSize = 0l;
            if (result instanceof StorageCommandResult.WriteSuccess resultSuccess) {
                storedFileSize = resultSuccess.getSize();
            }
            progressManager.storageSucceed(request,
                                           storageConfiguration.entryKeyUrl(entryKey.replaceFirst("^/*", "")),
                                           storedFileSize);
        } catch (MalformedURLException e) {
            LOGGER.error(e.getMessage(), e);
            progressManager.storageFailed(request, String.format("Invalid source url %s", request.getOriginUrl()));
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            progressManager.storageFailed(request, String.format("Store failed cause : %s", e.getMessage()));
        }
    }

    private StorageEntry buildStorageEntry(FileStorageRequest request, String entryKey, Flux<ByteBuffer> buffers) {
        return StorageEntry.builder()
                           .config(storageConfiguration)
                           .fullPath(entryKey)
                           .checksum(entryChecksum(request))
                           .size(entrySize(request))
                           .data(buffers)
                           .build();
    }

    private StorageCommandID createStorageCommandID(String taskId) {
        return new StorageCommandID(taskId, UUID.randomUUID());
    }

    protected String getEntryKey(FileReference fileReference) {
        return fileReference.getLocation()
                            .getUrl()
                            .replaceFirst(Pattern.quote(endpoint) + "(:[0-9]*)?/*", "")
                            .replaceFirst(Pattern.quote(bucket), "")
                            .substring(1);
    }

    private String getEntryKey(FileStorageRequest request) {
        String entryKey = request.getMetaInfo().getChecksum();
        if (request.getStorageSubDirectory() != null && !request.getStorageSubDirectory().isEmpty()) {
            entryKey = Paths.get(request.getStorageSubDirectory(), request.getMetaInfo().getChecksum()).toString();
            if (entryKey.charAt(0) == '/') {
                entryKey = entryKey.substring(1);
            }
        }
        return entryKey;
    }

    private Option<Long> entrySize(FileStorageRequest request) {
        return Option.some(request.getMetaInfo().getFileSize());
    }

    private Option<Tuple2<String, String>> entryChecksum(FileStorageRequest request) {
        return Option.some(Tuple.of(request.getMetaInfo().getAlgorithm(), request.getMetaInfo().getChecksum()));
    }

    @Override
    public boolean isValidUrl(String urlToValidate, Set<String> errors) {
        // Check endPoint in url
        if (!urlToValidate.startsWith(endpoint)) {
            errors.add("Url does not start with storage endpoint: " + endpoint);
            return false;
        }
        // Check bucket in url
        String prefixBucket = endpoint + File.separator + bucket;
        if (!urlToValidate.startsWith(prefixBucket)) {
            errors.add("Url does not correspond to storage bucket: " + bucket);
            return false;
        }
        // Check rootPath
        if (StringUtils.isNotBlank(rootPath)) {
            String prefixRootPath = prefixBucket + File.separator + rootPath.replaceFirst("^/*", "");
            if (!urlToValidate.startsWith(prefixRootPath)) {
                errors.add("Url does correspond to storage root path: " + rootPath);
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean allowPhysicalDeletion() {
        return allowPhysicalDeletion;
    }

    @Override
    public PreparationResponse<FileStorageWorkingSubset, FileStorageRequest> prepareForStorage(Collection<FileStorageRequest> fileReferenceRequests) {
        List<FileStorageWorkingSubset> workingSubsets = new ArrayList<>();
        workingSubsets.add(new FileStorageWorkingSubset(fileReferenceRequests));
        return PreparationResponse.build(workingSubsets, Maps.newHashMap());
    }

    @Override
    public PreparationResponse<FileDeletionWorkingSubset, FileDeletionRequest> prepareForDeletion(Collection<FileDeletionRequest> fileDeletionRequests) {
        List<FileDeletionWorkingSubset> workingSubsets = new ArrayList<>();
        workingSubsets.add(new FileDeletionWorkingSubset(fileDeletionRequests));
        return PreparationResponse.build(workingSubsets, Maps.newHashMap());
    }

    @Override
    public PreparationResponse<FileRestorationWorkingSubset, FileCacheRequest> prepareForRestoration(Collection<FileCacheRequest> requests) {
        List<FileRestorationWorkingSubset> workingSubsets = new ArrayList<>();
        workingSubsets.add(new FileRestorationWorkingSubset(requests));
        return PreparationResponse.build(workingSubsets, Maps.newHashMap());
    }

    /**
     * Get the file content length
     *
     * @param sourceUrl the url of file
     * @return the size of file, 0 if the file does not exist
     */
    protected long getFileSize(URL sourceUrl) {
        long fileSize = 0L;
        try {
            fileSize = DownloadUtils.getContentLength(sourceUrl, 0, s3StorageSettings.getStorages());
        } catch (IOException e) {
            LOGGER.error("Failure in the getting of file size : {}", sourceUrl, e);
        }
        return fileSize;
    }
}
