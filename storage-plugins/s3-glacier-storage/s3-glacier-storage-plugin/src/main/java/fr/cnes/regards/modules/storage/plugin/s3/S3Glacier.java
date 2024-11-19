package fr.cnes.regards.modules.storage.plugin.s3;

import fr.cnes.regards.framework.jpa.multitenant.lock.LockService;
import fr.cnes.regards.framework.jpa.multitenant.lock.LockServiceResponse;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginDestroy;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.modules.plugins.dto.PluginConfigurationDto;
import fr.cnes.regards.framework.s3.client.S3HighLevelReactiveClient;
import fr.cnes.regards.framework.s3.domain.GlacierFileStatus;
import fr.cnes.regards.framework.s3.domain.StorageCommandID;
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.modules.fileaccess.dto.AbstractStoragePluginConfigurationDto;
import fr.cnes.regards.modules.fileaccess.dto.FileReferenceWithoutOwnersDto;
import fr.cnes.regards.modules.fileaccess.dto.availability.NearlineFileStatusDto;
import fr.cnes.regards.modules.fileaccess.dto.output.worker.FileNamingStrategy;
import fr.cnes.regards.modules.fileaccess.dto.request.FileStorageRequestAggregationDto;
import fr.cnes.regards.modules.fileaccess.plugin.domain.*;
import fr.cnes.regards.modules.fileaccess.plugin.dto.FileCacheRequestDto;
import fr.cnes.regards.modules.fileaccess.plugin.dto.FileDeletionRequestDto;
import fr.cnes.regards.modules.storage.plugin.s3.configuration.*;
import fr.cnes.regards.modules.storage.plugin.s3.dto.S3GlacierStorageConfigurationDto;
import fr.cnes.regards.modules.storage.plugin.s3.task.*;
import fr.cnes.regards.modules.storage.plugin.s3.utils.LockTypeEnum;
import fr.cnes.regards.modules.storage.plugin.s3.utils.S3GlacierUtils;
import fr.cnes.regards.modules.storage.s3.common.AbstractS3Storage;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public static final String USE_EXTERNAL_CACHE_NAME = "useExternalCacheName";

    @Autowired
    private LockService lockService;

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

    private ThreadPoolTaskScheduler scheduler;

    private BasicThreadFactory factory;

    /**
     * S3 client used for {@link this#checkAvailability} checkAvailability} method only.
     */
    private S3HighLevelReactiveClient checkAvailabilityClient;

    @PluginInit(hasConfiguration = true)
    public void initGlacier(PluginConfigurationDto conf) {
        if (runtimeTenantResolver != null) {
            workspacePath = Path.of(rawWorkspacePath, runtimeTenantResolver.getTenant()).toString();
        } else {
            // In case the runtimeTenantResolver doesn't exist, use the raw workspace
            // This will happen in tests
            workspacePath = rawWorkspacePath;
        }
        factory = new BasicThreadFactory.Builder().namingPattern("s3-glacier-threadpool-thread-%d")
                                                  .priority(Thread.MAX_PRIORITY)
                                                  .build();
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
    }

    @Override
    public boolean isInternalCache() {
        return !useExternalCache;
    }

    @Override
    public void store(FileStorageWorkingSubset workingSet, IStorageProgressManager progressManager) {
        LOGGER.info("Glacier store requests received");
        ExecutorService storeExecutorService = null;
        try (S3HighLevelReactiveClient client = createS3Client()) {
            storeExecutorService = Executors.newFixedThreadPool(storeParallelTaskNumber, factory);
            String tenant = runtimeTenantResolver.getTenant();
            List<Future<LockServiceResponse<Void>>> taskResults = storeExecutorService.invokeAll(workingSet.getFileReferenceRequests()
                                                                                                           .stream()
                                                                                                           .map(request -> doStoreTask(
                                                                                                               request,
                                                                                                               client,
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
        } finally {
            if (storeExecutorService != null) {
                storeExecutorService.shutdownNow();
            }
        }
        LOGGER.info("End handling store requests");
    }

    public Callable<LockServiceResponse<Void>> doStoreTask(FileStorageRequestAggregationDto request,
                                                           S3HighLevelReactiveClient client,
                                                           IStorageProgressManager progressManager,
                                                           String tenant) {

        return () -> {
            long start = Instant.now().toEpochMilli();
            LOGGER.debug(TENANT_LOG, Thread.currentThread().getName(), tenant);
            runtimeTenantResolver.forceTenant(tenant);
            try {
                request.getMetaInfo().setFileSize(getFileSize(new URL(request.getOriginUrl())));
                if (request.getMetaInfo().getFileSize() > smallFileMaxSize) {
                    LOGGER.info("Storing file on S3 using standard upload");
                    handleStoreRequest(request, client, progressManager);
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
                                                                       request.getSubDirectory()),
                                            new StoreSmallFileTask(configuration, request, progressManager));
                    LOGGER.info("[S3 Monitoring] Storage task for {} took {} ms",
                                request.getOriginUrl(),
                                Instant.now().toEpochMilli() - start);
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
        ExecutorService executorService = null;
        try (S3HighLevelReactiveClient client = createS3Client()) {
            executorService = Executors.newFixedThreadPool(parallelTaskNumber, factory);
            List<Future<LockServiceResponse<Void>>> taskResults = executorService.invokeAll(workingSubset.getFileRestorationRequests()
                                                                                                         .stream()
                                                                                                         .map(request -> doRetrieveTask(
                                                                                                             request,
                                                                                                             client,
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
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
        }
        LOGGER.info("Handling of retrieve requests ended");
    }

    public Callable<LockServiceResponse<Void>> doRetrieveTask(FileCacheRequestDto fileCacheRequest,
                                                              S3HighLevelReactiveClient client,
                                                              IRestorationProgressManager progressManager,
                                                              String tenant) {

        return () -> {
            long start = Instant.now().toEpochMilli();
            LOGGER.debug(TENANT_LOG, Thread.currentThread().getName(), tenant);
            runtimeTenantResolver.forceTenant(tenant);
            try {
                Path fileRelativePath = getServerPath().relativize(Path.of(fileCacheRequest.getFileReference()
                                                                                           .getLocation()
                                                                                           .getUrl()));
                boolean isSmallFile = isASmallFileUrl(fileCacheRequest.getFileReference().getLocation().getUrl());
                if (isSmallFile && fileCacheRequest.getFileReference().getLocation().isPendingActionRemaining()) {
                    // The file has not been saved to S3 yet, it's still in the local archive building workspace
                    RetrieveLocalSmallFileTaskConfiguration configuration = new RetrieveLocalSmallFileTaskConfiguration(
                        fileRelativePath,
                        getArchiveBuildingWorkspacePath());
                    RetrieveLocalSmallFileTask task = new RetrieveLocalSmallFileTask(configuration,
                                                                                     fileCacheRequest,
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
                    LOGGER.info("[S3 Monitoring] Retrieval task for {} took {} ms",
                                fileCacheRequest.getFileReference().getLocation().getUrl(),
                                Instant.now().toEpochMilli() - start);
                    return null;
                }

                String lockName = S3GlacierUtils.getLockName(LockTypeEnum.LOCK_RESTORE,
                                                             null,
                                                             workspacePath,
                                                             fileRelativePath.toString());

                RetrieveCacheFileTask task = new RetrieveCacheFileTask(createRetrieveCacheFileTaskConfiguration(
                    fileRelativePath,
                    isSmallFile,
                    client,
                    lockName), fileCacheRequest, progressManager);

                LOGGER.debug("In thread {}, running RetrieveCacheFileTask from Glacier with lock",
                             Thread.currentThread().getName());

                lockService.runWithLock(lockName, task);

            } catch (InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
                progressManager.restoreFailed(fileCacheRequest,
                                              "The restoration task was interrupted before completion.");
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
        LOGGER.info("S3Glacier delete received requests");
        ExecutorService executorService = null;
        try (S3HighLevelReactiveClient client = createS3Client()) {
            executorService = Executors.newFixedThreadPool(parallelTaskNumber, factory);
            String tenant = runtimeTenantResolver.getTenant();
            List<Future<LockServiceResponse<Void>>> taskResults = executorService.invokeAll(workingSet.getFileDeletionRequests()
                                                                                                      .stream()
                                                                                                      .map(request -> doDeleteTask(
                                                                                                          request,
                                                                                                          client,
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
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
        }
        LOGGER.info("Handling of delete requests ended");
    }

    public Callable<LockServiceResponse<Void>> doDeleteTask(FileDeletionRequestDto request,
                                                            S3HighLevelReactiveClient client,
                                                            IDeletionProgressManager progressManager,
                                                            String tenant) {
        return () -> {
            long start = Instant.now().toEpochMilli();
            LOGGER.debug(TENANT_LOG, Thread.currentThread().getName(), tenant);
            runtimeTenantResolver.forceTenant(tenant);
            if (!isASmallFileUrl(request.getFileReference().getLocation().getUrl())) {
                handleDeleteRequest(request, client, progressManager);
            } else {
                handleDeleteSmallFileRequest(request, client, progressManager);
            }
            LOGGER.info("[S3 Monitoring] Deletion task for {} took {} ms",
                        request.getFileReference().getLocation().getUrl(),
                        Instant.now().toEpochMilli() - start);
            return null;
        };
    }

    private void handleDeleteSmallFileRequest(FileDeletionRequestDto request,
                                              S3HighLevelReactiveClient client,
                                              IDeletionProgressManager progressManager) {
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
                    client);
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
                    client,
                    s3AccessTimeout,
                    lockName,
                    Instant.now(),
                    renewMaxIterationWaitingPeriodInS,
                    renewCallDurationInMs,
                    standardStorageClassName,
                    lockService);
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
        ExecutorService executorService = null;
        try (Stream<Path> dirList = Files.walk(zipWorkspacePath); S3HighLevelReactiveClient client = createS3Client()) {
            executorService = Executors.newFixedThreadPool(parallelTaskNumber, factory);
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
                                                                                           client,
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
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
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
                                                  S3HighLevelReactiveClient client,
                                                  IPeriodicActionProgressManager progressManager,
                                                  String tenant,
                                                  boolean isSymLink) {
        return () -> {
            long start = Instant.now().toEpochMilli();
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
                progressManager,
                tenant,
                client);

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
                LOGGER.info("[S3 Monitoring] Archive submission task for {} took {} ms",
                            dirPath,
                            Instant.now().toEpochMilli() - start);
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
        ExecutorService executorService = null;
        try {
            executorService = Executors.newFixedThreadPool(parallelTaskNumber, factory);
            executorService.invokeAll(directoriesWithFiles.stream()
                                                          .map(dirToProcess -> doCleanDirectory(cacheWorkspacePath,
                                                                                                dirToProcess,
                                                                                                oldestAgeToKeep,
                                                                                                tenant))
                                                          .toList());
        } catch (InterruptedException e) {
            LOGGER.error("Clean archive cache process interrupted");
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
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
                                      Set<FileReferenceWithoutOwnersDto> filesWithPendingActions) {
        LOGGER.info("Glacier periodic pending actions started");
        String tenant = runtimeTenantResolver.getTenant();
        List<Future<LockServiceResponse<Void>>> taskResults = null;
        ExecutorService executorService = null;
        try {
            executorService = Executors.newFixedThreadPool(parallelTaskNumber, factory);
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
            LOGGER.error("Check pending action process interrupted");
        } catch (ExecutionException e) {
            LOGGER.error("Error during check pending action process", e);
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
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
        return new S3GlacierStorageConfigurationDto(storageConfiguration,
                                                    multipartThresholdMb,
                                                    nbParallelPartsUpload,
                                                    FileNamingStrategy.valueOf(fileNamingStrategy),
                                                    workspacePath,
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

    private boolean isSmallFile(FileReferenceWithoutOwnersDto fileReference) {
        S3GlacierUtils.S3GlacierUrl s3GlacierUrl = S3GlacierUtils.dispatchS3FilePath(fileReference.getLocation()
                                                                                                  .getUrl());
        return s3GlacierUrl.isSmallFileUrl();
    }

    @Override
    public InputStream download(FileReferenceWithoutOwnersDto fileReference)
        throws NearlineFileNotAvailableException, NearlineDownloadException {
        String entryKey = getEntryKey(fileReference);

        NearlineFileStatusDto nearlineFileStatusDto = checkAvailability(fileReference);
        if (!nearlineFileStatusDto.isAvailable()) {
            LOGGER.warn(nearlineFileStatusDto.getMessage());
            throw new NearlineFileNotAvailableException(nearlineFileStatusDto.getMessage());
        }
        Optional<Path> smallFilePathInWorkspace = findSmallFilePathInWorkspace(fileReference);
        try {
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
                                                                                     UUID.randomUUID()));
            }
        } catch (IOException e) { // NOSONAR
            LOGGER.error(FILE + fileReference.getMetaInfo().getFileName() + " cannot be download ", e);
            throw new NearlineDownloadException(FILE
                                                + fileReference.getMetaInfo().getFileName()
                                                + " cannot be download : "
                                                + e.getMessage());
        }
    }

    @Override
    public NearlineFileStatusDto checkAvailability(FileReferenceWithoutOwnersDto fileReference) {
        boolean availability = false;
        OffsetDateTime dateExpiration = null;
        // manage case of small files
        if (isSmallFile(fileReference)) {
            Optional<Path> smallFilePathInWorkspace = findSmallFilePathInWorkspace(fileReference);
            NearlineFileStatusDto status;
            if (smallFilePathInWorkspace.isPresent()) {
                LOGGER.debug("Small file available : {}", fileReference.getLocation().getUrl());
                status = new NearlineFileStatusDto(true,
                                                   null,
                                                   "Small file "
                                                   + fileReference.getMetaInfo().getFileName()
                                                   + " is available.");
            } else {
                LOGGER.debug("Small file not available : {}", fileReference.getLocation().getUrl());
                status = new NearlineFileStatusDto(false,
                                                   null,
                                                   "Small file "
                                                   + fileReference.getMetaInfo().getFileName()
                                                   + " is not available.");
            }
            return status;
        }

        S3HighLevelReactiveClient client = getCheckAvailabilityClient();

        // case of big files
        long start = Instant.now().toEpochMilli();
        GlacierFileStatus fileAvailable = client.isFileAvailable(storageConfiguration,
                                                                 getEntryKey(fileReference),
                                                                 standardStorageClassName).block();
        LOGGER.info("[S3 Monitoring] Checking availability of {} took {} ms",
                    getEntryKey(fileReference),
                    Instant.now().toEpochMilli() - start);

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
        }
        return new NearlineFileStatusDto(availability, dateExpiration, message);
    }

    private synchronized S3HighLevelReactiveClient getCheckAvailabilityClient() {
        if (checkAvailabilityClient == null) {
            checkAvailabilityClient = createS3Client();
        }
        return checkAvailabilityClient;
    }

    private Optional<Path> findSmallFilePathInWorkspace(FileReferenceWithoutOwnersDto fileReference) {
        Path s3FilePath = getServerPath().relativize(Path.of(fileReference.getLocation().getUrl()));
        S3GlacierUtils.S3GlacierUrl fileInfo = S3GlacierUtils.dispatchS3FilePath(s3FilePath.toString());
        if (fileReference.getLocation().isPendingActionRemaining()) {
            // action remaining means that archive containing the small file isn't sent to S3 yet.
            // so, we check in archive building workspace path (/zip) if file is present.
            return findSmallFilePathInWorkspace(fileInfo, getArchiveBuildingWorkspacePath());
        }
        return Optional.empty();
    }

    private Optional<Path> findSmallFilePathInWorkspace(S3GlacierUtils.S3GlacierUrl fileInfos, String path) {
        String relativeArchivePath = fileInfos.archiveFilePath();
        Path archiveCachePath = Path.of(path, relativeArchivePath);
        String archiveName = archiveCachePath.getFileName().toString();
        Optional<String> smallFileName = fileInfos.smallFileNameInArchive();

        if (smallFileName.isPresent()) {
            String dirName = S3GlacierUtils.computePathOfBuildDirectoryFromArchiveName(archiveName);
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

    private RetrieveCacheFileTaskConfiguration createRetrieveCacheFileTaskConfiguration(Path fileRelativePath,
                                                                                        boolean isSmallFile,
                                                                                        S3HighLevelReactiveClient client,
                                                                                        String lockName) {
        return new RetrieveCacheFileTaskConfiguration(fileRelativePath,
                                                      getCachePath(),
                                                      storageConfiguration,
                                                      s3AccessTimeout,
                                                      rootPath,
                                                      isSmallFile,
                                                      client,
                                                      lockName,
                                                      Instant.now(),
                                                      renewMaxIterationWaitingPeriodInS,
                                                      renewCallDurationInMs,
                                                      standardStorageClassName,
                                                      lockService,
                                                      useExternalCache);
    }
}
