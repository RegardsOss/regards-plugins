package fr.cnes.regards.modules.catalog.stac.service;


import fr.cnes.regards.framework.jpa.multitenant.test.AbstractMultitenantServiceTest;
import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.exception.EntityOperationForbiddenException;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.accessrights.domain.projects.Role;
import fr.cnes.regards.modules.catalog.stac.domain.properties.PropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.AbstractPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Catalog;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.service.RegardsStacCollectionConverter.IRegardsStacCollectionConverter;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.Dataset;
import fr.cnes.regards.modules.indexer.domain.aggregation.QueryableAttribute;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.dto.properties.*;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.CatalogSearchService;
import fr.cnes.regards.modules.search.service.CollectionWithStats;
import fr.cnes.regards.modules.search.service.SearchException;
import io.vavr.collection.List;
import io.vavr.collection.TreeSet;
import io.vavr.control.Try;
import org.assertj.core.util.Lists;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.InternalAggregations;
import org.elasticsearch.search.aggregations.InternalOrder;
import org.elasticsearch.search.aggregations.bucket.range.InternalDateRange;
import org.elasticsearch.search.aggregations.bucket.range.ParsedDateRange;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.metrics.stats.ParsedStats;
import org.junit.Test;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;
import org.locationtech.spatial4j.io.GeoJSONReader;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
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

    @MockBean
    private ConfigurationAccessorFactory configurationAccessorFactory;
    @Test
    public void testConvertCollection() throws EntityOperationForbiddenException, EntityNotFoundException, SearchException, MalformedURLException {
        Model model = Model.build("moduleName", "moduleDesc", EntityType.COLLECTION);
        fr.cnes.regards.modules.dam.domain.entities.Collection collection =
                new fr.cnes.regards.modules.dam.domain.entities.Collection(model, "perf", "providerId", "label");
        collection.setId(123455L);
        ParsedDateRange mock = Mockito.mock(ParsedDateRange.class);
        ArrayList arrayListMock = Mockito.mock(ArrayList.class);
        when(mock.getBuckets()).thenReturn(arrayListMock);
        doReturn(Mockito.mock(Range.Bucket.class)).when(arrayListMock).get(anyInt());
        when(mock.getBuckets().get(anyInt()).getFrom()).thenReturn(0L);
        when(mock.getBuckets().get(anyInt()).getTo()).thenReturn(Long.MAX_VALUE);
        java.util.List<Aggregation> parsedStats = Arrays.asList(mock);
        CollectionWithStats damCollection = new CollectionWithStats(collection, parsedStats);

        TreeSet<String> tags = TreeSet.of("URN:AIP:COLLECTION:perf:80282ac5-1b01-4e9d-a356-123456789012:V1");
        damCollection.getCollection().setTags(tags.toJavaSet());
        when(catalogSearchService.getCollectionWithDataObjectsStats(any(UniformResourceName.class),
                any(SearchType.class), any(java.util.Collection.class))).thenReturn(damCollection);

        ConfigurationAccessor value = Mockito.mock(ConfigurationAccessor.class);
        when(value.getGeoJSONReader()).thenReturn(new GeoJSONReader(SpatialContext.GEO, Mockito.mock(SpatialContextFactory.class)));
        when(value.getKeywords(anyString())).thenReturn(List.of("keywords"));
        when(value.getProviders(anyString())).thenReturn(List.of(new Provider("prov", "desc", new URL("http","localhost",1234,"file"), List.of(Provider.ProviderRole.HOST))));
        when(value.getLicense(anyString())).thenReturn("licence");
        StacProperty stacProperty = new StacProperty("model",
                "stacProp",
                "ext",
                false,
                0,
                PropertyType.BBOX,
                Mockito.mock(AbstractPropertyConverter.class));
        when(value.getStacProperties()).thenReturn(List.of(stacProperty));
        when(configurationAccessorFactory.makeConfigurationAccessor()).thenReturn(value);

        Try<Collection> collections = converter.convertRequest("URN:AIP:COLLECTION:perf:80282ac5-1b01-4e9d-a356-34eb0a15a4e2:V1");
        assertThat(collections.isSuccess(), is(true));

        assertThat(collections.get().getTitle(), is("label"));
    }

    @Test
    public void testConvertDataset() throws EntityOperationForbiddenException, EntityNotFoundException {
        Model model = Model.build("moduleName", "moduleDesc", EntityType.DATASET);
        Dataset dataset = new Dataset(model, "perf", "providerId", "label");
        java.util.List<Aggregation> parsedStats = Arrays.asList(new ParsedStats());

//        CollectionWithStats damDataset = new CollectionWithStats(Dataset, parsedStats);

        TreeSet<String> tags = TreeSet.of("URN:AIP:DATASET:perf:80282ac5-1b01-4e9d-a356-123456789012:V1");
//        damDataset.getCollection().setTags(tags.toJavaSet());
//        when(catalogSearchService.getCollectionWithDataObjectsStats(any(UniformResourceName.class),
//                any(SearchType.class), any(java.util.Collection.class))).thenReturn(damCollection);


//        Try<Collection> collections = converter.convertRequest("URN:AIP:DATASET:perf:80282ac5-1b01-4e9d-a356-34eb0a15a4e2:V1");
//        assertThat(collections.isSuccess(), is(true));

//        assertThat(collections.get().getTitle(), is("label"));
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

//        Try<Collection> collections = converter.convertRequest("URN:AIP:DATASET:perf:80282ac5-1b01-4e9d-a356-34eb0a15a4e2:V1");
//        assertThat(collections.isSuccess(), is(true));

//        assertThat(collections.get().getTitle(), is("label"));
    }


}
