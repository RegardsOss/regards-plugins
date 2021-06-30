package fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelVal;
import io.vavr.collection.List;
import org.junit.Test;

import static fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelType.DatetimeBased.MINUTE;
import static org.assertj.core.api.Assertions.assertThat;

public class DatePartsLevelDefTest {

    // GIVEN
    StacProperty prop = new StacProperty(null, null, "prop", "", false, 2, "MINUTE", StacPropertyType.DATETIME, null,
                                         Boolean.FALSE);

    DatePartsLevelDef ldef = new DatePartsLevelDef(prop, MINUTE);

    @Test
    public void testLabels() {
        // GIVEN
        DynCollLevelVal lval = ldef.parseValues("2020-12-31T23:59");
        //        List<DynCollSublevelVal> sublevelVals = lval.getSublevels();
        // WHEN/THEN
        assertThat(lval.getSublevels().get(0).getSublevelLabel()).isEqualTo("prop=2020");
        assertThat(lval.getSublevels().get(1).getSublevelLabel()).isEqualTo("prop=2020-12");
        assertThat(lval.getSublevels().get(2).getSublevelLabel()).isEqualTo("prop=2020-12-31");
        assertThat(lval.getSublevels().get(3).getSublevelLabel()).isEqualTo("prop=2020-12-31T23");
        assertThat(lval.getSublevels().get(4).getSublevelLabel()).isEqualTo("prop=2020-12-31T23:59");
    }

    @Test
    public void testParseValuesAndRenderValue() {
        // GIVEN
        DynCollLevelVal lval = ldef.parseValues("2020-12-31T23:59");
        List<DynCollSublevelVal> sublevelVals = lval.getSublevels();
        // WHEN/THEN
        assertThat(ldef.renderValue(new DynCollLevelVal(ldef, sublevelVals.take(1)))).isEqualTo("2020");
        assertThat(ldef.renderValue(new DynCollLevelVal(ldef, sublevelVals.take(2)))).isEqualTo("2020-12");
        assertThat(ldef.renderValue(new DynCollLevelVal(ldef, sublevelVals.take(3)))).isEqualTo("2020-12-31");
        assertThat(ldef.renderValue(new DynCollLevelVal(ldef, sublevelVals.take(4)))).isEqualTo("2020-12-31T23");
        assertThat(ldef.renderValue(new DynCollLevelVal(ldef, sublevelVals.take(5)))).isEqualTo("2020-12-31T23:59");
    }

    @Test
    public void isFullyValued() {
        // GIVEN
        DynCollLevelVal lval = ldef.parseValues("2020-12-31T23:59");
        List<DynCollSublevelVal> sublevelVals = lval.getSublevels();
        // WHEN/THEN
        assertThat(ldef.isFullyValued(new DynCollLevelVal(ldef, sublevelVals.take(1)))).isFalse();
        assertThat(ldef.isFullyValued(new DynCollLevelVal(ldef, sublevelVals.take(2)))).isFalse();
        assertThat(ldef.isFullyValued(new DynCollLevelVal(ldef, sublevelVals.take(3)))).isFalse();
        assertThat(ldef.isFullyValued(new DynCollLevelVal(ldef, sublevelVals.take(4)))).isFalse();
        assertThat(ldef.isFullyValued(new DynCollLevelVal(ldef, sublevelVals.take(5)))).isTrue();
    }

}