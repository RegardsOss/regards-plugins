package fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.dyncoll;

import fr.cnes.regards.modules.catalog.stac.testutils.gson.GsonAwareTest;
import fr.cnes.regards.modules.catalog.stac.testutils.random.RandomAwareTest;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.dyncoll.RestDynCollValSerdeServiceImpl.URN_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

public class RestDynCollValSerdeServiceImplTest implements GsonAwareTest, RandomAwareTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestDynCollValSerdeServiceImplTest.class);

    private EasyRandom rand = easyRandom();
    private RestDynCollValSerdeServiceImpl service = new RestDynCollValSerdeServiceImpl(gson());

    @Test
    public void test_serde() {
        List.range(0, 100)
            .forEach(i -> {
                RestDynCollVal value = randomInstance(RestDynCollVal.class);
                String urn = service.serialize(value);
                LOGGER.info("\n\tvalues={}\n\turn={}", value, urn);
                System.out.println(urn);
                assertThat(urn).startsWith(URN_PREFIX);
                assertThat(service.isListOfDynCollLevelValues(urn)).isTrue();
                Try<RestDynCollVal> deserValues = service.deserialize(urn);
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