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

import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.feature.client.IFeatureEntityClient;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.feature.dto.FeatureEntityDto;
import fr.cnes.regards.modules.feature.dto.FeatureFile;
import fr.cnes.regards.modules.feature.dto.FeatureFileAttributes;
import fr.cnes.regards.modules.feature.dto.urn.FeatureIdentifier;
import fr.cnes.regards.modules.feature.dto.urn.FeatureUniformResourceName;
import fr.cnes.regards.modules.fileaccess.dto.StorageLocationConfigurationDto;
import fr.cnes.regards.modules.filecatalog.dto.StorageLocationDto;
import fr.cnes.regards.modules.project.client.rest.IProjectsClient;
import fr.cnes.regards.modules.project.domain.Project;
import fr.cnes.regards.modules.storage.client.IStorageLocationRestClient;
import fr.cnes.regards.modules.storage.client.IStorageRestClient;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.SlicedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeType;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * @author Sébastien Binda
 **/
@Configuration
public class TestConfiguration {

    public static final FeatureUniformResourceName URN = FeatureUniformResourceName.build(FeatureIdentifier.FEATURE,
                                                                                          EntityType.DATA,
                                                                                          "tenantTest",
                                                                                          UUID.randomUUID(),
                                                                                          1);

    @Bean
    public IFeatureEntityClient featureEntityRawClient() {
        IFeatureEntityClient mock = Mockito.mock(IFeatureEntityClient.class);
        List<EntityModel<FeatureEntityDto>> entities = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            entities.add(EntityModel.of(FeatureEntityDto.build("source",
                                                               "session",
                                                               Feature.build(String.format("id_%d", i),
                                                                             "owner",
                                                                             URN,
                                                                             null,
                                                                             EntityType.DATA,
                                                                             "model"),
                                                               String.format("provider_id_%d", i),
                                                               1,
                                                               OffsetDateTime.now())));
        }
        attachAdditionalFieldsToEntities(entities);
        SlicedModel<EntityModel<FeatureEntityDto>> slicedModel = SlicedModel.of(entities,
                                                                                new SlicedModel.SliceMetadata(10, 10));
        ResponseEntity<SlicedModel<EntityModel<FeatureEntityDto>>> response = ResponseEntity.of(Optional.of(slicedModel));
        Mockito.when(mock.findAllSlice(Mockito.anyString(),
                                       Mockito.any(OffsetDateTime.class),
                                       Mockito.any(OffsetDateTime.class),
                                       Mockito.anyInt(),
                                       Mockito.anyInt(),
                                       Mockito.any(Sort.class),
                                       Mockito.anyBoolean())).thenReturn(response);
        return mock;
    }

    private void attachAdditionalFieldsToEntities(List<EntityModel<FeatureEntityDto>> entities) {
        FeatureFile featureFile = FeatureFile.build(FeatureFileAttributes.build(DataType.RAWDATA,
                                                                                MimeType.valueOf(
                                                                                    "application/octet-stream"),
                                                                                "filename",
                                                                                1L,
                                                                                "algorithm",
                                                                                "checksum"),
                                                    new AdditionalFieldRecord("totoValue"));
        entities.stream().map(EntityModel::getContent).filter(Objects::nonNull).forEach(entity -> {
            entity.getFeature().withFiles(featureFile);
        });
    }

    @Bean
    public IStorageRestClient storageRestClient() {
        return Mockito.mock(IStorageRestClient.class);
    }

    @Bean
    public IStorageLocationRestClient storageLocationRestClient() {
        IStorageLocationRestClient mock = Mockito.mock(IStorageLocationRestClient.class);
        List<EntityModel<StorageLocationDto>> list = List.of(EntityModel.of(StorageLocationDto.build("location",
                                                                                                     new StorageLocationConfigurationDto(
                                                                                                         "location",
                                                                                                         null,
                                                                                                         1L))));
        ResponseEntity<List<EntityModel<StorageLocationDto>>> response = ResponseEntity.of(Optional.of(list));
        Mockito.when(mock.retrieve()).thenReturn(response);
        return mock;
    }

    @Bean
    public IProjectsClient projectsClient() {
        IProjectsClient mock = Mockito.mock(IProjectsClient.class);
        Project project = new Project("desc", "icon", true, "name");
        ResponseEntity<EntityModel<Project>> response = ResponseEntity.of(Optional.of(EntityModel.of(project)));
        Mockito.when(mock.retrieveProject(Mockito.anyString())).thenReturn(response);
        return mock;
    }

    public record AdditionalFieldRecord(String totoKey) {
        // NOSONAR
    }
}
