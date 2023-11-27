package fr.cnes.regards.modules.storage.plugin.s3;

import fr.cnes.regards.framework.jpa.multitenant.lock.LockService;
import fr.cnes.regards.framework.jpa.multitenant.lock.LockServiceResponse;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginDestroy;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.modules.plugins.dto.PluginConfigurationDto;
import fr.cnes.regards.modules.filecatalog.dto.AbstractStoragePluginConfigurationDto;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.s3.client.GlacierFileStatus;
import fr.cnes.regards.framework.s3.domain.StorageCommandID;
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.modules.storage.domain.database.FileReference;
import fr.cnes.regards.modules.storage.domain.database.request.FileCacheRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileStorageRequest;
import fr.cnes.regards.modules.storage.domain.dto.AbstractStoragePluginConfigurationDto;
import fr.cnes.regards.modules.storage.domain.exception.NearlineDownloadException;
import fr.cnes.regards.modules.storage.domain.exception.NearlineFileNotAvailableException;
import fr.cnes.regards.modules.storage.domain.database.request.FileStorageRequestAggregation;
import fr.cnes.regards.modules.storage.domain.plugin.*;
import fr.cnes.regards.modules.storage.plugin.s3.configuration.*;
import fr.cnes.regards.modules.storage.plugin.s3.dto.S3GlacierStorageConfigurationDto;
import fr.cnes.regards.modules.storage.plugin.s3.task.*;
import fr.cnes.regards.modules.storage.plugin.s3.utils.LockTypeEnum;
import fr.cnes.regards.modules.storage.plugin.s3.utils.S3GlacierUtils;
import fr.cnes.regards.modules.storage.s3.common.AbstractS3Storage;
import fr.cnes.regards.modules.storage.service.glacier.GlacierArchiveService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Main class of plugin of storage(online type) in S3 server
 */
@Plugin(author = "REGARDS Team",
        description = "Plugin handling the storage on S3",
        id = "S3Glacier",
        version = "1.0",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CNES",
        markdown = "S3GlacierPlugin.md",
        url = "https://regardsoss.github.io/")
public class S3Glacier extends AbstractS3Storage implements INearlineStorageLocation {

    private static final Logger LOGGER = getLogger(S3Glacier.class);

    public static final String GLACIER_WORKSPACE_PATH = "Glacier_Workspace_Path";

    public static final String GLACIER_SMALL_FILE_MAX_SIZE = "Glacier_Small_File_Max_Size";

    public static final String GLACIER_SMALL_FILE_ARCHIVE_MAX_SIZE = "Glacier_Small_File_Archive_Max_Size";

    public static final String GLACIER_SMALL_FILE_ARCHIVE_DURATION_IN_HOURS = "Glacier_Small_File_Archive_Duration_In_Hours";

    public static final String GLACIER_PARALLEL_DELETE_AND_RESTORE_TASK_NUMBER = "Glacier_Parallel_Restore_Number";

    public static final String GLACIER_PARALLEL_STORE_TASK_NUMBER = "Glacier_Parallel_Upload_Number";

    public static final String GLACIER_ARCHIVE_CACHE_FILE_LIFETIME_IN_HOURS = "Glacier_Local_Workspace_File_Lifetime_In_Hours";

    public static final String GLACIER_S3_ACCESS_TRY_TIMEOUT = "Glacier_S3_Access_Try_Timeout";

    public static final String ZIP_DIR = "zip";

    public static final String TMP_DIR = "tmp";

    public static final String ARCHIVE_DATE_FORMAT = "yyyyMMddHHmmssSSS";

    public static final String BUILDING_DIRECTORY_PREFIX = "rs_zip_";

    public static final String CURRENT_ARCHIVE_SUFFIX = "_current";

    public static final String MD5_CHECKSUM = "MD5";

    public static final String ARCHIVE_EXTENSION = ".zip";

    public static final String LOCK_PREFIX = "LOCK_";

    public static final String LOCK_STORE_SUFFIX = "_STORE";

    public static final String LOCK_RESTORE_SUFFIX = "_RESTORE";

    public static final String TENANT_LOG = "In thread {}, forcing tenant to {}";

    public static final String SMALL_FILE_PARAMETER_NAME = "fileName";

    private static final String STANDARD_STORAGE_CLASS_NAME = "standardStorageClassName";

    private static final String FILE = "File ";

    @Autowired
    private LockService lockService;

    @Autowired
    private GlacierArchiveService glacierArchiveService;

