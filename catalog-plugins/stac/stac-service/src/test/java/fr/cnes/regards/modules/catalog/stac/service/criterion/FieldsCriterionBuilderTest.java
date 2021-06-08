package fr.cnes.regards.modules.catalog.stac.service.criterion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.Fields;
import fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.IdentityPropertyConverter;
import fr.cnes.regards.modules.indexer.domain.criterion.FieldExistsCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.NotCriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;

public class FieldsCriterionBuilderTest implements RegardsPropertyAccessorAwareTest {

    @Test
    public void testBuildCriterionEmpty() {
        // GIVEN
        List<StacProperty> properties = List.of();
        // WHEN
        Option<ICriterion> criterion = new FieldsCriterionBuilder().buildCriterion(properties,
                                                                                   new Fields(List.empty(), List.empty()));
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterionNull() {
        // GIVEN
        List<StacProperty> properties = List.of();
        // WHEN
        Option<ICriterion> criterion = new FieldsCriterionBuilder().buildCriterion(properties, null);
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterionInclude() {
        // GIVEN
        List<StacProperty> properties = List.of();
        Fields fields = new Fields(List.of("inc1"), List.empty());
        // WHEN
        Option<ICriterion> criterion = new FieldsCriterionBuilder().buildCriterion(properties, fields);
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterionIncludeMany() {
        // GIVEN
        RegardsPropertyAccessor accessor = accessor("regardsInc1", StacPropertyType.STRING, "value");
        List<StacProperty> properties = List.of(new StacProperty(accessor,

                null, "inc1", "inc", false, 0, null, StacPropertyType.STRING,
                new IdentityPropertyConverter<>(StacPropertyType.STRING)));
        Fields fields = new Fields(List.of("inc1", "inc2"), List.empty());
        // WHEN
        Option<ICriterion> criterion = new FieldsCriterionBuilder().buildCriterion(properties, fields);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(FieldExistsCriterion.class);

        FieldExistsCriterion fieldsCrit = (FieldExistsCriterion) criterion.get();
        assertThat(fieldsCrit.getName()).isEqualTo(accessor.getAttributeModel().getFullJsonPath());
    }

    @Test
    public void testBuildCriterionExclude() {
        // GIVEN
        List<StacProperty> properties = List.of();
        Fields fields = new Fields(List.empty(), List.of("exc1"));
        // WHEN
        Option<ICriterion> criterion = new FieldsCriterionBuilder().buildCriterion(properties, fields);
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterionExcludeMany() {
        // GIVEN
        RegardsPropertyAccessor accessor = accessor("regardsExc1", StacPropertyType.STRING, "value");

        List<StacProperty> properties = List.of(new StacProperty(accessor, null, "exc1", "exc", false, 0, null,
                StacPropertyType.STRING, new IdentityPropertyConverter<>(StacPropertyType.STRING)));

        Fields fields = new Fields(List.empty(), List.of("exc1", "exc2"));
        // WHEN
        Option<ICriterion> criterion = new FieldsCriterionBuilder().buildCriterion(properties, fields);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(NotCriterion.class);
        NotCriterion notCrit = (NotCriterion) criterion.get();
        assertThat(((FieldExistsCriterion) notCrit.getCriterion()).getName())
                .isEqualTo(accessor.getAttributeModel().getFullJsonPath());

    }

}