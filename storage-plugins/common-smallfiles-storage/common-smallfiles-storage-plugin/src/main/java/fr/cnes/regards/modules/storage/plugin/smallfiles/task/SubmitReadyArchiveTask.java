/*
 * Copyright 2017-2024 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.storage.plugin.smallfiles.task;

import fr.cnes.regards.modules.fileaccess.plugin.domain.IPeriodicActionProgressManager;
import fr.cnes.regards.modules.storage.plugin.smallfiles.ISmallFilesStorage;
import fr.cnes.regards.modules.storage.plugin.smallfiles.configuration.SubmitReadyArchiveTaskConfiguration;
import fr.cnes.regards.modules.storage.plugin.smallfiles.utils.SmallFilesUtils;
import org.apache.commons.lang3.time.DateUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Submit a small file archive that is either full or old enough to be closed even if not full :
 * <ul>
 *   <li>If its the _current directory and its old enough, close it</li>
 *   <li>Create the zip archive from the directory</li>
 *   <li>Send the zip archive to the glacier server</li>
 *   <li>Send the upload result through the progress manager for each file contained in the archive</li>
 * </ul>
 * The result of the upload is sent through the progress manager using
 * {@link IPeriodicActionProgressManager#storagePendingActionSucceed(String)} if it was successful or
 * {@link IPeriodicActionProgressManager#storagePendingActionError(Path)} otherwise
 *
 * @author Thibaud Michaudel
 **/
public class SubmitReadyArchiveTask extends AbstractSubmitArchiveTask {

    public SubmitReadyArchiveTask(SubmitReadyArchiveTaskConfiguration configuration,
                                  IPeriodicActionProgressManager progressManager) {
        super(configuration, progressManager);
    }

    @Override
    public Boolean run() {
        LOGGER.info("Starting CleanDirectoryTask on {}", configuration.dirPath());
        long start = System.currentTimeMillis();
        // Renaming _current if needed
        Optional<PathAndSuccessState> optionalFinalDirPath = renameCurrentIfNeeded(configuration.dirPath());
        if (optionalFinalDirPath.isEmpty()) {
            // It's the _current dir, and it is not old enough to send
            LOGGER.info("End of SubmitReadyArchiveTask on {} after {} ms",
                        configuration.dirPath(),
                        System.currentTimeMillis() - start);
            return true;
        }
        PathAndSuccessState finalDirPath = optionalFinalDirPath.get();

        boolean success = handleArchiveToSend(finalDirPath.dirPath());
        LOGGER.info("End of SubmitReadyArchiveTask on {} after {} ms",
                    configuration.dirPath(),
                    System.currentTimeMillis() - start);

        return success;
    }

    /**
     * Rename the _current directory if it is too old and return the new path
     */
    private Optional<PathAndSuccessState> renameCurrentIfNeeded(Path dirPath) {
        boolean continueOk = true;
        if (dirPath.getFileName().toString().endsWith(ISmallFilesStorage.CURRENT_ARCHIVE_SUFFIX)) {
            String currentName = dirPath.getFileName().toString();
            String nameWithoutSuffix = SmallFilesUtils.removeSuffix(currentName);
            try {
                Instant dirCreationDate = DateUtils.parseDate(SmallFilesUtils.removePrefix(nameWithoutSuffix),
                        ISmallFilesStorage.ARCHIVE_DATE_FORMAT).toInstant();

                if (dirCreationDate.plus(configuration.archiveMaxAge(), ChronoUnit.HOURS).isAfter(Instant.now())) {
                    // the directory is not old enough, nothing to do
                    return Optional.empty();
                }
                Path newDirPath = dirPath.getParent().resolve(nameWithoutSuffix);
                continueOk = dirPath.toFile().renameTo(newDirPath.toFile());
                dirPath = newDirPath;
                if (!continueOk) {
                    LOGGER.error("Error while renaming current building directory {}", currentName);
                }
            } catch (ParseException e) {
                LOGGER.error("Error while parsing directory name as a date : {}", nameWithoutSuffix, e);
            }
        }
        return Optional.of(new PathAndSuccessState(dirPath, continueOk));
    }

    @Override
    protected boolean handleEmptyDir(Path dirPath) {
        try {
            Files.delete(dirPath);
            return true;
        } catch (IOException e) {
            LOGGER.error("Error while deleting empty dir {}", dirPath, e);
            return false;
        }
    }
}
