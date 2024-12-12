package fr.cnes.regards.modules.storage.plugin.s3;

import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.s3.client.S3HighLevelReactiveClient;
import fr.cnes.regards.framework.s3.domain.StorageCommandID;
import fr.cnes.regards.framework.utils.file.DownloadUtils;
import fr.cnes.regards.modules.fileaccess.dto.FileReferenceWithoutOwnersDto;
import fr.cnes.regards.modules.fileaccess.dto.output.worker.FileNamingStrategy;
import fr.cnes.regards.modules.fileaccess.plugin.domain.*;
import fr.cnes.regards.modules.storage.plugin.s3.dto.S3StorageConfigurationDTO;
import fr.cnes.regards.modules.storage.s3.common.AbstractS3Storage;
import fr.cnes.regards.modules.storage.s3.common.dto.AbstractS3ConfigurationDto;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Main class of plugin of storage(online type) in S3 server
 */
@Plugin(author = "REGARDS Team",
        description = "Plugin handling the storage on S3",
        id = "S3",
        version = "1.0",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CNES",
        markdown = "S3StoragePlugin.md",
        url = "https://regardsoss.github.io/")
public class S3OnlineStorage extends AbstractS3Storage implements IOnlineStorageLocation {

    /**
     * Store a simple file workingsubsets in S3 server
     *
     * @param workingSet      the simple file workingsubsets
     * @param progressManager the progess manager
     */
    @Override
    public void store(FileStorageWorkingSubset workingSet, IStorageProgressManager progressManager) {
        String tenant = runtimeTenantResolver.getTenant();
        try (S3HighLevelReactiveClient client = createS3Client()) {
            workingSet.getFileReferenceRequests().forEach(request -> {
                runtimeTenantResolver.forceTenant(tenant);
                handleStoreRequest(request, client, progressManager);
            });
        }
    }

    /**
     * Retrieve a file reference by the downloading in S3 server
     *
     * @param fileReference the file reference
     * @return the input stream of file reference
     */
    @Override
    public InputStream retrieve(FileReferenceWithoutOwnersDto fileReference) throws FileNotFoundException {
        return DownloadUtils.getInputStreamFromS3Source(getEntryKey(fileReference),
                                                        storageConfiguration,
                                                        new StorageCommandID(String.format("%d", fileReference.getId()),
                                                                             UUID.randomUUID()));
    }

    /**
     * Delete a simple file workingsubsets in S3 server
     *
     * @param workingSet      the simple file workingsubsets
     * @param progressManager the progess manager
     */
    @Override
    public void delete(FileDeletionWorkingSubset workingSet, IDeletionProgressManager progressManager) {
        try (S3HighLevelReactiveClient client = createS3Client()) {
            workingSet.getFileDeletionRequests().forEach(r -> handleDeleteRequest(r, client, progressManager));
        }
    }

    @Override
    public AbstractS3ConfigurationDto createWorkerStoreConfiguration() {
        return new S3StorageConfigurationDTO(storageConfiguration,
                                             multipartThresholdMb,
                                             nbParallelPartsUpload,
                                             FileNamingStrategy.valueOf(fileNamingStrategy));
    }

}
