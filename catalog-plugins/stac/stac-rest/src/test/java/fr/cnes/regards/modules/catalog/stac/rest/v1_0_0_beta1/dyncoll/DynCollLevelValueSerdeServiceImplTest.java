package fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.dyncoll;

import fr.cnes.regards.modules.catalog.stac.testutils.gson.GsonAwareTest;
import fr.cnes.regards.modules.catalog.stac.testutils.random.RandomAwareTest;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.dyncoll.DynCollLevelValueSerdeServiceImpl.URN_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

public class DynCollLevelValueSerdeServiceImplTest implements GsonAwareTest, RandomAwareTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynCollLevelValueSerdeServiceImplTest.class);

    private EasyRandom rand = easyRandom();
    private DynCollLevelValueSerdeServiceImpl service = new DynCollLevelValueSerdeServiceImpl(gson());

    @Test
    public void test_serde() {
        List.range(0, 100)
            .forEach(i -> {
                List<RestDynCollLevelValue> values = randomList(rand, RestDynCollLevelValue.class, i);
                String urn = service.serialize(values);
                LOGGER.info("\n\tvalues={}\n\turn={}", values, urn);
                System.out.println(urn);
                assertThat(urn).startsWith(URN_PREFIX);
                assertThat(service.isListOfDynCollLevelValues(urn)).isTrue();
                Try<List<RestDynCollLevelValue>> deserValues = service.deserialize(urn);
                assertThat(deserValues).contains(values);
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