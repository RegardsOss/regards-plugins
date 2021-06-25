package fr.cnes.regards.modules.catalog.stac.service.criterion.query;

import static fr.cnes.regards.modules.catalog.stac.service.criterion.query.NumberQueryCriterionBuilder.DOUBLE_COMPARISON_PRECISION;
import static fr.cnes.regards.modules.indexer.domain.criterion.ComparisonOperator.GREATER;
import static fr.cnes.regards.modules.indexer.domain.criterion.ComparisonOperator.GREATER_OR_EQUAL;
import static fr.cnes.regards.modules.indexer.domain.criterion.ComparisonOperator.LESS;
import static fr.cnes.regards.modules.indexer.domain.criterion.ComparisonOperator.LESS_OR_EQUAL;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.NumberQueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.IdentityPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.service.criterion.RegardsPropertyAccessorAwareTest;
import fr.cnes.regards.modules.indexer.domain.criterion.ComparisonOperator;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.NotCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.OrCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.RangeCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.ValueComparison;
import io.vavr.collection.List;
import io.vavr.control.Option;

@SuppressWarnings("unchecked")
public class NumberQueryCriterionBuilderTest implements RegardsPropertyAccessorAwareTest {

    @Test
    public void testBuildCriterionEmpty() {
        // GIVEN
        List<StacProperty> properties = List
                .of(new StacProperty(accessor("regardsAttr", StacPropertyType.PERCENTAGE, 15d), null, "stacProp", "", false,
                        0, null, StacPropertyType.PERCENTAGE, new IdentityPropertyConverter<>(StacPropertyType.PERCENTAGE), Boolean.FALSE));
        // WHEN
        Option<ICriterion> criterion = new NumberQueryCriterionBuilder("stacProp").buildCriterion(properties, null);
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterionEq() {
        // GIVEN
        RegardsPropertyAccessor regardsAttr = accessor("regardsAttr", StacPropertyType.PERCENTAGE, 15d);
        List<StacProperty> properties = List.of(new StacProperty(regardsAttr, null, "stacProp", "", false, 0, null,
                StacPropertyType.PERCENTAGE, new IdentityPropertyConverter<>(StacPropertyType.PERCENTAGE), Boolean.FALSE));

        double value = 12d;
        NumberQueryObject qo = NumberQueryObject.builder().eq(value).build();
        // WHEN
        Option<ICriterion> criterion = new NumberQueryCriterionBuilder("stacProp").buildCriterion(properties, qo);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(RangeCriterion.class);

        RangeCriterion<Double> doubleRangeCriterion = (RangeCriterion<Double>) criterion.get();
        assertThatRangeGoesFromTo(doubleRangeCriterion, regardsAttr.getAttributeModel().getFullJsonPath(), GREATER,
                                  12d - DOUBLE_COMPARISON_PRECISION, LESS, 12d + DOUBLE_COMPARISON_PRECISION);
    }

    @Test
    public void testBuildCriterionNeq() {
        // GIVEN
        RegardsPropertyAccessor regardsAttr = accessor("regardsAttr", StacPropertyType.PERCENTAGE, 15d);
        List<StacProperty> properties = List.of(new StacProperty(regardsAttr, null, "stacProp", "", false, 0, null,
                StacPropertyType.PERCENTAGE, new IdentityPropertyConverter<>(StacPropertyType.PERCENTAGE), Boolean.FALSE));

        double value = 12d;
        NumberQueryObject qo = NumberQueryObject.builder().neq(value).build();
        // WHEN
        Option<ICriterion> criterion = new NumberQueryCriterionBuilder("stacProp").buildCriterion(properties, qo);
        // THEN
        assertThat(criterion).isNotEmpty();

        assertThat(criterion.get()).isInstanceOf(NotCriterion.class);

        RangeCriterion<Double> doubleRangeCriterion = (RangeCriterion<Double>) ((NotCriterion) criterion.get())
                .getCriterion();
        assertThatRangeGoesFromTo(doubleRangeCriterion, regardsAttr.getAttributeModel().getFullJsonPath(),

                                  GREATER, 12d - DOUBLE_COMPARISON_PRECISION, LESS, 12d + DOUBLE_COMPARISON_PRECISION);
    }

    @Test
    public void testBuildCriterionLtGte() {
        // GIVEN
        RegardsPropertyAccessor regardsAttr = accessor("regardsAttr", StacPropertyType.PERCENTAGE, 15d);
        List<StacProperty> properties = List.of(new StacProperty(regardsAttr, null, "stacProp", "", false, 0, null,
                StacPropertyType.STRING, new IdentityPropertyConverter<>(StacPropertyType.STRING), Boolean.FALSE));

        @SuppressWarnings("unused")
        double value = 12d;
        NumberQueryObject qo = NumberQueryObject.builder().gt(11d).lt(13d).gte(12d).lte(14d).build();
        // WHEN
        Option<ICriterion> criterion = new NumberQueryCriterionBuilder("stacProp").buildCriterion(properties, qo);
        // THEN
        assertThat(criterion).isNotEmpty();

        RangeCriterion<Double> doubleRangeCriterion = (RangeCriterion<Double>) criterion.get();
        assertThatRangeGoesFromTo(doubleRangeCriterion, regardsAttr.getAttributeModel().getFullJsonPath(), GREATER_OR_EQUAL,
                                  12d, LESS, 13d + DOUBLE_COMPARISON_PRECISION);
    }

    @Test
    public void testBuildCriterionIn() {
        // GIVEN
        RegardsPropertyAccessor regardsAttr = accessor("regardsAttr", StacPropertyType.PERCENTAGE, 15d);
        List<StacProperty> properties = List.of(new StacProperty(regardsAttr, null, "stacProp", "", false, 0, null,
                StacPropertyType.STRING, new IdentityPropertyConverter<>(StacPropertyType.STRING), Boolean.FALSE));

        NumberQueryObject qo = NumberQueryObject.builder().in(List.of(12d, 13d)).build();
        // WHEN
        Option<ICriterion> criterion = new NumberQueryCriterionBuilder("stacProp").buildCriterion(properties, qo);
        // THEN
        assertThat(criterion).isNotEmpty();

        java.util.List<ICriterion> innerCrits = ((OrCriterion) criterion.get()).getCriterions();
        assertThat(innerCrits).hasSize(2);
        assertThatRangeGoesFromTo((RangeCriterion<Double>) innerCrits.get(0),
                                  regardsAttr.getAttributeModel().getFullJsonPath(), GREATER_OR_EQUAL,
                                  12d - DOUBLE_COMPARISON_PRECISION, LESS_OR_EQUAL, 12d + DOUBLE_COMPARISON_PRECISION);
        assertThatRangeGoesFromTo((RangeCriterion<Double>) innerCrits.get(1),
                                  regardsAttr.getAttributeModel().getFullJsonPath(), GREATER_OR_EQUAL,
                                  13d - DOUBLE_COMPARISON_PRECISION, LESS_OR_EQUAL, 13d + DOUBLE_COMPARISON_PRECISION);
    }

    private void assertThatRangeGoesFromTo(RangeCriterion<Double> doubleRangeCriterion, String attr,
            ComparisonOperator opMin, double min, ComparisonOperator opMax, double max) {
        assertThat(doubleRangeCriterion.getName()).isEqualTo(attr);
        List<ValueComparison<Double>> valueComps = List.ofAll(doubleRangeCriterion.getValueComparisons());
        assertThat(valueComps).hasSize(2);
        assertThat(valueComps).anyMatch(vc -> (vc.getOperator() == opMin) && vc.getValue().equals(min))
                .anyMatch(vc -> (vc.getOperator() == opMax) && vc.getValue().equals(max));
    }

}