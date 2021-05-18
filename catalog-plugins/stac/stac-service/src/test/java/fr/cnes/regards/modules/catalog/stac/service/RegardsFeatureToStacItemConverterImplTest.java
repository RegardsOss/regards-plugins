package fr.cnes.regards.modules.catalog.stac.service;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.error;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.info;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.COLLECTION;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.ROOT;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.SELF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

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
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Item;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.Centroid;
import fr.cnes.regards.modules.catalog.stac.domain.utils.StacGeoHelper;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.criterion.RegardsPropertyAccessorAwareTest;
import fr.cnes.regards.modules.catalog.stac.service.item.RegardsFeatureToStacItemConverterImpl;
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

    RegardsFeatureToStacItemConverterImpl service = new RegardsFeatureToStacItemConverterImpl(new StacGeoHelper(gson),
            configurationAccessorFactory, uriParamAdder);

    @Test
    public void convertFeatureToItem() throws Exception {
        when(uriParamAdder.makeAuthParam()).thenAnswer(i -> Tuple.of("token", "theJwtToken"));
        when(uriParamAdder.appendParams(any())).thenCallRealMethod();

        when(configurationAccessorFactory.makeConfigurationAccessor()).thenReturn(configurationAccessor);
        when(configurationAccessor.getGeoJSONReader())
                .thenAnswer(i -> stacGeoHelper.makeGeoJSONReader(stacGeoHelper.updateFactory(true)));

        when(linkCreator.createRootLink()).thenAnswer(i -> Option.of(uri("/root")).map(uri -> new Link(uri, ROOT, "", "")));
        when(linkCreator.createCollectionLink(anyString(), anyString())).thenAnswer(i -> Option
                .of(uri("/collection/" + i.getArgument(0))).map(uri -> new Link(uri, COLLECTION, "", "")));
        when(linkCreator.createItemLink(anyString(), anyString()))
                .thenAnswer(i -> Option.of(new URI("/collection/" + i.getArgument(0) + "/item/" + i.getArgument(1)))
                        .map(uri -> new Link(uri, SELF, "", "")));

        List<StacProperty> stacProperties = List.of(new StacProperty(
                accessor("regardsAttr", StacPropertyType.DATETIME, OffsetDateTime.now().minusYears(2L)), "stac:prop", "",
                false, 0, null, StacPropertyType.DATETIME, new IdentityPropertyConverter<>(StacPropertyType.DATETIME)));
        FeatureUniformResourceName itemIpId = FeatureUniformResourceName.build(FeatureIdentifier.FEATURE, EntityType.DATA,
                                                                               tenant, UUID.randomUUID(), 1);
        DataObjectFeature dof = new DataObjectFeature(itemIpId, "theProvider", "theLabel", "theSessionOwner", "theSession",
                "theModelName");
        DataObject feature = DataObject.wrap(model, dof, true);
        String parentDatasetIpId = FeatureUniformResourceName
                .build(FeatureIdentifier.FEATURE, EntityType.DATASET, tenant, UUID.randomUUID(), 1).toString();
        Polygon polygon = IGeometry.simplePolygon(0d, 0d, 0d, 3d, 3d, 0d);
        feature.setGeometry(polygon);
        feature.addTags(parentDatasetIpId);
        feature.addProperty(IProperty.buildDate("regardsAttr", OffsetDateTime.now().minusYears(1L)));
        feature.getFeature().setFiles(createDataFiles());

        Try<Item> result = service.convertFeatureToItem(stacProperties, linkCreator, feature)
                .onFailure(t -> error(LOGGER, t.getMessage(), t));
        info(LOGGER, "result: {}", result);
        assertThat(result).isNotEmpty();

        Item item = result.get();
        assertThat(item.getId()).isEqualTo(itemIpId.toString());
        assertThat(item.getBbox()).isEqualTo(new BBox(0d, 0d, 3d, 3d));
        assertThat(item.getGeometry()).isEqualTo(polygon);
        assertThat(item.getCentroid()).isEqualTo(new Centroid(1d, 1d));
        assertThat(item.getCollection()).isEqualTo(parentDatasetIpId);
        assertThat(item.getLinks()).hasSize(3)
                .anyMatch(l -> l.getHref().equals(uri("/root")) && l.getRel().equals(Link.Relations.ROOT))
                .anyMatch(l -> l.getHref().equals(uri("/collection/" + parentDatasetIpId))
                        && l.getRel().equals(Link.Relations.COLLECTION))
                .anyMatch(l -> l.getHref().equals(uri("/collection/" + parentDatasetIpId + "/item/" + itemIpId))
                        && l.getRel().equals(Link.Relations.SELF));
        assertThat(item.getAssets()).hasSize(2);
        assertThat(item.getAssets().head()._2.getHref()).matches(uri -> uri.getQuery().contains("token=theJwtToken"));

    }

    public URI uri(String s) {
        try {
            return new URI(s);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private Multimap<DataType, DataFile> createDataFiles() throws Exception {
        Multimap<DataType, DataFile> fileMultimapF2 = ArrayListMultimap.create();

        DataFile feat1File1 = new DataFile();
        feat1File1.setOnline(true);
        feat1File1.setUri(uri("file:///test/quicklook.jpg"));
        feat1File1.setFilename("quicklook.jpg");
        feat1File1.setFilesize(42000L);
        feat1File1.setReference(false);
        feat1File1.setChecksum("feat1_file1");
        feat1File1.setDigestAlgorithm("MD5");
        feat1File1.setMimeType(MediaType.TEXT_PLAIN);
        feat1File1.setDataType(DataType.QUICKLOOK_SD);

        DataFile feat2File2 = new DataFile();
        feat2File2.setOnline(true);
        feat2File2.setUri(uri("file:///test/feat2_file2.txt"));
        feat2File2.setFilename("feat2_file2.txt");
        feat2File2.setFilesize(3050L);
        feat2File2.setReference(false);
        feat2File2.setChecksum("feat2_file2");
        feat2File2.setDigestAlgorithm("MD5");
        feat2File2.setMimeType(MediaType.TEXT_PLAIN);
        feat2File2.setDataType(DataType.RAWDATA);

        fileMultimapF2.put(DataType.QUICKLOOK_SD, feat1File1);
        fileMultimapF2.put(DataType.RAWDATA, feat2File2);

        return fileMultimapF2;
    }
}