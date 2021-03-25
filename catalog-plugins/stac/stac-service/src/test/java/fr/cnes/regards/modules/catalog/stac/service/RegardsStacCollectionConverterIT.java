package fr.cnes.regards.modules.catalog.stac.service;


import com.google.common.collect.Sets;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.framework.geojson.geometry.Point;
import fr.cnes.regards.framework.jpa.multitenant.test.AbstractMultitenantServiceTest;
import fr.cnes.regards.framework.oais.urn.OAISIdentifier;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.service.RegardsStacCollectionConverter.IRegardsStacCollectionConverter;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.feature.CollectionFeature;
import fr.cnes.regards.modules.indexer.dao.EsRepository;
import fr.cnes.regards.modules.indexer.dao.spatial.GeoHelper;
import fr.cnes.regards.modules.indexer.dao.spatial.ProjectGeoSettings;
import fr.cnes.regards.modules.indexer.domain.spatial.Crs;
import fr.cnes.regards.modules.model.domain.Model;
import io.vavr.control.Try;
import org.elasticsearch.common.geo.GeoPoint;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ActiveProfiles({"test", "feign"})
@TestPropertySource(locations = {"classpath:test.properties"},
        properties = {"spring.jIAttributeModelClientpa.properties.hibernate.default_schema=public", "regards.elasticsearch.http.port=9200"
                , "regards.elasticsearch.host:172.26.47.52"})
