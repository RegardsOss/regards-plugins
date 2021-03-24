package fr.cnes.regards.modules.catalog.stac.service.collection.dynamic.helpers;

import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.IdentityPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.DatePartsLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.NumberRangeLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.StringPrefixLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.NumberRangeSublevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.StringPrefixSublevelDef;
import fr.cnes.regards.modules.catalog.stac.service.StacSearchCriterionBuilder;
import fr.cnes.regards.modules.catalog.stac.service.criterion.RegardsPropertyAccessorAwareTest;
import fr.cnes.regards.modules.indexer.dao.IEsRepository;
import fr.cnes.regards.modules.indexer.dao.spatial.ProjectGeoSettings;
import fr.cnes.regards.modules.indexer.domain.spatial.Crs;
import io.vavr.collection.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType.*;
import static java.time.OffsetDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class DynCollValNextSublevelHelperImplTest implements RegardsPropertyAccessorAwareTest {

    // GIVEN

    IEsRepository esRepository = Mockito.mock(IEsRepository.class);
    DynCollLevelValToQueryObjectConverter levelValToQueryObjectConverter = new DynCollLevelValToQueryObjectConverterImpl();
    StacSearchCriterionBuilder criterionBuilder = Mockito.mock(StacSearchCriterionBuilder.class);
    IRuntimeTenantResolver tenantResolver = Mockito.mock(IRuntimeTenantResolver.class);
    ProjectGeoSettings projectGeoSettings = Mockito.mock(ProjectGeoSettings.class);

    DynCollValNextSublevelHelperImpl helper = new DynCollValNextSublevelHelperImpl(
            esRepository,
            levelValToQueryObjectConverter,
            criterionBuilder,
            tenantResolver,
            projectGeoSettings
    );

    StacProperty prop1 = new StacProperty(
            accessor("prop1", NUMBER, 12d),
            "prop1",
            "",
            false,
            1,
            "0;10;20",
            NUMBER,
            new IdentityPropertyConverter<>(NUMBER)
    );
    StacProperty prop2 = new StacProperty(
            accessor("prop2", DATETIME, now()),
            "prop2",
            "",
            false,
            2,
            "DAY",
            DATETIME,
            new IdentityPropertyConverter<>(DATETIME)
    );
    StacProperty prop3 = new StacProperty(
            accessor("prop3", STRING, ""),
            "prop3",
            "",
            false,
            3,
            "PREFIX(2,9)",
            STRING,
            new IdentityPropertyConverter<>(STRING)
    );

    NumberRangeLevelDef numberRangeLevelDef = new NumberRangeLevelDef(prop1, new NumberRangeSublevelDef(0, 10, 20));
    DatePartsLevelDef datePartsLevelDef = new DatePartsLevelDef(prop2, DynCollSublevelType.DatetimeBased.DAY);
    StringPrefixLevelDef stringPrefixLevelDef = new StringPrefixLevelDef(prop3, List.of(
            new StringPrefixSublevelDef(0, false, true),
            new StringPrefixSublevelDef(1, false, true)
    ));

    DynCollDef def = new DynCollDef(List.of(
            numberRangeLevelDef,
            datePartsLevelDef,
            stringPrefixLevelDef
    ));

    @Before
    public void init() {
        when(tenantResolver.getTenant()).thenReturn("theTenant");
        when(projectGeoSettings.getCrs()).thenReturn(Crs.WGS_84);
        when(esRepository.minDate(any(), any(), anyString())).thenAnswer(i -> OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        when(esRepository.maxDate(any(), any(), anyString())).thenAnswer(i -> OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    public void nextSublevels_empty() {
        // WHEN
        List<DynCollVal> actual = helper.nextSublevels(new DynCollVal(def, List.empty()));
        // THEN
        assertThat(actual).hasSize(4);
        assertThat(actual.get(0).getLevels().get(0).getSublevels().get(0).getSublevelValue()).isEqualTo("<0.0");
        assertThat(actual.get(1).getLevels().get(0).getSublevels().get(0).getSublevelValue()).isEqualTo("0.0;10.0");
        assertThat(actual.get(2).getLevels().get(0).getSublevels().get(0).getSublevelValue()).isEqualTo("10.0;20.0");
        assertThat(actual.get(3).getLevels().get(0).getSublevels().get(0).getSublevelValue()).isEqualTo(">20.0");
    }

    @Test
    public void nextSublevels_hasNum() {
        // WHEN
        List<DynCollVal> actual = helper.nextSublevels(new DynCollVal(def,List.of(
                numberRangeLevelDef.parseValues("20;30")
        )));
        // THEN
        assertThat(actual).hasSize(2);
        assertThat(actual.get(0).getLevels().get(1).getSublevels().get(0).getSublevelValue()).isEqualTo("2020");
        assertThat(actual.get(1).getLevels().get(1).getSublevels().get(0).getSublevelValue()).isEqualTo("2021");
    }

    @Test
    public void nextSublevels_hasNumYear() {
        // WHEN
        List<DynCollVal> actual = helper.nextSublevels(new DynCollVal(def,List.of(
                numberRangeLevelDef.parseValues("20;30"),
                datePartsLevelDef.parseValues("2020")
        )));
        // THEN
        assertThat(actual).hasSize(12);
        assertThat(actual.get(0).getLevels().get(1).getSublevels().get(1).getSublevelValue()).isEqualTo("2020-01");
        // ...
        assertThat(actual.get(11).getLevels().get(1).getSublevels().get(1).getSublevelValue()).isEqualTo("2020-12");
    }


    @Test
    public void nextSublevels_hasNumYearMonth() {
        // WHEN
        List<DynCollVal> actual = helper.nextSublevels(new DynCollVal(def,List.of(
                numberRangeLevelDef.parseValues("20;30"),
                datePartsLevelDef.parseValues("2020-02")
        )));
        // THEN
        assertThat(actual).hasSize(28);
        assertThat(actual.get(0).getLevels().get(1).getSublevels().get(2).getSublevelValue()).isEqualTo("2020-02-01");
        // ...
        assertThat(actual.get(27).getLevels().get(1).getSublevels().get(2).getSublevelValue()).isEqualTo("2020-02-28");
    }

    @Test
    public void nextSublevels_hasNumYearMonthDay() {
        // WHEN
        List<DynCollVal> actual = helper.nextSublevels(new DynCollVal(def,List.of(
                numberRangeLevelDef.parseValues("20;30"),
                datePartsLevelDef.parseValues("2020-02-16")
        )));
        // THEN
        assertThat(actual).hasSize(10);
        assertThat(actual.get(0).getLevels().get(2).getSublevels().get(0).getSublevelValue()).isEqualTo("0");
        // ...
        assertThat(actual.get(9).getLevels().get(2).getSublevels().get(0).getSublevelValue()).isEqualTo("9");
    }


    @Test
    public void nextSublevels_hasNumYearMonthDayFst() {
        // WHEN
        List<DynCollVal> actual = helper.nextSublevels(new DynCollVal(def,List.of(
                numberRangeLevelDef.parseValues("20;30"),
                datePartsLevelDef.parseValues("2020-02-16"),
                stringPrefixLevelDef.parseValues("7")
        )));
        // THEN
        assertThat(actual).hasSize(10);
        assertThat(actual.get(0).getLevels().get(2).getSublevels().get(1).getSublevelValue()).isEqualTo("70");
        // ...
        assertThat(actual.get(9).getLevels().get(2).getSublevels().get(1).getSublevelValue()).isEqualTo("79");
    }

    @Test
    public void nextSublevels_full() {
        // WHEN
        List<DynCollVal> actual = helper.nextSublevels(new DynCollVal(def,List.of(
                numberRangeLevelDef.parseValues("20;30"),
                datePartsLevelDef.parseValues("2020-02-16"),
                stringPrefixLevelDef.parseValues("77")
        )));
        // THEN
        assertThat(actual).isEmpty();
    }

}