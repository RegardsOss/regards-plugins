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
package fr.cnes.regards.modules.storage.plugin.s3;

import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.s3.S3StorageConfiguration;
import fr.cnes.regards.framework.s3.domain.S3Server;
import fr.cnes.regards.modules.storage.domain.database.FileLocation;
import fr.cnes.regards.modules.storage.domain.database.FileReference;
import fr.cnes.regards.modules.storage.domain.database.FileReferenceMetaInfo;
import fr.cnes.regards.modules.storage.domain.database.request.FileCacheRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileDeletionRequest;
import fr.cnes.regards.modules.storage.domain.database.request.FileStorageRequest;
import fr.cnes.regards.modules.storage.domain.plugin.IDeletionProgressManager;
import fr.cnes.regards.modules.storage.domain.plugin.IPeriodicActionProgressManager;
import fr.cnes.regards.modules.storage.domain.plugin.IRestorationProgressManager;
import fr.cnes.regards.modules.storage.domain.plugin.IStorageProgressManager;
import fr.cnes.regards.modules.storage.plugin.s3.task.*;
import fr.cnes.regards.modules.storage.service.glacier.GlacierArchiveService;
import io.jsonwebtoken.lang.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;

import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Sébastien Binda
 **/
public class S3GlacierLockTest {

    private static final String ROOT_PATH = "test/locks/test_1/";

    private LockServiceMock lockServiceMock;

    private S3GlacierMock glacier;

    public S3GlacierLockTest() {
    }

    @Before
    public void init() throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException {
        lockServiceMock = new LockServiceMock();
        S3StorageConfiguration s3settings = new S3StorageConfiguration();
        s3settings.getStorages().add(new S3Server("endpoint", "region", "key", "secret", "bucket"));
        glacier = new S3GlacierMock(lockServiceMock,
                                    s3settings,
                                    500,
                                    ROOT_PATH,
                                    "https://s3.datalake-qualif.cnes.fr",
                                    "bucket",
                                    Mockito.mock(GlacierArchiveService.class),
                                    Mockito.mock(IRuntimeTenantResolver.class));
    }

    @Test
    public void test_store_big_file_multiples_locks() throws Exception {
        // Given
        FileStorageRequest request1 = createStoreFileRequest("node1", false);
        FileStorageRequest request2 = createStoreFileRequest("node1", false);
        FileStorageRequest request3 = createStoreFileRequest("node1", false);
        FileStorageRequest request4 = createStoreFileRequest("node1", false);
        // When
        glacier.mockBigFile();
        glacier.doStoreTask(request1, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        glacier.doStoreTask(request2, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        glacier.doStoreTask(request3, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        glacier.mockSmallFile();
        glacier.doStoreTask(request4, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getWaitingLock().isEmpty(), "No lock should exists");
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 1, "One lock should exists");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(StoreSmallFileTask.class.getName()),
                      "There should be one StoreSmallFileTask lock");
    }

    @Test
    public void test_store_small_file_on_same_node_locked() throws Exception {
        // Given
        FileStorageRequest request1 = createStoreFileRequest("node1", true);
        FileStorageRequest request2 = createStoreFileRequest("node1", true);
        FileStorageRequest request3 = createStoreFileRequest("node2", true);
        FileStorageRequest request4 = createStoreFileRequest("node2", true);
        // When
        glacier.mockSmallFile();
        glacier.doStoreTask(request1, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        glacier.doStoreTask(request2, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        glacier.doStoreTask(request3, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        glacier.doStoreTask(request4, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getWaitingLock().size() == 2, "There should 2 task waiting for lock");
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 2, "There should 2 distinct locks taken");
    }

    @Test
    public void test_restore_small_file_local_locked_by_store_small_file_on_same_node() throws Exception {
        // Given
        FileStorageRequest request = createStoreFileRequest("node1", true);
        FileCacheRequest cacheRequest = createRestoreFileRequest("node1", true, true);
        // When
        glacier.doStoreTask(request, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        glacier.doRetrieveTask(cacheRequest, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getWaitingLock().size() == 1,
                      "There should be one task waiting for lock available.");
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 1, "There should be one task running.");
        Assert.isTrue(lockServiceMock.getWaitingLock().containsValue(RetrieveLocalSmallFileTask.class.getName()),
                      "There should be a RetrieveLocalSmallFileTask waiting for lock");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(StoreSmallFileTask.class.getName()),
                      "There should be a StoreSmallFileTask running");
    }

    @Test
    public void test_restore_small_file_local_not_locked_by_store_small_file_on_other_node() throws Exception {
        // Given
        FileStorageRequest request = createStoreFileRequest("node1", true);
        FileCacheRequest cacheRequest = createRestoreFileRequest("node2", true, true);
        // When
        glacier.doStoreTask(request, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        glacier.doRetrieveTask(cacheRequest, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getWaitingLock().isEmpty(), "No locked task should exists");
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 2, " There should be 2 distinct locks acquired");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(StoreSmallFileTask.class.getName()),
                      "There should a lock acquired for StoreSmallFileTask");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(RetrieveLocalSmallFileTask.class.getName()),
                      "There should be a lock acquired for RetrieveLocalSmallFileTask");
    }

