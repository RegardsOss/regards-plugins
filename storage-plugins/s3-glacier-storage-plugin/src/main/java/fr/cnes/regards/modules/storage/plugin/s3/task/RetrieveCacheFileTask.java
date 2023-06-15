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
package fr.cnes.regards.modules.storage.plugin.s3.task;

import fr.cnes.regards.framework.utils.file.ZipUtils;
import fr.cnes.regards.modules.storage.domain.database.request.FileCacheRequest;
import fr.cnes.regards.modules.storage.domain.plugin.IRestorationProgressManager;
import fr.cnes.regards.modules.storage.plugin.s3.S3Glacier;
import fr.cnes.regards.modules.storage.plugin.s3.configuration.RetrieveCacheFileTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.s3.utils.S3GlacierUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import software.amazon.awssdk.services.s3.model.RestoreObjectResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Task to retrieve a file (no matter the size) from the server for the S3 Glacier.
 * Before asking the server, we check if the file or the containing archive is already present il the local cache.
 * <ul>
 * <li>If the file is present in the cache, copy it and send a success to the progress manager</li>
 * <li>If the containing archive is present in the cache, extract the file, then copy it and send a success to the
 * progress manager</li>
 * </ul>
 * Otherwise, send a restoration request to the server and wait for the file to be available to download
 * <ul>
 * <li>If the file is available, download it to the cache directory, copy it and send a success to the progress
 * manager</li>
 * <li>If the file isn't available after the given timeout, send a failure to the progress manager</li>
 * </ul>
 *
 * @author Thibaud Michaudel
 **/
public class RetrieveCacheFileTask extends AbstractRetrieveFileTask {

    private static final Logger LOGGER = getLogger(RetrieveCacheFileTask.class);

    public static final int INITIAL_DELAY = 1000;

    private RetrieveCacheFileTaskConfiguration configuration;

    public RetrieveCacheFileTask(RetrieveCacheFileTaskConfiguration configuration,
                                 FileCacheRequest request,
                                 IRestorationProgressManager progressManager) {
        super(request, progressManager);
        this.configuration = configuration;
    }

    @Override
    public void run() {
        if (configuration.isSmallFile()) {
            retrieveSmallFile();
        } else {
            retrieveBigFile();
        }
    }

    private void retrieveSmallFile() {
        Path fileCachePathWithArchiveDelimiter = Path.of(configuration.cachePath())
                                                     .resolve(configuration.fileRelativePath());
        String[] split = fileCachePathWithArchiveDelimiter.getFileName()
                                                          .toString()
                                                          .split(Pattern.quote(S3Glacier.ARCHIVE_DELIMITER));
        String archiveName = split[0];
        String dirName = archiveName.substring(0, archiveName.indexOf(S3Glacier.ARCHIVE_EXTENSION));
        Path localPath = fileCachePathWithArchiveDelimiter.getParent().resolve(dirName).resolve(split[1]);
        if (Files.exists(localPath)) {
            copyFileAndHandleSuccess(localPath);
            return;
        }
        // Also check if the archive already exists
        Path archivePath = fileCachePathWithArchiveDelimiter.getParent().resolve(archiveName);
        if (Files.exists(archivePath)) {
            extractThenCopyFileAndHandleSuccess(localPath, archivePath);
            return;
        }

        String filePathWithArchive = configuration.fileRelativePath().toString();
        String archiveRelativePath = filePathWithArchive.substring(0,
                                                                   filePathWithArchive.indexOf(S3Glacier.ARCHIVE_DELIMITER));
        String key = StringUtils.isNotBlank(configuration.rootPath()) ?
            configuration.rootPath() + File.separator + archiveRelativePath :
            archiveRelativePath;

        // Restore
        configuration.s3Client()
                     .restore(configuration.s3Configuration(), key)
                     .doOnError(e -> LOGGER.warn("The requested file {} is present but its storage class is not "
                                                 + "the expected one, the restoration process will continue as if"
                                                 + ".", request.getFileReference().getLocation().getUrl()))
                     .onErrorReturn(RestoreObjectResponse.builder().build())
                     .block();

        // Launch check restoration process
        boolean restorationComplete = S3GlacierUtils.checkRestorationComplete(Path.of(configuration.cachePath(),
                                                                                      archiveRelativePath),
                                                                              key,
                                                                              configuration.s3Configuration(),
                                                                              configuration.s3AccessTimeout(),
                                                                              configuration.lockName(),
                                                                              configuration.lockCreationDate(),
                                                                              configuration.renewDuration(),
                                                                              configuration.lockService());

        if (restorationComplete) {
            extractThenCopyFileAndHandleSuccess(localPath, archivePath);
        } else {
            progressManager.restoreFailed(request, "Error while trying to restore file, timeout exceeded");
        }
    }

    private void retrieveBigFile() {
        String key = StringUtils.isNotBlank(configuration.rootPath()) ?
            configuration.rootPath() + File.separator + configuration.fileRelativePath().toString() :
            configuration.fileRelativePath().toString();

        // Restore
        configuration.s3Client()
                     .restore(configuration.s3Configuration(), key)
                     .doOnError(e -> LOGGER.warn("The requested file {} is present but its storage class is not "
                                                 + "the expected one, the restoration process will continue as if"
                                                 + ".", request.getFileReference().getLocation().getUrl()))
                     .onErrorReturn(RestoreObjectResponse.builder().build())
                     .block();

        // Launch check restoration process
        Path targetPath = Path.of(request.getRestorationDirectory(),
                                  configuration.fileRelativePath().getFileName().toString());
        boolean restorationComplete = S3GlacierUtils.checkRestorationComplete(targetPath,
                                                                              key,
                                                                              configuration.s3Configuration(),
                                                                              configuration.s3AccessTimeout(),
                                                                              configuration.lockName(),
                                                                              configuration.lockCreationDate(),
                                                                              configuration.renewDuration(),
                                                                              configuration.lockService());
        if (restorationComplete) {
            progressManager.restoreSucceed(request, targetPath);
        } else {
            progressManager.restoreFailed(request, "Error while trying to restore file, timeout exceeded");
        }
    }

    private void extractThenCopyFileAndHandleSuccess(Path localPath, Path archivePath) {
        try {
            ZipUtils.extractFile(archivePath, localPath.getFileName().toString(), localPath.getParent());
            if (Files.exists(localPath)) {
                copyFileAndHandleSuccess(localPath);
            } else {
                progressManager.restoreFailed(request,
                                              String.format("The requested file %s is not present in the archive %s",
                                                            localPath.getFileName().toString(),
                                                            archivePath.getFileName().toString()));
            }
        } catch (IOException e) {
            LOGGER.error("Error while extracting file {} from archive {}", localPath, archivePath, e);
            progressManager.restoreFailed(request,
                                          String.format(
                                              "Error when trying to extract the requested file %s from the archive %s",
                                              localPath.getFileName().toString(),
                                              archivePath.getFileName().toString()));

        }
    }
}
