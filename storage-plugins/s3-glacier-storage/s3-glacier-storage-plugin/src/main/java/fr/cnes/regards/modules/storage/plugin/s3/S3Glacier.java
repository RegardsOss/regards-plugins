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
/**
 *
 **/
package fr.cnes.regards.modules.storage.plugin.s3;

import fr.cnes.regards.framework.jpa.multitenant.lock.LockService;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginDestroy;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.modules.plugins.dto.PluginConfigurationDto;
import fr.cnes.regards.framework.s3.client.S3HighLevelReactiveClient;
import fr.cnes.regards.framework.s3.domain.*;
import fr.cnes.regards.framework.s3.exception.S3ClientException;
import fr.cnes.regards.framework.s3.utils.StorageConfigUtils;
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.modules.fileaccess.dto.AbstractStoragePluginConfigurationDto;
import fr.cnes.regards.modules.fileaccess.dto.FileReferenceWithoutOwnersDto;
import fr.cnes.regards.modules.fileaccess.dto.availability.NearlineFileStatusDto;
import fr.cnes.regards.modules.fileaccess.dto.availability.NearlineFileStatusDtoStatus;
import fr.cnes.regards.modules.fileaccess.dto.output.worker.FileNamingStrategy;
import fr.cnes.regards.modules.fileaccess.dto.request.FileStorageRequestAggregationDto;
import fr.cnes.regards.modules.fileaccess.plugin.domain.*;
import fr.cnes.regards.modules.fileaccess.plugin.dto.FileDeletionRequestDto;
import fr.cnes.regards.modules.storage.plugin.s3.dto.S3GlacierStorageConfigurationDto;
import fr.cnes.regards.modules.storage.plugin.smallfiles.AbstractSmallFileFacade;
import fr.cnes.regards.modules.storage.plugin.smallfiles.ISmallFilesStorage;
import fr.cnes.regards.modules.storage.plugin.smallfiles.configuration.DeleteSmallFileTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.smallfiles.configuration.PeriodicActionSmallFileTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.smallfiles.configuration.RetrieveSmallFileTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.smallfiles.configuration.StoreSmallFileTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.smallfiles.utils.RestoreResponse;
import fr.cnes.regards.modules.storage.plugin.smallfiles.utils.SmallFilesUtils;
import fr.cnes.regards.modules.storage.s3.common.AbstractS3Storage;
import io.vavr.Tuple;
import io.vavr.control.Option;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

import static fr.cnes.regards.modules.storage.plugin.smallfiles.ISmallFilesStorage.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Main class of plugin of storage(nearline type) in S3 server
 */
@Plugin(author = "REGARDS Team",
        description = "Plugin handling the storage on S3 server",
        id = "S3Glacier",
        version = "1.0",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CNES",
        markdown = "S3GlacierPlugin.md",
        url = "https://regardsoss.github.io/")
public class S3Glacier extends AbstractS3Storage implements INearlineStorageLocation {

    private static final Logger LOGGER = getLogger(S3Glacier.class);

    public static final String GLACIER_S3_ACCESS_TRY_TIMEOUT = "Glacier_S3_Access_Try_Timeout";

    public static final String GLACIER_PARALLEL_AVAILABILITY_TASK_NUMBER = "Glacier_Parallel_Availability_Number";

    public static final String ZIP_DIR = "zip";

    public static final String TMP_DIR = "tmp";

    public static final String ARCHIVE_DATE_FORMAT = "yyyyMMddHHmmssSSS";

    public static final String BUILDING_DIRECTORY_PREFIX = "rs_zip_";

    public static final String CURRENT_ARCHIVE_SUFFIX = "_current";

    public static final String MD5_CHECKSUM = "MD5";

    public static final String ARCHIVE_EXTENSION = ".zip";

    public static final String SMALL_FILE_PARAMETER_NAME = "fileName";

    private static final String STANDARD_STORAGE_CLASS_NAME = "standardStorageClassName";

