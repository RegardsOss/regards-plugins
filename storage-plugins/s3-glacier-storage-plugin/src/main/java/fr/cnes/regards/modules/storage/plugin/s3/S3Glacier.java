package fr.cnes.regards.modules.storage.plugin.s3;

import fr.cnes.regards.framework.jpa.multitenant.lock.LockService;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.s3.domain.StorageCommand;
import fr.cnes.regards.framework.s3.domain.StorageCommandID;
import fr.cnes.regards.framework.s3.domain.StorageEntry;
import fr.cnes.regards.framework.s3.exception.S3ClientException;
import fr.cnes.regards.framework.utils.file.ChecksumUtils;
import fr.cnes.regards.framework.utils.file.ZipUtils;
import fr.cnes.regards.modules.storage.domain.database.request.FileCacheRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileStorageRequest;
import fr.cnes.regards.modules.storage.domain.plugin.*;
import fr.cnes.regards.modules.storage.plugin.s3.configuration.*;
import fr.cnes.regards.modules.storage.plugin.s3.task.*;
import fr.cnes.regards.modules.storage.service.glacier.GlacierArchiveService;
import io.vavr.Tuple;
import io.vavr.control.Option;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Main class of plugin of storage(online type) in S3 server
 */
@Plugin(author = "REGARDS Team", description = "Plugin handling the storage on S3", id = "S3Glacier", version = "1.0",
    contact = "regards@c-s.fr", license = "GPLv3", owner = "CNES", markdown = "S3GlacierPlugin.md",
    url = "https://regardsoss.github.io/")
public class S3Glacier extends S3OnlineStorage implements INearlineStorageLocation {

    private static final Logger LOGGER = getLogger(S3Glacier.class);

    public static final String GLACIER_WORKSPACE_PATH = "Glacier_Workspace_Path";

    public static final String GLACIER_SMALL_FILE_MAX_SIZE = "Glacier_Small_File_Max_Size";

    public static final String GLACIER_SMALL_FILE_ARCHIVE_MAX_SIZE = "Glacier_Small_File_Archive_Max_Size";

    public static final String GLACIER_SMALL_FILE_ARCHIVE_DURATION_IN_HOURS = "Glacier_Small_File_Archive_Duration_In_Hours";

    public static final String GLACIER_PARALLEL_TASK_NUMBER = "Glacier_Parallel_Upload_Number";

    public static final String GLACIER_LOCAL_WORKSPACE_FILE_LIFETIME_IN_HOURS = "Glacier_Local_Workspace_File_Lifetime_In_Hours";

    public static final String GLACIER_S3_ACCESS_TRY_TIMEOUT = "Glacier_S3_Access_Try_Timeout";

    public static final String GLACIER_S3_RENEW_CALL_DURATION = "Glacier_S3_Renew_Call_Duration";

    public static final String ZIP_DIR = "zip";

    public static final String TMP_DIR = "tmp";

    public static final String ARCHIVE_DATE_FORMAT = "yyyyMMddHHmmssSSS";

    public static final String CURRENT_ARCHIVE_SUFFIX = "_current";

    public static final String MD5_CHECKSUM = "MD5";

    public static final String ARCHIVE_EXTENSION = ".zip";

    public static final String ARCHIVE_DELIMITER = "?";

    public static final Long MAX_TASK_DELAY = 3600L;

    public static final String LOCK_PREFIX = "LOCK_";

    public static final String LOCK_STORE_SUFFIX = "_STORE";

    public static final String LOCK_RESTORE_SUFFIX = "_RESTORE";

    @Autowired
    private LockService lockService;

    @Autowired
    private GlacierArchiveService glacierArchiveService;

    ExecutorService executorService;

    @PluginParameter(name = GLACIER_WORKSPACE_PATH,
        description = "Local workspace for archive building and restoring cache", label = "Workspace path")
    private String workspacePath;

