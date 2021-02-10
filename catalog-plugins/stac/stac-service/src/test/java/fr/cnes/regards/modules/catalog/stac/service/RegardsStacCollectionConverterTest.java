package fr.cnes.regards.modules.catalog.stac.service;


import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.service.RegardsStacCollectionConverter.IRegardsStacCollectionConverter;
import io.vavr.control.Try;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;


@ActiveProfiles("test")
@RunWith(SpringRunner.class)
public class RegardsStacCollectionConverterTest {
    @Autowired
    IRegardsStacCollectionConverter converter;

    private Try<Collection> basicTestCase(EntityType collection) {

        Try<Collection> collections = converter.convertRequest("URN:AIP:Collection:CDPP::1");
        assertThat(collections.isSuccess(), is(true));

        return collections;
    }

    @Test
    public void testConvertCollection(){
        Try<Collection> collections = basicTestCase(EntityType.COLLECTION);

        assertThat(collections.get().getTitle(), is("toto"));
    }

    @Test
    public void testConvertDataset(){
        Try<Collection> collections = basicTestCase(EntityType.DATASET);
    }
}
