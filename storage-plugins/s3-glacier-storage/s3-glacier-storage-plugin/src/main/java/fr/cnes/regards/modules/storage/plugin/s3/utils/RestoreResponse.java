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
package fr.cnes.regards.modules.storage.plugin.s3.utils;

import fr.cnes.regards.framework.s3.client.S3HighLevelReactiveClient;
import fr.cnes.regards.framework.s3.domain.GlacierFileStatus;
import fr.cnes.regards.framework.s3.domain.StorageConfig;

/**
 * Response from {@link S3GlacierUtils#restore(S3HighLevelReactiveClient, StorageConfig, String, String) GlacierUtils restore}
 *
 * @author Thibaud Michaudel
 **/
public record RestoreResponse(RestoreStatus status,
                              GlacierFileStatus glacierFileStatus,
                              Exception exception) {

    public RestoreResponse(RestoreStatus status, GlacierFileStatus glacierFileStatus) {
        this(status, glacierFileStatus, null);
    }

}
