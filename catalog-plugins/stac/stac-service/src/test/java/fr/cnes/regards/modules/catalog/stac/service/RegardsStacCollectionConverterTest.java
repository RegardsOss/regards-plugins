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
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.service.collection.ExtentSummaryService;
import fr.cnes.regards.modules.catalog.stac.service.collection.Static.IStaticCollectionService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.SearchKey;
import fr.cnes.regards.modules.indexer.domain.SimpleSearchKey;
import fr.cnes.regards.modules.indexer.domain.aggregation.QueryableAttribute;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.opensearch.service.exception.OpenSearchUnknownParameter;
import fr.cnes.regards.modules.search.domain.plugin.CollectionWithStats;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.CatalogSearchService;
import fr.cnes.regards.modules.search.service.SearchException;
import io.vavr.collection.List;
import io.vavr.collection.TreeSet;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.apache.lucene.search.grouping.GroupFacetCollector;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.bucket.range.ParsedDateRange;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.jeasy.random.EasyRandom;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;
import org.locationtech.spatial4j.io.GeoJSONReader;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.*;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.COLLECTION;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ActiveProfiles({"test"})
@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = {RegardsStacCollectionConverterTest.ScanningConfiguration.class})
public class RegardsStacCollectionConverterTest {

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

    @MockBean
    private ExtentSummaryService extentSummaryService;


    private fr.cnes.regards.modules.dam.domain.entities.Collection generateRandomDamCollection() {
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
    public void testConvertCollection() throws EntityOperationForbiddenException, EntityNotFoundException, SearchException, MalformedURLException, OpenSearchUnknownParameter {
        fr.cnes.regards.modules.dam.domain.entities.Collection collection = generateRandomDamCollection();

        ParsedDateRange parsedDateRange = Mockito.mock(ParsedDateRange.class);
        ArrayList arrayListMock = Mockito.mock(ArrayList.class);
        FacetPage facetPage = Mockito.mock(FacetPage.class);
        OGCFeatLinkCreator featLinkCreator = Mockito.mock(OGCFeatLinkCreator.class);
        fr.cnes.regards.modules.dam.domain.entities.Collection collectResult = generateRandomDamCollection();
        ConfigurationAccessor configurationAccessor = Mockito.mock(ConfigurationAccessor.class);


        when(parsedDateRange.getBuckets()).thenReturn(arrayListMock);
        doReturn(Mockito.mock(Range.Bucket.class)).when(arrayListMock).get(anyInt());
        when(parsedDateRange.getBuckets().get(anyInt()).getFrom()).thenReturn(0L);
        when(parsedDateRange.getBuckets().get(anyInt()).getTo()).thenReturn(Long.MAX_VALUE);
        java.util.List<Aggregation> parsedStats = Arrays.asList(parsedDateRange);
        CollectionWithStats damCollection = new CollectionWithStats(collection, parsedStats);

        TreeSet<String> tags = TreeSet.of("URN:AIP:COLLECTION:perf:80282ac5-1b01-4e9d-a356-123456789012:V1");
        damCollection.getCollection().setTags(tags.toJavaSet());
        when(catalogSearchService.getCollectionWithDataObjectsStats(any(UniformResourceName.class),
                any(SearchType.class), any(java.util.Collection.class))).thenReturn(damCollection);


        when(facetPage.getContent()).thenReturn(List.of(collectResult).asJava());

        when(catalogSearchService.search(any(ICriterion.class),
                any(SearchType.class),
                any(ArrayList.class),
                any(Pageable.class)))
                .thenReturn(facetPage);

        when(catalogSearchService.search(any(ICriterion.class),
                any(SimpleSearchKey.class),
                isNull(),
                any(Pageable.class)))
                .thenReturn(facetPage);


        when(configurationAccessor.getGeoJSONReader()).thenReturn(new GeoJSONReader(SpatialContext.GEO, Mockito.mock(SpatialContextFactory.class)));
        when(configurationAccessor.getKeywords(anyString())).thenReturn(List.of("keywords"));
        when(configurationAccessor.getProviders(anyString())).thenReturn(List.of(new Provider("prov", "desc", new URL("http", "localhost", 1234, "file"), List.of(Provider.ProviderRole.HOST))));
        when(configurationAccessor.getLicense(anyString())).thenReturn("licence");

        when(featLinkCreator.createRootLink())
                .thenAnswer(i -> Option.of(uri("/root"))
                        .map(uri -> new Link(uri, ROOT, "", "")));
        when(featLinkCreator.createCollectionLink(anyString(), anyString()))
                .thenAnswer(i -> Option.of(uri("/collection/" + i.getArgument(0)))
                        .map(uri -> new Link(uri, COLLECTION, "", "")));
        when(featLinkCreator.createItemLink(anyString(), anyString()))
                .thenAnswer(i -> Option.of(new URI("/collection/" + i.getArgument(0) + "/item/" + i.getArgument(1)))
                        .map(uri -> new Link(uri, SELF, "", "")));
        when(featLinkCreator.createCollectionItemsLinkWithRel(anyString(), anyString()))
                .thenAnswer(i -> Option.of(new URI("/collection/" + i.getArgument(0) + "/item/" + i.getArgument(1)))
                        .map(uri -> new Link(uri, SELF, "", "")).map(x -> x.withRel("items")));

        when(featLinkCreator.createCollectionLinkWithRel(anyString(), anyString(), anyString()))
                .thenAnswer(i -> {
                    if (i.getArgument(2).equals("child")) {
                        return Option.of(uri("/collection/" + i.getArgument(0)))
                                .map(uri -> new Link(uri, COLLECTION, "", "")).map(l -> l.withRel("child"));
                    }

                    return Option.of(uri("/collection/" + i.getArgument(0)))
                            .map(uri -> new Link(uri, COLLECTION, "", "")).map(l -> l.withRel("parent"));

                });

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
        StacProperty dateTimeProp = new StacProperty(
                Mockito.mock(RegardsPropertyAccessor.class),
                "stacProp",
                "ext",
                false,
                1,
                "dynFormat",
                StacPropertyType.DATETIME,
                Mockito.mock(AbstractPropertyConverter.class)
        );
        when(configurationAccessor.getDatetimeStacProperty()).thenReturn(dateTimeProp);
        when(configurationAccessor.getStacProperties()).thenReturn(List.of(stacProperty));
        when(configurationAccessorFactory.makeConfigurationAccessor()).thenReturn(configurationAccessor);
        QueryableAttribute queryableAttribute = mock(QueryableAttribute.class);
        when(extentSummaryService.extentSummaryQueryableAttributes(
                any(StacProperty.class),
                any(List.class)
        )).thenReturn(List.of(queryableAttribute));

        Try<Collection> collections = converter.convertRequest("URN:AIP:COLLECTION:perf:80282ac5-1b01-4e9d-a356-34eb0a15a4e2:V1",
                featLinkCreator,
                configurationAccessor);
        assertThat(collections.isSuccess(), is(true));

        assertThat(collections.get().getTitle(), is(collection.getLabel()));
        assertThat(collections.get().getId(), is(collection.getId().toString()));

        Assert.assertFalse(collections.get().getLinks().isEmpty());
        Assert.assertEquals(1, collections.get().getLinks().map(Link::getRel).count(x -> x.equals("parent")));

    }

    public URI uri(String s) {
        try {
            return new URI(s);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }


}
