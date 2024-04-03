package fr.cnes.regards.modules.storage.plugin.s3.configuration;/*
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

import fr.cnes.regards.framework.s3.client.S3HighLevelReactiveClient;
import fr.cnes.regards.framework.s3.dto.StorageConfigDto;
import fr.cnes.regards.modules.fileaccess.plugin.domain.IPeriodicActionProgressManager;

import java.nio.file.Path;

/**
 * @author Thibaud Michaudel
 **/
public record SubmitReadyArchiveTaskConfiguration(Path dirPath,
                                                  String workspacePath,
                                                  int archiveMaxAge,
                                                  String rootPath,
                                                  String storageName,
                                                  StorageConfigDto storageConfiguration,
                                                  int multipartThresholdMb,
                                                  IPeriodicActionProgressManager progressManager,
                                                  String tenant,
                                                  S3HighLevelReactiveClient s3client) {

}
