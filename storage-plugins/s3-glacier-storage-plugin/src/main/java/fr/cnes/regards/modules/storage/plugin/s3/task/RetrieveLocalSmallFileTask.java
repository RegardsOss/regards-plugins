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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

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

    private final RetrieveLocalSmallFileTaskConfiguration configuration;

    public RetrieveLocalSmallFileTask(RetrieveLocalSmallFileTaskConfiguration configuration,
                                      FileCacheRequest request,
                                      IRestorationProgressManager progressManager) {
        super(request, progressManager);
        this.configuration = configuration;
    }

    @Override
    public void run() {

        Path localPathWithArchiveDelimiter = Path.of(configuration.archiveBuildingWorkspacePath())
                                                 .resolve(configuration.fileRelativePath());
        String[] split = localPathWithArchiveDelimiter.getFileName()
                                                      .toString()
                                                      .split(Pattern.quote(S3Glacier.ARCHIVE_DELIMITER));
        String dirName = split[0].substring(0, split[0].indexOf(S3Glacier.ARCHIVE_EXTENSION));
        Path localPath = localPathWithArchiveDelimiter.getParent().resolve(dirName).resolve(split[1]);
        Path localPathCurrent = localPathWithArchiveDelimiter.getParent()
                                                             .resolve(dirName + S3Glacier.CURRENT_ARCHIVE_SUFFIX)
                                                             .resolve(split[1]);

        if (Files.exists(localPath)) {
            copyFileAndHandleSuccess(localPath);
        } else if (Files.exists(localPathCurrent)) {
            copyFileAndHandleSuccess(localPathCurrent);
        } else {
            progressManager.restoreFailed(request,
                                          String.format("The requested file %s was not found locally",
                                                        localPath.getFileName()));
        }
    }
}
