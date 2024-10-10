package fr.cnes.regards.modules.catalog.stac.service.criterion.query;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.IdentityPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.service.criterion.RegardsPropertyAccessorAwareTest;
import fr.cnes.regards.modules.indexer.domain.criterion.*;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.Test;

import java.time.OffsetDateTime;

import static java.time.OffsetDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;

public class StringQueryCriterionBuilderTest implements RegardsPropertyAccessorAwareTest {

    @Test
    public void testBuildCriterionEmpty() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(accessor("regardsProp",
                                                                          StacPropertyType.STRING,
                                                                          "toto"),
                                                                 null,
                                                                 "stacProp",
                                                                 "",
                                                                 false,
                                                                 0,
                                                                 null,
                                                                 StacPropertyType.STRING,
                                                                 new IdentityPropertyConverter<>(StacPropertyType.STRING),
                                                                 Boolean.FALSE));
        // WHEN
        Option<ICriterion> criterion = new StringQueryCriterionBuilder("stacProp").buildCriterion(properties, null);
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterionEq() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(accessor("regardsAttr",
                                                                          StacPropertyType.STRING,
                                                                          "toto"),
                                                                 null,
                                                                 "stacProp",
                                                                 "",
                                                                 false,
                                                                 0,
                                                                 null,
                                                                 StacPropertyType.STRING,
                                                                 new IdentityPropertyConverter<>(StacPropertyType.STRING),
                                                                 Boolean.FALSE));

        OffsetDateTime now = now();
        SearchBody.StringQueryObject qo = SearchBody.StringQueryObject.builder().eq("toto").build();
        // WHEN
        Option<ICriterion> criterion = new StringQueryCriterionBuilder("stacProp").buildCriterion(properties, qo);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(StringMatchCriterion.class);

        assertThat(((StringMatchCriterion) criterion.get()).getName()).isEqualTo("feature.properties.regardsAttr");
        assertThat(((StringMatchCriterion) criterion.get()).getValue()).isEqualTo("toto");
    }

    @Test
    public void testBuildCriterionAll() {
        // GIVEN
        List<StacProperty> properties = List.of(new StacProperty(accessor("regardsAttr",
                                                                          StacPropertyType.STRING,
                                                                          "test"),
                                                                 null,
                                                                 "stacProp",
                                                                 "",
                                                                 false,
                                                                 0,
                                                                 null,
                                                                 StacPropertyType.STRING,
                                                                 new IdentityPropertyConverter<>(StacPropertyType.STRING),
                                                                 Boolean.FALSE));

        OffsetDateTime now = now();
        SearchBody.StringQueryObject qo = SearchBody.StringQueryObject.builder()
                                                                      .eq("test")
                                                                      .neq("notest")
                                                                      .startsWith("te")
                                                                      .endsWith("st")
                                                                      .contains("es")
                                                                      .containsAll(List.of("contains1", "contains2"))
                                                                      .in(List.of("in1", "in2", "in3"))
                                                                      .build();

        // WHEN
        Option<ICriterion> criterion = new StringQueryCriterionBuilder("stacProp").buildCriterion(properties, qo);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(AndCriterion.class);

        AndCriterion andCrit = (AndCriterion) criterion.get();
        java.util.List<ICriterion> andCrits = andCrit.getCriterions();
        assertThat(andCrits).hasSize(7);

        //eq criterion
        assertThat(andCrits.get(0)).isInstanceOf(StringMatchCriterion.class);
        assertThat(((StringMatchCriterion) andCrits.get(0)).getName()).isEqualTo("feature.properties.regardsAttr");
        assertThat(((StringMatchCriterion) andCrits.get(0)).getType()).isEqualTo(MatchType.EQUALS);
        assertThat(((StringMatchCriterion) andCrits.get(0)).getValue()).isEqualTo("test");

        //neq criterion
        assertThat(andCrits.get(1)).isInstanceOf(NotCriterion.class);
        assertThat(((StringMatchCriterion) ((NotCriterion) andCrits.get(1)).getCriterion()).getName()).isEqualTo(
            "feature.properties.regardsAttr");
        assertThat(((StringMatchCriterion) ((NotCriterion) andCrits.get(1)).getCriterion()).getType()).isEqualTo(
            MatchType.EQUALS);
        assertThat(((StringMatchCriterion) ((NotCriterion) andCrits.get(1)).getCriterion()).getValue()).isEqualTo(
            "notest");

        //startsWith criterion
        assertThat(andCrits.get(2)).isInstanceOf(StringMatchCriterion.class);
        assertThat(((StringMatchCriterion) andCrits.get(2)).getName()).isEqualTo("feature.properties.regardsAttr");
        assertThat(((StringMatchCriterion) andCrits.get(2)).getType()).isEqualTo(MatchType.REGEXP);
        assertThat(((StringMatchCriterion) andCrits.get(2)).getValue()).isEqualTo("[tT][eE].*");

        //endsWith criterion
        assertThat(andCrits.get(3)).isInstanceOf(StringMatchCriterion.class);
        assertThat(((StringMatchCriterion) andCrits.get(3)).getName()).isEqualTo("feature.properties.regardsAttr");
        assertThat(((StringMatchCriterion) andCrits.get(3)).getType()).isEqualTo(MatchType.REGEXP);
        assertThat(((StringMatchCriterion) andCrits.get(3)).getValue()).isEqualTo(".*[sS][tT]");

        //conatins criterion
        assertThat(andCrits.get(4)).isInstanceOf(StringMatchCriterion.class);
        assertThat(((StringMatchCriterion) andCrits.get(4)).getName()).isEqualTo("feature.properties.regardsAttr");
        assertThat(((StringMatchCriterion) andCrits.get(4)).getType()).isEqualTo(MatchType.CONTAINS);
        assertThat(((StringMatchCriterion) andCrits.get(4)).getValue()).isEqualTo("es");

        //containsAll criterion
        assertThat(andCrits.get(5)).isInstanceOf(AndCriterion.class);
        assertThat(((AndCriterion) andCrits.get(5)).getCriterions()).hasSize(2);
        java.util.List<ICriterion> containsAllCrits = ((AndCriterion) andCrits.get(5)).getCriterions();

        assertThat(containsAllCrits.get(0)).isInstanceOf(StringMatchCriterion.class);
        assertThat(((StringMatchCriterion) containsAllCrits.get(0)).getName()).isEqualTo("feature.properties"
                                                                                         + ".regardsAttr");
        assertThat(((StringMatchCriterion) containsAllCrits.get(0)).getType()).isEqualTo(MatchType.CONTAINS);
        assertThat(((StringMatchCriterion) containsAllCrits.get(0)).getValue()).isEqualTo("contains1");

        assertThat(containsAllCrits.get(1)).isInstanceOf(StringMatchCriterion.class);
        assertThat(((StringMatchCriterion) containsAllCrits.get(1)).getName()).isEqualTo("feature.properties"
                                                                                         + ".regardsAttr");
        assertThat(((StringMatchCriterion) containsAllCrits.get(1)).getType()).isEqualTo(MatchType.CONTAINS);
        assertThat(((StringMatchCriterion) containsAllCrits.get(1)).getValue()).isEqualTo("contains2");

        //in criterion
        assertThat(andCrits.get(6)).isInstanceOf(OrCriterion.class);
        assertThat(((OrCriterion) andCrits.get(6)).getCriterions()).hasSize(3);
        java.util.List<ICriterion> inCrits = ((OrCriterion) andCrits.get(6)).getCriterions();

        assertThat(inCrits.get(0)).isInstanceOf(StringMatchCriterion.class);
        assertThat(((StringMatchCriterion) inCrits.get(0)).getName()).isEqualTo("feature.properties" + ".regardsAttr");
        assertThat(((StringMatchCriterion) inCrits.get(0)).getType()).isEqualTo(MatchType.EQUALS);
        assertThat(((StringMatchCriterion) inCrits.get(0)).getValue()).isEqualTo("in1");

        assertThat(inCrits.get(1)).isInstanceOf(StringMatchCriterion.class);
        assertThat(((StringMatchCriterion) inCrits.get(1)).getName()).isEqualTo("feature.properties" + ".regardsAttr");
        assertThat(((StringMatchCriterion) inCrits.get(1)).getType()).isEqualTo(MatchType.EQUALS);
        assertThat(((StringMatchCriterion) inCrits.get(1)).getValue()).isEqualTo("in2");

        assertThat(inCrits.get(2)).isInstanceOf(StringMatchCriterion.class);
        assertThat(((StringMatchCriterion) inCrits.get(2)).getName()).isEqualTo("feature.properties" + ".regardsAttr");
        assertThat(((StringMatchCriterion) inCrits.get(2)).getType()).isEqualTo(MatchType.EQUALS);
        assertThat(((StringMatchCriterion) inCrits.get(2)).getValue()).isEqualTo("in3");

    }

}