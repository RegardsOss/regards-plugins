package fr.cnes.regards.modules.storage.plugin.s3;

import com.google.common.collect.Maps;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.s3.client.S3HighLevelReactiveClient;
import fr.cnes.regards.framework.s3.domain.*;
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
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import static org.slf4j.LoggerFactory.getLogger;

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
        label = "Multipart threshold in Mb", defaultValue = "5")
    private int nbParallelPartsUpload;

    /**
     * Do not use this field, use the getClient getter
     */
    private S3HighLevelReactiveClient clientCache;

    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    @PluginInit
    public void init() {
        rootPath = rootPath == null ? "" : rootPath;
    }

    private S3HighLevelReactiveClient getClient() {
        if (clientCache == null) {
            Scheduler scheduler = Schedulers.newParallel("s3-reactive-client", 10);
            int maxBytesPerPart = multipartThresholdMb * 1024 * 1024;
            clientCache = new S3HighLevelReactiveClient(scheduler, maxBytesPerPart, nbParallelPartsUpload);
        }
        return clientCache;
    }

    @Override
    public InputStream retrieve(FileReference fileReference) {

        StorageCommandID cmdId = new StorageCommandID(String.format("%d", fileReference.getId()), UUID.randomUUID());

        StorageConfig storageConfig = buildStorageConfig(getRootPathFromUrl(fileReference));

        String entryKey = storageConfig.entryKey(fileReference.getMetaInfo().getChecksum());

        StorageCommand.Read readCmd = StorageCommand.read(storageConfig, cmdId, entryKey);
        return getClient().read(readCmd)
                          .flatMap(readResult -> readResult.matchReadResult(this::toInputStream,
                                                                            unreachable -> Mono.error(new ModuleException(
                                                                                "Unreachable server: "
                                                                                + unreachable.toString())),
                                                                            notFound -> Mono.error(new FileNotFoundException(
                                                                                "Entry not found"))))
                          .block();
    }

    private Mono<InputStream> toInputStream(StorageCommandResult.ReadingPipe pipe) {
        DataBufferFactory dbf = new DefaultDataBufferFactory();
        return pipe.getEntry()
                   .flatMap(entry -> DataBufferUtils.join(entry.getData().map(dbf::wrap)))
                   .map(DataBuffer::asInputStream);
    }

    @Override
    public void delete(FileDeletionWorkingSubset workingSet, IDeletionProgressManager progressManager) {
        String tenant = runtimeTenantResolver.getTenant();

        Stream.ofAll(workingSet.getFileDeletionRequests()).flatMap(request -> Try.of(() -> {
            LOGGER.info("Start deleting {} with location {}",
                        request.getFileReference().getMetaInfo().getFileName(),
                        request.getFileReference().getLocation().getUrl());
            StorageCommandID cmdId = new StorageCommandID(request.getJobId(), UUID.randomUUID());

            StorageConfig storageConfig = buildStorageConfig(getRootPathFromUrl(request.getFileReference()));

            String entryKey = storageConfig.entryKey(request.getFileReference().getMetaInfo().getChecksum());

            StorageCommand.Delete deleteCmd = new StorageCommand.Delete.Impl(storageConfig, cmdId, entryKey);
            return getClient().delete(deleteCmd)
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

    @Override
    public void store(FileStorageWorkingSubset workingSet, IStorageProgressManager progressManager) {
        Stream.ofAll(workingSet.getFileReferenceRequests()).flatMap(request -> Try.of(() -> {
            LOGGER.info("Start storing {}", request.getOriginUrl());
            URL sourceUrl = new URL(request.getOriginUrl());

            request.getMetaInfo().setFileSize(getFileSize(sourceUrl));

            Flux<ByteBuffer> buffers = DataBufferUtils.readInputStream(sourceUrl::openStream,
                                                                       new DefaultDataBufferFactory(),
                                                                       multipartThresholdMb * 1024 * 1024)
                                                      .map(DataBuffer::asByteBuffer);

            StorageConfig storageConfig = buildStorageConfig(makeRootPath(request.getStorageSubDirectory()));
            StorageCommandID cmdId = new StorageCommandID(request.getJobId(), UUID.randomUUID());

            String entryKey = storageConfig.entryKey(request.getMetaInfo().getChecksum());

            StorageEntry storageEntry = StorageEntry.builder()
                                                    .config(storageConfig)
                                                    .fullPath(entryKey)
                                                    .checksum(entryChecksum(request))
                                                    .size(entrySize(request))
                                                    .data(buffers)
                                                    .build();

            StorageCommand.Write writeCmd = new StorageCommand.Write.Impl(storageConfig, cmdId, entryKey, storageEntry);

            return getClient().write(writeCmd)
                              .flatMap(writeResult -> writeResult.matchWriteResult(Mono::just,
                                                                                   unreachable -> Mono.error(new RuntimeException(
                                                                                       "Unreachable endpoint")),
                                                                                   failure -> Mono.error(new RuntimeException(
                                                                                       "Write failure in S3 storage"))))
                              .doOnError(t -> {
                                  LOGGER.error("End storing {}", request.getOriginUrl(), t);
                                  progressManager.storageFailed(request, "Write failure in S3 storage");

                              })
                              .doOnSuccess(success -> {
                                  LOGGER.info("End storing {}", request.getOriginUrl());
                                  progressManager.storageSucceed(request,
                                                                 storageConfig.entryKeyUrl(entryKey.replaceFirst("^/*",
                                                                                                                 "")),
                                                                 success.getSize());
                              })
                              .block();
        }));
    }

    private String getRootPathFromUrl(FileReference fileReference) {
        return fileReference.getLocation()
                            .getUrl()
                            .replaceFirst(Pattern.quote(endpoint) + "/*", "")
                            .replaceFirst(Pattern.quote(bucket), "")
                            .replaceFirst(Pattern.quote(fileReference.getMetaInfo().getChecksum()), "");
    }

    private String makeRootPath(String subDirectory) {
        if (subDirectory == null) {
            return rootPath;
        }
        return Paths.get(rootPath, subDirectory).toString();
    }

    private StorageConfig buildStorageConfig(String path) {
        return StorageConfig.builder()
                            .endpoint(endpoint)
                            .bucket(bucket)
                            .key(key)
                            .secret(secret)
                            .region(region)
                            .rootPath(path)
                            .build();
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
        return true;
    }

    @Override
    public PreparationResponse<FileStorageWorkingSubset, FileStorageRequest> prepareForStorage(Collection<FileStorageRequest> fileReferenceRequests) {
        List<FileStorageWorkingSubset> workingSubsets = Stream.ofAll(fileReferenceRequests)
                                                              .map(r -> new FileStorageWorkingSubset(Collections.singleton(
                                                                  r)))
                                                              .toJavaList();
        return PreparationResponse.build(workingSubsets, Maps.newHashMap());
    }

    @Override
    public PreparationResponse<FileDeletionWorkingSubset, FileDeletionRequest> prepareForDeletion(Collection<FileDeletionRequest> fileDeletionRequests) {
        List<FileDeletionWorkingSubset> workingSubsets = Stream.ofAll(fileDeletionRequests)
                                                               .map(r -> new FileDeletionWorkingSubset(Collections.singleton(
                                                                   r)))
                                                               .toJavaList();
        return PreparationResponse.build(workingSubsets, Maps.newHashMap());
    }

    @Override
    public PreparationResponse<FileRestorationWorkingSubset, FileCacheRequest> prepareForRestoration(Collection<FileCacheRequest> requests) {
        List<FileRestorationWorkingSubset> workingSubsets = Stream.ofAll(requests)
                                                                  .map(r -> new FileRestorationWorkingSubset(Collections.singleton(
                                                                      r)))
                                                                  .toJavaList();
        return PreparationResponse.build(workingSubsets, Maps.newHashMap());
    }

    private long getFileSize(URL sourceUrl) {
        long fileSize = 0l;
        URLConnection urlConnection = null;
        try {
            try {
                urlConnection = sourceUrl.openConnection();
                fileSize = urlConnection.getContentLengthLong();
            } finally {
                if (urlConnection != null)
                    urlConnection.getInputStream().close();
            }
        } catch (IOException e) {
            LOGGER.error("Failure in the getting of file size : {}", sourceUrl, e);
        }
        return fileSize;
    }
}
