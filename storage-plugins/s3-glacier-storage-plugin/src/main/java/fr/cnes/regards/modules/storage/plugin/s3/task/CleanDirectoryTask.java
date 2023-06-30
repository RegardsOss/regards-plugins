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
import fr.cnes.regards.modules.storage.plugin.s3.configuration.CleanDirectoryTaskConfiguration;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Task cleaning a directory by removing all regular files older than a given age in it.
 * This task does nothing to directories (they won't be removed or entered to be cleaned)
 *
 * @author Thibaud Michaudel
 **/
public class CleanDirectoryTask implements LockServiceTask<Void> {

    private static final org.slf4j.Logger LOGGER = getLogger(CleanDirectoryTask.class);

    private final CleanDirectoryTaskConfiguration configuration;

    public CleanDirectoryTask(CleanDirectoryTaskConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Void run() {
        LOGGER.info("Starting CleanDirectoryTask on {}", configuration.directoryPath());
        long start = System.currentTimeMillis();
        boolean emptyDir = true;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configuration.directoryPath())) {
            for (Path path : stream) {
                BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
                if (attr.lastModifiedTime().toInstant().isBefore(configuration.oldestAgeToKeep())) {
                    //Delete the file only if it's too old
                    Files.delete(path);
                } else {
                    emptyDir = false;
                }
            }
            if (emptyDir) {
                Files.delete(configuration.directoryPath());
            }
        } catch (IOException e) {
            LOGGER.error("Error while deleting file {}", configuration.directoryPath(), e);
        }
        LOGGER.info("End of CleanDirectoryTask on {} after {} ms",
                    configuration.directoryPath(),
                    System.currentTimeMillis() - start);
        return null;
    }
}
