package fr.cnes.regards.modules.catalog.stac.service;


import fr.cnes.regards.framework.jpa.multitenant.test.AbstractMultitenantServiceTest;
import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.exception.EntityOperationForbiddenException;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Catalog;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.service.RegardsStacCollectionConverter.IRegardsStacCollectionConverter;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.dto.properties.*;
import fr.cnes.regards.modules.search.service.CatalogSearchService;
import io.vavr.collection.TreeSet;
import io.vavr.control.Try;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

//@EnableWebMvc
@ActiveProfiles({"test", "feign"})
@TestPropertySource(locations = {"classpath:test.properties"},
        properties = {"spring.jpa.properties.hibernate.default_schema=public", "regards.elasticsearch.http.port=9200"
                , "regards.elasticsearch.host:172.26.47.52"})
public class RegardsStacCollectionConverterTest extends AbstractMultitenantServiceTest {

    @Autowired
    IRegardsStacCollectionConverter converter;

    @MockBean
    private CatalogSearchService catalogSearchService;

    @Test
    public void testConvertCollection() throws EntityOperationForbiddenException, EntityNotFoundException {
        Model model = Model.build("moduleName", "moduleDesc", EntityType.COLLECTION);
        fr.cnes.regards.modules.dam.domain.entities.Collection damCollection =
                new fr.cnes.regards.modules.dam.domain.entities.Collection(model, "perf", "providerId", "label");

        TreeSet<String> tags = TreeSet.of("URN:AIP:COLLECTION:perf:80282ac5-1b01-4e9d-a356-123456789012:V1");
        damCollection.setTags(tags.toJavaSet());
        when(catalogSearchService.get(any(UniformResourceName.class))).thenReturn(damCollection);

        Try<Collection> collections = converter.convertRequest("URN:AIP:COLLECTION:perf:80282ac5-1b01-4e9d-a356-34eb0a15a4e2:V1");
        assertThat(collections.isSuccess(), is(true));

        assertThat(collections.get().getTitle(), is("label"));
    }

    @Test
    public void testConvertDataset() throws EntityOperationForbiddenException, EntityNotFoundException {
        Model model = Model.build("moduleName", "moduleDesc", EntityType.DATASET);
        fr.cnes.regards.modules.dam.domain.entities.Dataset damDataset =
                new fr.cnes.regards.modules.dam.domain.entities.Dataset(model, "perf", "providerId", "label");

        TreeSet<String> tags = TreeSet.of("URN:AIP:DATASET:perf:80282ac5-1b01-4e9d-a356-123456789012:V1");
        damDataset.setTags(tags.toJavaSet());
        when(catalogSearchService.get(any(UniformResourceName.class))).thenReturn(damDataset);

        Try<Collection> collections = converter.convertRequest("URN:AIP:DATASET:perf:80282ac5-1b01-4e9d-a356-34eb0a15a4e2:V1");
        assertThat(collections.isSuccess(), is(true));

        assertThat(collections.get().getTitle(), is("label"));
    }

    @Test
    public void testConvertDatasetWithObjects() throws EntityOperationForbiddenException, EntityNotFoundException {
        Model model = Model.build("PEPS_DATASET_MODEL", "moduleDesc", EntityType.DATASET);
        fr.cnes.regards.modules.dam.domain.entities.Dataset damDataset =
                new fr.cnes.regards.modules.dam.domain.entities.Dataset(model, "perf", "Sentinel-1A",
                        "Sentinel-1A");

        TreeSet<String> tags = TreeSet.of("URN:AIP:DATASET:perf:80282ac5-1b01-4e9d-a356-123456789012:V1");
        damDataset.setTags(tags.toJavaSet());
        HashSet<IProperty<?>> properties = new HashSet<>();
        StringProperty integerProperty = new StringProperty();
        integerProperty.setName("programm");
        integerProperty.setValue("A1");
        StringProperty plateformProp = new StringProperty();
        plateformProp.setName("plateform");
        plateformProp.setValue("Sentinel1");
        properties.add(integerProperty);
        properties.add(plateformProp);
        damDataset.setProperties(properties);
//        damDataset.setL
        when(catalogSearchService.get(any(UniformResourceName.class))).thenReturn(damDataset);

        Try<Collection> collections = converter.convertRequest("URN:AIP:DATASET:perf:80282ac5-1b01-4e9d-a356-34eb0a15a4e2:V1");
        assertThat(collections.isSuccess(), is(true));

        assertThat(collections.get().getTitle(), is("label"));
    }


}
