package fr.cnes.regards.modules.storage.plugin.smallfiles;

import fr.cnes.regards.framework.jpa.multitenant.lock.LockService;
import fr.cnes.regards.framework.jpa.multitenant.lock.LockServiceResponse;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.s3.domain.GlacierFileStatus;
import fr.cnes.regards.framework.s3.domain.S3Server;
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.modules.fileaccess.dto.FileReferenceWithoutOwnersDto;
import fr.cnes.regards.modules.fileaccess.dto.request.FileStorageRequestAggregationDto;
import fr.cnes.regards.modules.fileaccess.plugin.domain.*;
import fr.cnes.regards.modules.fileaccess.plugin.dto.FileCacheRequestDto;
import fr.cnes.regards.modules.fileaccess.plugin.dto.FileDeletionRequestDto;
import fr.cnes.regards.modules.storage.plugin.smallfiles.configuration.*;
import fr.cnes.regards.modules.storage.plugin.smallfiles.task.*;
import fr.cnes.regards.modules.storage.plugin.smallfiles.utils.LockTypeEnum;
import fr.cnes.regards.modules.storage.plugin.smallfiles.utils.RestoreResponse;
import fr.cnes.regards.modules.storage.plugin.smallfiles.utils.SmallFilesUtils;
import jakarta.annotation.Nullable;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.slf4j.LoggerFactory.getLogger;

/*

 * Copyright 2017-2025 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
 * along with REGARDS. If not, see `<http://www.gnu.org/licenses/>`.
 */
public abstract class AbstractSmallFileFacade implements ISmallFilesStorage {

    private static final Logger LOGGER = getLogger(AbstractSmallFileFacade.class);

    public static final String TENANT_LOG = "In thread {}, forcing tenant to {}";

    @Getter
    private final IRuntimeTenantResolver runtimeTenantResolver;

    private final LockService lockService;

    private final BasicThreadFactory factory;

    public AbstractSmallFileFacade(IRuntimeTenantResolver runtimeTenantResolver, LockService lockService) {
        factory = new BasicThreadFactory.Builder().namingPattern(getThreadNamingPattern())
                                                  .priority(Thread.MAX_PRIORITY)
                                                  .build();
        this.runtimeTenantResolver = runtimeTenantResolver;
        this.lockService = lockService;
    }