    /**
     * Parallel thread executor service for store actions.
     * As store actions can use a lot of memory (depending on configuration) it has his own pool.
     */
    ExecutorService storeExecutorService;

    /**
     * Parallel thread executor service for all actions except store.
     */
    ExecutorService executorService;

    @PluginParameter(name = GLACIER_WORKSPACE_PATH,
                     description = "Local workspace for archive building and restoring cache",
                     label = "Workspace path")
    private String rawWorkspacePath;

    @PluginParameter(name = GLACIER_SMALL_FILE_MAX_SIZE,
                     description = "Threshold under which files are categorized as small files, in bytes.",
                     label = "Small file max size in bytes.",
                     defaultValue = "1048576")
    private int smallFileMaxSize;

    @PluginParameter(name = GLACIER_SMALL_FILE_ARCHIVE_MAX_SIZE,
                     description = "Threshold beyond which a small files archive is considered as full and closed, in"
                                   + " bytes.",
                     label = "Archive max size in bytes.",
                     defaultValue = "10485760")
    private int archiveMaxSize;

    @PluginParameter(name = GLACIER_SMALL_FILE_ARCHIVE_DURATION_IN_HOURS,
                     description = "Determines when the current small files archive is considered too old and should "
                                   + "be closed, in hours",
                     label = "Archive max age in hours",
                     defaultValue = "24")
    private int archiveMaxAge;

    @PluginParameter(name = GLACIER_PARALLEL_DELETE_AND_RESTORE_TASK_NUMBER,
                     description = "Number of parallel tasks for file restoration and deletion.",
                     label = "Number of file to restore or to delete in parallel",
                     defaultValue = "20")
    private int parallelTaskNumber;

    @PluginParameter(name = GLACIER_PARALLEL_STORE_TASK_NUMBER,
                     description = "Number of parallel files to store. A high number of parallel files needs to raise"
                                   + " microservice available memory resource.",
                     label = "Number of files to store in parallel",
                     defaultValue = "5")
    private int storeParallelTaskNumber;

    @PluginParameter(name = GLACIER_ARCHIVE_CACHE_FILE_LIFETIME_IN_HOURS,
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
     * Timeout to acquire a new lock to run a {@link CleanDirectoryTask}
     */
    @Value("${regards.glacier.clean.cache.task.lock.acquire.timeout:60}")
    private int cleanCacheTaskLockAcquireTimeout = 60;

    /**
     * Scheduler periodic time to run {@link CleanDirectoryTask}
     */
    @Value("${regards.glacier.scheduled.cache.clean.minutes:60}")
    private int scheduledCacheClean = 60;

    private String workspacePath;

    private String storageName;

    private ThreadPoolTaskScheduler scheduler;

    @PluginInit(hasConfiguration = true)
    public void initGlacier(PluginConfigurationDto conf) {
        if (runtimeTenantResolver != null) {
            workspacePath = Path.of(rawWorkspacePath, runtimeTenantResolver.getTenant()).toString();
        } else {
            // In case the runtimeTenantResolver doesn't exist, use the raw workspace
            // This will happen in tests
            workspacePath = rawWorkspacePath;
        }
        BasicThreadFactory factory = new BasicThreadFactory.Builder().namingPattern("s3-glacier-threadpool-thread-%d")
                                                                     .priority(Thread.MAX_PRIORITY)
                                                                     .build();
        storeExecutorService = Executors.newFixedThreadPool(storeParallelTaskNumber, factory);
        executorService = Executors.newFixedThreadPool(parallelTaskNumber, factory);
        storageName = conf.getBusinessId();
        initWorkspaceCleanScheduler();
    }

    private void initWorkspaceCleanScheduler() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        Trigger trigger = new PeriodicTrigger(scheduledCacheClean, TimeUnit.MINUTES);

        Runnable cleanTask = () -> {
            LOGGER.info("Starting scheduled workspace cleaning");
            cleanArchiveCache();
            LOGGER.info("End of scheduled workspace cleaning");
        };
        scheduler.schedule(cleanTask, trigger);
    }

    @PluginDestroy
    public void onDestroy() {
        LOGGER.warn("Shutdown of the plugin, this may cause errors as the currently running tasks will be "
                    + "terminated");
        scheduler.shutdown();
        storeExecutorService.shutdownNow();
        executorService.shutdown();
    }

