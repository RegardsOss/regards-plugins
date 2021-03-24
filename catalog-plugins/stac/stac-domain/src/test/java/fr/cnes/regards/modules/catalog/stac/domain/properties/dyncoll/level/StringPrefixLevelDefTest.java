package fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.StringPrefixSublevelDef;
import io.vavr.collection.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StringPrefixLevelDefTest {

    // GIVEN
    StacProperty prop = new StacProperty(null, "prop", "", false, 2, "PREFIX(3,9)", StacPropertyType.STRING, null);
    StringPrefixLevelDef ldef = new StringPrefixLevelDef(prop, List.of(
            new StringPrefixSublevelDef(0, false, true),
            new StringPrefixSublevelDef(1, false, true),
            new StringPrefixSublevelDef(2, false, true)
    ));
    List<StringPrefixSublevelDef> sublevelDefs = ldef.getSublevels();

    @Test
    public void testLabels() {
        // GIVEN
        DynCollLevelVal lval = ldef.parseValues("123");
        List<DynCollSublevelVal> sublevelVals = lval.getSublevels();
        // WHEN/THEN
        assertThat(lval.getSublevels().get(0).getSublevelLabel()).isEqualTo("prop=1...");
        assertThat(lval.getSublevels().get(1).getSublevelLabel()).isEqualTo("prop=12...");
        assertThat(lval.getSublevels().get(2).getSublevelLabel()).isEqualTo("prop=123...");
    }

    @Test
    public void testParseValuesAndRenderValue() {
        // GIVEN
        DynCollLevelVal lval = ldef.parseValues("123");
        List<DynCollSublevelVal> sublevelVals = lval.getSublevels();
        // WHEN/THEN
        assertThat(ldef.renderValue(new DynCollLevelVal(ldef, sublevelVals.take(1)))).isEqualTo("1");
        assertThat(ldef.renderValue(new DynCollLevelVal(ldef, sublevelVals.take(2)))).isEqualTo("12");
        assertThat(ldef.renderValue(new DynCollLevelVal(ldef, sublevelVals.take(3)))).isEqualTo("123");
    }

    @Test
    public void isFullyValued() {
        // GIVEN
        DynCollLevelVal lval = ldef.parseValues("123");
        List<DynCollSublevelVal> sublevelVals = lval.getSublevels();
        // WHEN/THEN
        assertThat(ldef.isFullyValued(new DynCollLevelVal(ldef, sublevelVals.take(1)))).isFalse();
        assertThat(ldef.isFullyValued(new DynCollLevelVal(ldef, sublevelVals.take(2)))).isFalse();
        assertThat(ldef.isFullyValued(new DynCollLevelVal(ldef, sublevelVals.take(3)))).isTrue();
    }
}