public class RegardsStacCollectionConverterIT extends AbstractMultitenantServiceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegardsStacCollectionConverterIT.class);

    public static final String ITEMSTENANT = "PROJECT";

    OffsetDateTime offsetDateTimeFrom = OffsetDateTime.of(LocalDateTime.of(2017, 05, 12, 05, 45), ZoneOffset.UTC);
    OffsetDateTime offsetDateTimeTo = OffsetDateTime.now();

    @Autowired
    IRegardsStacCollectionConverter converter;

    @Autowired
    EsRepository repository;

    @MockBean
    ProjectGeoSettings projectGeoSettings;

    @Before
    public void initMethod(){

        try {
            repository.deleteIndex(ITEMSTENANT);
        }catch (Exception e){

        }
        Assert.assertTrue( repository.createIndex(ITEMSTENANT));

        // Creations for first two
        Model model1 = new Model();
        Model collectionModel = new Model();
        collectionModel.setName("model_1" + System.currentTimeMillis());
        collectionModel.setType(EntityType.COLLECTION);
        collectionModel.setVersion("1");
        collectionModel.setDescription("Test data object model");
        model1.setType(EntityType.COLLECTION);
        fr.cnes.regards.modules.dam.domain.entities.Collection collection =
                new fr.cnes.regards.modules.dam.domain.entities.Collection(collectionModel, ITEMSTENANT, "COL", "collection");


        CollectionFeature feature = collection.getFeature();
        collection.setId(1L);
        collection.setTags(Sets.newHashSet("TEST collection"));
        collection.setLabel("toto");
        UniformResourceName collectionUniformResourceName = UniformResourceName.build(OAISIdentifier.AIP.name(), EntityType.COLLECTION, ITEMSTENANT,
                UUID.fromString("80282ac5-1b01-4e9d-a356-123456789012"), 1, null,
                null);
        collection.setIpId(collectionUniformResourceName);

        Point point = IGeometry.point(1.3747632, 43.524768);
//        collection.setWgs84(GeoHelper.normalize(point));
//        collection.setNormalizedGeometry(GeoHelper.normalize(point));
//        collection.getFeature().setGeometry(GeoHelper.normalize(point));
//        collection.getFeature().setNormalizedGeometry(GeoHelper.normalize(point));

        DataObject dataObject1 = new DataObject(new Model(), ITEMSTENANT, "provider", "label");
        GeoPoint do1SePoint = new GeoPoint(43.4461681,-0.0369283);
        GeoPoint do1NwPoint = new GeoPoint(43.7695852,-0.5334374);
        dataObject1.setId(2L);
        dataObject1.setIpId(UniformResourceName.build(OAISIdentifier.AIP.name(), EntityType.DATA, ITEMSTENANT,
                UUID.fromString("74f2c965-0136-47f0-93e1-4fd098db1234"), 1, null,
                null));

        dataObject1.setCreationDate(offsetDateTimeFrom);
        dataObject1.setSePoint(do1SePoint);
        dataObject1.setNwPoint(do1NwPoint);
        dataObject1.setTags(Sets.newHashSet(collectionUniformResourceName.toString()));
        dataObject1.setLabel("titi");
        Point point1 = IGeometry.point(-0.0369283, 43.7695852);
        dataObject1.setWgs84(GeoHelper.normalize(point1));
        dataObject1.setNormalizedGeometry(GeoHelper.normalize(point1));
        dataObject1.getFeature().setGeometry(GeoHelper.normalize(point1));
        dataObject1.getFeature().setNormalizedGeometry(GeoHelper.normalize(point));

        DataObject dataObject2 = new DataObject(new Model(), ITEMSTENANT, "provider", "label");

        GeoPoint do2SePoint = new GeoPoint(42.95009340967441, 17.138151798412633);
        GeoPoint do2NwPoint = new GeoPoint(42.963693206490134, 17.112059269965048);
        dataObject2.setId(2L);
        dataObject2.setIpId(UniformResourceName.build(OAISIdentifier.AIP.name(), EntityType.DATA, ITEMSTENANT,
                UUID.fromString("74f2c965-0136-47f0-93e1-4fd098db5678"), 1, null,
                null));
        dataObject2.setCreationDate(offsetDateTimeTo);
        dataObject2.setSePoint(do2SePoint);
        dataObject2.setNwPoint(do2NwPoint);
        dataObject2.setTags(Sets.newHashSet(collectionUniformResourceName.toString()));
        dataObject2.setLabel("korcula");
        Point point2 = IGeometry.point(17.138151798412633, 42.95009340967441);
        dataObject2.setWgs84(GeoHelper.normalize(point2));
        dataObject2.setNormalizedGeometry(GeoHelper.normalize(point2));
        dataObject2.getFeature().setGeometry(GeoHelper.normalize(point2));
        dataObject2.getFeature().setNormalizedGeometry(GeoHelper.normalize(point));


        repository.save(ITEMSTENANT, collection);
        repository.save(ITEMSTENANT, dataObject1);
        repository.save(ITEMSTENANT, dataObject2);
        repository.refresh(ITEMSTENANT);

    }

    @After
    public void cleanUp(){
        repository.deleteIndex(ITEMSTENANT);
    }

    @Test
    public void testConvertCollection() {
        when(projectGeoSettings.getCrs()).thenReturn(Crs.WGS_84);
        Try<Collection> result = converter.convertRequest("URN:AIP:COLLECTION:"+ITEMSTENANT+":80282ac5-1b01-4e9d-a356-123456789012:V1")
                .onFailure(t -> {
                    LOGGER.error("Fail to get Collection and stats");
                    Assert.fail();
                });

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(-0.5334374,result.get().getExtent().getSpatial().getBbox().get(0).getMinX(), 0.0000001);
        Assert.assertEquals(43.7695852,result.get().getExtent().getSpatial().getBbox().get(0).getMaxY(), 0.0000001);
        Assert.assertEquals(17.1381517,result.get().getExtent().getSpatial().getBbox().get(0).getMaxX(), 0.0000001);
        Assert.assertEquals(42.9500934,result.get().getExtent().getSpatial().getBbox().get(0).getMinY(), 0.0000001);

        Assert.assertEquals(offsetDateTimeFrom.toInstant(), result.get().getExtent().getTemporal().getInterval().get()._1.get().toInstant());
        Assert.assertEquals(offsetDateTimeTo.toInstant(), result.get().getExtent().getTemporal().getInterval().get()._2.get().toInstant());

        Assert.assertEquals("toto", result.get().getTitle());
        Assert.assertEquals("1", result.get().getId());

    }

}