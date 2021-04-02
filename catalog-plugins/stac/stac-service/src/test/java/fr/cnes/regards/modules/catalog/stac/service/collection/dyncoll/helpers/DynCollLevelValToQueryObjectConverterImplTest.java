package fr.cnes.regards.modules.catalog.stac.service.collection.dyncoll.helpers;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.DatePartsLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.NumberRangeLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.StringPrefixLevelDef;
import org.junit.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

public class DynCollLevelValToQueryObjectConverterImplTest {

    DynCollLevelDefParserImpl parser = new DynCollLevelDefParserImpl();
    DynCollLevelValToQueryObjectConverterImpl converter = new DynCollLevelValToQueryObjectConverterImpl();

    @Test
    public void stringPrefixQueryObject() {
        // GIVEN
        StringPrefixLevelDef def = (StringPrefixLevelDef) parser.parse(new StacProperty(null, "prop", "", false, -1, "PREFIX(2,A9)", StacPropertyType.STRING, null));

        // WHEN
        ItemSearchBody.QueryObject q = converter.stringPrefixQueryObject(def.parseValues("AB"), def);

        // THEN
        assertThat(q).isEqualTo(ItemSearchBody.StringQueryObject.builder().startsWith("AB").build());
    }

    @Test
    public void datePartsYearQueryObject() {
        // GIVEN
        DatePartsLevelDef def = (DatePartsLevelDef) parser.parse(new StacProperty(null, "prop", "", false, -1, "YEAR", StacPropertyType.DATETIME, null));

        // WHEN
        ItemSearchBody.QueryObject q = converter.datePartsQueryObject(def.parseValues("2021"), def);

        // THEN
        assertThat(q).isEqualTo(ItemSearchBody.DatetimeQueryObject.builder()
            .gte(OffsetDateTime.of(2021, 01, 01, 00, 00, 00, 000, ZoneOffset.UTC))
            .lt(OffsetDateTime.of(2022, 01, 01, 00, 00, 00, 000, ZoneOffset.UTC))
            .build()
        );
    }

    @Test
    public void datePartsMonthQueryObject() {
        // GIVEN
        DatePartsLevelDef def = (DatePartsLevelDef) parser.parse(new StacProperty(null, "prop", "", false, -1, "MONTH", StacPropertyType.DATETIME, null));

        // WHEN
        ItemSearchBody.QueryObject q = converter.datePartsQueryObject(def.parseValues("2021-02"), def);

        // THEN
        assertThat(q).isEqualTo(ItemSearchBody.DatetimeQueryObject.builder()
                .gte(OffsetDateTime.of(2021, 02, 01, 00, 00, 00, 000, ZoneOffset.UTC))
                .lt(OffsetDateTime.of(2021, 03, 01, 00, 00, 00, 000, ZoneOffset.UTC))
                .build()
        );
    }

    @Test
    public void datePartsMonthYearQueryObject() {
        // GIVEN
        DatePartsLevelDef def = (DatePartsLevelDef) parser.parse(new StacProperty(null, "prop", "", false, -1, "MONTH", StacPropertyType.DATETIME, null));

        // WHEN
        ItemSearchBody.QueryObject q = converter.datePartsQueryObject(def.parseValues("2021"), def);

        // THEN
        assertThat(q).isEqualTo(ItemSearchBody.DatetimeQueryObject.builder()
                .gte(OffsetDateTime.of(2021, 01, 01, 00, 00, 00, 000, ZoneOffset.UTC))
                .lt(OffsetDateTime.of(2022, 01, 01, 00, 00, 00, 000, ZoneOffset.UTC))
                .build()
        );
    }

    @Test
    public void numberRangeQueryObject() {
        // GIVEN
        NumberRangeLevelDef def = (NumberRangeLevelDef) parser.parse(new StacProperty(null, "prop", "", false, -1, "5;10;95", StacPropertyType.NUMBER, null));

        // WHEN
        ItemSearchBody.QueryObject q = converter.numberRangeQueryObject(def.parseValues("15;25"), def);

        // THEN
        assertThat(q).isEqualTo(ItemSearchBody.NumberQueryObject.builder()
                .gte(15d)
                .lte(25d)
                .build()
        );
    }

}