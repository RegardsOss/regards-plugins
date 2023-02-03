package fr.cnes.regards.modules.storage.plugin.s3;

import com.google.common.collect.Maps;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
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
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.utils.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Main class of plugin of storage(online type) in S3 server
 */
@Plugin(author = "REGARDS Team", description = "Plugin handling the storage on S3", id = "S3", version = "1.0",
    contact = "regards@c-s.fr", license = "GPLv3", owner = "CNES", markdown = "S3StoragePlugin.md",
    url = "https://regardsoss.github.io/")
public class S3OnlineStorage implements IOnlineStorageLocation {

    public static final String S3_SERVER_ENDPOINT_PARAM_NAME = "S3_Server_Endpoint";

    public static final String S3_SERVER_REGION_PARAM_NAME = "S3_Server_Region";

    public static final String S3_SERVER_KEY_PARAM_NAME = "S3_Server_Key";

    public static final String S3_SERVER_SECRET_PARAM_NAME = "S3_Server_Secret";

    public static final String S3_SERVER_BUCKET_PARAM_NAME = "S3_Server_Bucket";

    public static final String S3_SERVER_ROOT_PATH_PARAM_NAME = "Root_Path";

    public static final String UPLOAD_WITH_MULTIPART_THRESHOLD_IN_MB_PARAM_NAME = "Upload_With_Multipart_Threshold_In_Mb";

    public static final String MULTIPART_PARALLEL_PARAM_NAME = "Upload_With_Multipart_Threshold_In_Mb";

    /**
     * Plugin parameter name of the can delete attribute
     */
    public static final String S3_ALLOW_DELETION = "S3_Allow_Deletion";

    private static final Logger LOGGER = getLogger(S3OnlineStorage.class);

    @PluginParameter(name = S3_SERVER_ENDPOINT_PARAM_NAME,
        description = "Endpoint of the S3 server (format: http://{ip or server name}:{port})",
        label = "S3 server endpoint")
    private String endpoint;

    @PluginParameter(name = S3_SERVER_REGION_PARAM_NAME, description = "Region of the S3 server",
        label = "S3 server region")
    private String region;

    @PluginParameter(name = S3_SERVER_KEY_PARAM_NAME, description = "Key of the S3 server", label = "S3 server key")
    private String key;

    @PluginParameter(name = S3_SERVER_SECRET_PARAM_NAME, description = "Secret of the S3 server",
        label = "S3 server secret", sensitive = true)
    private String secret;

    @PluginParameter(name = S3_SERVER_BUCKET_PARAM_NAME, description = "Bucket of the S3 server",
        label = "S3 server bucket")
    private String bucket;

    /**
     * Parameter used for URL validation. Only URL starting with {endpoint}/{bucket}/{root_path} is valid.
     * As a file can be accessible at the root of the bucket, this parameter is optional.
     */
    @PluginParameter(name = S3_SERVER_ROOT_PATH_PARAM_NAME, description = "Root path of this storage in the S3 server",
        label = "Storage root path", optional = true)
    private String rootPath;

    @PluginParameter(name = UPLOAD_WITH_MULTIPART_THRESHOLD_IN_MB_PARAM_NAME,
        description = "Number of Mb for a file size over which multipart upload is used",
        label = "Multipart threshold in Mb", defaultValue = "5")
    private int multipartThresholdMb;

    @PluginParameter(name = MULTIPART_PARALLEL_PARAM_NAME, description = "Number of parallel parts to upload",
        label = "Number of parallel parts during multipart upload", defaultValue = "5")
    private int nbParallelPartsUpload;

    @PluginParameter(name = S3_ALLOW_DELETION, label = "Enable effective deletion of files",
        description = "If deletion is allowed, files are physically deleted else files are only removed from references",
        defaultValue = "false")
    private Boolean allowPhysicalDeletion;

    /**
     * Cache for the client S3
     * (Do not use this field, use the createS3Client)
     */
    private S3HighLevelReactiveClient clientCache;

    /**
     * Configuration of S3 server
     */
    private StorageConfig storageConfiguration;

    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    /**
     * Settings for the configuration of available S3 server
     */
    @Autowired
    private S3StorageConfiguration s3StorageSettings;