    @PluginParameter(name = GLACIER_SMALL_FILE_MAX_SIZE,
        description = "Maximum size of a file for it treated as a small file and stored in group",
        label = "Small file max size", defaultValue = "1048576")
    private int smallFileMaxSize;

    @PluginParameter(name = GLACIER_SMALL_FILE_ARCHIVE_MAX_SIZE,
        description = "Determines when the small files archive is considered full and should be closed",
        label = "Archive max size", defaultValue = "10485760")
    private int archiveMaxSize;

    @PluginParameter(name = GLACIER_SMALL_FILE_ARCHIVE_DURATION_IN_HOURS,
        description = "Determines when the small files archive is considered too old and should be closed, in hours",
        label = "Archive max age", defaultValue = "24")
    private int archiveMaxAge;

    @PluginParameter(name = GLACIER_PARALLEL_TASK_NUMBER, description =
        "Number of tasks that can be handled at the same time in each job. The "
        + "different tasks (Store, Restore, Delete) use the same thread pool ", label = "Parallel Task Number",
        defaultValue = "20")
    private int parallelTaskNumber;

    @PluginParameter(name = GLACIER_LOCAL_WORKSPACE_FILE_LIFETIME_IN_HOURS,
        description = "Duration for which the restored files will be kept in the local workspace",
        label = "Local workspace duration", defaultValue = "24")
    private int localWorkspaceFileLifetime;

    @PluginParameter(name = GLACIER_S3_ACCESS_TRY_TIMEOUT,
        description = "Timeout during S3 access after which the job will return an error", label = "S3 Access Timeout",
        defaultValue = "3600")
    private int s3AccessTimeout;

    @PluginParameter(name = GLACIER_S3_RENEW_CALL_DURATION,
        description = "Upper bound estimation of the time it will take for the renew lock call to be processed",
        label = "Lock renew call duration", defaultValue = "1000")
    private int renewCallDuration;

    @Override
    @PluginInit
    public void init() {
        super.init();
        executorService = Executors.newFixedThreadPool(parallelTaskNumber);
    }

    @Override
    public void store(FileStorageWorkingSubset workingSet, IStorageProgressManager progressManager) {
        workingSet.getFileReferenceRequests().forEach(request -> doStore(request, progressManager));
    }

    private void doStore(FileStorageRequest request, IStorageProgressManager progressManager) {
        try {
            request.getMetaInfo().setFileSize(getFileSize(new URL(request.getOriginUrl())));
            executorService.submit(() -> {
                if (request.getMetaInfo().getFileSize() > smallFileMaxSize) {
                    handleStoreRequest(request, progressManager);
                } else {
                    StoreSmallFileTaskConfiguration configuration = new StoreSmallFileTaskConfiguration(workspacePath,
                                                                                                        s3StorageSettings.getStorages(),
                                                                                                        storageConfiguration,
                                                                                                        archiveMaxSize,
                                                                                                        rootPath);
                    try {
                        lockService.runWithLock(LOCK_PREFIX
                                                + replaceSeparatorInLock(request.getStorageSubDirectory())
                                                + LOCK_STORE_SUFFIX,
                                                new StoreSmallFileTask(configuration, request, progressManager));
                    } catch (InterruptedException e) {
                        LOGGER.error(e.getMessage(), e);
                        progressManager.storageFailed(request,
                                                      String.format("The storage task was interrupted "
                                                                    + "before completion."));
                    }
                }
            });
        } catch (MalformedURLException e) {
            LOGGER.error(e.getMessage(), e);
            progressManager.storageFailed(request, String.format("Invalid source url %s", request.getOriginUrl()));
        }
    }

    @Override
    public void retrieve(FileRestorationWorkingSubset workingSubset, IRestorationProgressManager progressManager) {
        workingSubset.getFileRestorationRequests().forEach(request -> doRetrieve(request, progressManager));
    }