    @Test
    public void test_restore_small_file_remote_not_locked_by_store_small_file_on_same_node() throws Exception {
        // Given
        FileStorageRequest request = createStoreFileRequest("node1", true);
        FileCacheRequest cacheRequest = createRestoreFileRequest("node1", true, false);
        // When
        glacier.doStoreTask(request, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        glacier.doRetrieveTask(cacheRequest, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getWaitingLock().isEmpty(), "No locked task should exists");
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 2, " There should be 2 distinct locks acquired");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(StoreSmallFileTask.class.getName()),
                      "There should a lock acquired for StoreSmallFileTask");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(RetrieveCacheFileTask.class.getName()),
                      "There should be a lock acquired for RetrieveLocalSmallFileTask");
    }

    @Test
    public void test_restore_big_file_remote_not_locked_by_store_small_file_on_same_node() throws Exception {
        // Given
        FileStorageRequest request = createStoreFileRequest("node1", true);
        FileCacheRequest cacheRequest = createRestoreFileRequest("node1", true, false);
        // When
        glacier.doStoreTask(request, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        glacier.doRetrieveTask(cacheRequest, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getWaitingLock().isEmpty(), "No locked task should exists");
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 2, " There should be 2 distinct locks acquired");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(StoreSmallFileTask.class.getName()),
                      "There should a lock acquired for StoreSmallFileTask");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(RetrieveCacheFileTask.class.getName()),
                      "There should be a lock acquired for RetrieveLocalSmallFileTask");
    }

    @Test
    public void test_restore_multiple_big_files_no_lock() throws Exception {
        // Given
        FileCacheRequest request1 = createRestoreFileRequest("node1", false, false);
        FileCacheRequest request2 = createRestoreFileRequest("node1", false, false);
        FileCacheRequest request3 = createRestoreFileRequest("node1", false, false);
        // When
        glacier.doRetrieveTask(request1, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        glacier.doRetrieveTask(request2, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        glacier.doRetrieveTask(request3, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getWaitingLock().isEmpty(), "No task should be waiting a lock");
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 3, "3 distinct locks taken");
    }

    @Test
    public void test_restore_local_small_file_not_locked_by_restore_big_file_on_same_node() throws Exception {
        // Given
        FileCacheRequest request1 = createRestoreFileRequest("node1", true, true);
        FileCacheRequest request2 = createRestoreFileRequest("node1", false, false);
        FileCacheRequest request3 = createRestoreFileRequest("node1", false, false);
        // When
        glacier.doRetrieveTask(request1, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        glacier.doRetrieveTask(request2, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        glacier.doRetrieveTask(request3, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getWaitingLock().isEmpty(), "No task should be waiting a lock");
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 3, "3 distinct locks taken");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(RetrieveLocalSmallFileTask.class.getName()));
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(RetrieveCacheFileTask.class.getName()));
    }

    @Test
    public void test_restore_remote_small_file_not_locked_by_restore_other_remote_small_file() throws Exception {
        // Given
        FileCacheRequest request1 = createRestoreFileRequest("node1", true, false);
        FileCacheRequest request2 = createRestoreFileRequest("node1", true, false);
        FileCacheRequest request3 = createRestoreFileRequest("node1", true, false);
        // When
        glacier.doRetrieveTask(request1, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        glacier.doRetrieveTask(request2, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        glacier.doRetrieveTask(request3, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getWaitingLock().isEmpty(), "No task should be waiting a lock");
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 3, "3 distinct locks taken");
        Assert.isTrue(!lockServiceMock.getLockAcquired().containsValue(RetrieveLocalSmallFileTask.class.getName()));
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(RetrieveCacheFileTask.class.getName()));
    }

    @Test
    public void test_restore_remote_small_file_locked_by_restore_same_remote_small_file() throws Exception {
        // Given
        FileCacheRequest request1 = createRestoreFileRequest("node1", true, false);
        FileCacheRequest request2 = createRestoreFileRequest("node1", true, false, Optional.of("archive1"));
        FileCacheRequest request3 = createRestoreFileRequest("node1", true, false, Optional.of("archive1"));
        FileCacheRequest request4 = createRestoreFileRequest("node2", true, false, Optional.of("archive1"));
        // When
        glacier.doRetrieveTask(request1, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        glacier.doRetrieveTask(request2, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        glacier.doRetrieveTask(request3, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        glacier.doRetrieveTask(request4, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getWaitingLock().size() == 1, "There should be on restore task waiting for lock");
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 3, "There should be 3 distinct locks taken");
        Assert.isTrue(!lockServiceMock.getLockAcquired().containsValue(RetrieveLocalSmallFileTask.class.getName()));
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(RetrieveCacheFileTask.class.getName()));
        Assert.isTrue(lockServiceMock.getWaitingLock().containsValue(RetrieveCacheFileTask.class.getName()));
    }

    @Test
    public void test_delete_big_file_no_lock() throws Exception {
        // Given
        FileDeletionRequest request = createDeletionRequest("node1", false, false, Optional.empty());
        // When
        glacier.doDeleteTask(request, Mockito.mock(IDeletionProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getLockAcquired().isEmpty(), " There should ne lock for big file deletion task");
        Assert.isTrue(lockServiceMock.getWaitingLock().isEmpty(), " There should ne lock for big file deletion task");
    }

    @Test
    public void test_delete_local_small_file_locked_by_other_deletion_on_same_node() throws Exception {
        // Given
        FileDeletionRequest request = createDeletionRequest("node1", true, true, Optional.of("archive1"));
        FileDeletionRequest request2 = createDeletionRequest("node1", true, true, Optional.empty());
        FileDeletionRequest request3 = createDeletionRequest("node2", true, true, Optional.of("archive1"));
        // When
        glacier.doDeleteTask(request, Mockito.mock(IDeletionProgressManager.class), "tenant").call();
        glacier.doDeleteTask(request2, Mockito.mock(IDeletionProgressManager.class), "tenant").call();
        glacier.doDeleteTask(request3, Mockito.mock(IDeletionProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 2, " There should 2 different locks taken");
        Assert.isTrue(lockServiceMock.getWaitingLock().size() == 1,
                      " There should one lock deleteSmallTask waiting for lock");
        Assert.isTrue(lockServiceMock.getWaitingLock().containsValue(DeleteLocalSmallFileTask.class.getName()),
                      " There should one lock deleteSmallTask waiting for lock");
    }

    @Test
    public void test_delete_remote_small_file_locked_by_restore_on_same_archive() throws Exception {
        // Given
        FileCacheRequest restoreRequest = createRestoreFileRequest("node1", true, false, Optional.of("archive1"));
        FileDeletionRequest deleteRequest = createDeletionRequest("node1", true, false, Optional.of("archive1"));
        FileDeletionRequest deleteRequest2 = createDeletionRequest("node1", true, false, Optional.empty());
        // When
        glacier.doRetrieveTask(restoreRequest, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        glacier.doDeleteTask(deleteRequest, Mockito.mock(IDeletionProgressManager.class), "tenant").call();
        glacier.doDeleteTask(deleteRequest2, Mockito.mock(IDeletionProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 2, " There should 2 different locks taken");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(RetrieveCacheFileTask.class.getName()));
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(RestoreAndDeleteSmallFileTask.class.getName()));
        Assert.isTrue(lockServiceMock.getWaitingLock().size() == 1,
                      " There should one lock deleteSmallTask waiting for lock");
        Assert.isTrue(lockServiceMock.getWaitingLock().containsValue(RestoreAndDeleteSmallFileTask.class.getName()),
                      " There should one lock RetrieveCacheFileTask waiting for lock");
    }

    @Test
    public void test_delete_local_small_file_locked_by_store_small_file_on_same_node() throws Exception {
        // Given
        FileStorageRequest storeRequest = createStoreFileRequest("node1", true);
        FileStorageRequest storeRequest2 = createStoreFileRequest("node2", true);
        FileDeletionRequest deleteRequest = createDeletionRequest("node1", true, true, Optional.of("archive1"));
        FileDeletionRequest deleteRequest2 = createDeletionRequest("node2", true, true, Optional.of("archive1"));
        // When
        glacier.mockSmallFile();
        glacier.doStoreTask(storeRequest, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        glacier.doStoreTask(storeRequest2, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        glacier.doDeleteTask(deleteRequest, Mockito.mock(IDeletionProgressManager.class), "tenant").call();
        glacier.doDeleteTask(deleteRequest2, Mockito.mock(IDeletionProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 2, " There should one taken");
        Assert.isTrue(lockServiceMock.getWaitingLock().size() == 2, " There should one task waiting for lock");
        Assert.isTrue(lockServiceMock.getWaitingLock()
                                     .values()
                                     .stream()
                                     .allMatch(value -> Objects.equals(value,
                                                                       DeleteLocalSmallFileTask.class.getName())),
                      "The RestoreAndDeleteSmallFileTask should be waiting for lock");
        Assert.isTrue(lockServiceMock.getLockAcquired()
                                     .values()
                                     .stream()
                                     .allMatch(value -> value.equals(StoreSmallFileTask.class.getName())),
                      "The StoreSmallFileTask should be running");
    }

    @Test
    public void test_submit_rdy_archive_lock_other_store_request_on_same_node() throws Exception {
        // Given
        FileStorageRequest storeRequest = createStoreFileRequest("node1", true);
        // when
        glacier.mockSmallFile();
        glacier.doSubmitReadyArchive(Paths.get("/tmp/zip", ROOT_PATH, "node1"),
                                     Mockito.mock(IPeriodicActionProgressManager.class),
                                     "tenant",
                                     false).call();
        glacier.doStoreTask(storeRequest, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 1, " There should one taken");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(SubmitReadyArchiveTask.class.getName()));
        Assert.isTrue(lockServiceMock.getWaitingLock().size() == 1, " There should one taken");
        Assert.isTrue(lockServiceMock.getWaitingLock().containsValue(StoreSmallFileTask.class.getName()));
    }

    @Test
    public void test_submit_rdy_archive_do_not_lock_restore_if_building() throws Exception {
        // Given
        FileCacheRequest restoreRequest = createRestoreFileRequest("node1", true, false, Optional.of("archive1"));
        // when
        glacier.doSubmitReadyArchive(Paths.get("/tmp/zip", ROOT_PATH, "node1/rs_zip_archive1"),
                                     Mockito.mock(IPeriodicActionProgressManager.class),
                                     "tenant",
                                     false).call();
        glacier.doRetrieveTask(restoreRequest, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 2, " There should be two taken");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(SubmitReadyArchiveTask.class.getName()));
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(RetrieveCacheFileTask.class.getName()));
        Assert.isTrue(lockServiceMock.getWaitingLock().isEmpty(), "No task should be waiting for lock");
    }

    @Test
    public void test_submit_rdy_archive_lock_restore_if_updating() throws Exception {
        // Given
        FileCacheRequest restoreRequest = createRestoreFileRequest("node1", true, false, Optional.of("archive1"));
        // when
        glacier.doSubmitReadyArchive(Paths.get("/tmp/zip", ROOT_PATH, "node1/rs_zip_archive1"),
                                     Mockito.mock(IPeriodicActionProgressManager.class),
                                     "tenant",
                                     true).call();
        glacier.doRetrieveTask(restoreRequest, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 1, " There should one taken");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(SubmitUpdatedArchiveTask.class.getName()));
        Assert.isTrue(lockServiceMock.getWaitingLock().size() == 1, "One task should be waiting for a lock");
        Assert.isTrue(lockServiceMock.getWaitingLock().containsValue(RetrieveCacheFileTask.class.getName()));
    }

    @Test
    public void test_submit_rdy_archive_lock_other_restore_remote_local_file_request_on_same_archive_name()
        throws Exception {
        // Given
        FileStorageRequest storeRequest = createStoreFileRequest("node1", true);
        // When
        glacier.doCleanDirectory(Paths.get("/tmp/zip"),
                                 Paths.get("/tmp/zip", ROOT_PATH, "node1/rs_zip_archive1"),
                                 Instant.now(),
                                 "tenant").call();
        glacier.doStoreTask(storeRequest, Mockito.mock(IStorageProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 2, " There should be 2 locks taken");
        Assert.isTrue(lockServiceMock.getWaitingLock().size() == 0, " There should be 0 task waiting for lock");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(StoreSmallFileTask.class.getName()),
                      " There should be 1 task running");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(StoreSmallFileTask.class.getName()),
                      " There should be 1 task running");
    }

    @Test
    public void test_clean_directory_lock_restore_archive() throws Exception {
        // Given
        FileCacheRequest restoreRequest = createRestoreFileRequest("node1", true, false, Optional.of("archive1"));
        // When
        glacier.doCleanDirectory(Paths.get("/tmp/zip"),
                                 Paths.get("/tmp/zip", ROOT_PATH, "node1/rs_zip_archive1"),
                                 Instant.now(),
                                 "tenant").call();
        glacier.doRetrieveTask(restoreRequest, Mockito.mock(IRestorationProgressManager.class), "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 1, " There should be 1 locks taken");
        Assert.isTrue(lockServiceMock.getWaitingLock().size() == 1, " There should be 1 task waiting for lock");
        Assert.isTrue(lockServiceMock.getWaitingLock().containsValue(RetrieveCacheFileTask.class.getName()),
                      " There should be 1 task waiting for lock");
    }

    @Test
    public void test_check_pending_action_lock_restore_remote_file() throws Exception {
        // Given
        FileCacheRequest restoreRequest = createRestoreFileRequest("node1", true, false, Optional.of("archive1"));
        // When
        glacier.doCleanDirectory(Paths.get("/tmp/zip"),
                                 Paths.get("/tmp/zip", ROOT_PATH, "node1/rs_zip_archive1"),
                                 Instant.now(),
                                 "tenant").call();
        glacier.doCheckPendingAction(restoreRequest.getFileReference().getLocation().getUrl(),
                                     Mockito.mock(IPeriodicActionProgressManager.class),
                                     "tenant").call();
        // Then
        Assert.isTrue(lockServiceMock.getLockAcquired().size() == 1, " There should be 1 locks taken");
        Assert.isTrue(lockServiceMock.getLockAcquired().containsValue(CleanDirectoryTask.class.getName()),
                      " There should be 1 task running");
        Assert.isTrue(lockServiceMock.getWaitingLock().size() == 1, " There should be 1 task waiting for lock");
        Assert.isTrue(lockServiceMock.getWaitingLock().containsValue(CheckPendingActionTask.class.getName()),
                      " There should be 1 task waiting for lock");
    }

    private FileStorageRequest createStoreFileRequest(String node, boolean smallFile) {
        String checksum = "123456";
        String algorithm = "MD5";
        String fileName = "file.txt";
        Long fileSize = smallFile ? 10L : 50000000L;
        MimeType mimeType = MediaType.APPLICATION_OCTET_STREAM;
        FileReferenceMetaInfo metaInfos = new FileReferenceMetaInfo(checksum, algorithm, fileName, fileSize, mimeType);
        String owner = "owner";
        Optional<String> storageSubDirectory = Optional.of(node);
        String originUrl = "file:///tmp/file.txt";
        String storage = "storage";
        String groupId = "groupId";
        String sessionOwner = "sessionOwner";
        String session = "session";
        return new FileStorageRequest(owner,
                                      metaInfos,
                                      originUrl,
                                      storage,
                                      storageSubDirectory,
                                      groupId,
                                      sessionOwner,
                                      session);
    }

    private FileCacheRequest createRestoreFileRequest(String node, boolean smallFile, boolean local) {
        return createRestoreFileRequest(node, smallFile, local, Optional.empty());
    }

    private FileCacheRequest createRestoreFileRequest(String node,
                                                      boolean smallFile,
                                                      boolean local,
                                                      Optional<String> archiveName) {

        return new FileCacheRequest(createFileReference(node, smallFile, local, archiveName),
                                    "restorationDirectory",
                                    OffsetDateTime.now(),
                                    "groupId");
    }

    private FileReference createFileReference(String node,
                                              boolean smallFile,
                                              boolean local,
                                              Optional<String> archiveName) {
        FileReferenceMetaInfo metaInfo = new FileReferenceMetaInfo("123456789",
                                                                   "MD5",
                                                                   "file.txt",
                                                                   smallFile ? 10L : 5000000L,
                                                                   MediaType.APPLICATION_OCTET_STREAM);
        String fileName = (smallFile ?
            "/" + archiveName.orElse(String.valueOf(UUID.randomUUID())) + ".zip?fileName=" :
            "/") + UUID.randomUUID();
        FileLocation location = new FileLocation("storage",
                                                 "https://s3.datalake-qualif.cnes.fr/bucket/"
                                                 + ROOT_PATH
                                                 + "/"
                                                 + node
                                                 + fileName,
                                                 local);
        FileReference fileReference = new FileReference("owner", metaInfo, location);
        fileReference.setId(1L);
        return fileReference;
    }

    public FileDeletionRequest createDeletionRequest(String node,
                                                     boolean smallFile,
                                                     boolean local,
                                                     Optional<String> archiveName) {
        return new FileDeletionRequest(createFileReference(node, smallFile, local, archiveName),
                                       "groupeId",
                                       "sessionOwner",
                                       "session");
    }

}