    /**
     * Initialize the storage configuration of S3 server
     */
    @PluginInit
    public void init() {
        storageConfiguration = StorageConfig.builder()
                                            .endpoint(endpoint)
                                            .bucket(bucket)
                                            .key(key)
                                            .secret(secret)
                                            .region(region)
                                            .rootPath(rootPath)
                                            .build();
    }

    /**
     * Create the client S3 in order to communicate with S3 server
     *
     * @return the client S3
     */
    private S3HighLevelReactiveClient createS3Client() {
        if (clientCache == null) {
            Scheduler scheduler = Schedulers.newParallel("s3-reactive-client", 10);
            int maxBytesPerPart = multipartThresholdMb * 1024 * 1024;
            clientCache = new S3HighLevelReactiveClient(scheduler, maxBytesPerPart, nbParallelPartsUpload);
        }
        return clientCache;
    }

    /**
     * Retrieve a file reference by the downloading in S3 server
     *
     * @param fileReference the file reference
     * @return the input stream of file reference
     * @throws FileNotFoundException
     */
    @Override
    public InputStream retrieve(FileReference fileReference) throws FileNotFoundException {
        return DownloadUtils.getInputStreamFromS3Source(getEntryKey(fileReference),
                                                        storageConfiguration,
                                                        new StorageCommandID(String.format("%d", fileReference.getId()),
                                                                             UUID.randomUUID()));
    }

    private Mono<InputStream> toInputStream(StorageCommandResult.ReadingPipe pipe) {
        DataBufferFactory dbf = new DefaultDataBufferFactory();
        return pipe.getEntry()
                   .flatMap(entry -> DataBufferUtils.join(entry.getData().map(dbf::wrap)))
                   .map(DataBuffer::asInputStream);
    }

    /**
     * Delete a simple file workingsubsets in S3 server
     *
     * @param workingSet      the simple file workingsubsets
     * @param progressManager the progess manager
     */
    @Override
    public void delete(FileDeletionWorkingSubset workingSet, IDeletionProgressManager progressManager) {
        String tenant = runtimeTenantResolver.getTenant();

        Stream.ofAll(workingSet.getFileDeletionRequests()).flatMap(request -> Try.of(() -> {
            LOGGER.info("Start deleting {} with location {}",
                        request.getFileReference().getMetaInfo().getFileName(),
                        request.getFileReference().getLocation().getUrl());
            StorageCommandID cmdId = new StorageCommandID(request.getJobId(), UUID.randomUUID());

            StorageCommand.Delete deleteCmd = new StorageCommand.Delete.Impl(storageConfiguration,
                                                                             cmdId,
                                                                             getEntryKey(request.getFileReference()));
            return createS3Client().delete(deleteCmd)
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
        }));
    }

    /**
     * Store a simple file workingsubsets in S3 server
     *
     * @param workingSet      the simple file workingsubsets
     * @param progressManager the progess manager
     */
    @Override
    public void store(FileStorageWorkingSubset workingSet, IStorageProgressManager progressManager) {

        Stream.ofAll(workingSet.getFileReferenceRequests()).flatMap(request -> Try.of(() -> {
            LOGGER.info("[{}] Start storing {}", request.getJobId(), request.getOriginUrl());
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
            // Check the checksum of stored file
            if (isStoredFileValidated(request, entryKey)) {
                long storedFileSize = 0l;
                if (result instanceof StorageCommandResult.WriteSuccess resultSuccess) {
                    storedFileSize = resultSuccess.getSize();
                }
                progressManager.storageSucceed(request,
                                               storageConfiguration.entryKeyUrl(entryKey.replaceFirst("^/*", "")),
                                               storedFileSize);
            } else {
                progressManager.storageFailed(request, "Checksum does not match with expected one");
            }
            return result;
        }));
    }

