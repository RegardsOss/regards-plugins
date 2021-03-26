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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ActiveProfiles({"test", "feign"})
@TestPropertySource(locations = {"classpath:test.properties"},
    properties = {
        "spring.jpa.properties.hibernate.default_schema=public",
        "regards.elasticsearch.http.port=9200",
        "regards.elasticsearch.host=172.26.47.52"
})
public class RegardsStacCollectionConverterIT extends AbstractMultitenantServiceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegardsStacCollectionConverterIT.class);

    public static final String ITEMSTENANT = "PROJECT";

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

        collection.setId(1L);
        collection.setTags(Sets.newHashSet("TEST collection"));
        collection.setLabel("toto");
        UniformResourceName collectionUniformResourceName = UniformResourceName.build(OAISIdentifier.AIP.name(), EntityType.COLLECTION, ITEMSTENANT,
                UUID.fromString("80282ac5-1b01-4e9d-a356-123456789012"), 1, null,
                null);
        collection.setIpId(collectionUniformResourceName);

        Point point = IGeometry.point(1.3747632, 43.524768);

        DataObject dataObject2 = new DataObject(new Model(), ITEMSTENANT, "provider", "label");
        GeoPoint do2SePoint = new GeoPoint(43.4461681,-0.0369283);
        GeoPoint do2NwPoint = new GeoPoint(43.7695852,-0.5334374);
        dataObject2.setId(2L);
        dataObject2.setIpId(UniformResourceName.build(OAISIdentifier.AIP.name(), EntityType.DATA, ITEMSTENANT,
                UUID.fromString("74f2c965-0136-47f0-93e1-4fd098db1234"), 1, null,
                null));
        dataObject2.setCreationDate(OffsetDateTime.now());
        dataObject2.setSePoint(do2SePoint);
        dataObject2.setNwPoint(do2NwPoint);
        dataObject2.setTags(Sets.newHashSet(collectionUniformResourceName.toString()));
        dataObject2.setLabel("titi");
        Point point2 = IGeometry.point(-0.0369283, 43.7695852);
        dataObject2.setWgs84(GeoHelper.normalize(point2));
        dataObject2.setNormalizedGeometry(GeoHelper.normalize(point2));
        dataObject2.getFeature().setGeometry(GeoHelper.normalize(point2));
        dataObject2.getFeature().setNormalizedGeometry(GeoHelper.normalize(point));


        repository.save(ITEMSTENANT, collection);
        repository.save(ITEMSTENANT, dataObject2);
        repository.refresh(ITEMSTENANT);

    }

    @After
    public void cleanUp(){
        repository.deleteIndex(ITEMSTENANT);
    }

    @Test
    public void testConvertCollection() {
        DataObject collection = repository.get(ITEMSTENANT,
                EntityType.COLLECTION.toString(), "URN:AIP:DATA:" + ITEMSTENANT + ":74f2c965-0136-47f0-93e1-4fd098db1234:V1",
                DataObject.class);


        when(projectGeoSettings.getCrs()).thenReturn(Crs.WGS_84);
        Try<Collection> result = converter
                .convertRequest("URN:AIP:COLLECTION:"+ITEMSTENANT+":80282ac5-1b01-4e9d-a356-123456789012:V1")
                .onFailure(t -> {
                    LOGGER.error("Fail to get Collection and stats");
                    Assert.fail();
                });

    }

}
