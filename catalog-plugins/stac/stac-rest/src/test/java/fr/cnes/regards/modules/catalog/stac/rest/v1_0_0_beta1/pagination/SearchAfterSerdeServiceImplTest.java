package fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.pagination;

import fr.cnes.regards.modules.catalog.stac.testutils.gson.GsonAwareTest;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SearchAfterSerdeServiceImplTest implements GsonAwareTest {

    @Test
    public void testSerde() {
        // GIVEN
        SearchAfterSerdeServiceImpl service = new SearchAfterSerdeServiceImpl(gson());
        List<Object> values = List.of("value", 12d);
        // WHEN
        String repr = service.serialize(values);
        // THEN
        Try<List<Object>> deserialize = service.deserialize(repr);
        assertThat(deserialize).contains(values);
    }

}