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

import fr.cnes.regards.framework.jpa.multitenant.lock.LockServiceTask;
import fr.cnes.regards.modules.storage.domain.database.request.FileCacheRequest;
import fr.cnes.regards.modules.storage.domain.plugin.IRestorationProgressManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Base class for retrieve tasks
 *
 * @author Thibaud Michaudel
 **/
public abstract class AbstractRetrieveFileTask implements LockServiceTask<Void> {

    private static final Logger LOGGER = getLogger(AbstractRetrieveFileTask.class);

    protected FileCacheRequest request;

    protected IRestorationProgressManager progressManager;

    protected AbstractRetrieveFileTask(FileCacheRequest request, IRestorationProgressManager progressManager) {
        this.request = request;
        this.progressManager = progressManager;
    }

    protected void copyFileAndHandleSuccess(Path localFilePath) {
        Path targetPath = Path.of(request.getRestorationDirectory()).resolve(localFilePath.getFileName());
        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(localFilePath, targetPath);
            progressManager.restoreSucceed(request, targetPath);
        } catch (IOException e) {
            LOGGER.error("Error while copying {} to {}", localFilePath, targetPath, e);
            progressManager.restoreFailed(request,
                                          String.format("The requested file %s was found locally but the"
                                                        + " copy to the target directory %s failed",
                                                        localFilePath,
                                                        targetPath));
        }
    }
}
