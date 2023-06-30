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

import fr.cnes.regards.modules.storage.domain.database.request.FileCacheRequest;
import fr.cnes.regards.modules.storage.domain.plugin.IRestorationProgressManager;
import fr.cnes.regards.modules.storage.plugin.s3.S3Glacier;
import fr.cnes.regards.modules.storage.plugin.s3.configuration.RetrieveLocalSmallFileTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.s3.utils.S3GlacierUtils;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Task to retrieve small file that have yet to be sent to the server for S3 Glacier
 * <ul>
 * <li>If it exists, copy the file to the target location and send a success result to the progress manager</li>
 * <li>If it doesnt, send a failed result to the progress manager</li>
 * </ul>
 *
 * @author Thibaud Michaudel
 **/
public class RetrieveLocalSmallFileTask extends AbstractRetrieveFileTask {

    private static final Logger LOGGER = getLogger(RetrieveLocalSmallFileTask.class);

    private final RetrieveLocalSmallFileTaskConfiguration configuration;

    public RetrieveLocalSmallFileTask(RetrieveLocalSmallFileTaskConfiguration configuration,
                                      FileCacheRequest request,
                                      IRestorationProgressManager progressManager) {
        super(request, progressManager);
        this.configuration = configuration;
    }

    @Override
    public Void run() {
        LOGGER.info("Starting RetrieveLocalSmallFileTask on {}", configuration.fileRelativePath());
        long start = System.currentTimeMillis();
        Path localPathWithArchiveDelimiter = Path.of(configuration.archiveBuildingWorkspacePath())
                                                 .resolve(configuration.fileRelativePath());

        S3GlacierUtils.S3GlacierUrl fileInfos = S3GlacierUtils.dispatchS3FilePath(localPathWithArchiveDelimiter.getFileName()
                                                                                                               .toString());
        String archiveName = fileInfos.archiveFilePath();
        Optional<String> smallFileName = fileInfos.smallFileNameInArchive();

        if (smallFileName.isPresent()) {
            String dirName = S3GlacierUtils.createBuildDirectoryFromArchiveName(archiveName);
            Path localPath = localPathWithArchiveDelimiter.getParent().resolve(dirName).resolve(smallFileName.get());
            Path localPathCurrent = localPathWithArchiveDelimiter.getParent()
                                                                 .resolve(dirName + S3Glacier.CURRENT_ARCHIVE_SUFFIX)
                                                                 .resolve(smallFileName.get());

            if (Files.exists(localPath)) {
                copyFileAndHandleSuccess(localPath);
            } else if (Files.exists(localPathCurrent)) {
                copyFileAndHandleSuccess(localPathCurrent);
            } else {
                progressManager.restoreFailed(request,
                                              String.format("The requested file %s was not found locally",
                                                            localPath.getFileName()));
            }
        } else {
            progressManager.restoreFailed(request,
                                          String.format("The requested file %s was not found locally. Url "
                                                        + "does not match a smallFile url with %s parameter",
                                                        S3Glacier.SMALL_FILE_PARAMETER_NAME,
                                                        localPathWithArchiveDelimiter));
        }
        LOGGER.info("End of RetrieveLocalSmallFileTask on {} after {} ms",
                    configuration.fileRelativePath(),
                    System.currentTimeMillis() - start);
        return null;
    }
}
