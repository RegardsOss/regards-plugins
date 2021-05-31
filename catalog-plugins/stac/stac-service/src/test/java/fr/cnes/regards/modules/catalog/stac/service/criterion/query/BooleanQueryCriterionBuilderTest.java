package fr.cnes.regards.modules.catalog.stac.service.criterion.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.BooleanQueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.IdentityPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.service.criterion.RegardsPropertyAccessorAwareTest;
import fr.cnes.regards.modules.indexer.domain.criterion.AndCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.BooleanMatchCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;

public class BooleanQueryCriterionBuilderTest implements RegardsPropertyAccessorAwareTest {

    @Test
    public void testBuildCriterionEmpty() {
        // GIVEN
        List<StacProperty> properties = List
                .of(new StacProperty(accessor("regardsBool", StacPropertyType.STRING, true), null, "stacBool", "", false, 0,
                        null, StacPropertyType.STRING, new IdentityPropertyConverter<>(StacPropertyType.STRING)));
        // WHEN
        Option<ICriterion> criterion = new BooleanQueryCriterionBuilder("stacBool").buildCriterion(properties, null);
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterion() {
        // GIVEN
        List<StacProperty> properties = List
                .of(new StacProperty(accessor("regardsBool", StacPropertyType.STRING, true), null, "stacBool", "", false, 0,
                        null, StacPropertyType.STRING, new IdentityPropertyConverter<>(StacPropertyType.STRING)));
        BooleanQueryObject bqo = BooleanQueryObject.builder().eq(true).neq(false).build();
        // WHEN
        Option<ICriterion> criterion = new BooleanQueryCriterionBuilder("stacBool").buildCriterion(properties, bqo);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(AndCriterion.class);

        AndCriterion andCrit = (AndCriterion) criterion.get();
        java.util.List<ICriterion> andCrits = andCrit.getCriterions();
        assertThat(andCrits).hasSize(2);

        assertThat(andCrits.get(0)).isInstanceOf(BooleanMatchCriterion.class);
        assertThat(((BooleanMatchCriterion) andCrits.get(0)).getName()).isEqualTo("feature.properties.regardsBool");
        assertThat(((BooleanMatchCriterion) andCrits.get(0)).getValue()).isEqualTo(true);

        assertThat(andCrits.get(1)).isInstanceOf(BooleanMatchCriterion.class);
        assertThat(((BooleanMatchCriterion) andCrits.get(1)).getName()).isEqualTo("feature.properties.regardsBool");
        assertThat(((BooleanMatchCriterion) andCrits.get(1)).getValue()).isEqualTo(true);
    }

}