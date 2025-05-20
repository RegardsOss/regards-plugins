package fr.cnes.regards.modules.catalog.stac.rest.pagination;

import com.google.gson.GsonBuilder;
import fr.cnes.regards.modules.catalog.stac.domain.api.DateInterval;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.SearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.gson.BBoxTypeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.api.gson.DateIntervalTypeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.api.gson.QueryObjectTypeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.spec.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.service.utils.Base64Codec;
import fr.cnes.regards.modules.catalog.stac.testutils.gson.GsonAwareTest;
import fr.cnes.regards.modules.catalog.stac.testutils.random.RandomAwareTest;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.junit.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.in;

public class SearchOtherPageItemBodySerdeServiceImplTest implements GsonAwareTest, RandomAwareTest {

    @Test
    public void testSerde() {
        // GIVEN
        SearchOtherPageItemBodySerdeServiceImpl service = new SearchOtherPageItemBodySerdeServiceImpl(gson());
        randomList(ItemSearchBody.class, 100).forEach(item -> {
            // WHEN
            String repr = service.serialize(item);
            // THEN
            Try<ItemSearchBody> deserialize = service.deserialize(repr);
            assertThat(deserialize).contains(item);
        });
    }

    // TODO: factorize somehow this method, also in AbstractDomainSerdeTest
    @Override
    public void updateRandomParameters(EasyRandom generator, EasyRandomParameters params) {
        params.randomize(Extent.Temporal.class,
                         () -> new Extent.Temporal(List.range(0, generator.nextInt(10))
                                                       .map(i -> Tuple.of(generator.nextBoolean() ?
                                                                              null :
                                                                              generator.nextObject(OffsetDateTime.class),
                                                                          generator.nextBoolean() ?
                                                                              null :
                                                                              generator.nextObject(OffsetDateTime.class)))))
              .randomize(SearchBody.QueryObject.class, () -> {
                  return generator.nextBoolean() ?
                      generator.nextBoolean() ?
                          generator.nextObject(SearchBody.BooleanQueryObject.class) :
                          generator.nextObject(SearchBody.NumberQueryObject.class) :
                      generator.nextObject(SearchBody.StringQueryObject.class);
              })
              .randomize(DateInterval.class, () -> {
                  return generator.nextBoolean() ?
                      DateInterval.single(generator.nextObject(OffsetDateTime.class)) :
                      generator.nextBoolean() ?
                          DateInterval.from(generator.nextObject(OffsetDateTime.class)) :
                          generator.nextBoolean() ?
                              DateInterval.to(generator.nextObject(OffsetDateTime.class)) :
                              DateInterval.of(generator.nextObject(OffsetDateTime.class),
                                              generator.nextObject(OffsetDateTime.class));
              });
    }

    // TODO: factorize somehow this method, also in AbstractDomainSerdeTest
    @Override
    public GsonBuilder updateGsonBuilder(GsonBuilder builder) {
        builder.registerTypeAdapter(BBox.class, new BBoxTypeAdapter());
        builder.registerTypeAdapter(SearchBody.QueryObject.class, new QueryObjectTypeAdapter());
        builder.registerTypeAdapter(DateInterval.class, new DateIntervalTypeAdapter());
        return builder;
    }

    @Test
    public void testPadding() {
        testPadding("");
        testPadding("f");
        testPadding("fo");
        testPadding("foo");
        testPadding("foob");
        testPadding("fooba");
        testPadding("foobar");
    }

    private void testPadding(String input) {
        // GIVEN
        Base64Codec codec = Base64CodecImpl.getInstance();
        // WHEN
        String noPadding = codec.toBase64(input);
        // THEN
        String decoded = codec.fromBase64(noPadding);
        assertThat(input).isEqualTo(decoded);
    }

    private static class Base64CodecImpl implements Base64Codec {

        private static final Base64CodecImpl INSTANCE = new Base64CodecImpl();

        public static Base64CodecImpl getInstance() {
            return INSTANCE;
        }
    }
}