    /**
     * Check if the checksum of stored file is identical with the expected one
     *
     * @param request  the request
     * @param entryKey the entry key for the S3 server
     * @return true if the 2 checksums are identical; false otherwise
     */
    private boolean isStoredFileValidated(FileStorageRequest request, String entryKey) {
        boolean isValid = true;
        LOGGER.debug("[{}] Retrieve checksum of file {} from s3 server with endPoint [{}] in bucket [{}].",
                     request.getJobId(),
                     entryKey,
                     storageConfiguration.getEndpoint(),
                     storageConfiguration.getBucket());

        StorageCommand.Check checkCmd = StorageCommand.check(storageConfiguration,
                                                             createStorageCommandID(request.getJobId()),
                                                             entryKey);

        String realChecksum = getRealChecksum(request, entryKey, checkCmd);

        String expectedChecksum = request.getMetaInfo().getChecksum();
        // Check 2 checksums
        if (realChecksum.equals(expectedChecksum)) {
            return isValid;
        }
        LOGGER.debug(
            "[{}] The checksum of the stored file [{}] is not the same as the checksum of the input file [{}] to store in the S3 server.",
            request.getJobId(),
            realChecksum,
            expectedChecksum);
        LOGGER.info(
            "[{}] Deleting the file {} from the s3 server with endPoint [{}] in bucket [{}] because the checksum does not match the expected one.",
            request.getJobId(),
            entryKey,
            storageConfiguration.getEndpoint(),
            storageConfiguration.getBucket());

        StorageCommand.Delete deleteCmd = StorageCommand.delete(storageConfiguration,
                                                                createStorageCommandID(request.getJobId()),
                                                                storageConfiguration.entryKey(entryKey));
        createS3Client().delete(deleteCmd)
                        .flatMap(r -> r.matchDeleteResult(Mono::just,
                                                          unreachable -> Mono.error(new RuntimeException(String.format(
                                                              "Unreachable [endpoint: %s] : %s [bucket: %s]",
                                                              storageConfiguration.getEndpoint(),
                                                              unreachable.getThrowable().getMessage(),
                                                              storageConfiguration.getBucket()))),
                                                          failure -> Mono.error(new RuntimeException(String.format(
                                                              "Delete failure [bucket: %s] [endpoint: %s]",
                                                              storageConfiguration.getBucket(),
                                                              storageConfiguration.getEndpoint())))))

                        .doOnError(t -> LOGGER.error(
                            "[{}] Failed [bucket: {}] to delete file {} [endpoint: {}] the checksum does not match with the expected one :",
                            request.getJobId(),
                            storageConfiguration.getBucket(),
                            entryKey,
                            storageConfiguration.getEndpoint(),
                            t))
                        .doOnSuccess(success -> LOGGER.info(
                            "[{}] Success [bucket: {}] end deleting of file {} [endpoint: {}] the checksum does not match with the expected one",
                            request.getJobId(),
                            storageConfiguration.getBucket(),
                            entryKey,
                            storageConfiguration.getEndpoint()))
                        .subscribe();
        return !isValid;
    }

    private String getRealChecksum(FileStorageRequest request, String entryKey, StorageCommand.Check checkCmd) {
        Optional<String> eTag = createS3Client().eTag(checkCmd)
                                                .doOnError(t -> LOGGER.error(
                                                    "[{}] Failed [bucket: {}] to retrieve checksum of file {} [endpoint: {}] to verify checksum :",
                                                    request.getJobId(),
                                                    storageConfiguration.getBucket(), entryKey,
                                                    storageConfiguration.getEndpoint(),
                                                    t))
                                                .doOnSuccess(success -> LOGGER.info(
                                                    "[{}] Success [bucket: {}] to retrieve checksum of file {} [endpoint: {}] to verify checksum",
                                                    request.getJobId(),
                                                    storageConfiguration.getBucket(), entryKey,
                                                    storageConfiguration.getEndpoint()))
                                                .block();
        String realChecksum = "";
        if (eTag != null && eTag.isPresent()) {
            realChecksum = eTag.get();
        }
        return realChecksum;
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

    private String getEntryKey(FileReference fileReference) {
        return fileReference.getLocation()
                            .getUrl()
                            .replaceFirst(Pattern.quote(endpoint) + "/*", "")
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
    private long getFileSize(URL sourceUrl) {
        long fileSize = 0L;
        try {
            fileSize = DownloadUtils.getContentLength(sourceUrl, 0, s3StorageSettings.getStorages());
        } catch (IOException e) {
            LOGGER.error("Failure in the getting of file size : {}", sourceUrl, e);
        }
        return fileSize;
    }
}
