package fr.cnes.regards.modules.storage.plugins.s3.mock;

import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.modules.fileaccess.plugin.domain.NearlineDownloadException;
import fr.cnes.regards.modules.fileaccess.plugin.domain.NearlineFileNotAvailableException;
import fr.cnes.regards.modules.filecatalog.dto.FileReferenceWithoutOwnersDto;
import fr.cnes.regards.modules.filecatalog.dto.availability.NearlineFileStatusDto;
import fr.cnes.regards.modules.storage.plugin.s3.S3Glacier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Plugins that simulate CNES datalake access
 */
@Plugin(author = "REGARDS Team",
        description = "Plugin MOCK for handling the storage on S3",
        id = "S3GlacierMock",
        version = "1.0",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CNES",
        url = "https://regardsoss.github.io/")
public class S3GlacierPluginMock extends S3Glacier {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3GlacierPluginMock.class);

    @Override
    public InputStream download(FileReferenceWithoutOwnersDto fileReference)
        throws NearlineFileNotAvailableException, NearlineDownloadException {
        DatalakeStorageStatus status = DatalakeStorageStatus.getStatusOf(fileReference);
        LOGGER.info("[S3-MOCK] Try to download {} which is stored in {}",
                    fileReference.getMetaInfo().getFileName(),
                    status);
        return switch (status) {
            case T2, T3_RESTORED -> super.download(fileReference);
            case T3 -> throw new NearlineFileNotAvailableException("T3 file not downloadable");
            case NONE -> throw new NearlineDownloadException(" Not T3 or T2 file : " + fileReference.getMetaInfo()
                                                                                                    .getFileName());
        };
    }

    @Override
    public NearlineFileStatusDto checkAvailability(FileReferenceWithoutOwnersDto fileReference) {
        DatalakeStorageStatus status = DatalakeStorageStatus.getStatusOf(fileReference);
        LOGGER.info("[S3-MOCK] Check availability of {} which is stored in {}",
                    fileReference.getMetaInfo().getFileName(),
                    status);
        return switch (status) {
            case T2 -> new NearlineFileStatusDto(true, null, null);
            case T3_RESTORED -> new NearlineFileStatusDto(false, null, "This file is on T2 and needs to be restored");
            default -> new NearlineFileStatusDto(false, null, "Not on T2");
        };
    }
}