    private static final String FILE = "File ";

    @Autowired
    protected LockService lockService;

    @PluginParameter(name = SMALL_FILES_WORKSPACE_PATH,
                     description = "Local workspace for archive building and restoring cache",
                     label = "Workspace path")
    private String rawWorkspacePath;

    @PluginParameter(name = SMALL_FILES_MAX_SIZE,
                     description = "Threshold under which files are categorized as small files, in bytes.",
                     label = "Small file max size in bytes.",
                     defaultValue = "1048576")
    private int smallFileMaxSize;

    @PluginParameter(name = SMALL_FILES_ARCHIVE_MAX_SIZE,
                     description = "Threshold beyond which a small files archive is considered as full and closed, in"
                                   + " bytes.",
                     label = "Archive max size in bytes.",
                     defaultValue = "10485760")
    private int archiveMaxSize;

    @PluginParameter(name = SMALL_FILES_ARCHIVE_DURATION_IN_HOURS,
                     description = "Determines when the current small files archive is considered too old and should "
                                   + "be closed, in hours",
                     label = "Archive max age in hours",
                     defaultValue = "24")
    private int archiveMaxAge;

    @PluginParameter(name = SMALL_FILES_PARALLEL_DELETE_AND_RESTORE_TASK_NUMBER,
                     description = "Number of parallel tasks for file restoration and deletion.",
                     label = "Number of file to restore or to delete in parallel",
                     defaultValue = "20")
    private int parallelTaskNumber;

    @PluginParameter(name = GLACIER_PARALLEL_AVAILABILITY_TASK_NUMBER,
                     description = "Number of parallel tasks for file availability.",
                     label = "Number of availability requests in parallel",
                     defaultValue = "10")
    private int availabilityParallelTaskNumber;

    @PluginParameter(name = SMALL_FILES_PARALLEL_STORE_TASK_NUMBER,
                     description = "Number of parallel files to store. A high number of parallel files needs to raise"
                                   + " microservice available memory resource.",
                     label = "Number of files to store in parallel",
                     defaultValue = "5")
    private int storeParallelTaskNumber;

    @PluginParameter(name = SMALL_FILES_ARCHIVE_CACHE_FILE_LIFETIME_IN_HOURS,
                     description = "Duration in hours for which the restored small file archives and their "
                                   + "content will be kept in the archive cache in order to limit multiple "
                                   + "restoration of the same archive",
                     label = "Small file archive cache lifetime in hours",
                     defaultValue = "24")
    private int archiveCacheLifetime;

    @PluginParameter(name = GLACIER_S3_ACCESS_TRY_TIMEOUT,
                     description = "Timeout in seconds during S3 access after which the job will return an error",
                     label = "S3 Access Timeout in seconds",
                     defaultValue = "3600")
    private int s3AccessTimeout;

    @PluginParameter(name = STANDARD_STORAGE_CLASS_NAME,
                     description = "The name of the standard storage class if different from STANDARD",
                     label = "Standard storage class name",
                     optional = true)
    private String standardStorageClassName;

    @PluginParameter(name = USE_EXTERNAL_CACHE_NAME,
                     description = "If the external cache is enabled, the storage uses the external cache, otherwise "
                                   + "the internal cache of REGARDS.",
                     label = "Enable external cache",
                     optional = true,
                     defaultValue = "false")
    private boolean useExternalCache;

    /**
     *
     */
    @Value("${regards.glacier.renew.max.iteration.wait.period:120}")
    private int renewMaxIterationWaitingPeriodInS = 120;

    /**
     * Duration in milliseconds before end of locking time to run lock renew. To avoid lock renew done too late.
     */
    @Value("${regards.glacier.renew.call.duration:1000}")
    private int renewCallDurationInMs = 1000;

    /**
     * Timeout to acquire a new lock to run a {@link fr.cnes.regards.modules.storage.plugin.smallfiles.task.CleanDirectoryTask}
     */
    @Value("${regards.glacier.clean.cache.task.lock.acquire.timeout:60}")
    private int cleanCacheTaskLockAcquireTimeout = 60;

