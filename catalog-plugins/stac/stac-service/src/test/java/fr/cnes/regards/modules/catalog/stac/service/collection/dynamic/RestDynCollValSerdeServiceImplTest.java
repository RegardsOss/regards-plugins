package fr.cnes.regards.modules.catalog.stac.service.collection.dynamic;

import com.google.gson.Gson;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.rest.RestDynCollVal;
import fr.cnes.regards.modules.catalog.stac.testutils.gson.GsonAwareTest;
import fr.cnes.regards.modules.catalog.stac.testutils.random.RandomAwareTest;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static fr.cnes.regards.modules.catalog.stac.service.collection.dynamic.RestDynCollValSerdeServiceImpl.URN_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

public class RestDynCollValSerdeServiceImplTest implements GsonAwareTest, RandomAwareTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestDynCollValSerdeServiceImplTest.class);

    private Gson gson = gson();
    private EasyRandom rand = easyRandom();
    private RestDynCollValSerdeServiceImpl service = new RestDynCollValSerdeServiceImpl(gson());

    @Test
    public void test_serde() {
        List.range(0, 100)
            .forEach(i -> {
                RestDynCollVal value = randomInstance(RestDynCollVal.class);
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
        randomList(rand, String.class, 100)
            .forEach(s -> {
                assertThat(service.isListOfDynCollLevelValues(s)).isFalse();
            });
    }
}