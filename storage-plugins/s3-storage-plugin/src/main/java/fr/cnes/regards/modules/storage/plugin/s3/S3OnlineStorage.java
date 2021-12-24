package fr.cnes.regards.modules.storage.plugin.s3;

import com.google.common.collect.Maps;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
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
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
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

    private static final Logger LOGGER = getLogger(S3OnlineStorage.class);

    @PluginParameter(name = S3_SERVER_ENDPOINT_PARAM_NAME, description = "Endpoint of the S3 server",
            label = "S3 server endpoint")
    private String endpoint;

    @PluginParameter(name = S3_SERVER_REGION_PARAM_NAME, description = "Region of the S3 server",
            label = "S3 server region")
    private String region;

    @PluginParameter(name = S3_SERVER_KEY_PARAM_NAME, description = "Key of the S3 server", label = "S3 server key")
    private String key;

    @PluginParameter(name = S3_SERVER_SECRET_PARAM_NAME, description = "Secret of the S3 server",
            label = "S3 server secret")
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

    /**
     * Do not use this field, use the getStorageConfig getter
     */
    private StorageConfig storageConfigCache;

    /**
     * Do not use this field, use the getClient getter
     */
    private S3HighLevelReactiveClient clientCache;

    private String getEntryKey(StorageConfig storageConfig, String url) {
        return url.replaceFirst(Pattern.quote(storageConfig.getEndpoint()) + "/*", "")
                .replaceFirst(Pattern.quote(storageConfig.getBucket()) + "/*", "");
    }

    private StorageConfig getStorageConfig() {
        if (storageConfigCache == null) {
            storageConfigCache = StorageConfig.builder().endpoint(endpoint).bucket(bucket).key(key).secret(secret)
                    .region(region).rootPath(rootPath).build();
        }
        return storageConfigCache;
    }

    private S3HighLevelReactiveClient getClient() {
        if (clientCache == null) {
            Scheduler scheduler = Schedulers.newParallel("s3-reactive-client", 10);
            int maxBytesPerPart = multipartThresholdMb * 1024 * 1024;
            clientCache = new S3HighLevelReactiveClient(scheduler, maxBytesPerPart);
        }
        return clientCache;
    }

    @Override
    public InputStream retrieve(FileReference fileReference) {
        String url = fileReference.getLocation().getUrl();
        StorageConfig storageConfig = getStorageConfig();
        String entryKey = getEntryKey(storageConfig, url);
        StorageCommand.Read readCmd = StorageCommand.read(storageConfig, new StorageCommandID(
                String.format("%d", fileReference.getId()), UUID.randomUUID()), entryKey);
        Mono<StorageCommandResult.ReadResult> read = getClient().read(readCmd);

        return read.flatMap(result -> result.matchReadResult(this::toInputStream, unreachable -> Mono.error(
                new ModuleException("Unreachable server: " + unreachable.toString())), notFound -> Mono.error(
                new FileNotFoundException("Entry not found")))).block();
    }

    private Mono<InputStream> toInputStream(StorageCommandResult.ReadingPipe pipe) {
        DataBufferFactory dbf = new DefaultDataBufferFactory();
        return pipe.getEntry().flatMap(entry -> DataBufferUtils.join(entry.getData().map(dbf::wrap)))
                .map(DataBuffer::asInputStream);
    }

    @Override
    public void delete(FileDeletionWorkingSubset workingSet, IDeletionProgressManager progressManager) {
        Stream.ofAll(workingSet.getFileDeletionRequests()).flatMap(request -> Try.of(() -> {
            LOGGER.info("Start deleting {}", request);
            StorageCommandID cmdId = new StorageCommandID(request.getJobId(), UUID.randomUUID());
            StorageConfig storageConfig = getStorageConfig();
            String entryKey = storageConfig.entryKey(request.getFileReference().getMetaInfo().getChecksum());
            StorageCommand.Delete deleteCmd = new StorageCommand.Delete.Impl(storageConfig, cmdId, entryKey);
            return getClient().delete(deleteCmd).flatMap(result -> result.matchDeleteResult(Mono::just,
                                                                                            unreachable -> Mono.error(
                                                                                                    new RuntimeException(
                                                                                                            "Unreachable endpoint")),
                                                                                            failure -> Mono.error(
                                                                                                    new RuntimeException(
                                                                                                            "Delete failure"))))
                    .doOnError(t -> {
                        LOGGER.error("End deleting {}", request, t);
                        progressManager.deletionFailed(request, "Delete failure");
                    }).doOnSuccess(success -> {
                        LOGGER.info("End deleting {}", request);
                        progressManager.deletionSucceed(request);
                    }).block();
        }));
    }

    @Override
    public void store(FileStorageWorkingSubset workingSet, IStorageProgressManager progressManager) {

        Stream.ofAll(workingSet.getFileReferenceRequests()).flatMap(request -> Try.of(() -> {
            LOGGER.info("Start storing {}", request);
            URL sourceUrl = new URL(request.getOriginUrl());
            Flux<ByteBuffer> buffers = DataBufferUtils.readInputStream(sourceUrl::openStream,
                                                                       new DefaultDataBufferFactory(),
                                                                       multipartThresholdMb * 1024 * 1024)
                    .map(DataBuffer::asByteBuffer);
            StorageConfig storageConfig = getStorageConfig();
            StorageCommandID cmdId = new StorageCommandID(request.getJobId(), UUID.randomUUID());
            String entryKey = storageConfig.entryKey(request.getMetaInfo().getChecksum());
            StorageEntry entry = StorageEntry.builder().config(storageConfig).fullPath(entryKey)
                    .checksum(entryChecksum(request)).size(entrySize(request)).data(buffers).build();
            StorageCommand.Write writeCmd = new StorageCommand.Write.Impl(storageConfig, cmdId, entryKey, entry);
            return getClient().write(writeCmd).flatMap(r -> r.matchWriteResult(Mono::just, unreachable -> Mono.error(
                    new RuntimeException("Unreachable endpoint")), failure -> Mono.error(
                    new RuntimeException("Write failure")))).doOnError(t -> {
                LOGGER.error("End storing {}", request, t);
                progressManager.storageFailed(request, "Write failure");
            }).doOnSuccess(success -> {
                LOGGER.info("End storing {}", request);
                progressManager.storageSucceed(request, storageConfig.entryKeyUrl(entryKey), success.getSize());
            }).block();
        }));
    }

    private Option<Long> entrySize(FileStorageRequest request) {
        return Option.some(request.getMetaInfo().getFileSize());
    }

    private Option<Tuple2<String, String>> entryChecksum(FileStorageRequest request) {
        return Option.some(Tuple.of(request.getMetaInfo().getAlgorithm(), request.getMetaInfo().getChecksum()));
    }

    @Override
    public boolean isValidUrl(String urlToValidate, Set<String> errors) {
        StorageConfig config = getStorageConfig();
        boolean result = true;
        String endpoint = config.getEndpoint();
        String bucket = config.getBucket();
        String rootPath = config.getRootPath();
        if (!urlToValidate.startsWith(endpoint)) {
            errors.add("Url does not start with storage endpoint: " + endpoint);
            result = false;
        }
        String prefixBucket = endpoint.replaceFirst("/*$", "") + "/" + bucket;
        if (!urlToValidate.startsWith(prefixBucket)) {
            errors.add("Url does not correspond to storage bucket: " + bucket);
            result = false;
        }
        String prefixRootPath = prefixBucket + "/" + Optional.ofNullable(rootPath).orElse("").replaceFirst("^/*", "");
        if (!urlToValidate.startsWith(prefixRootPath)) {
            errors.add("Url does correspond to storage root path: " + rootPath);
            result = false;
        }
        return result;
    }

    @Override
    public boolean allowPhysicalDeletion() {
        return true;
    }

    @Override
    public PreparationResponse<FileStorageWorkingSubset, FileStorageRequest> prepareForStorage(
            Collection<FileStorageRequest> fileReferenceRequests) {
        List<FileStorageWorkingSubset> workingSubsets = Stream.ofAll(fileReferenceRequests)
                .map(r -> new FileStorageWorkingSubset(Collections.singleton(r))).toJavaList();
        return PreparationResponse.build(workingSubsets, Maps.newHashMap());
    }

    @Override
    public PreparationResponse<FileDeletionWorkingSubset, FileDeletionRequest> prepareForDeletion(
            Collection<FileDeletionRequest> fileDeletionRequests) {
        List<FileDeletionWorkingSubset> workingSubsets = Stream.ofAll(fileDeletionRequests)
                .map(r -> new FileDeletionWorkingSubset(Collections.singleton(r))).toJavaList();
        return PreparationResponse.build(workingSubsets, Maps.newHashMap());
    }

    @Override
    public PreparationResponse<FileRestorationWorkingSubset, FileCacheRequest> prepareForRestoration(
            Collection<FileCacheRequest> requests) {
        List<FileRestorationWorkingSubset> workingSubsets = Stream.ofAll(requests)
                .map(r -> new FileRestorationWorkingSubset(Collections.singleton(r))).toJavaList();
        return PreparationResponse.build(workingSubsets, Maps.newHashMap());
    }
}
