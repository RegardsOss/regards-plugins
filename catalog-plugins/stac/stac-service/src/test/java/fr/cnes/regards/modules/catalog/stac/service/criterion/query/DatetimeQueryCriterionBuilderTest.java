package fr.cnes.regards.modules.catalog.stac.service.criterion.query;

import static fr.cnes.regards.modules.indexer.domain.criterion.ComparisonOperator.GREATER;
import static fr.cnes.regards.modules.indexer.domain.criterion.ComparisonOperator.GREATER_OR_EQUAL;
import static fr.cnes.regards.modules.indexer.domain.criterion.ComparisonOperator.LESS;
import static fr.cnes.regards.modules.indexer.domain.criterion.ComparisonOperator.LESS_OR_EQUAL;
import static java.time.OffsetDateTime.now;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.Test;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.DatetimeQueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.IdentityPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.service.criterion.RegardsPropertyAccessorAwareTest;
import fr.cnes.regards.modules.indexer.domain.criterion.AndCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.DateMatchCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.DateRangeCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.NotCriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;

public class DatetimeQueryCriterionBuilderTest implements RegardsPropertyAccessorAwareTest {

    @Test
    public void testBuildCriterionEmpty() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(
                accessor("regardsProp", StacPropertyType.DATETIME, now(UTC)), null, "stacProp", "", false, 0, null,
                StacPropertyType.DATETIME, new IdentityPropertyConverter<>(StacPropertyType.DATETIME), Boolean.FALSE));
        // WHEN
        Option<ICriterion> criterion = new DatetimeQueryCriterionBuilder("stacProp").buildCriterion(properties, null);
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterionEq() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(
                accessor("regardsAttr", StacPropertyType.DATETIME, now(UTC)), null, "stacProp", "", false, 0, null,
                StacPropertyType.STRING, new IdentityPropertyConverter<>(StacPropertyType.DATETIME), Boolean.FALSE));

        OffsetDateTime now = now();
        DatetimeQueryObject qo = DatetimeQueryObject.builder().eq(now).build();
        // WHEN
        Option<ICriterion> criterion = new DatetimeQueryCriterionBuilder("stacProp").buildCriterion(properties, qo);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(DateMatchCriterion.class);

        assertThat(((DateMatchCriterion) criterion.get()).getName()).isEqualTo("feature.properties.regardsAttr");
        assertThat(((DateMatchCriterion) criterion.get()).getValue()).isEqualTo(now);
    }

    @Test
    public void testBuildCriterionAll() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(
                accessor("regardsAttr", StacPropertyType.DATETIME, now(UTC)), null, "stacProp", "", false, 0, null,
                StacPropertyType.DATETIME, new IdentityPropertyConverter<>(StacPropertyType.DATETIME), Boolean.FALSE));

        OffsetDateTime now = now();
        DatetimeQueryObject qo = DatetimeQueryObject.builder().eq(now).neq(now.minusDays(1)).gt(now.minusMinutes(2))
                .lt(now.plusMinutes(2)).gte(now).lte(now).in(List.of(now)).build();

        // WHEN
        Option<ICriterion> criterion = new DatetimeQueryCriterionBuilder("stacProp").buildCriterion(properties, qo);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(AndCriterion.class);

        AndCriterion andCrit = (AndCriterion) criterion.get();
        java.util.List<ICriterion> andCrits = andCrit.getCriterions();
        assertThat(andCrits).hasSize(7);

        assertThat(andCrits.get(0)).isInstanceOf(DateMatchCriterion.class);
        assertThat(((DateMatchCriterion) andCrits.get(0)).getName()).isEqualTo("feature.properties.regardsAttr");
        assertThat(((DateMatchCriterion) andCrits.get(0)).getValue()).isEqualTo(now);

        assertThat(andCrits.get(1)).isInstanceOf(NotCriterion.class);
        assertThat(((DateMatchCriterion) ((NotCriterion) andCrits.get(1)).getCriterion()).getName())
                .isEqualTo("feature.properties.regardsAttr");
        assertThat(((DateMatchCriterion) ((NotCriterion) andCrits.get(1)).getCriterion()).getValue())
                .isEqualTo(now.minusDays(1));

        assertThat(andCrits.get(2)).isInstanceOf(DateRangeCriterion.class);
        assertThat(((DateRangeCriterion) andCrits.get(2)).getName()).isEqualTo("feature.properties.regardsAttr");
        assertThat(List.ofAll(((DateRangeCriterion) andCrits.get(2)).getValueComparisons()).get(0).getValue())
                .isEqualTo(now.plusMinutes(2));
        assertThat(List.ofAll(((DateRangeCriterion) andCrits.get(2)).getValueComparisons()).get(0).getOperator())
                .isEqualTo(LESS);

        assertThat(andCrits.get(3)).isInstanceOf(DateRangeCriterion.class);
        assertThat(((DateRangeCriterion) andCrits.get(3)).getName()).isEqualTo("feature.properties.regardsAttr");
        assertThat(List.ofAll(((DateRangeCriterion) andCrits.get(3)).getValueComparisons()).get(0).getValue())
                .isEqualTo(now);
        assertThat(List.ofAll(((DateRangeCriterion) andCrits.get(3)).getValueComparisons()).get(0).getOperator())
                .isEqualTo(LESS_OR_EQUAL);

        assertThat(andCrits.get(4)).isInstanceOf(DateRangeCriterion.class);
        assertThat(((DateRangeCriterion) andCrits.get(4)).getName()).isEqualTo("feature.properties.regardsAttr");
        assertThat(List.ofAll(((DateRangeCriterion) andCrits.get(4)).getValueComparisons()).get(0).getValue())
                .isEqualTo(now.minusMinutes(2));
        assertThat(List.ofAll(((DateRangeCriterion) andCrits.get(4)).getValueComparisons()).get(0).getOperator())
                .isEqualTo(GREATER);

        assertThat(andCrits.get(5)).isInstanceOf(DateRangeCriterion.class);
        assertThat(((DateRangeCriterion) andCrits.get(5)).getName()).isEqualTo("feature.properties.regardsAttr");
        assertThat(List.ofAll(((DateRangeCriterion) andCrits.get(5)).getValueComparisons()).get(0).getValue())
                .isEqualTo(now);
        assertThat(List.ofAll(((DateRangeCriterion) andCrits.get(5)).getValueComparisons()).get(0).getOperator())
                .isEqualTo(GREATER_OR_EQUAL);

        assertThat(andCrits.get(6)).isInstanceOf(DateMatchCriterion.class);
        assertThat(((DateMatchCriterion) andCrits.get(6)).getName()).isEqualTo("feature.properties.regardsAttr");
        assertThat(((DateMatchCriterion) andCrits.get(6)).getValue()).isEqualTo(now);
    }

}