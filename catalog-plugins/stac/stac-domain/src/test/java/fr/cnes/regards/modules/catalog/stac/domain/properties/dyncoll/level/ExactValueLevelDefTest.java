package fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;

public class ExactValueLevelDefTest {

    // GIVEN
    StacProperty prop = new StacProperty(null, null, "prop", "", false, 2, "", StacPropertyType.STRING, null);

    ExactValueLevelDef ldef = new ExactValueLevelDef(prop);

    @Test
    public void testLabels() {
        // GIVEN
        DynCollLevelVal lval = ldef.parseValues("Test");
        // WHEN/THEN
        assertThat(lval.getSublevels().get(0).getSublevelLabel()).isEqualTo("prop=Test");
    }

    @Test
    public void testParseValuesAndRenderValue() {
        // GIVEN
        DynCollLevelVal lval = ldef.parseValues("Test");
        // WHEN/THEN
        assertThat(ldef.renderValue(lval)).isEqualTo("Test");
    }

    @Test
    public void isFullyValued() {
        // GIVEN
        DynCollLevelVal lval = ldef.parseValues("Test");
        // WHEN/THEN
        assertThat(ldef.isFullyValued(lval)).isTrue();
    }
}