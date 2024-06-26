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
package fr.cnes.regards.modules.storage.s3.common.service;

import fr.cnes.regards.framework.s3.client.S3HighLevelReactiveClient;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Service to create {@link S3HighLevelReactiveClient s3 clients}. The client reactor scheduler will be named
 * <i>storageName</i>-s3-reactive-client
 * @author Thibaud Michaudel
 **/
@Service
public class S3ClientCreatorService {

    /**
     * Create a new {@link S3HighLevelReactiveClient s3 client}
     */
    public S3HighLevelReactiveClient createS3Client(String storageName, int multipartThresholdMb, int nbParallelPartsUpload) {
        Scheduler scheduler = Schedulers.newParallel(String.format("%s-s3-reactive-client", storageName), 10);
        int maxBytesPerPart = multipartThresholdMb * 1024 * 1024;
        return new S3HighLevelReactiveClient(scheduler, maxBytesPerPart, nbParallelPartsUpload);
    }
}