    /**
     * Scheduler periodic time to run {@link fr.cnes.regards.modules.storage.plugin.smallfiles.task.CleanDirectoryTask}
     */
    @Value("${regards.glacier.scheduled.cache.clean.minutes:60}")
    private int scheduledCacheClean = 60;

    private String workspacePath;

    private ThreadPoolTaskScheduler scheduler;

    /**
     * S3 client used for {@link this#download(FileReferenceWithoutOwnersDto)} download} method to check for file
     * availability.
     */
    private S3HighLevelReactiveClient checkAvailabilityClient;

    private BasicThreadFactory factory = new BasicThreadFactory.Builder().namingPattern(
        "s3-glacier-availability-thread-%d").priority(Thread.MAX_PRIORITY).build();

    @PluginInit(hasConfiguration = true)
    public void initGlacier(PluginConfigurationDto conf) {
        if (runtimeTenantResolver != null) {
            workspacePath = Path.of(rawWorkspacePath, runtimeTenantResolver.getTenant()).toString();
        } else {
            // In case the runtimeTenantResolver doesn't exist, use the raw workspace
            // This will happen in tests
            workspacePath = rawWorkspacePath;
        }
        storageName = conf.getBusinessId();
        initWorkspaceCleanScheduler();
    }

    private void initWorkspaceCleanScheduler() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        Trigger trigger = new PeriodicTrigger(scheduledCacheClean, TimeUnit.MINUTES);

