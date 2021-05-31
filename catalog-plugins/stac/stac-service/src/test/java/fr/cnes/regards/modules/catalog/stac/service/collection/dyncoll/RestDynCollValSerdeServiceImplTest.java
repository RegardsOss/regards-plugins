package fr.cnes.regards.modules.catalog.stac.service.collection.dyncoll;

import static fr.cnes.regards.modules.catalog.stac.service.collection.dyncoll.RestDynCollValSerdeServiceImpl.URN_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.rest.RestDynCollVal;
import fr.cnes.regards.modules.catalog.stac.testutils.gson.GsonAwareTest;
import fr.cnes.regards.modules.catalog.stac.testutils.random.RandomAwareTest;
import io.vavr.collection.List;
import io.vavr.control.Try;

public class RestDynCollValSerdeServiceImplTest implements GsonAwareTest, RandomAwareTest {

    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(RestDynCollValSerdeServiceImplTest.class);

    private final Gson gson = gson();

    private final EasyRandom rand = easyRandom();

    private final RestDynCollValSerdeServiceImpl service = new RestDynCollValSerdeServiceImpl(gson());

    @Test
    public void test_serde() {
        List.range(0, 100).forEach(i -> {
            RestDynCollVal value = randomInstance(RestDynCollVal.class);
            @SuppressWarnings("unused")
            String json = gson.toJson(value);
            //info(LOGGER, "value={}", json);
            String urn = service.toUrn(value);
            //info(LOGGER, "\n\tvalues={}\n\turn={}", value, urn);
            System.out.println(urn);
            assertThat(urn).startsWith(URN_PREFIX);
            assertThat(service.isListOfDynCollLevelValues(urn)).isTrue();
            Try<RestDynCollVal> deserValues = service.fromUrn(urn);
            assertThat(deserValues).contains(value);
        });
    }

    @Test
    public void test_isListOfDynCollLevelValues() {
        randomList(rand, String.class, 100).forEach(s -> {
            assertThat(service.isListOfDynCollLevelValues(s)).isFalse();
        });
    }
}