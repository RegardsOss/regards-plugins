package fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.pagination;

import com.google.gson.GsonBuilder;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.DateInterval;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.gson.DateIntervalTypeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.gson.QueryObjectTypeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.testutils.gson.GsonAwareTest;
import fr.cnes.regards.modules.catalog.stac.testutils.random.RandomAwareTest;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.junit.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class SearchOtherPageItemBodySerdeServiceImplTest implements GsonAwareTest, RandomAwareTest {

    @Test
    public void testSerde() {
        // GIVEN
        SearchOtherPageItemBodySerdeServiceImpl service = new SearchOtherPageItemBodySerdeServiceImpl(gson());
        randomList(ItemSearchBody.class, 100)
            .forEach(item -> {
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
        params.randomize(Extent.Temporal.class, () ->
                new Extent.Temporal(List.range(0, generator.nextInt(10))
                        .map(i -> Tuple.of(
                                generator.nextBoolean() ? Option.none() : Option.of(generator.nextObject(OffsetDateTime.class)),
                                generator.nextBoolean() ? Option.none() : Option.of(generator.nextObject(OffsetDateTime.class))
                        ))
                )
        )
                .randomize(ItemSearchBody.QueryObject.class, () -> {
                    return generator.nextBoolean()
                            ? generator.nextBoolean()
                            ? generator.nextObject(ItemSearchBody.BooleanQueryObject.class)
                            : generator.nextObject(ItemSearchBody.NumberQueryObject.class)
                            : generator.nextObject(ItemSearchBody.StringQueryObject.class);
                })
                .randomize(DateInterval.class, () -> {
                    return generator.nextBoolean() ? DateInterval.single(generator.nextObject(OffsetDateTime.class))
                            : generator.nextBoolean() ? DateInterval.from(generator.nextObject(OffsetDateTime.class))
                            : generator.nextBoolean() ? DateInterval.to(generator.nextObject(OffsetDateTime.class))
                            : DateInterval.of(generator.nextObject(OffsetDateTime.class), generator.nextObject(OffsetDateTime.class));
                });
    }

    // TODO: factorize somehow this method, also in AbstractDomainSerdeTest
    @Override
    public GsonBuilder updateGsonBuilder(GsonBuilder builder) {
        builder.registerTypeAdapter(BBox.class, new BBox.BBoxTypeAdapter());
        builder.registerTypeAdapter(ItemSearchBody.QueryObject.class, new QueryObjectTypeAdapter());
        builder.registerTypeAdapter(DateInterval.class, new DateIntervalTypeAdapter());
        return builder;
    }
}