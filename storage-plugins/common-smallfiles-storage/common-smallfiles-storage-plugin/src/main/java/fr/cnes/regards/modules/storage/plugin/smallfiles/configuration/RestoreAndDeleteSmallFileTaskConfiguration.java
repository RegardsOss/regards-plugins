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
package fr.cnes.regards.modules.storage.plugin.smallfiles.configuration;

import fr.cnes.regards.framework.jpa.multitenant.lock.LockService;
import fr.cnes.regards.modules.storage.plugin.smallfiles.ISmallFilesStorage;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Data of configuration for the task in order to restore and to delete small file in cache
 *
 * @author Thibaud Michaudel
 **/
public record RestoreAndDeleteSmallFileTaskConfiguration(Path fileRelativePath,
                                                         String cachePath,
                                                         String archiveBuildingWorkspacePath,
                                                         ISmallFilesStorage interfaceSmallFiles,
                                                         String lockName,
                                                         Instant lockCreationDate,
                                                         int renewMaxIterationWaitingPeriodInS,
                                                         long renewDuration,
                                                         LockService lockService) {

}
