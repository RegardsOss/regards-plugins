package fr.cnes.regards.modules.storage.plugins.s3.mock;

import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.modules.fileaccess.dto.FileReferenceWithoutOwnersDto;
import fr.cnes.regards.modules.fileaccess.dto.availability.NearlineFileStatusDto;
import fr.cnes.regards.modules.fileaccess.plugin.domain.NearlineDownloadException;
import fr.cnes.regards.modules.fileaccess.plugin.domain.NearlineFileNotAvailableException;
import fr.cnes.regards.modules.storage.plugin.s3.S3Glacier;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

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

    private static final Logger LOGGER = getLogger(S3GlacierPluginMock.class);

    @Autowired
    private S3MockService s3MockService;

    @Override
    public InputStream download(FileReferenceWithoutOwnersDto fileReference)
        throws NearlineFileNotAvailableException, NearlineDownloadException {
        LOGGER.info("[S3-GLACIER-MOCK-PLUGIN] ask download of file {}", fileReference.getLocation().getUrl());
        s3MockService.throwIfCannotDownload(fileReference);
        return super.download(fileReference);
    }

    @Override
    public List<NearlineFileStatusDto> checkAvailability(List<FileReferenceWithoutOwnersDto> fileReferences) {
        List<NearlineFileStatusDto> results = new ArrayList<>();
        List<FileReferenceWithoutOwnersDto> filesToCheck = new ArrayList<>();
        for (FileReferenceWithoutOwnersDto fileReference : fileReferences) {
            LOGGER.info("[S3-GLACIER-MOCK-PLUGIN] check availability of file {}", fileReference.getLocation().getUrl());
            Optional<NearlineFileStatusDto> oNearlineFileStatusDto = s3MockService.checkAvailability(fileReference);
            if (oNearlineFileStatusDto.isPresent()) {
                results.add(oNearlineFileStatusDto.get());
            } else {
                filesToCheck.add(fileReference);
            }
        }
        results.addAll(super.checkAvailability(filesToCheck));

        return results;
    }
}