    private void doRetrieve(FileCacheRequest request, IRestorationProgressManager progressManager) {
        executorService.submit(() -> {
            try {
                Path serverRootPath = getServerWithRootPath();
                Path node = Path.of(request.getFileReference().getLocation().getUrl())
                                .getParent()
                                .relativize(serverRootPath);

                Path fileRelativePath = serverRootPath.relativize(Path.of(request.getFileReference()
                                                                                 .getLocation()
                                                                                 .getUrl()));
                boolean isSmallFile = isASmallFileUrl(request.getFileReference().getLocation().getUrl());
                if (isSmallFile && request.getFileReference().getLocation().isPendingActionRemaining()) {
                    // The file has not been saved to S3 yet, it's still in the local archive building workspace
                    RetrieveLocalSmallFileTaskConfiguration configuration = new RetrieveLocalSmallFileTaskConfiguration(
                        fileRelativePath,
                        getArchiveBuildingWorkspacePath());
                    RetrieveLocalSmallFileTask task = new RetrieveLocalSmallFileTask(configuration,
                                                                                     request,
                                                                                     progressManager);
                    lockService.runWithLock(LOCK_PREFIX + replaceSeparatorInLock(node.toString()) + LOCK_STORE_SUFFIX,
                                            task);
                    return;
                }

                String lockName = LOCK_PREFIX
                                  + replaceSeparatorInLock(fileRelativePath.toString())
                                  + LOCK_RESTORE_SUFFIX;

                RetrieveCacheFileTaskConfiguration configuration = new RetrieveCacheFileTaskConfiguration(
                    fileRelativePath,
                    getCachePath(),
                    storageConfiguration,
                    s3AccessTimeout,
                    rootPath,
                    isSmallFile,
                    createS3Client(),
                    lockName,
                    Instant.now(),
                    renewCallDuration,
                    lockService);
                RetrieveCacheFileTask task = new RetrieveCacheFileTask(configuration, request, progressManager);

                lockService.runWithLock(lockName, task);

            } catch (InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
                progressManager.restoreFailed(request, "The deletion task was interrupted before completion.");
            }
        });
    }

    private String getArchiveBuildingWorkspacePath() {
        return workspacePath + File.separator + ZIP_DIR;
    }

    private String getCachePath() {
        return workspacePath + File.separator + TMP_DIR;
    }

    @Override
    public void delete(FileDeletionWorkingSubset workingSet, IDeletionProgressManager progressManager) {
        workingSet.getFileDeletionRequests().forEach(request -> doDelete(request, progressManager));
    }

    private void doDelete(FileDeletionRequest request, IDeletionProgressManager progressManager) {
        if (!isASmallFileUrl(request.getFileReference().getLocation().getUrl())) {
            handleDeleteRequest(request, progressManager);
        } else {
            try {
                Path serverRootPath = getServerWithRootPath();
                Path node = Path.of(request.getFileReference().getLocation().getUrl())
                                .getParent()
                                .relativize(serverRootPath);
                Path fileRelativePath = serverRootPath.relativize(Path.of(request.getFileReference()
                                                                                 .getLocation()
                                                                                 .getUrl()));

                if (request.getFileReference().getLocation().isPendingActionRemaining()) {
                    DeleteLocalSmallFileTaskConfiguration configuration = new DeleteLocalSmallFileTaskConfiguration(
                        fileRelativePath,
                        getArchiveBuildingWorkspacePath(),
                        storageConfiguration,
                        createS3Client());
                    DeleteLocalSmallFileTask task = new DeleteLocalSmallFileTask(configuration,
                                                                                 request,
                                                                                 progressManager);
                    lockService.runWithLock(LOCK_PREFIX + replaceSeparatorInLock(node.toString()) + LOCK_STORE_SUFFIX,
                                            task);

                } else {
                    String lockName = LOCK_PREFIX
                                      + replaceSeparatorInLock(fileRelativePath.toString())
                                      + LOCK_RESTORE_SUFFIX;

                    RestoreAndDeleteSmallFileTaskConfiguration configuration = new RestoreAndDeleteSmallFileTaskConfiguration(
                        fileRelativePath,
                        getCachePath(),
                        rootPath,
                        getArchiveBuildingWorkspacePath(),
                        node,
                        storageConfiguration,
                        createS3Client(),
                        s3AccessTimeout,
                        lockName,
                        Instant.now(),
                        renewCallDuration,
                        lockService);
                    RestoreAndDeleteSmallFileTask task = new RestoreAndDeleteSmallFileTask(configuration,
                                                                                           request,
                                                                                           progressManager);

                    lockService.runWithLock(lockName, task);
                }
            } catch (InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
                progressManager.deletionFailed(request, "The deletion task was interrupted before completion.");
            }
        }
    }

