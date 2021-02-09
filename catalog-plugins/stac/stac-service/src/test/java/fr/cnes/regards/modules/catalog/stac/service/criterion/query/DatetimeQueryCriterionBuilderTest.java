package fr.cnes.regards.modules.catalog.stac.service.criterion.query;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.DatetimeQueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.PropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.IdentityPropertyConverter;
import fr.cnes.regards.modules.indexer.domain.criterion.*;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.Test;

import java.time.OffsetDateTime;

import static fr.cnes.regards.modules.indexer.domain.criterion.ComparisonOperator.*;
import static java.time.OffsetDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;

public class DatetimeQueryCriterionBuilderTest {

    @Test
    public void testBuildCriterionEmpty() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(
                "regardsAttr", "stacProp",
                "", false, 0, PropertyType.DATETIME,
                new IdentityPropertyConverter<>(PropertyType.DATETIME)
        ));
        // WHEN
        Option<ICriterion> criterion = new DatetimeQueryCriterionBuilder("stacProp")
            .buildCriterion(properties, null);
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterionEq() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(
                "regardsAttr", "stacProp",
                "", false, 0, PropertyType.STRING,
                new IdentityPropertyConverter<>(PropertyType.STRING)
        ));

        OffsetDateTime now = now();
        DatetimeQueryObject qo = new DatetimeQueryObject(now, null, null, null, null, null, null);
        // WHEN
        Option<ICriterion> criterion = new DatetimeQueryCriterionBuilder("stacProp")
                .buildCriterion(properties, qo);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(DateMatchCriterion.class);

        assertThat(((DateMatchCriterion)criterion.get()).getName()).isEqualTo("regardsAttr");
        assertThat(((DateMatchCriterion)criterion.get()).getValue()).isEqualTo(now);
    }


    @Test
    public void testBuildCriterionAll() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(
                "regardsAttr", "stacProp",
                "", false, 0, PropertyType.STRING,
                new IdentityPropertyConverter<>(PropertyType.STRING)
        ));

        OffsetDateTime now = now();
        DatetimeQueryObject qo = new DatetimeQueryObject(
                now,
                now.minusDays(1),
                now.minusMinutes(2),
                now.plusMinutes(2),
                now,
                now,
                List.of(now)
        );

        // WHEN
        Option<ICriterion> criterion = new DatetimeQueryCriterionBuilder("stacProp")
                .buildCriterion(properties, qo);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(AndCriterion.class);

        AndCriterion andCrit = (AndCriterion) criterion.get();
        java.util.List<ICriterion> andCrits = andCrit.getCriterions();
        assertThat(andCrits).hasSize(7);

        assertThat(andCrits.get(0)).isInstanceOf(DateMatchCriterion.class);
        assertThat(((DateMatchCriterion)andCrits.get(0)).getName()).isEqualTo("regardsAttr");
        assertThat(((DateMatchCriterion)andCrits.get(0)).getValue()).isEqualTo(now);

        assertThat(andCrits.get(1)).isInstanceOf(NotCriterion.class);
        assertThat(((DateMatchCriterion)((NotCriterion)andCrits.get(1)).getCriterion()).getName()).isEqualTo("regardsAttr");
        assertThat(((DateMatchCriterion)((NotCriterion)andCrits.get(1)).getCriterion()).getValue()).isEqualTo(now.minusDays(1));

        assertThat(andCrits.get(2)).isInstanceOf(DateRangeCriterion.class);
        assertThat(((DateRangeCriterion)andCrits.get(2)).getName()).isEqualTo("regardsAttr");
        assertThat(List.ofAll(((DateRangeCriterion)andCrits.get(2)).getValueComparisons()).get(0).getValue()).isEqualTo(now.plusMinutes(2));
        assertThat(List.ofAll(((DateRangeCriterion)andCrits.get(2)).getValueComparisons()).get(0).getOperator()).isEqualTo(LESS);

        assertThat(andCrits.get(3)).isInstanceOf(DateRangeCriterion.class);
        assertThat(((DateRangeCriterion)andCrits.get(3)).getName()).isEqualTo("regardsAttr");
        assertThat(List.ofAll(((DateRangeCriterion)andCrits.get(3)).getValueComparisons()).get(0).getValue()).isEqualTo(now);
        assertThat(List.ofAll(((DateRangeCriterion)andCrits.get(3)).getValueComparisons()).get(0).getOperator()).isEqualTo(LESS_OR_EQUAL);

        assertThat(andCrits.get(4)).isInstanceOf(DateRangeCriterion.class);
        assertThat(((DateRangeCriterion)andCrits.get(4)).getName()).isEqualTo("regardsAttr");
        assertThat(List.ofAll(((DateRangeCriterion)andCrits.get(4)).getValueComparisons()).get(0).getValue()).isEqualTo(now.minusMinutes(2));
        assertThat(List.ofAll(((DateRangeCriterion)andCrits.get(4)).getValueComparisons()).get(0).getOperator()).isEqualTo(GREATER);

        assertThat(andCrits.get(5)).isInstanceOf(DateRangeCriterion.class);
        assertThat(((DateRangeCriterion)andCrits.get(5)).getName()).isEqualTo("regardsAttr");
        assertThat(List.ofAll(((DateRangeCriterion)andCrits.get(5)).getValueComparisons()).get(0).getValue()).isEqualTo(now);
        assertThat(List.ofAll(((DateRangeCriterion)andCrits.get(5)).getValueComparisons()).get(0).getOperator()).isEqualTo(GREATER_OR_EQUAL);

        assertThat(andCrits.get(6)).isInstanceOf(DateMatchCriterion.class);
        assertThat(((DateMatchCriterion)andCrits.get(6)).getName()).isEqualTo("regardsAttr");
        assertThat(((DateMatchCriterion)andCrits.get(6)).getValue()).isEqualTo(now);
    }

}