package fr.cnes.regards.modules.catalog.stac.service;


import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.exception.EntityOperationForbiddenException;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.AbstractPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.service.collection.Static.IStaticCollectionService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.search.domain.plugin.CollectionWithStats;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.CatalogSearchService;
import fr.cnes.regards.modules.search.service.SearchException;
import io.vavr.collection.List;
import io.vavr.collection.TreeSet;
import io.vavr.control.Try;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.bucket.range.ParsedDateRange;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;
import org.locationtech.spatial4j.io.GeoJSONReader;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ActiveProfiles({"test"})
@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = { RegardsStacCollectionConverterTest.ScanningConfiguration.class })
public class RegardsStacCollectionConverterTest{

    @Configuration
    @ComponentScan(basePackageClasses = {IStaticCollectionService.class})
    public static class ScanningConfiguration {

    }

    @Autowired
    IStaticCollectionService converter;

    @MockBean
    private CatalogSearchService catalogSearchService;

    @MockBean
    private ConfigurationAccessorFactory configurationAccessorFactory;


    private fr.cnes.regards.modules.dam.domain.entities.Collection generateRandomDamCollection(){
        EasyRandom easyRandom = new EasyRandom();
        String label = easyRandom.nextObject(String.class);
        String perf = easyRandom.nextObject(String.class);
        String providerId = easyRandom.nextObject(String.class);
        Model model = easyRandom.nextObject(Model.class);
        model.setType(EntityType.COLLECTION);
        fr.cnes.regards.modules.dam.domain.entities.Collection collection =
                new fr.cnes.regards.modules.dam.domain.entities.Collection(model, perf, providerId, label);
        long id = easyRandom.nextLong();
        collection.setId(id);
        return collection;
    }

    @Test
    public void testConvertCollection() throws EntityOperationForbiddenException, EntityNotFoundException, SearchException, MalformedURLException {
        fr.cnes.regards.modules.dam.domain.entities.Collection collection = generateRandomDamCollection();

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
        StacProperty stacProperty = new StacProperty(
                Mockito.mock(RegardsPropertyAccessor.class),
                "stacProp",
                "ext",
                false,
                1,
                "dynFormat",
                StacPropertyType.NUMBER,
                Mockito.mock(AbstractPropertyConverter.class)
        );
        when(value.getStacProperties()).thenReturn(List.of(stacProperty));
        when(configurationAccessorFactory.makeConfigurationAccessor()).thenReturn(value);

        Try<Collection> collections = converter.convertRequest("URN:AIP:COLLECTION:perf:80282ac5-1b01-4e9d-a356-34eb0a15a4e2:V1");
        assertThat(collections.isSuccess(), is(true));

        assertThat(collections.get().getTitle(), is(collection.getLabel()));
        assertThat(collections.get().getId(), is(collection.getId().toString()));
        assertThat(collections.get().getDescription(), is(collection.getModel().getDescription()));
    }



}