    @Override
    public void runPeriodicAction(IPeriodicActionProgressManager progressManager) {
        submitReadyArchives(progressManager);
        cleanLocalWorkspace();
    }

    private void submitReadyArchives(IPeriodicActionProgressManager progressManager) {
        Path zipWorkspacePath = Paths.get(workspacePath, ZIP_DIR);
        if (!Files.exists(zipWorkspacePath)) {
            return;
        }
        try (Stream<Path> dirList = Files.walk(zipWorkspacePath, 2)) {
            // Directory that will be stored are located in /<WORKSPACE>/<ZIP_DIR>/<NODE>/
            dirList.filter(dir -> !dir.equals(zipWorkspacePath) && !dir.getParent().equals(zipWorkspacePath))
                   .forEach(dir -> doSubmitReadyArchive(dir, progressManager));
        } catch (IOException e) {
            LOGGER.error("Error while attempting to access small files archives workspace during periodic "
                         + "actions, no archives will be submitted to S3 and no request will be updated");
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void doSubmitReadyArchive(Path dirPath, IPeriodicActionProgressManager progressManager) {
        boolean continueOk = true;
        // Renaming _current if needed
        PathAndSuccessState finalDirPath = renameCurrentIfNeeded(dirPath, continueOk);
        if (finalDirPath == null) {
            return;
        }

        // Creating list of files to add to the archive
        List<File> filesList;
        try (Stream<Path> filesListStream = Files.list(finalDirPath.dirPath())) {
            filesList = filesListStream.map(Path::toFile).toList();
        } catch (IOException e) {
            LOGGER.error(
                "Error while attempting to access small files directory {} in archives workspace during periodic "
                + "actions, no files from this directory will be submitted to S3 and no request will be updated",
                finalDirPath.dirPath());
            LOGGER.error(e.getMessage(), e);
            return;
        }

        // Computing relative path of the archive on the storage
        String archivePathOnStorage = Paths.get(Paths.get(workspacePath, ZIP_DIR)
                                                     .relativize(finalDirPath.dirPath().getParent())
                                                     .toString(),
                                                finalDirPath.dirPath().getFileName().toString() + ".zip").toString();

        Path archiveToCreate = finalDirPath.dirPath()
                                           .getParent()
                                           .resolve(finalDirPath.dirPath().getFileName() + ".zip");

        boolean storageSuccess;
        if (finalDirPath.continueOk()) {
            // Creating and sending archive
            storageSuccess = createAndSendArchive(filesList, archivePathOnStorage, archiveToCreate);
        } else {
            storageSuccess = false;
        }

        // Sending storageSuccess of error to progressManager
        handleEndSubmit(progressManager, filesList, archivePathOnStorage, storageSuccess);

        // Cleaning
        cleanBuildingArchive(finalDirPath.dirPath(), filesList, archiveToCreate, storageSuccess);

    }

    /**
     * Rename the _current directory if it is too old and return the new path
     */
    private PathAndSuccessState renameCurrentIfNeeded(Path dirPath, boolean continueOk) {
        if (dirPath.getFileName().toString().endsWith(CURRENT_ARCHIVE_SUFFIX)) {
            String currentName = dirPath.getFileName().toString();
            String nameWithoutSuffix = currentName.substring(0, currentName.length() - CURRENT_ARCHIVE_SUFFIX.length());
            try {
                Instant dirCreationDate = DateUtils.parseDate(nameWithoutSuffix, ARCHIVE_DATE_FORMAT).toInstant();

                if (dirCreationDate.plus(archiveMaxAge, ChronoUnit.HOURS).isAfter(Instant.now())) {
                    // the directory is not old enough, nothing to do
                    return null;
                }
                Path newDirPath = dirPath.getParent().resolve(nameWithoutSuffix);
                continueOk = dirPath.toFile().renameTo(newDirPath.toFile());
                dirPath = newDirPath;
                if (!continueOk) {
                    LOGGER.error("Error while renaming current building directory {}", currentName);
                }
            } catch (ParseException e) {
                LOGGER.error("Error while parsing directory name as a date : {}", nameWithoutSuffix, e);
            }
        }
        PathAndSuccessState pathAndSuccessState = new PathAndSuccessState(dirPath, continueOk);
        return pathAndSuccessState;
    }

    private record PathAndSuccessState(Path dirPath, boolean continueOk) {

    }

    /**
     * Create the archive from the directory content and send it to the server
     */
    private boolean createAndSendArchive(List<File> filesList, String archivePathOnStorage, Path archiveToCreate) {
        try {
            boolean archiveCreationSuccess = ZipUtils.createZipArchive(archiveToCreate.toFile(), filesList);
            if (!archiveCreationSuccess) {
                LOGGER.error("Error while creating archive {}", archiveToCreate.getFileName());
                return false;
            }

            // Sending archive
            String entryKey = storageConfiguration.entryKey(archivePathOnStorage);
            String checksum = ChecksumUtils.computeHexChecksum(archiveToCreate, MD5_CHECKSUM);
            Long fileSize = Files.size(archiveToCreate);

            Flux<ByteBuffer> buffers = DataBufferUtils.read(archiveToCreate,
                                                            new DefaultDataBufferFactory(),
                                                            multipartThresholdMb * 1024 * 1024)
                                                      .map(DataBuffer::asByteBuffer);

            StorageEntry storageEntry = StorageEntry.builder()
                                                    .config(storageConfiguration)
                                                    .fullPath(entryKey)
                                                    .checksum(Option.some(Tuple.of(MD5_CHECKSUM, checksum)))
                                                    .size(Option.some(fileSize))
                                                    .data(buffers)
                                                    .build();
            String taskId = "S3GlacierPeriodicAction" + OffsetDateTime.now()
                                                                      .format(DateTimeFormatter.ofPattern(
                                                                          ARCHIVE_DATE_FORMAT));

            // Sending archive
            sendArchive(archiveToCreate, entryKey, storageEntry, taskId);

            // Saving archive information
            glacierArchiveService.saveGlacierArchive(storageConfiguration.entryKeyUrl(Paths.get(rootPath != null ?
                                                                                                    rootPath :
                                                                                                    "",
                                                                                                archivePathOnStorage)
                                                                                           .toString()).toString(),
                                                     checksum,
                                                     fileSize);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("Error while sending created archive, unknown algorithm {}", MD5_CHECKSUM, e);
            return false;
        } catch (IOException e) {
            LOGGER.error("Error while sending created archive", e);
            return false;
        } catch (S3ClientException e) {
            LOGGER.error("Error while  writing on storage", e);
            return false;
        }
        return true;
    }

    /**
     * Send the archive to the server
     */
    private void sendArchive(Path archiveToCreate, String entryKey, StorageEntry storageEntry, String taskId)
        throws S3ClientException {
        StorageCommand.Write writeCmd = new StorageCommand.Write.Impl(storageConfiguration,
                                                                      new StorageCommandID(taskId, UUID.randomUUID()),
                                                                      entryKey,
                                                                      storageEntry);

        createS3Client().write(writeCmd)
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

    }

    /**
     * Send success status to the progress manager for each file that were pending
     */
    private void handleEndSubmit(IPeriodicActionProgressManager progressManager,
                                 List<File> filesList,
                                 String archivePathOnStorage,
                                 boolean storageSuccess) {
        filesList.forEach(file -> {
            if (storageSuccess) {
                URL storedFileUrl = storageConfiguration.entryKeyUrl(Paths.get(rootPath != null ? rootPath : "",
                                                                               archivePathOnStorage)
                                                                     + ARCHIVE_DELIMITER
                                                                     + file.getName());
                progressManager.storagePendingActionSucceed(storedFileUrl.toString());
            } else {
                progressManager.storagePendingActionError(file.toPath());
            }
        });
    }

    /**
     * Delete the created local archive and the directory content if the storage succeeded
     */
    private static void cleanBuildingArchive(Path dirPath,
                                             List<File> filesList,
                                             Path archiveToCreate,
                                             boolean storageSuccess) {
        if (storageSuccess) {
            //Delete files only if the storage succeeded
            filesList.forEach(file -> {
                try {
                    Files.delete(file.toPath());
                } catch (IOException e) {
                    LOGGER.error("Error while deleting {}", file.getName(), e);
                }
            });
            try {
                Files.delete(dirPath);
            } catch (DirectoryNotEmptyException e) {
                LOGGER.error("Could not delete {} as it is not empty", dirPath.getFileName(), e);
            } catch (IOException e) {
                LOGGER.error("Error while deleting {}", dirPath.getFileName(), e);
            }
        }

        try {
            Files.deleteIfExists(archiveToCreate);
        } catch (IOException e) {
            LOGGER.error("Error while deleting the archive {}", archiveToCreate.getFileName(), e);
        }
    }

    private void cleanLocalWorkspace() {
        Path cacheWorkspacePath = Paths.get(workspacePath, TMP_DIR);
        if (!Files.exists(cacheWorkspacePath)) {
            return;
        }
        Instant oldestAgeToKeep = OffsetDateTime.now().minusHours(localWorkspaceFileLifetime).toInstant();
        try (Stream<Path> dirList = Files.list(cacheWorkspacePath)) {
            dirList.forEach(path -> cleanDirectoryRec(path, oldestAgeToKeep));
        } catch (IOException e) {
            LOGGER.error("Error while accessing cache in {}", cacheWorkspacePath, e);
        }

    }

    private void cleanDirectoryRec(Path dir, Instant oldestAgeToKeep) {
        try (Stream<Path> dirList = Files.list(dir)) {
            dirList.forEach(path -> {

                if (Files.isDirectory(path)) {
                    cleanDirectoryRec(path, oldestAgeToKeep);
                } else {
                    try {
                        BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
                        if (attr.lastModifiedTime().toInstant().isBefore(oldestAgeToKeep)) {
                            Files.delete(path);
                        }
                    } catch (IOException e) {
                        LOGGER.error("Error while deleting file {}", path, e);
                    }
                }
            });
            try (Stream<Path> entries = Files.list(dir)) {
                if (entries.findFirst().isEmpty()) { // Dir empty
                    Files.delete(dir);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error while cleaning directory {}", dir, e);
        }
    }

    public static String replaceSeparatorInLock(String lockNameWithSeparator) {
        return lockNameWithSeparator.replace(File.separator, "_");
    }

    private Path getServerWithRootPath() {
        return Path.of((rootPath != null ?
            Paths.get(endpoint, bucket, rootPath) :
            Paths.get(endpoint, bucket)).toString());
    }

    private boolean isASmallFileUrl(String url) {
        String resourceName = url.substring(url.lastIndexOf("/"));
        return resourceName.contains(ARCHIVE_DELIMITER);
    }
}