    @Override
    public void store(FileStorageWorkingSubset workingSet, IStorageProgressManager progressManager) {
        LOGGER.info("Glacier store requests received");
        try {
            String tenant = runtimeTenantResolver.getTenant();
            List<Future<LockServiceResponse<Void>>> taskResults = storeExecutorService.invokeAll(workingSet.getFileReferenceRequests()
                                                                                                           .stream()
                                                                                                           .map(request -> doStoreTask(
                                                                                                               request,
                                                                                                               progressManager,
                                                                                                               tenant))
                                                                                                           .toList());

            // Wait for all tasks to complete
            for (Future<LockServiceResponse<Void>> future : taskResults) {
                future.get();
            }

        } catch (InterruptedException e) {
            LOGGER.error("Storage process interrupted");
        } catch (ExecutionException e) {
            LOGGER.error("Error during storage process", e);
        }
        LOGGER.info("End handling store requests");
    }

    public Callable<LockServiceResponse<Void>> doStoreTask(FileStorageRequestAggregation request,
                                                           IStorageProgressManager progressManager,
                                                           String tenant) {

        return () -> {
            LOGGER.debug(TENANT_LOG, Thread.currentThread().getName(), tenant);
            runtimeTenantResolver.forceTenant(tenant);
            try {
                request.getMetaInfo().setFileSize(getFileSize(new URL(request.getOriginUrl())));
                if (request.getMetaInfo().getFileSize() > smallFileMaxSize) {
                    LOGGER.info("Storing file on S3 using standard upload");
                    handleStoreRequest(request, progressManager);
                    LOGGER.info("End Storing file on S3");
                } else {
                    StoreSmallFileTaskConfiguration configuration = new StoreSmallFileTaskConfiguration(workspacePath,
                                                                                                        s3StorageSettings.getStorages(),
                                                                                                        storageConfiguration,
                                                                                                        archiveMaxSize,
                                                                                                        rootPath);
                    LOGGER.debug("In thread {}, running StoreSmallFileTask from Glacier with lock",
                                 Thread.currentThread().getName());
                    /**
                     * Lock the node directory (with LOCK_STORE) where the file will be stored to prevent other storage 
                     * jobs to handle the same node.
                     * @see S3Glacier#doStoreTask
                     **/

                    // the same node (there is one _current building archive per node)
                    lockService.runWithLock(S3GlacierUtils.getLockName(LockTypeEnum.LOCK_STORE,
                                                                       rootPath,
                                                                       workspacePath,
                                                                       request.getStorageSubDirectory()),
                                            new StoreSmallFileTask(configuration, request, progressManager));
                }
            } catch (MalformedURLException e) {
                LOGGER.error(e.getMessage(), e);
                progressManager.storageFailed(request, String.format("Invalid source url %s", request.getOriginUrl()));
            } catch (InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
                progressManager.storageFailed(request, "The storage task was interrupted before completion.");
                Thread.currentThread().interrupt();
            }
            return null;
        };
    }

    @Override
    public void retrieve(FileRestorationWorkingSubset workingSubset, IRestorationProgressManager progressManager) {
        LOGGER.info("Glacier retrieve requests received");
        String tenant = runtimeTenantResolver.getTenant();
        try {
            List<Future<LockServiceResponse<Void>>> taskResults = executorService.invokeAll(workingSubset.getFileRestorationRequests()
                                                                                                         .stream()
                                                                                                         .map(request -> doRetrieveTask(
                                                                                                             request,
                                                                                                             progressManager,
                                                                                                             tenant))
                                                                                                         .toList());
            // Wait for all tasks to complete
            for (Future<LockServiceResponse<Void>> future : taskResults) {
                future.get();
            }

        } catch (InterruptedException e) {
            LOGGER.error("Retrieval process interrupted");
        } catch (ExecutionException e) {
            LOGGER.error("Error during retrieval process", e);
        }
        LOGGER.info("Handling of retrieve requests ended");
    }

