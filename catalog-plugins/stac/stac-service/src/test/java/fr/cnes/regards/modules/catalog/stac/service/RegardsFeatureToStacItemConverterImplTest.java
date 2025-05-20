package fr.cnes.regards.modules.catalog.stac.service;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.framework.geojson.geometry.Polygon;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.IdentityPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.domain.spec.Item;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Relation;
import fr.cnes.regards.modules.catalog.stac.domain.spec.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.domain.utils.StacGeoHelper;
import fr.cnes.regards.modules.catalog.stac.service.collection.IdMappingService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.criterion.RegardsPropertyAccessorAwareTest;
import fr.cnes.regards.modules.catalog.stac.service.item.RegardsFeatureToStacItemConverterImpl;
import fr.cnes.regards.modules.catalog.stac.service.item.properties.PropertyExtractionServiceImpl;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.UriParamAdder;
import fr.cnes.regards.modules.catalog.stac.service.link.UriParamAdderImpl;
import fr.cnes.regards.modules.catalog.stac.testutils.gson.GsonAwareTest;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.feature.dto.urn.FeatureIdentifier;
import fr.cnes.regards.modules.feature.dto.urn.FeatureUniformResourceName;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.error;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.info;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RegardsFeatureToStacItemConverterImplTest implements GsonAwareTest, RegardsPropertyAccessorAwareTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegardsFeatureToStacItemConverterImplTest.class);

    Gson gson = gson();

    StacGeoHelper stacGeoHelper = new StacGeoHelper(gson);

    Model model = Model.build("theModelName", "theModelDesc", EntityType.DATA);

    String tenant = "the-tenant";

    OGCFeatLinkCreator linkCreator = mock(OGCFeatLinkCreator.class);

    ConfigurationAccessor configurationAccessor = mock(ConfigurationAccessor.class);

    ConfigurationAccessorFactory configurationAccessorFactory = mock(ConfigurationAccessorFactory.class);

    UriParamAdder uriParamAdder = mock(UriParamAdderImpl.class);

    IdMappingService idMappingService = mock(IdMappingService.class);

    RegardsFeatureToStacItemConverterImpl service = new RegardsFeatureToStacItemConverterImpl(new StacGeoHelper(gson),
                                                                                              configurationAccessorFactory,
                                                                                              new PropertyExtractionServiceImpl(
                                                                                                  uriParamAdder),
                                                                                              idMappingService);

    @Test
    public void convertFeatureToItem() throws Exception {
        when(uriParamAdder.makeAuthParam()).thenAnswer(i -> Tuple.of("token", "theJwtToken"));
        when(uriParamAdder.appendParams(any())).thenCallRealMethod();

        when(configurationAccessorFactory.makeConfigurationAccessor()).thenReturn(configurationAccessor);
        when(configurationAccessor.getGeoJSONReader()).thenAnswer(i -> stacGeoHelper.makeGeoJSONReader(stacGeoHelper.updateFactory(
            true)));

        when(linkCreator.createLandingPageLink(Relation.ROOT)).thenAnswer(i -> Option.of(uri("/root"))
                                                                                     .map(uri -> new Link(uri,
                                                                                                          Relation.ROOT,
                                                                                                          "",
                                                                                                          "",
                                                                                                          HttpMethod.GET,
                                                                                                          null)));
        when(linkCreator.createCollectionLink(any(Relation.class), anyString(), anyString())).thenAnswer(i -> Option.of(
                                                                                                                        uri("/collection/" + i.getArgument(1)))
                                                                                                                    .map(
                                                                                                                        uri -> new Link(
                                                                                                                            uri,
                                                                                                                            (Relation) i.getArgument(
                                                                                                                                0),
                                                                                                                            "",
                                                                                                                            "",
                                                                                                                            HttpMethod.GET,
                                                                                                                            null)));
        when(linkCreator.createItemLink(any(Relation.class),
                                        anyString(),
                                        anyString())).thenAnswer(i -> Option.of(new URI("/collection/"
                                                                                        + i.getArgument(1)
                                                                                        + "/item/"
                                                                                        + i.getArgument(2)))
                                                                            .map(uri -> new Link(uri,
                                                                                                 Relation.SELF,
                                                                                                 "",
                                                                                                 "",
                                                                                                 HttpMethod.GET,
                                                                                                 null)));

        when(idMappingService.getStacIdByUrn(any())).thenReturn("stacId");

        List<StacProperty> stacProperties = List.of(new StacProperty(accessor("regardsAttr",
                                                                              StacPropertyType.DATETIME,
                                                                              OffsetDateTime.now().minusYears(2L)),
                                                                     null,
                                                                     "stac:prop",
                                                                     "",
                                                                     false,
                                                                     0,
                                                                     null,
                                                                     StacPropertyType.DATETIME,
                                                                     new IdentityPropertyConverter<>(StacPropertyType.DATETIME),
                                                                     Boolean.FALSE));
        FeatureUniformResourceName itemIpId = FeatureUniformResourceName.build(FeatureIdentifier.FEATURE,
                                                                               EntityType.DATA,
                                                                               tenant,
                                                                               UUID.randomUUID(),
                                                                               1);
        DataObjectFeature dof = new DataObjectFeature(itemIpId,
                                                      "theProvider",
                                                      "theLabel",
                                                      "theSessionOwner",
                                                      "theSession",
                                                      "theModelName");
        DataObject feature = DataObject.wrap(model, dof, true);

        Mockito.when(idMappingService.getItemId(any(), anyString(), anyBoolean())).thenReturn(itemIpId.toString());

        String parentDatasetIpId = FeatureUniformResourceName.build(FeatureIdentifier.FEATURE,
                                                                    EntityType.DATASET,
                                                                    tenant,
                                                                    UUID.randomUUID(),
                                                                    1).toString();
        Polygon polygon = IGeometry.simplePolygon(0d, 0d, 0d, 3d, 3d, 0d);
        feature.setGeometry(polygon);
        feature.addTags(parentDatasetIpId);
        feature.addProperty(IProperty.buildDate("regardsAttr", OffsetDateTime.now().minusYears(1L)));
        feature.getFeature().setFiles(createDataFiles());

        Try<Item> result = service.convertFeatureToItem(stacProperties, null, linkCreator, feature)
                                  .onFailure(t -> error(LOGGER, t.getMessage(), t));
        info(LOGGER, "result: {}", result);
        assertThat(result).isNotEmpty();

        Item item = result.get();
        assertThat(item.getId()).isEqualTo(itemIpId.toString());
        assertThat(item.getBbox()).isEqualTo(new BBox(0d, 0d, 3d, 3d));
        assertThat(item.getGeometry()).isEqualTo(polygon);
        assertThat(item.getCollection()).isEqualTo("stacId");
        assertThat(item.getLinks()).hasSize(4)
                                   .anyMatch(l -> l.href().equals(uri("/root")) && l.rel()
                                                                                    .equals(Relation.ROOT.getValue()))
                                   .anyMatch(l -> l.href().equals(uri("/collection/" + "stacId")) && l.rel()
                                                                                                      .equals(Relation.COLLECTION.getValue()))
                                   .anyMatch(l -> l.href().equals(uri("/collection/" + "stacId" + "/item/" + itemIpId))
                                                  && l.rel().equals(Relation.SELF.getValue()));
        assertThat(item.getAssets()).hasSize(2);
        assertThat(item.getAssets().head()._2.getHref()).matches(uri -> uri.getQuery().contains("token=theJwtToken"));
        assertThat(item.getAssets().head()._2.getAdditionalFields()).isNotNull();
        assertThat(item.getAssets().head()._2.getAdditionalFields().size()).isEqualTo(3);
        assertThat(item.getAssets().head()._2.getAdditionalFields().get("key3")).isNotNull();
        assertThat(item.getAssets().head()._2.getAdditionalFields().get("key3").getAsJsonObject().size()).isEqualTo(2);
    }

    public URI uri(String s) {
        try {
            return new URI(s);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private Multimap<DataType, DataFile> createDataFiles() {
        Multimap<DataType, DataFile> fileMultimapF2 = ArrayListMultimap.create();

        DataFile feat1File1 = new DataFile();
        feat1File1.setOnline(true);
        feat1File1.setUri("file:///test/quicklook.jpg");
        feat1File1.setFilename("quicklook.jpg");
        feat1File1.setFilesize(42000L);
        feat1File1.setReference(false);
        feat1File1.setChecksum("feat1_file1");
        feat1File1.setDigestAlgorithm("MD5");
        feat1File1.setMimeType(MediaType.TEXT_PLAIN);
        feat1File1.setDataType(DataType.QUICKLOOK_SD);

        Map<String, Object> innerObject = new HashMap<>();
        innerObject.put("innerKey1", 123);
        innerObject.put("innerKey2", Arrays.asList("a", "b", "c"));

        Map<String, Object> outerObject = new HashMap<>();
        outerObject.put("key1", "value1");
        outerObject.put("key2", 42);
        outerObject.put("key3", innerObject);

        DataFile feat2File2 = new DataFile();
        feat2File2.setOnline(true);
        feat2File2.setUri("file:///test/feat2_file2.txt");
        feat2File2.setFilename("feat2_file2.txt");
        feat2File2.setFilesize(3050L);
        feat2File2.setReference(false);
        feat2File2.setChecksum("feat2_file2");
        feat2File2.setDigestAlgorithm("MD5");
        feat2File2.setMimeType(MediaType.TEXT_PLAIN);
        feat2File2.setDataType(DataType.RAWDATA);
        feat2File2.setAdditionalFields(outerObject);

        fileMultimapF2.put(DataType.QUICKLOOK_SD, feat1File1);
        fileMultimapF2.put(DataType.RAWDATA, feat2File2);

        return fileMultimapF2;
    }
}