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
package fr.cnes.regards.modules.dam.plugins.datasources;

import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.feature.client.IFeatureEntityClient;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.feature.dto.FeatureEntityDto;
import fr.cnes.regards.modules.project.client.rest.IProjectsClient;
import fr.cnes.regards.modules.storage.client.IStorageLocationRestClient;
import fr.cnes.regards.modules.storage.client.IStorageRestClient;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Sébastien Binda
 **/
@Configuration
public class TestConfiguration {

    @Bean
    public IFeatureEntityClient featureEntityClient() {
        IFeatureEntityClient mock = Mockito.mock(IFeatureEntityClient.class);
        List<EntityModel<FeatureEntityDto>> entities = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            entities.add(EntityModel.of(FeatureEntityDto.build("source",
                                                               "session",
                                                               Feature.build(String.format("id_%d", i),
                                                                             "owner",
                                                                             null,
                                                                             null,
                                                                             EntityType.DATA,
                                                                             "model"),
                                                               String.format("provider_id_%d", i),
                                                               1,
                                                               OffsetDateTime.now())));
        }
        PagedModel<EntityModel<FeatureEntityDto>> pagedModel = PagedModel.of(entities,
                                                                             new PagedModel.PageMetadata(10,
                                                                                                         10,
                                                                                                         10,
                                                                                                         1));
        ResponseEntity<PagedModel<EntityModel<FeatureEntityDto>>> response = ResponseEntity.of(Optional.of(pagedModel));
        Mockito.when(mock.findAll(Mockito.anyString(),
                                  Mockito.any(OffsetDateTime.class),
                                  Mockito.any(OffsetDateTime.class),
                                  Mockito.anyInt(),
                                  Mockito.anyInt(),
                                  Mockito.any(Sort.class))).thenReturn(response);
        return mock;
    }

    @Bean
    public IStorageRestClient storageRestClient() {
        return Mockito.mock(IStorageRestClient.class);
    }

    @Bean
    public IStorageLocationRestClient storageLocationRestClient() {
        return Mockito.mock(IStorageLocationRestClient.class);
    }

    @Bean
    public IProjectsClient projectsClient() {
        return Mockito.mock(IProjectsClient.class);
    }

}
