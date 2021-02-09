package fr.cnes.regards.modules.catalog.stac.service.criterion;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.DateInterval;
import fr.cnes.regards.modules.catalog.stac.domain.properties.PropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.IdentityPropertyConverter;
import fr.cnes.regards.modules.indexer.domain.criterion.DateMatchCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.DateRangeCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class DateIntervalCriterionBuilderTest {

    @Test
    public void testBuildCriterionNull() {
        // GIVEN
        List<StacProperty> properties = List.of();
        // WHEN
        Option<ICriterion> criterion = new DateIntervalCriterionBuilder().buildCriterion(properties, null);
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterionSame() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(
                "regardsDatetime", "datetime",
                "", false, 0, PropertyType.STRING,
                new IdentityPropertyConverter<>(PropertyType.STRING)
        ));
        OffsetDateTime now = OffsetDateTime.now();
        DateInterval interval = DateInterval.single(now);
        // WHEN
        Option<ICriterion> criterion = new DateIntervalCriterionBuilder().buildCriterion(properties, interval);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(DateMatchCriterion.class);
        assertThat(((DateMatchCriterion)criterion.get()).getName()).isEqualTo("regardsDatetime");
        assertThat(((DateMatchCriterion)criterion.get()).getValue()).isEqualTo(now);
    }

    @Test
    public void testBuildCriterionDiff() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(
                "regardsDatetime", "datetime",
                "", false, 0, PropertyType.STRING,
                new IdentityPropertyConverter<>(PropertyType.STRING)
        ));
        OffsetDateTime now = OffsetDateTime.now();
        DateInterval interval = DateInterval.of(now.minusHours(2), now);
        // WHEN
        Option<ICriterion> criterion = new DateIntervalCriterionBuilder().buildCriterion(properties, interval);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(DateRangeCriterion.class);
        assertThat(((DateRangeCriterion)criterion.get()).getName()).isEqualTo("regardsDatetime");
    }

}