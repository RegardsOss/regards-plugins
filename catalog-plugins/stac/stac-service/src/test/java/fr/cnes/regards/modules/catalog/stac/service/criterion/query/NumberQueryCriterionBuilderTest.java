package fr.cnes.regards.modules.catalog.stac.service.criterion.query;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.NumberQueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.PropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.IdentityPropertyConverter;
import fr.cnes.regards.modules.indexer.domain.criterion.*;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.Test;

import static fr.cnes.regards.modules.catalog.stac.service.criterion.query.NumberQueryCriterionBuilder.DOUBLE_COMPARISON_PRECISION;
import static fr.cnes.regards.modules.indexer.domain.criterion.ComparisonOperator.*;
import static org.assertj.core.api.Assertions.assertThat;

public class NumberQueryCriterionBuilderTest {

    @Test
    public void testBuildCriterionEmpty() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(
                "regardsAttr", "stacProp",
                "", false, 0, PropertyType.DATETIME,
                new IdentityPropertyConverter<>(PropertyType.DATETIME)
        ));
        // WHEN
        Option<ICriterion> criterion = new NumberQueryCriterionBuilder("stacProp")
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

        double value = 12d;
        NumberQueryObject qo = new NumberQueryObject(value, null, null, null, null, null, null);
        // WHEN
        Option<ICriterion> criterion = new NumberQueryCriterionBuilder("stacProp")
                .buildCriterion(properties, qo);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(RangeCriterion.class);

        RangeCriterion<Double> doubleRangeCriterion = (RangeCriterion<Double>) criterion.get();
        assertThatRangeGoesFromTo(doubleRangeCriterion, "regardsAttr",
                GREATER, 12d - DOUBLE_COMPARISON_PRECISION,
                LESS,12d + DOUBLE_COMPARISON_PRECISION
        );
    }

    @Test
    public void testBuildCriterionNeq() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(
                "regardsAttr", "stacProp",
                "", false, 0, PropertyType.STRING,
                new IdentityPropertyConverter<>(PropertyType.STRING)
        ));

        double value = 12d;
        NumberQueryObject qo = new NumberQueryObject(null, value, null, null, null, null, null);
        // WHEN
        Option<ICriterion> criterion = new NumberQueryCriterionBuilder("stacProp")
                .buildCriterion(properties, qo);
        // THEN
        assertThat(criterion).isNotEmpty();

        assertThat(criterion.get()).isInstanceOf(NotCriterion.class);

        RangeCriterion<Double> doubleRangeCriterion = (RangeCriterion<Double>) ((NotCriterion)criterion.get()).getCriterion();
        assertThatRangeGoesFromTo(doubleRangeCriterion, "regardsAttr",
                GREATER, 12d - DOUBLE_COMPARISON_PRECISION,
                LESS,12d + DOUBLE_COMPARISON_PRECISION
        );
    }

    @Test
    public void testBuildCriterionLtGte() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(
                "regardsAttr", "stacProp",
                "", false, 0, PropertyType.STRING,
                new IdentityPropertyConverter<>(PropertyType.STRING)
        ));

        double value = 12d;
        NumberQueryObject qo = new NumberQueryObject(null, null, 11d, 13d, 12d, 14d, null);
        // WHEN
        Option<ICriterion> criterion = new NumberQueryCriterionBuilder("stacProp")
                .buildCriterion(properties, qo);
        // THEN
        assertThat(criterion).isNotEmpty();

        RangeCriterion<Double> doubleRangeCriterion = (RangeCriterion<Double>)criterion.get();
        assertThatRangeGoesFromTo(doubleRangeCriterion, "regardsAttr",
                GREATER_OR_EQUAL, 12d,
                LESS,13d + DOUBLE_COMPARISON_PRECISION
        );
    }

    @Test
    public void testBuildCriterionIn() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(
                "regardsAttr", "stacProp",
                "", false, 0, PropertyType.STRING,
                new IdentityPropertyConverter<>(PropertyType.STRING)
        ));

        NumberQueryObject qo = new NumberQueryObject(null, null, null, null, null, null,
                List.of(12d, 13d));
        // WHEN
        Option<ICriterion> criterion = new NumberQueryCriterionBuilder("stacProp")
                .buildCriterion(properties, qo);
        // THEN
        assertThat(criterion).isNotEmpty();

        java.util.List<ICriterion> innerCrits = ((OrCriterion) criterion.get()).getCriterions();
        assertThat(innerCrits).hasSize(2);
        assertThatRangeGoesFromTo(
                (RangeCriterion<Double>)innerCrits.get(0), "regardsAttr",
                GREATER_OR_EQUAL, 12d - DOUBLE_COMPARISON_PRECISION,
                LESS_OR_EQUAL,12d + DOUBLE_COMPARISON_PRECISION
        );
        assertThatRangeGoesFromTo(
                (RangeCriterion<Double>)innerCrits.get(1), "regardsAttr",
                GREATER_OR_EQUAL, 13d - DOUBLE_COMPARISON_PRECISION,
                LESS_OR_EQUAL,13d + DOUBLE_COMPARISON_PRECISION
        );
    }

    private void assertThatRangeGoesFromTo(
        RangeCriterion<Double> doubleRangeCriterion,
        String attr,
        ComparisonOperator opMin,
        double min,
        ComparisonOperator opMax,
        double max
    ) {
        assertThat(doubleRangeCriterion.getName()).isEqualTo(attr);
        List<ValueComparison<Double>> valueComps = List.ofAll(doubleRangeCriterion.getValueComparisons());
        assertThat(valueComps).hasSize(2);
        assertThat(valueComps)
                .anyMatch(vc -> vc.getOperator() == opMin && vc.getValue().equals(min))
                .anyMatch(vc -> vc.getOperator() == opMax && vc.getValue().equals(max));
    }

}