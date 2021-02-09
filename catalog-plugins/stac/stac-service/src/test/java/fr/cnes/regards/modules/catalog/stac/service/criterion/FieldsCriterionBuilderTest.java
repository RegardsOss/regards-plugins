package fr.cnes.regards.modules.catalog.stac.service.criterion;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.Fields;
import fr.cnes.regards.modules.catalog.stac.domain.properties.PropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.IdentityPropertyConverter;
import fr.cnes.regards.modules.indexer.domain.criterion.AndCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.FieldExistsCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.NotCriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldsCriterionBuilderTest {

    @Test
    public void testBuildCriterionEmpty() {
        // GIVEN
        List<StacProperty> properties = List.of();
        // WHEN
        Option<ICriterion> criterion = new FieldsCriterionBuilder().buildCriterion(properties, new Fields(List.empty(), List.empty()));
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
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(FieldExistsCriterion.class);
        assertThat(((FieldExistsCriterion)criterion.get()).getName()).isEqualTo("inc1");
    }

    @Test
    public void testBuildCriterionIncludeMany() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(
                "regardsInc1", "inc1",
                "inc", false, 0, PropertyType.STRING,
                new IdentityPropertyConverter<>(PropertyType.STRING)
        ));
        Fields fields = new Fields(List.of("inc1", "inc2"), List.empty());
        // WHEN
        Option<ICriterion> criterion = new FieldsCriterionBuilder().buildCriterion(properties, fields);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(AndCriterion.class);


        AndCriterion andCrit = (AndCriterion) criterion.get();
        java.util.List<ICriterion> andCrits = andCrit.getCriterions();
        assertThat(andCrits).hasSize(2);

        assertThat(andCrits.get(0)).isInstanceOf(FieldExistsCriterion.class);
        assertThat(((FieldExistsCriterion)andCrits.get(0)).getName()).isEqualTo("regardsInc1");

        assertThat(andCrits.get(1)).isInstanceOf(FieldExistsCriterion.class);
        assertThat(((FieldExistsCriterion)andCrits.get(1)).getName()).isEqualTo("inc2");
    }

    @Test
    public void testBuildCriterionExclude() {
        // GIVEN
        List<StacProperty> properties = List.of();
        Fields fields = new Fields(List.empty(), List.of("exc1"));
        // WHEN
        Option<ICriterion> criterion = new FieldsCriterionBuilder().buildCriterion(properties, fields);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(NotCriterion.class);
        assertThat(((NotCriterion)criterion.get()).getCriterion()).isInstanceOf(FieldExistsCriterion.class);
        assertThat(((FieldExistsCriterion)((NotCriterion)criterion.get()).getCriterion()).getName()).isEqualTo("exc1");
    }

    @Test
    public void testBuildCriterionExcludeMany() {
        // GIVEN
        List<StacProperty> properties = List.of(
                new StacProperty(
                        "regardsExc1", "exc1",
                        "exc", false, 0, PropertyType.STRING,
                        new IdentityPropertyConverter<>(PropertyType.STRING)
                )
        );

        Fields fields = new Fields(List.empty(), List.of("exc1", "exc2"));
        // WHEN
        Option<ICriterion> criterion = new FieldsCriterionBuilder().buildCriterion(properties, fields);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(AndCriterion.class);

        AndCriterion andCrit = (AndCriterion) criterion.get();
        java.util.List<ICriterion> andCrits = andCrit.getCriterions();
        assertThat(andCrits).hasSize(2);

        assertThat(andCrits.get(0)).isInstanceOf(NotCriterion.class);
        assertThat(((FieldExistsCriterion)((NotCriterion)andCrits.get(0)).getCriterion()).getName()).isEqualTo("regardsExc1");

        assertThat(andCrits.get(1)).isInstanceOf(NotCriterion.class);
        assertThat(((FieldExistsCriterion)((NotCriterion)andCrits.get(1)).getCriterion()).getName()).isEqualTo("exc2");

    }


}