    public void store(FileStorageWorkingSubset workingSet,
                      IStorageProgressManager progressManager,
                      StoreSmallFileTaskConfiguration configuration) {
        LOGGER.info("Glacier store requests received");
        ExecutorService storeExecutorService = null;
        try {
            storeExecutorService = Executors.newFixedThreadPool(configuration.storeParallelTaskNumber(), factory);
            String tenant = runtimeTenantResolver.getTenant();
            List<Future<LockServiceResponse<Void>>> taskResults = storeExecutorService.invokeAll(workingSet.getFileReferenceRequests()
                                                                                                           .stream()
                                                                                                           .map(request -> doStoreTask(
                                                                                                               request,
                                                                                                               configuration,
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
                                                           StoreSmallFileTaskConfiguration configuration,
                                                           IStorageProgressManager progressManager,
                                                           String tenant) {

        return () -> {
            LOGGER.debug(TENANT_LOG, Thread.currentThread().getName(), tenant);
            runtimeTenantResolver.forceTenant(tenant);
            try {
                request.getMetaInfo()
                       .setFileSize(getFileSize(new URL(request.getOriginUrl()), configuration.storages()));
                if (request.getMetaInfo().getFileSize() > configuration.smallFileMaxSize()) {
                    LOGGER.info("Storing file on S3 using standard upload");
                    handleStoreFileRequest(request, progressManager, configuration.rootPath());
                    LOGGER.info("End Storing file on S3");
                } else {
                    LOGGER.debug("In thread {}, running StoreSmallFileTask from Glacier with lock",
                                 Thread.currentThread().getName());
                    /**
                     * Lock the node directory (with LOCK_STORE) where the file will be stored to prevent other storage
                     * jobs to handle the same node.
                     * @see S3Glacier#doStoreTask
                     **/

                    // the same node (there is one _current building archive per node)
                    lockService.runWithLock(SmallFilesUtils.getLockName(LockTypeEnum.LOCK_STORE,
                                                                        configuration.rootPath(),
                                                                        configuration.workspacePath(),
                                                                        request.getSubDirectory()),
                                            new StoreSmallFileTask(configuration,
                                                                   request,
                                                                   progressManager,
                                                                   this::getStorageUrl));
                }
            } catch (MalformedURLException e) {
                LOGGER.error(e.getMessage(), e);
                storageFailedWithMalformedUrlException(request, progressManager);
            } catch (InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
                progressManager.storageFailed(request, "The storage task was interrupted before completion.");
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                //The MalformedURLException can be encapsulated in a RuntimeException
                LOGGER.error(e.getMessage(), e);
                if (e.getCause() instanceof MalformedURLException) {
                    storageFailedWithMalformedUrlException(request, progressManager);
                } else {
                    progressManager.storageFailed(request,
                                                  String.format("Unexpected exception occurred : %s : %s",
                                                                e.getClass(),
                                                                e.getMessage()));
                }
            }
            return null;
        };
    }

    private static void storageFailedWithMalformedUrlException(FileStorageRequestAggregationDto request,
                                                               IStorageProgressManager progressManager) {
        progressManager.storageFailed(request,
                                      String.format("Invalid url, check storage endpoint for %s and source " + "url %s",
                                                    request.getStorage(),
                                                    request.getOriginUrl()));
    }

    public void retrieve(FileRestorationWorkingSubset workingSubset,
                         IRestorationProgressManager progressManager,
                         RetrieveSmallFileTaskConfiguration configuration) {
        LOGGER.info("Glacier retrieve requests received");
        String tenant = runtimeTenantResolver.getTenant();
        ExecutorService executorService = null;
        try {
            executorService = Executors.newFixedThreadPool(configuration.parallelTaskNumber(), factory);
            List<Future<LockServiceResponse<Void>>> taskResults = executorService.invokeAll(workingSubset.getFileRestorationRequests()
                                                                                                         .stream()
                                                                                                         .map(request -> doRetrieveTask(
                                                                                                             request,
                                                                                                             configuration,
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
                                                              RetrieveSmallFileTaskConfiguration configuration,
                                                              IRestorationProgressManager progressManager,
                                                              String tenant) {

        return () -> {
            LOGGER.debug(TENANT_LOG, Thread.currentThread().getName(), tenant);
            runtimeTenantResolver.forceTenant(tenant);
            try {

                Path fileRelativePath = getFileRelativePath(fileCacheRequest.getFileReference().getLocation().getUrl());
                boolean isSmallFile = isASmallFileUrl(fileCacheRequest.getFileReference().getLocation().getUrl());
                if (isSmallFile && fileCacheRequest.getFileReference().getLocation().isPendingActionRemaining()) {
                    // The file has not been saved to S3 yet, it's still in the local archive building workspace
                    RetrieveLocalSmallFileTaskConfiguration configurationLocal = new RetrieveLocalSmallFileTaskConfiguration(
                        fileRelativePath,
                        getArchiveBuildingWorkspacePath(configuration.workspacePath()));
                    RetrieveLocalSmallFileTask task = new RetrieveLocalSmallFileTask(configurationLocal,
                                                                                     fileCacheRequest,
                                                                                     progressManager);
                    LOGGER.debug("In thread {}, running RetrieveLocalSmallFileTask from Glacier with lock",
                                 Thread.currentThread().getName());
                    /** Lock the small file archive (with LOCK_STORE) to prevent delete or sendArchiveJob to
                     * alter archive during copy to cache (LOCK_STORE is used by both delete and store jobs)
                     * @see S3Glacier#doDeleteTask and {@link S3Glacier#doStoreTask}
                     **/
                    lockService.runWithLock(SmallFilesUtils.getLockName(LockTypeEnum.LOCK_STORE,
                                                                        null,
                                                                        configuration.workspacePath(),
                                                                        fileRelativePath.getParent().toString()), task);
                    return null;
                }

                String lockName = SmallFilesUtils.getLockName(LockTypeEnum.LOCK_RESTORE,
                                                              null,
                                                              configuration.workspacePath(),
                                                              fileRelativePath.toString());

                RetrieveCacheFileTask task = new RetrieveCacheFileTask(createRetrieveCacheFileTaskConfiguration(
                    fileCacheRequest.getFileReference().getLocation().getUrl(),
                    fileRelativePath,
                    isSmallFile,
                    lockName,
                    configuration), fileCacheRequest, progressManager);

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

    public void delete(FileDeletionWorkingSubset workingSet,
                       IDeletionProgressManager progressManager,
                       DeleteSmallFileTaskConfiguration configuration) {
        LOGGER.info("S3Glacier delete received requests");
        ExecutorService executorService = null;
        try {
            executorService = Executors.newFixedThreadPool(configuration.parallelTaskNumber(), factory);
            String tenant = runtimeTenantResolver.getTenant();
            List<Future<LockServiceResponse<Void>>> taskResults = executorService.invokeAll(workingSet.getFileDeletionRequests()
                                                                                                      .stream()
                                                                                                      .map(request -> doDeleteTask(
                                                                                                          request,
                                                                                                          configuration,
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
                                                            DeleteSmallFileTaskConfiguration configuration,
                                                            IDeletionProgressManager progressManager,
                                                            String tenant) {
        return () -> {
            LOGGER.debug(TENANT_LOG, Thread.currentThread().getName(), tenant);
            runtimeTenantResolver.forceTenant(tenant);
            if (!isASmallFileUrl(request.getFileReference().getLocation().getUrl())) {
                handleDeleteFileRequest(request, progressManager);
            } else {
                handleDeleteSmallFileRequest(request, configuration, progressManager);
            }
            return null;
        };
    }

    private void handleDeleteSmallFileRequest(FileDeletionRequestDto request,
                                              DeleteSmallFileTaskConfiguration configuration,
                                              IDeletionProgressManager progressManager) {
        try {
            Path fileRelativePath = getFileRelativePath(request.getFileReference().getLocation().getUrl());

            if (request.getFileReference().getLocation().isPendingActionRemaining()) {
                // The small file is still in the local building directory, it is not necessary to restore it
                DeleteLocalSmallFileTaskConfiguration configurationLocal = new DeleteLocalSmallFileTaskConfiguration(
                    fileRelativePath,
                    getArchiveBuildingWorkspacePath(configuration.workspacePath()));
                DeleteLocalSmallFileTask task = new DeleteLocalSmallFileTask(configurationLocal,
                                                                             request,
                                                                             progressManager);
                LOGGER.debug("In thread {}, running DeleteLocalSmallFileTask from Glacier with lock",
                             Thread.currentThread().getName());
                /** Lock the building archive (with STORE LOCK) to prevent Pending Action Job to close and send the
                 * archive.
                 * @see {@link S3Glacier#doSubmitReadyArchive}
                 **/
                lockService.runWithLock(SmallFilesUtils.getLockName(LockTypeEnum.LOCK_STORE,
                                                                    null,
                                                                    configuration.workspacePath(),
                                                                    fileRelativePath.getParent() != null ?
                                                                        fileRelativePath.getParent().toString() :
                                                                        ""), task);

            } else {
                /**
                 * Lock the archive (with RESTORE LOCK) to prevent other deletion jobs or restore jobs to retrieve
                 * the same archive
                 * @see {@link S3Glacier#doDeleteTask} and {@link S3Glacier#doRetrieveTask}
                 */
                String lockName = SmallFilesUtils.getLockName(LockTypeEnum.LOCK_RESTORE,
                                                              null,
                                                              configuration.workspacePath(),
                                                              fileRelativePath.toString());

                RestoreAndDeleteSmallFileTaskConfiguration configurationRestoreDelete = new RestoreAndDeleteSmallFileTaskConfiguration(
                    fileRelativePath,
                    getCachePath(configuration.workspacePath()),
                    getArchiveBuildingWorkspacePath(configuration.workspacePath()),
                    this,
                    lockName,
                    Instant.now(),
                    configuration.renewMaxIterationWaitingPeriodInS(),
                    configuration.renewCallDurationInMs(),
                    lockService);
                RestoreAndDeleteSmallFileTask task = new RestoreAndDeleteSmallFileTask(configurationRestoreDelete,
                                                                                       request,
                                                                                       progressManager);
                LOGGER.debug("In thread {}, running RestoreAndDeleteSmallFileTask from S3Glacier with lock",
                             Thread.currentThread().getName());
                lockService.runWithLock(lockName, task);
            }
        } catch (InterruptedException | MalformedURLException e) {
            LOGGER.error(e.getMessage(), e);
            progressManager.deletionFailed(request, "The deletion task was interrupted before completion.");
        }
    }

    public void runPeriodicAction(IPeriodicActionProgressManager progressManager,
                                  PeriodicActionSmallFileTaskConfiguration configuration) {
        LOGGER.info("Glacier periodic actions started");
        submitReadyArchives(progressManager, configuration);
        cleanArchiveCache(configuration);
        LOGGER.info("Glacier periodic actions ended");
    }

    private void submitReadyArchives(IPeriodicActionProgressManager progressManager,
                                     PeriodicActionSmallFileTaskConfiguration configuration) {
        Path zipWorkspacePath = Paths.get(configuration.workspacePath(), ZIP_DIR);
        String tenant = runtimeTenantResolver.getTenant();
        if (!Files.exists(zipWorkspacePath)) {
            return;
        }
        ExecutorService executorService = null;
        try (Stream<Path> dirList = Files.walk(zipWorkspacePath)/*; S3HighLevelReactiveClient client =
            createS3Client()*/) {
            executorService = Executors.newFixedThreadPool(configuration.parallelTaskNumber(), factory);
            // Directory that will be stored are located in /<WORKSPACE>/<ZIP_DIR>/<NODE>/, they can be symbolic link
            // It is important to differentiate between actual directories and symbolic link to use locks correctly
            Map<Boolean, List<Path>> dirToProcessList = dirList.filter(dir -> dir.getFileName()
                                                                                 .toString()
                                                                                 .startsWith(BUILDING_DIRECTORY_PREFIX))
                                                               .collect(Collectors.groupingBy(Files::isSymbolicLink));
            List<Callable<Boolean>> processes = dirToProcessList.entrySet()
                                                                .stream()
                                                                .flatMap(entry -> entry.getValue()
                                                                                       .stream()
                                                                                       .map(dir -> doSubmitReadyArchive(
                                                                                           dir,
                                                                                           configuration,
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
                progressManager.allPendingActionSucceed(configuration.storageName());
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
                                                  PeriodicActionSmallFileTaskConfiguration configuration,
                                                  IPeriodicActionProgressManager progressManager,
                                                  String tenant,
                                                  boolean isSymLink) {
        return () -> {
            LOGGER.debug(TENANT_LOG, Thread.currentThread().getName(), tenant);
            runtimeTenantResolver.forceTenant(tenant);
            SubmitReadyArchiveTaskConfiguration submitReadyArchiveTaskConfiguration = new SubmitReadyArchiveTaskConfiguration(
                dirPath,
                configuration.workspacePath(),
                this,
                configuration.archiveMaxAge(),
                configuration.storageName());

            if (isSymLink) {
                SubmitUpdatedArchiveTask task = new SubmitUpdatedArchiveTask(submitReadyArchiveTaskConfiguration,
                                                                             progressManager);

                /** Lock the building archive (with STORE LOCK) to prevent storage and deletion jobs to alter the archive
                 * @see S3Glacier#doStoreTask} and {@link S3Glacier#doDeleteTask}
                 */
                LockServiceResponse<Boolean> res = lockService.runWithLock(SmallFilesUtils.getLockName(LockTypeEnum.LOCK_RESTORE,
                                                                                                       null,
                                                                                                       configuration.workspacePath(),
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
                LockServiceResponse<Boolean> res = lockService.runWithLock(SmallFilesUtils.getLockName(LockTypeEnum.LOCK_STORE,
                                                                                                       null,
                                                                                                       configuration.workspacePath(),
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
    public void cleanArchiveCache(PeriodicActionSmallFileTaskConfiguration configuration) {
        Path cacheWorkspacePath = Paths.get(configuration.workspacePath(), TMP_DIR);
        String tenant = runtimeTenantResolver != null ? runtimeTenantResolver.getTenant() : "";
        if (!Files.exists(cacheWorkspacePath)) {
            return;
        }
        Instant oldestAgeToKeep = OffsetDateTime.now().minusHours(configuration.archiveCacheLifetime()).toInstant();
        List<Path> directoriesWithFiles = new ArrayList<>();
        getDirectoriesWithFilesToDelete(directoriesWithFiles,
                                        cacheWorkspacePath,
                                        oldestAgeToKeep,
                                        cacheWorkspacePath,
                                        Paths.get(configuration.workspacePath(), ZIP_DIR));
        ExecutorService executorService = null;
        try {
            executorService = Executors.newFixedThreadPool(configuration.parallelTaskNumber(), factory);
            executorService.invokeAll(directoriesWithFiles.stream()
                                                          .map(dirToProcess -> doCleanDirectory(cacheWorkspacePath,
                                                                                                dirToProcess,
                                                                                                oldestAgeToKeep,
                                                                                                tenant,
                                                                                                configuration))
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
                                                                String tenant,
                                                                PeriodicActionSmallFileTaskConfiguration configuration) {
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
            boolean processRun = lockService.tryRunWithLock(SmallFilesUtils.getLockName(LockTypeEnum.LOCK_RESTORE,
                                                                                        null,
                                                                                        configuration.workspacePath(),
                                                                                        dirPath.toString()),
                                                            task,
                                                            configuration.cleanCacheTaskLockAcquireTimeout(),
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

    private boolean isASmallFileUrl(String url) {
        try {
            return SmallFilesUtils.dispatchUrl(url).isSmallFileUrl();
        } catch (URISyntaxException e) {
            LOGGER.error(e.getMessage(), e);
            return false;
        }
    }

    private RetrieveCacheFileTaskConfiguration createRetrieveCacheFileTaskConfiguration(String fileUrl,
                                                                                        Path fileRelativePath,
                                                                                        boolean isSmallFile,
                                                                                        String lockName,
                                                                                        RetrieveSmallFileTaskConfiguration configuration) {
        return new RetrieveCacheFileTaskConfiguration(fileUrl,
                                                      fileRelativePath,
                                                      getCachePath(configuration.workspacePath()),
                                                      this,
                                                      isSmallFile,
                                                      lockName,
                                                      Instant.now(),
                                                      configuration.renewMaxIterationWaitingPeriodInS(),
                                                      configuration.renewCallDurationInMs(),
                                                      lockService,
                                                      configuration.useExternalCache());
    }

    private String getArchiveBuildingWorkspacePath(String workspacePath) {
        return workspacePath + File.separator + ZIP_DIR;
    }

    private String getCachePath(String workspacePath) {
        return workspacePath + File.separator + TMP_DIR;
    }

    /**
     * Get the file content length
     *
     * @param sourceUrl the url of file
     * @param storages  List of S3 storages server manage by regards
     * @return the size of file, 0 if the file does not exist
     */
    protected long getFileSize(URL sourceUrl, List<S3Server> storages) {
        long fileSize = 0L;
        try {
            fileSize = DownloadUtils.getContentLength(sourceUrl, 0, storages);
        } catch (IOException e) {
            LOGGER.error("Failure in the getting of file size : {}", sourceUrl, e);
        }
        return fileSize;
    }

    public void runCheckPendingAction(IPeriodicActionProgressManager progressManager,
                                      Set<FileReferenceWithoutOwnersDto> filesWithPendingActions,
                                      int parallelTaskNumber,
                                      String workspacePath) {
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
                                                                                                            tenant,
                                                                                                            workspacePath))
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
                                                                    String tenant,
                                                                    String workspacePath) {
        return () -> {
            LOGGER.debug(TENANT_LOG, Thread.currentThread().getName(), tenant);
            runtimeTenantResolver.forceTenant(tenant);
            SmallFilesUtils.GlacierUrl s3GlacierUrl = SmallFilesUtils.dispatchUrl(url);
            if (!s3GlacierUrl.isSmallFileUrl()) {
                LOGGER.error("The URL {} is not a small file url and has a pending action which is not supported by "
                             + "the S3Glacier", url);
                return null;
            }
            try {
                Path archiveRelativePath = getFileRelativePath(s3GlacierUrl.archiveFilePath());
                CheckPendingActionTaskConfiguration checkPendingActionTaskConfiguration = new CheckPendingActionTaskConfiguration(
                    url,
                    archiveRelativePath,
                    s3GlacierUrl.smallFileNameInArchive().get(),
                    workspacePath,
                    this::existsStorageUrl);
                CheckPendingActionTask task = new CheckPendingActionTask(checkPendingActionTaskConfiguration,
                                                                         progressManager);
                /**
                 * Lock the archive (with RESTORE LOCK) to prevent Deletion Job from deleting the file we are checking
                 * the validity of pending actions.
                 *
                 */
                lockService.runWithLock(SmallFilesUtils.getLockName(LockTypeEnum.LOCK_RESTORE,
                                                                    null,
                                                                    workspacePath,
                                                                    archiveRelativePath.toString()), task);
            } catch (MalformedURLException e) {
                LOGGER.error("Error running check pending file action. Cause:" + e.getMessage(), e);
            }
            return null;
        };

    }

    public abstract RestoreResponse restore(String key, @Nullable Integer availabilityHours);

    public abstract boolean downloadFile(Path targetFilePath, String key, @Nullable String taskId);

    public abstract GlacierFileStatus downloadAfterRestoreFile(Path targetFilePath,
                                                               String key,
                                                               String lockName,
                                                               Instant lockCreationDate,
                                                               int renewMaxIterationWaitingPeriodInS,
                                                               Long renewCallDurationInMs,
                                                               LockService lockService);

    public abstract GlacierFileStatus checkRestorationComplete(String key,
                                                               String lockName,
                                                               Instant lockCreationDate,
                                                               int renewMaxIterationWaitingPeriodInS,
                                                               Long renewCallDurationInMs,
                                                               LockService lockService);

    public abstract Path getFileRelativePath(String fileRelativePath) throws MalformedURLException;

    public abstract boolean deleteArchive(String taskId, String entryKey);

    public abstract URL getStorageUrl(String entryKey);

    public abstract String storeFile(Path filePath, String filePathOnStorage, String checksum, Long fileSize)
        throws IOException;

    public abstract boolean existsStorageUrl(Path path);

    public abstract void handleStoreFileRequest(FileStorageRequestAggregationDto request,
                                                IStorageProgressManager progressManager,
                                                String rootPath);

    public abstract void handleDeleteFileRequest(FileDeletionRequestDto request,
                                                 IDeletionProgressManager progressManager);

    public abstract String getThreadNamingPattern();
}