    public Callable<LockServiceResponse<Void>> doRetrieveTask(FileCacheRequest request,
                                                              IRestorationProgressManager progressManager,
                                                              String tenant) {

        return () -> {
            LOGGER.debug(TENANT_LOG, Thread.currentThread().getName(), tenant);
            runtimeTenantResolver.forceTenant(tenant);
            try {
                Path fileRelativePath = getServerPath().relativize(Path.of(request.getFileReference()
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
                    LOGGER.debug("In thread {}, running RetrieveLocalSmallFileTask from Glacier with lock",
                                 Thread.currentThread().getName());
                    /** Lock the small file archive (with LOCK_STORE) to prevent delete or sendArchiveJob to
                     * alter archive during copy to cache (LOCK_STORE is used by both delete and store jobs)
                     * @see S3Glacier#doDeleteTask and {@link S3Glacier#doStoreTask}
                     **/
                    lockService.runWithLock(S3GlacierUtils.getLockName(LockTypeEnum.LOCK_STORE,
                                                                       null,
                                                                       workspacePath,
                                                                       fileRelativePath.getParent().toString()), task);
                    return null;
                }

                String lockName = S3GlacierUtils.getLockName(LockTypeEnum.LOCK_RESTORE,
                                                             null,
                                                             workspacePath,
                                                             fileRelativePath.toString());

                RetrieveCacheFileTaskConfiguration configuration = new RetrieveCacheFileTaskConfiguration(
                    fileRelativePath,
                    getCachePath(),
                    storageConfiguration,
                    s3AccessTimeout,
                    rootPath,
                    isSmallFile,
                    getS3Client(),
                    lockName,
                    Instant.now(),
                    renewMaxIterationWaitingPeriodInS,
                    renewCallDurationInMs,
                    standardStorageClassName,
                    lockService);
                RetrieveCacheFileTask task = new RetrieveCacheFileTask(configuration, request, progressManager);

                LOGGER.debug("In thread {}, running RetrieveCacheFileTask from Glacier with lock",
                             Thread.currentThread().getName());

                lockService.runWithLock(lockName, task);

            } catch (InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
                progressManager.restoreFailed(request, "The restoration task was interrupted before completion.");
            }
            return null;
        };
    }

    private String getArchiveBuildingWorkspacePath() {
        return workspacePath + File.separator + ZIP_DIR;
    }

    private String getCachePath() {
        return workspacePath + File.separator + TMP_DIR;
    }

    @Override
    public void delete(FileDeletionWorkingSubset workingSet, IDeletionProgressManager progressManager) {
        LOGGER.info("Glacier delete requests received");
        try {
            String tenant = runtimeTenantResolver.getTenant();
            List<Future<LockServiceResponse<Void>>> taskResults = executorService.invokeAll(workingSet.getFileDeletionRequests()
                                                                                                      .stream()
                                                                                                      .map(request -> doDeleteTask(
                                                                                                          request,
                                                                                                          progressManager,
                                                                                                          tenant))
                                                                                                      .toList());
            // Wait for all tasks to complete
            for (Future<LockServiceResponse<Void>> future : taskResults) {
                future.get();
            }
        } catch (InterruptedException e) {
            LOGGER.error("Deletion process interrupted");
        } catch (ExecutionException e) {
            LOGGER.error("Error during deletion process", e);
        }
        LOGGER.info("Handling of delete requests ended");
    }

    public Callable<LockServiceResponse<Void>> doDeleteTask(FileDeletionRequest request,
                                                            IDeletionProgressManager progressManager,
                                                            String tenant) {
        return () -> {
            LOGGER.debug(TENANT_LOG, Thread.currentThread().getName(), tenant);
            runtimeTenantResolver.forceTenant(tenant);
            if (!isASmallFileUrl(request.getFileReference().getLocation().getUrl())) {
                handleDeleteRequest(request, progressManager);
            } else {
                handleDeleteSmallFileRequest(request, progressManager);
            }
            return null;
        };
    }

    private void handleDeleteSmallFileRequest(FileDeletionRequest request, IDeletionProgressManager progressManager) {
        try {
            Path serverRootPath = getServerPath();
            Path node = Path.of(request.getFileReference().getLocation().getUrl()).getParent();
            Path fileRelativePath = serverRootPath.relativize(Path.of(request.getFileReference()
                                                                             .getLocation()
                                                                             .getUrl()));

            if (request.getFileReference().getLocation().isPendingActionRemaining()) {
                // The small file is still in the local building directory, it is not necessary to restore it
                DeleteLocalSmallFileTaskConfiguration configuration = new DeleteLocalSmallFileTaskConfiguration(
                    fileRelativePath,
                    getArchiveBuildingWorkspacePath(),
                    storageName,
                    storageConfiguration,
                    getS3Client(),
                    glacierArchiveService);
                DeleteLocalSmallFileTask task = new DeleteLocalSmallFileTask(configuration, request, progressManager);
                LOGGER.debug("In thread {}, running DeleteLocalSmallFileTask from Glacier with lock",
                             Thread.currentThread().getName());
                /** Lock the building archive (with STORE LOCK) to prevent Pending Action Job to close and send the
                 * archive.
                 * @see {@link S3Glacier#doSubmitReadyArchive}
                 **/
                lockService.runWithLock(S3GlacierUtils.getLockName(LockTypeEnum.LOCK_STORE,
                                                                   null,
                                                                   workspacePath,
                                                                   fileRelativePath.getParent() != null ?
                                                                       fileRelativePath.getParent().toString() :
                                                                       ""), task);

            } else {
                /**
                 * Lock the archive (with RESTORE LOCK) to prevent other deletion jobs or restore jobs to retrieve
                 * the same archive
                 * @see {@link S3Glacier#doDeleteTask} and {@link S3Glacier#doRetrieveTask}
                 */
                String lockName = S3GlacierUtils.getLockName(LockTypeEnum.LOCK_RESTORE,
                                                             null,
                                                             workspacePath,
                                                             fileRelativePath.toString());

                RestoreAndDeleteSmallFileTaskConfiguration configuration = new RestoreAndDeleteSmallFileTaskConfiguration(
                    fileRelativePath,
                    getCachePath(),
                    rootPath,
                    getArchiveBuildingWorkspacePath(),
                    node,
                    storageName,
                    storageConfiguration,
                    getS3Client(),
                    s3AccessTimeout,
                    lockName,
                    Instant.now(),
                    renewMaxIterationWaitingPeriodInS,
                    renewCallDurationInMs,
                    standardStorageClassName,
                    lockService,
                    glacierArchiveService);
                RestoreAndDeleteSmallFileTask task = new RestoreAndDeleteSmallFileTask(configuration,
                                                                                       request,
                                                                                       progressManager);
                LOGGER.debug("In thread {}, running RestoreAndDeleteSmallFileTask from S3Glacier with lock",
                             Thread.currentThread().getName());
                lockService.runWithLock(lockName, task);
            }
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
            progressManager.deletionFailed(request, "The deletion task was interrupted before completion.");
        }
    }

    @Override
    public void runPeriodicAction(IPeriodicActionProgressManager progressManager) {
        LOGGER.info("Glacier periodic actions started");
        submitReadyArchives(progressManager);
        cleanArchiveCache();
        LOGGER.info("Glacier periodic actions ended");
    }

    private void submitReadyArchives(IPeriodicActionProgressManager progressManager) {
        Path zipWorkspacePath = Paths.get(workspacePath, S3Glacier.ZIP_DIR);
        String tenant = runtimeTenantResolver.getTenant();
        if (!Files.exists(zipWorkspacePath)) {
            return;
        }
        SubmitReadyArchiveTaskConfiguration submitReadyArchiveTaskConfiguration = new SubmitReadyArchiveTaskConfiguration(
            zipWorkspacePath,
            workspacePath,
            archiveMaxAge,
            rootPath,
            storageName,
            storageConfiguration,
            multipartThresholdMb,
            glacierArchiveService,
            progressManager,
            tenant,
            getS3Client());

        try (Stream<Path> dirList = Files.walk(zipWorkspacePath)) {
            // Directory that will be stored are located in /<WORKSPACE>/<ZIP_DIR>/<NODE>/, they can be symbolic link
            // It is important to differentiate between actual directories and symbolic link to use locks correctly
            Map<Boolean, List<Path>> dirToProcessList = dirList.filter(dir -> dir.getFileName()
                                                                                 .toString()
                                                                                 .startsWith(S3Glacier.BUILDING_DIRECTORY_PREFIX))
                                                               .collect(Collectors.groupingBy(Files::isSymbolicLink));
            List<Callable<Boolean>> processes = dirToProcessList.entrySet()
                                                                .stream()
                                                                .flatMap(entry -> entry.getValue()
                                                                                       .stream()
                                                                                       .map(dir -> doSubmitReadyArchive(
                                                                                           dir,
                                                                                           progressManager,
                                                                                           tenant,
                                                                                           entry.getKey())))
                                                                .toList();
            List<Future<Boolean>> res = executorService.invokeAll(processes);
            boolean success = res.stream().allMatch(futureRes -> {
                try {
                    return futureRes.get();
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.error(e.getMessage(), e);
                    return false;
                }
            });
            if (success) {
                progressManager.allPendingActionSucceed(storageName);
            }

        } catch (IOException e) {
            LOGGER.error("Error while attempting to access small files archives workspace during periodic "
                         + "actions, no archives will be submitted to S3 and no request will be updated");
            LOGGER.error(e.getMessage(), e);
        } catch (InterruptedException e) {
            LOGGER.error("Submit archives process interrupted");
        }
    }

    /**
     * Submit to the S3 an archive containing the file present in the given folder.
     * An archive is considered ready if one of the following is true for its directory :
     * <ul>
     * <li>The directory is full (size > archiveMaxSize), in which case the directory doesn't have the  _current
     * suffix</li>
     * <li>The directory is already present on the server and it has been modified (one of the file has been
     * deleted), in which case the directory doesn't have the _current suffix and its a symbolic link to the actual
     * directory in the /tmp workspace</li>
     * <li>The directory is the _current directory but is is old enough to be sent even if not full (age >
     *     archiveMaxAge, in which case it has the _current suffix and will be renamed to remove it</li>
     * </ul>
     */
    public Callable<Boolean> doSubmitReadyArchive(Path dirPath,
                                                  IPeriodicActionProgressManager progressManager,
                                                  String tenant,
                                                  boolean isSymLink) {
        return () -> {
            LOGGER.debug(TENANT_LOG, Thread.currentThread().getName(), tenant);
            runtimeTenantResolver.forceTenant(tenant);
            SubmitReadyArchiveTaskConfiguration submitReadyArchiveTaskConfiguration = new SubmitReadyArchiveTaskConfiguration(
                dirPath,
                workspacePath,
                archiveMaxAge,
                rootPath,
                storageName,
                storageConfiguration,
                multipartThresholdMb,
                glacierArchiveService,
                progressManager,
                tenant,
                getS3Client());

            if (isSymLink) {
                SubmitUpdatedArchiveTask task = new SubmitUpdatedArchiveTask(submitReadyArchiveTaskConfiguration,
                                                                             progressManager);

                /** Lock the building archive (with STORE LOCK) to prevent storage and deletion jobs to alter the archive
                 * @see S3Glacier#doStoreTask} and {@link S3Glacier#doDeleteTask}
                 */
                LockServiceResponse<Boolean> res = lockService.runWithLock(S3GlacierUtils.getLockName(LockTypeEnum.LOCK_RESTORE,
                                                                                                      null,
                                                                                                      workspacePath,
                                                                                                      dirPath.toString()),
                                                                           task);
                return res.isExecuted() && res.getResponse();
            } else {
                SubmitReadyArchiveTask task = new SubmitReadyArchiveTask(submitReadyArchiveTaskConfiguration,
                                                                         progressManager);
                /** Lock the restored archive (with RESTORE LOCK) to prevent deletion jobs to alter the
                 * archive while it is uploaded
                 * @see {@link S3Glacier#doDeleteTask}
                 */
                LockServiceResponse<Boolean> res = lockService.runWithLock(S3GlacierUtils.getLockName(LockTypeEnum.LOCK_STORE,
                                                                                                      null,
                                                                                                      workspacePath,
                                                                                                      dirPath.toString()),
                                                                           task);
                return res.isExecuted() && res.getResponse();
            }
        };
    }

    /**
     * Clean the cache containing the restored archive. This will remove archives and their extracted content if they
     * are older than the age defined in plugin parameter (age > archiveCacheLifetime).
     */
    private void cleanArchiveCache() {
        Path cacheWorkspacePath = Paths.get(workspacePath, S3Glacier.TMP_DIR);
        String tenant = runtimeTenantResolver.getTenant();
        if (!Files.exists(cacheWorkspacePath)) {
            return;
        }
        Instant oldestAgeToKeep = OffsetDateTime.now().minusHours(archiveCacheLifetime).toInstant();
        List<Path> directoriesWithFiles = new ArrayList<>();
        getDirectoriesWithFilesToDelete(directoriesWithFiles,
                                        cacheWorkspacePath,
                                        oldestAgeToKeep,
                                        cacheWorkspacePath,
                                        Paths.get(workspacePath, S3Glacier.ZIP_DIR));
        try {
            executorService.invokeAll(directoriesWithFiles.stream()
                                                          .map(dirToProcess -> doCleanDirectory(cacheWorkspacePath,
                                                                                                dirToProcess,
                                                                                                oldestAgeToKeep,
                                                                                                tenant))
                                                          .toList());
        } catch (InterruptedException e) {
            LOGGER.error("Clean archive cache process interrupted");
        }
    }

    public Callable<LockServiceResponse<Void>> doCleanDirectory(Path cacheWorkspacePath,
                                                                Path dirPath,
                                                                Instant oldestAgeToKeep,
                                                                String tenant) {
        return () -> {
            LOGGER.debug(TENANT_LOG, Thread.currentThread().getName(), tenant);
            runtimeTenantResolver.forceTenant(tenant);
            CleanDirectoryTaskConfiguration cleanDirectoryTaskConfiguration = new CleanDirectoryTaskConfiguration(
                dirPath,
                oldestAgeToKeep);
            CleanDirectoryTask task = new CleanDirectoryTask(cleanDirectoryTaskConfiguration);
            String dirToClean = cacheWorkspacePath.relativize(dirPath).toString();
            /**
             * Lock the archive (with RESTORE LOCK) to prevent retrieve jobs or delete jobs to access it while
             * it's being deleted.
             * @see S3Glacier#doRetrieveTask and {@link S3Glacier#doDeleteTask}
             */
            boolean processRun = lockService.tryRunWithLock(S3GlacierUtils.getLockName(LockTypeEnum.LOCK_RESTORE,
                                                                                       null,
                                                                                       workspacePath,
                                                                                       dirPath.toString()),
                                                            task,
                                                            cleanCacheTaskLockAcquireTimeout,
                                                            TimeUnit.SECONDS).isExecuted();
            if (!processRun) {
                LOGGER.warn("Unable to acquire lock on {} to clean the directory, this should mean that a restoration"
                            + " process on the directory is currently running. The directory won't be cleaned before "
                            + "the next scheduled cleaning", dirToClean);
            }
            return null;
        };
    }

    /**
     * A directory need to be deleted if it fulfills the following :
     * <ul>
     *     <li>It has the rs_zip_ prefix</li>
     *     <li>It is NOT used to update an archive following a deletion, this means there is no symbolic link in
     *     the building workspace to this directory</li>
     *     <li>It is older than the oldest age to keep</li>
     * </ul>
     */
    private void getDirectoriesWithFilesToDelete(List<Path> directoriesWithFiles,
                                                 Path directoryPath,
                                                 Instant oldestAgeToKeep,
                                                 Path cachePath,
                                                 Path buildingPath) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directoryPath)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    // Check directory with conditions :
                    // - directory is a building directory like rs_zip_*
                    // - directory contains at least one file to expired or directory is empty
                    // - directory is not associated to a current building archive so no symbolic link exists in
                    // building directory.
                    if (path.getFileName().toString().startsWith(BUILDING_DIRECTORY_PREFIX) && (hasFilesTooOld(path,
                                                                                                               oldestAgeToKeep)
                                                                                                || FileUtils.isEmptyDirectory(
                        path.toFile())) && !hasSymLink(path, cachePath, buildingPath)) {
                        directoriesWithFiles.add(path);
                    } else {
                        // Else handle recursive subdirectories
                        getDirectoriesWithFilesToDelete(directoriesWithFiles,
                                                        path,
                                                        oldestAgeToKeep,
                                                        cachePath,
                                                        buildingPath);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error while getting directories to clean", e);
        }
    }

    private boolean hasSymLink(Path path, Path cachePath, Path buildingPath) {
        Path pathInBuildingWorkspace = buildingPath.resolve(cachePath.relativize(path));
        return Files.isSymbolicLink(pathInBuildingWorkspace);
    }

    private boolean hasFilesTooOld(Path directoryPath, Instant oldestAgeToKeep) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directoryPath)) {
            for (Path path : stream) {
                BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
                if (Files.isRegularFile(path) && attr.lastModifiedTime().toInstant().isBefore(oldestAgeToKeep)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean hasPeriodicAction() {
        return true;
    }

    @Override
    public void runCheckPendingAction(IPeriodicActionProgressManager progressManager,
                                      Set<FileReference> filesWithPendingActions) {
        LOGGER.info("Glacier periodic pending actions started");
        String tenant = runtimeTenantResolver.getTenant();
        List<Future<LockServiceResponse<Void>>> taskResults = null;
        try {
            taskResults = executorService.invokeAll(filesWithPendingActions.stream()
                                                                           .map(ref -> doCheckPendingAction(ref.getLocation()
                                                                                                               .getUrl(),
                                                                                                            progressManager,
                                                                                                            tenant))
                                                                           .toList());
            // Wait for all tasks to complete
            for (Future<LockServiceResponse<Void>> future : taskResults) {
                future.get();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        LOGGER.info("Glacier periodic pending actions ended");
    }

    public Callable<LockServiceResponse<Void>> doCheckPendingAction(String url,
                                                                    IPeriodicActionProgressManager progressManager,
                                                                    String tenant) {
        return () -> {
            LOGGER.debug(TENANT_LOG, Thread.currentThread().getName(), tenant);
            runtimeTenantResolver.forceTenant(tenant);
            S3GlacierUtils.S3GlacierUrl s3GlacierUrl = S3GlacierUtils.dispatchS3Url(url);
            if (!s3GlacierUrl.isSmallFileUrl()) {
                LOGGER.error("The URL {} is not a small file url and has a pending action which is not supported by "
                             + "the S3Glacier", url);
                return null;
            }
            Path archiveRelativePath = getServerPath().relativize(Path.of(s3GlacierUrl.archiveFilePath()));
            CheckPendingActionTaskConfiguration checkPendingActionTaskConfiguration = new CheckPendingActionTaskConfiguration(
                url,
                archiveRelativePath,
                s3GlacierUrl.smallFileNameInArchive().get(),
                workspacePath,
                storageConfiguration);
            CheckPendingActionTask task = new CheckPendingActionTask(checkPendingActionTaskConfiguration,
                                                                     progressManager);
            /**
             * Lock the archive (with RESTORE LOCK) to prevent Deletion Job from deleting the file we are checking
             * the validity of pending actions.
             *
             */
            lockService.runWithLock(S3GlacierUtils.getLockName(LockTypeEnum.LOCK_RESTORE,
                                                               null,
                                                               workspacePath,
                                                               archiveRelativePath.toString()), task);
            return null;
        };

    }

    @Override
    public AbstractStoragePluginConfigurationDto createWorkerStoreConfiguration() {
        return new S3GlacierStorageConfigurationDto(allowPhysicalDeletion,
                                                    multipartThresholdMb,
                                                    nbParallelPartsUpload,
                                                    rawWorkspacePath,
                                                    smallFileMaxSize,
                                                    archiveMaxSize,
                                                    archiveMaxAge,
                                                    parallelTaskNumber,
                                                    archiveCacheLifetime,
                                                    s3AccessTimeout,
                                                    standardStorageClassName);
    }

    private Path getServerPath() {
        return Paths.get(endpoint, bucket);
    }

    private boolean isASmallFileUrl(String url) {
        try {
            return S3GlacierUtils.dispatchS3Url(url).isSmallFileUrl();
        } catch (URISyntaxException e) {
            LOGGER.error(e.getMessage(), e);
            return false;
        }
    }

    public InputStream download(FileReference fileReference)
        throws NearlineFileNotAvailableException, NearlineDownloadException {
        InputStream inputStreamFromS3Source;
        String entryKey = getEntryKey(fileReference);
        checkFileAvailibity(entryKey, fileReference);
        try {
            inputStreamFromS3Source = DownloadUtils.getInputStreamFromS3Source(entryKey,
                                                                               storageConfiguration,
                                                                               new StorageCommandID(String.format("%d",
                                                                                                                  fileReference.getId()),
                                                                                                    UUID.randomUUID()));
        } catch (FileNotFoundException e) {
            LOGGER.error(FILE + fileReference.getMetaInfo().getFileName() + " cannot be download : " + e.getMessage());
            throw new NearlineDownloadException(FILE
                                                + fileReference.getMetaInfo().getFileName()
                                                + " cannot be download : "
                                                + e.getMessage());
        }
        return inputStreamFromS3Source;
    }

    /**
     * Ask to S3 client if file is available to download, and throw if not
     */
    private void checkFileAvailibity(String entryKey, FileReference fileReference)
        throws NearlineFileNotAvailableException {
        String fileName = fileReference.getMetaInfo().getFileName();
        GlacierFileStatus fileAvailable = getS3Client().isFileAvailable(storageConfiguration,
                                                                        entryKey,
                                                                        standardStorageClassName).block();
        String errorMessage;
        if (fileAvailable == null) {
            errorMessage = FILE + fileName + " is not available";
        } else {
            errorMessage = switch (fileAvailable) {
                case EXPIRED -> FILE + fileName + " is expired";
                case RESTORE_PENDING -> "Restoration of file " + fileName + " is pending";
                case NOT_AVAILABLE -> FILE + fileName + " is not available";
                default -> null;
            };
        }
        if (errorMessage != null) {
            LOGGER.warn(errorMessage);
            throw new NearlineFileNotAvailableException(errorMessage);
        }
    }
}