        Runnable cleanTask = () -> {
            LOGGER.info("Starting scheduled workspace cleaning");
            PeriodicActionSmallFileTaskConfiguration configuration = new PeriodicActionSmallFileTaskConfiguration(
                rootPath,
                workspacePath,
                storageName,
                parallelTaskNumber,
                archiveMaxAge,
                archiveCacheLifetime,
                cleanCacheTaskLockAcquireTimeout);
            getSmallFilesFacade(null).cleanArchiveCache(configuration);
            LOGGER.info("End of scheduled workspace cleaning");
        };
        scheduler.schedule(cleanTask, trigger);
    }

    @PluginDestroy
    public void onDestroy() {
        LOGGER.warn("Shutdown of the plugin, this may cause errors as the currently running tasks will be "
                    + "terminated");
        scheduler.shutdown();
        if (checkAvailabilityClient != null) {
            checkAvailabilityClient.close();
        }
    }

    @Override
    public boolean isInternalCache() {
        return !useExternalCache;
    }

    @Override
    public void store(FileStorageWorkingSubset workingSet, IStorageProgressManager progressManager) {
        try (S3HighLevelReactiveClient client = createS3Client()) {
            getSmallFilesFacade(client).store(workingSet,
                                              progressManager,
                                              new StoreSmallFileTaskConfiguration(workspacePath,
                                                                                  s3StorageSettings.getStorages(),
                                                                                  archiveMaxSize,
                                                                                  rootPath,
                                                                                  storeParallelTaskNumber,
                                                                                  smallFileMaxSize));
        }
    }

    @Override
    public void retrieve(FileRestorationWorkingSubset workingSubset, IRestorationProgressManager progressManager) {
        try (S3HighLevelReactiveClient client = createS3Client()) {
            getSmallFilesFacade(client).retrieve(workingSubset,
                                                 progressManager,
                                                 new RetrieveSmallFileTaskConfiguration(rootPath,
                                                                                        workspacePath,
                                                                                        storeParallelTaskNumber,
                                                                                        renewMaxIterationWaitingPeriodInS,
                                                                                        renewCallDurationInMs,
                                                                                        useExternalCache));
        }
    }

    @Override
    public void delete(FileDeletionWorkingSubset workingSet, IDeletionProgressManager progressManager) {
        try (S3HighLevelReactiveClient client = createS3Client()) {
            getSmallFilesFacade(client).delete(workingSet,
                                               progressManager,
                                               new DeleteSmallFileTaskConfiguration(rootPath,
                                                                                    workspacePath,
                                                                                    storageName,
                                                                                    storeParallelTaskNumber,
                                                                                    renewMaxIterationWaitingPeriodInS,
                                                                                    renewCallDurationInMs));
        }
    }

    @Override
    public void runPeriodicAction(IPeriodicActionProgressManager progressManager) {
        try (S3HighLevelReactiveClient client = createS3Client()) {
            PeriodicActionSmallFileTaskConfiguration configuration = new PeriodicActionSmallFileTaskConfiguration(
                rootPath,
                workspacePath,
                storageName,
                parallelTaskNumber,
                archiveMaxAge,
                archiveCacheLifetime,
                cleanCacheTaskLockAcquireTimeout);
            getSmallFilesFacade(client).runPeriodicAction(progressManager, configuration);
        }
    }

    @Override
    public boolean hasPeriodicAction() {
        return true;
    }

    @Override
    public void runCheckPendingAction(IPeriodicActionProgressManager progressManager,
                                      Set<FileReferenceWithoutOwnersDto> filesWithPendingActions) {
        getSmallFilesFacade(null).runCheckPendingAction(progressManager,
                                                        filesWithPendingActions,
                                                        parallelTaskNumber,
                                                        workspacePath);
    }

    @Override
    public AbstractStoragePluginConfigurationDto createWorkerStoreConfiguration() {
        return new S3GlacierStorageConfigurationDto(storageConfiguration,
                                                    multipartThresholdMb,
                                                    nbParallelPartsUpload,
                                                    FileNamingStrategy.valueOf(fileNamingStrategy),
                                                    workspacePath,
                                                    smallFileMaxSize,
                                                    standardStorageClassName);
    }

    private boolean isSmallFile(FileReferenceWithoutOwnersDto fileReference) {
        SmallFilesUtils.GlacierUrl s3GlacierUrl = SmallFilesUtils.dispatchFilePath(fileReference.getLocation()
                                                                                                .getUrl());
        return s3GlacierUrl.isSmallFileUrl();
    }

    @Override
    public InputStream download(FileReferenceWithoutOwnersDto fileReference)
        throws NearlineFileNotAvailableException, NearlineDownloadException {

        NearlineFileStatusDto nearlineFileStatusDto = doCheckAvailability(fileReference, getCheckAvailabilityClient());
        if (!nearlineFileStatusDto.getAvailable().equals(NearlineFileStatusDtoStatus.AVAILABLE)) {
            LOGGER.warn(nearlineFileStatusDto.getMessage());
            throw new NearlineFileNotAvailableException(nearlineFileStatusDto.getMessage());
        }

        try {
            String entryKey = getEntryKey(fileReference);
            Optional<Path> smallFilePathInWorkspace = findSmallFilePathInWorkspace(fileReference);
            if (smallFilePathInWorkspace.isPresent()) {
                // download small file
                // don't manage any lock here, if file is not accessible, the download just fail here.
                return Files.newInputStream(smallFilePathInWorkspace.get(), StandardOpenOption.READ);
            } else {
                // download big file
                return DownloadUtils.getInputStreamFromS3Source(entryKey,
                                                                storageConfiguration,
                                                                new StorageCommandID(String.format("%d",
                                                                                                   fileReference.getId()),
                                                                                     UUID.randomUUID()),
                                                                10);
            }
        } catch (IOException e) { // NOSONAR
            LOGGER.error(FILE + "{} cannot be downloaded ", fileReference.getMetaInfo().getFileName(), e);
            throw new NearlineDownloadException(FILE
                                                + fileReference.getMetaInfo().getFileName()
                                                + " cannot be downloaded : "
                                                + e.getMessage());
        }
    }

    @Override
    public List<NearlineFileStatusDto> checkAvailability(List<FileReferenceWithoutOwnersDto> fileReferences) {
        List<NearlineFileStatusDto> results = new ArrayList<>();
        ExecutorService executorService = null;
        try {
            S3HighLevelReactiveClient client = getCheckAvailabilityClient();
            executorService = Executors.newFixedThreadPool(availabilityParallelTaskNumber, factory);
            List<Future<NearlineFileStatusDto>> availabilitiesResults = executorService.invokeAll(fileReferences.stream()
                                                                                                                .map(
                                                                                                                    fileReference -> doCheckAvailabilityCallable(
                                                                                                                        fileReference,
                                                                                                                        client))
                                                                                                                .toList());
            // Wait for all tasks to complete
            for (Future<NearlineFileStatusDto> future : availabilitiesResults) {
                results.add(future.get());
            }
        } catch (InterruptedException e) {
            LOGGER.error("Check availability process interrupted");
            addNonAvailableResultsForUnprocessedFiles(results, fileReferences);
        } catch (ExecutionException e) {
            LOGGER.error("Error during check availability process", e);
            addNonAvailableResultsForUnprocessedFiles(results, fileReferences);
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
        }
        return results;
    }

    public Callable<NearlineFileStatusDto> doCheckAvailabilityCallable(FileReferenceWithoutOwnersDto fileReference,
                                                                       S3HighLevelReactiveClient client) {
        return () -> doCheckAvailability(fileReference, client);
    }

    public NearlineFileStatusDto doCheckAvailability(FileReferenceWithoutOwnersDto fileReference,
                                                     S3HighLevelReactiveClient client) {
        boolean availability = false;
        OffsetDateTime dateExpiration = null;
        // manage case of small files
        GlacierFileStatus fileAvailable;
        try {
            if (isSmallFile(fileReference)) {
                Optional<Path> smallFilePathInWorkspace = findSmallFilePathInWorkspace(fileReference);
                NearlineFileStatusDto status;
                if (smallFilePathInWorkspace.isPresent()) {
                    LOGGER.debug("Small file available : {}", fileReference.getLocation().getUrl());
                    status = new NearlineFileStatusDto(fileReference.getChecksum(),
                                                       NearlineFileStatusDtoStatus.AVAILABLE,
                                                       null,
                                                       "Small file "
                                                       + fileReference.getMetaInfo().getFileName()
                                                       + " is available.");
                } else {
                    LOGGER.debug("Small file not available : {}", fileReference.getLocation().getUrl());
                    status = new NearlineFileStatusDto(fileReference.getChecksum(),
                                                       NearlineFileStatusDtoStatus.UNAVAILABLE,
                                                       null,
                                                       "Small file "
                                                       + fileReference.getMetaInfo().getFileName()
                                                       + " is not available.");
                }
                return status;
            }

            // case of big files
            long start = Instant.now().toEpochMilli();

            fileAvailable = client.isFileAvailable(storageConfiguration,
                                                   getEntryKey(fileReference),
                                                   standardStorageClassName).block();
            LOGGER.trace("[S3 Monitoring] Checking availability of {} took {} ms",
                         getEntryKey(fileReference),
                         Instant.now().toEpochMilli() - start);
        } catch (MalformedURLException e) {
            return new NearlineFileStatusDto(fileReference.getChecksum(),
                                             NearlineFileStatusDtoStatus.ERROR,
                                             null,
                                             "Unable to check file availability because the url is invalid ");
        }

        String message;
        if (fileAvailable != null) {
            String fileName = fileReference.getMetaInfo().getFileName();
            message = switch (fileAvailable.getStatus()) {
                case EXPIRED -> FILE + fileName + " is expired.";
                case RESTORE_PENDING -> "Restoration of file " + fileName + " is pending.";
                case NOT_AVAILABLE -> FILE + fileName + " is not available.";
                // in all other cases, file is available
                default -> {
                    availability = true;
                    dateExpiration = fileAvailable.getExpirationDate() == null ?
                        null :
                        fileAvailable.getExpirationDate().toOffsetDateTime();
                    yield FILE + fileName + " is available.";
                }
            };
        } else {
            message = "Error accessing s3 client. Please check service log for more information.";
            return new NearlineFileStatusDto(fileReference.getChecksum(),
                                             NearlineFileStatusDtoStatus.ERROR,
                                             null,
                                             message);
        }
        return new NearlineFileStatusDto(fileReference.getChecksum(),
                                         availability ?
                                             NearlineFileStatusDtoStatus.AVAILABLE :
                                             NearlineFileStatusDtoStatus.UNAVAILABLE,
                                         dateExpiration,
                                         message);
    }

    /**
     * Consider files that couldn't be processed as unavailable
     *
     * @param results results of the check availability process, this list will be modified by this method
     */
    private void addNonAvailableResultsForUnprocessedFiles(List<NearlineFileStatusDto> results,
                                                           List<FileReferenceWithoutOwnersDto> fileReferences) {
        List<String> processedChecksums = results.stream().map(NearlineFileStatusDto::getChecksum).toList();
        List<FileReferenceWithoutOwnersDto> unprocessedFiles = fileReferences.stream()
                                                                             .filter(file -> !processedChecksums.contains(
                                                                                 file.getChecksum()))
                                                                             .toList();
        results.addAll(unprocessedFiles.stream()
                                       .map(file -> new NearlineFileStatusDto(file.getChecksum(),
                                                                              NearlineFileStatusDtoStatus.ERROR,
                                                                              null,
                                                                              "Error during the check availability "
                                                                              + "process"))
                                       .toList());
    }

    private Optional<Path> findSmallFilePathInWorkspace(FileReferenceWithoutOwnersDto fileReference)
        throws MalformedURLException {
        Path s3FilePath = Path.of(getEntryKey(fileReference.getLocation().getUrl()));
        SmallFilesUtils.GlacierUrl fileInfo = SmallFilesUtils.dispatchFilePath(s3FilePath.toString());
        if (fileReference.getLocation().isPendingActionRemaining()) {
            // action remaining means that archive containing the small file isn't sent to S3 yet.
            // so, we check in archive building workspace path (/zip) if file is present.
            return findSmallFilePathInWorkspace(fileInfo, getArchiveBuildingWorkspacePath());
        }
        return Optional.empty();
    }

    private String getArchiveBuildingWorkspacePath() {
        return workspacePath + File.separator + ZIP_DIR;
    }

    private Optional<Path> findSmallFilePathInWorkspace(SmallFilesUtils.GlacierUrl fileInfos, String path) {
        String relativeArchivePath = fileInfos.archiveFilePath();
        Path archiveCachePath = Path.of(path, relativeArchivePath);
        String archiveName = archiveCachePath.getFileName().toString();
        Optional<String> smallFileName = fileInfos.smallFileNameInArchive();

        if (smallFileName.isPresent()) {
            String dirName = SmallFilesUtils.computePathOfBuildDirectoryFromArchiveName(archiveName);
            Path localPath = archiveCachePath.getParent().resolve(dirName).resolve(smallFileName.get());
            if (Files.exists(localPath)) {
                return Optional.of(localPath);
            }
            // check in _current folder
            Path localPathWithCurrent = archiveCachePath.getParent()
                                                        .resolve(dirName.concat(CURRENT_ARCHIVE_SUFFIX))
                                                        .resolve(smallFileName.get());
            if (Files.exists(localPathWithCurrent)) {
                return Optional.of(localPathWithCurrent);
            }
        }
        // in all other cases return empty
        return Optional.empty();
    }

    private synchronized S3HighLevelReactiveClient getCheckAvailabilityClient() {
        if (checkAvailabilityClient == null) {
            checkAvailabilityClient = createS3Client();
        }
        return checkAvailabilityClient;
    }

    public AbstractSmallFileFacade getSmallFilesFacade(S3HighLevelReactiveClient client) {
        return new S3SmallFilesFacade(client);
    }

    public class S3SmallFilesFacade extends AbstractSmallFileFacade {

        private static final Logger LOGGER = getLogger(S3SmallFilesFacade.class);

        private final S3HighLevelReactiveClient client;

        public S3SmallFilesFacade(S3HighLevelReactiveClient s3Client) {
            super(runtimeTenantResolver, lockService);
            this.client = s3Client;
        }

        @Override
        public void handleDeleteFileRequest(FileDeletionRequestDto request, IDeletionProgressManager progressManager) {
            handleDeleteRequest(request, client, progressManager);
        }

        @Override
        public String getThreadNamingPattern() {
            return "s3-glacier-threadpool-thread-%d";
        }

        @Override
        public RestoreResponse restore(String key, @Nullable Integer availabilityHours) {
            return SmallFilesUtils.restore(client, storageConfiguration, key, standardStorageClassName, null);
        }

        @Override
        public boolean downloadFile(Path targetFilePath, String key, @Nullable String taskId) {
            return SmallFilesUtils.downloadFile(targetFilePath, key, storageConfiguration, null);
        }

        @Override
        public GlacierFileStatus downloadAfterRestoreFile(Path targetFilePath,
                                                          String key,
                                                          String lockName,
                                                          Instant lockCreationDate,
                                                          int renewMaxIterationWaitingPeriodInS,
                                                          Long renewCallDurationInMs,
                                                          LockService lockService) {
            return SmallFilesUtils.downloadAfterRestoreFile(targetFilePath,
                                                            key,
                                                            storageConfiguration,
                                                            s3AccessTimeout,
                                                            lockName,
                                                            lockCreationDate,
                                                            renewMaxIterationWaitingPeriodInS,
                                                            renewCallDurationInMs,
                                                            standardStorageClassName,
                                                            lockService,
                                                            client);
        }

        @Override
        public GlacierFileStatus checkRestorationComplete(String key,
                                                          String lockName,
                                                          Instant lockCreationDate,
                                                          int renewMaxIterationWaitingPeriodInS,
                                                          Long renewCallDurationInMs,
                                                          LockService lockService) {
            return SmallFilesUtils.checkRestorationComplete(key,
                                                            storageConfiguration,
                                                            s3AccessTimeout,
                                                            lockName,
                                                            lockCreationDate,
                                                            renewMaxIterationWaitingPeriodInS,
                                                            renewCallDurationInMs,
                                                            standardStorageClassName,
                                                            lockService,
                                                            client);
        }

        @Override
        public Path getFileRelativePath(String fileUrl) throws MalformedURLException {
            return Path.of(getEntryKey(fileUrl));
        }

        @Override
        public boolean deleteArchive(String taskId, String entryKey) {
            StorageCommand.Delete deleteCmd = new StorageCommand.Delete.Impl(storageConfiguration,
                                                                             new StorageCommandID(taskId,
                                                                                                  UUID.randomUUID()),
                                                                             entryKey);
            try {
                StorageCommandResult.DeleteSuccess result = client.delete(deleteCmd)
                                                                  .flatMap(deleteResult -> deleteResult.matchDeleteResult(
                                                                      Mono::just,
                                                                      unreachable -> Mono.error(new RuntimeException(
                                                                          "Unreachable endpoint")),
                                                                      failure -> Mono.error(new RuntimeException(
                                                                          "Delete failure in S3 storage"))))
                                                                  .onErrorResume(e -> Mono.error(new S3ClientException(e)))
                                                                  .block();
                if (result != null) {
                    String archiveUrl = StorageConfigUtils.entryKeyUrl(storageConfiguration, entryKey).toString();
                    return result.matchDeleteResult(r -> onDeleteSuccess(entryKey, archiveUrl),
                                                    r -> onStorageUnreachable(r, archiveUrl),
                                                    r -> onDeleteFailure(archiveUrl));
                } else {
                    return false;
                }
            } catch (S3ClientException e) {
                LOGGER.error("Error while deleting empty archive in S3 Storage", e);
                return false;
            }
        }

        private Boolean onDeleteFailure(String archiveUrl) {
            LOGGER.error("Deletion failure for archive {}", archiveUrl);
            return false;
        }

        private Boolean onStorageUnreachable(StorageCommandResult.UnreachableStorage unreachableStorage,
                                             String archiveUrl) {
            LOGGER.error("Deletion failure for archive {}", archiveUrl);
            LOGGER.error(unreachableStorage.getThrowable().getMessage(), unreachableStorage.getThrowable());
            return false;
        }

        public Boolean onDeleteSuccess(String dirPath, String archiveUrl) {
            LOGGER.info(
                "Archive {} successfully deleted from remote S3 storage. Deleting local archive {} and reference in database.",
                archiveUrl,
                dirPath);
            return true;
        }

        @Override
        public URL getStorageUrl(String entryKey) {
            return StorageConfigUtils.entryKeyUrl(storageConfiguration, entryKey);
        }

        @Override
        public String storeFile(Path filePath, String filePathOnStorage, String checksum, Long fileSize)
            throws IOException {
            Flux<ByteBuffer> buffers = DataBufferUtils.read(filePath, new DefaultDataBufferFactory(), 1024)
                                                      .map(DataBuffer::asByteBuffer);

            StorageEntry storageEntry = StorageEntry.builder()
                                                    .config(storageConfiguration)
                                                    .fullPath(filePathOnStorage)
                                                    .checksum(Option.some(Tuple.of(ISmallFilesStorage.MD5_CHECKSUM,
                                                                                   checksum)))
                                                    .size(Option.some(fileSize))
                                                    .data(buffers)
                                                    .build();
            String taskId = "GlacierPeriodicAction" + OffsetDateTime.now()
                                                                    .format(DateTimeFormatter.ofPattern(
                                                                        ISmallFilesStorage.ARCHIVE_DATE_FORMAT));

            // Sending archive
            handleArchiveToSend(filePath, filePathOnStorage, storageEntry, taskId);

            return StorageConfigUtils.entryKeyUrl(storageConfiguration, filePathOnStorage).toString();
        }

        @Override
        public boolean existsStorageUrl(Path path) {
            return DownloadUtils.existsS3(path.toString(), storageConfiguration);
        }

        /**
         * Send the archive to the server
         */
        private void handleArchiveToSend(Path archiveToCreate,
                                         String entryKey,
                                         StorageEntry storageEntry,
                                         String taskId) throws S3ClientException {
            StorageCommand.Write writeCmd = new StorageCommand.Write.Impl(storageConfiguration,
                                                                          new StorageCommandID(taskId,
                                                                                               UUID.randomUUID()),
                                                                          entryKey,
                                                                          storageEntry);
            LOGGER.info("Glacier accessing S3 to send small file archive");
            client.write(writeCmd)
                  .flatMap(writeResult -> writeResult.matchWriteResult(Mono::just,
                                                                       unreachable -> Mono.error(new RuntimeException(
                                                                           "Unreachable endpoint")),
                                                                       failure -> Mono.error(new RuntimeException(
                                                                           "Write failure in S3 storage"))))
                  .onErrorResume(e -> {
                      LOGGER.error("[{}] End storing {}", taskId, archiveToCreate.getFileName(), e);
                      return Mono.error(new S3ClientException(e));
                  })
                  .doOnSuccess(success -> {
                      LOGGER.info("[{}] End storing {}", taskId, archiveToCreate.getFileName());
                  })
                  .block();
            LOGGER.info("Glacier S3 access ended");

        }

        @Override
        public void handleStoreFileRequest(FileStorageRequestAggregationDto request,
                                           IStorageProgressManager progressManager,
                                           String s3RootPath) {
            handleStoreRequest(request, client, progressManager, s3RootPath);
        }
    }
}
