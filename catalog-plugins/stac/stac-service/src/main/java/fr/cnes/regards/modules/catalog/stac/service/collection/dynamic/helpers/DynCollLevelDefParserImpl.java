/*
 * Copyright 2017-2021 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
 *
 * This file is part of REGARDS.
 *
 * REGARDS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * REGARDS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with REGARDS. If not, see <http://www.gnu.org/licenses/>.
 */

package fr.cnes.regards.modules.catalog.stac.service.collection.dynamic.helpers;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.*;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.NumberRangeSublevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.StringPrefixSublevelDef;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.micrometer.core.instrument.util.StringUtils.isBlank;

/**
 * Base implementation for {@link DynCollLevelDefParser}.
 */
@Component
public class DynCollLevelDefParserImpl implements DynCollLevelDefParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynCollLevelDefParserImpl.class);

    @Override
    public DynCollLevelDef<?> parse(StacProperty prop) {
        switch (prop.getStacType()) {
            case STRING: return parseStringLevelDef(prop);
            case PERCENTAGE: case ANGLE: case LENGTH: case NUMBER: return parseNumberLevelDef(prop);
            case DATETIME: return parseDateTimeLevelDef(prop);
            default: return parseExactLevelDef(prop);
        }
    }

    private DynCollLevelDef<?> parseExactLevelDef(StacProperty prop) {
        return new ExactValueLevelDef(prop);
    }

    private DynCollLevelDef<?> parseDateTimeLevelDef(StacProperty prop) {
        String format = prop.getDynamicCollectionFormat();
        DynCollSublevelType.DatetimeBased sublevelDeepestType = Try
            .of(() -> DynCollSublevelType.DatetimeBased.valueOf(format))
            .onFailure(t -> LOGGER.warn("Unparsable date sublevel definition: {}, using 'DAY' instead", format, t))
            // DAY by default if not specified or unparsable
            .getOrElse(DynCollSublevelType.DatetimeBased.DAY);

        return new DatePartsLevelDef(prop, sublevelDeepestType);
    }

    private static final Pattern NUMBER_FORMAT_PATTERN =
            Pattern.compile("^(?<min>-?\\d*(?:\\.\\d+)?);(?<step>\\d*(?:\\.\\d+));(?<max>-?\\d*(?:\\.\\d+)?)?$");

    private DynCollLevelDef<?> parseNumberLevelDef(StacProperty prop) {
        String format = Option.of(prop.getDynamicCollectionFormat())
                .map(String::trim)
                .getOrElse("");

        return Try.of(() -> {
            Matcher matcher;
            if (isBlank(format)) {
                return parseExactLevelDef(prop);
            }
            else if ((matcher = NUMBER_FORMAT_PATTERN.matcher(format)).matches()) {
                Double min = Double.parseDouble(matcher.group("min"));
                Double step = Double.parseDouble(matcher.group("step"));
                Double max = Double.parseDouble(matcher.group("max"));

                NumberRangeSublevelDef rangeSublevel = new NumberRangeSublevelDef(min, step, max);

                return new NumberRangeLevelDef(prop, rangeSublevel);
            }
            else {
                return parseExactLevelDef(prop);
            }
        })
        .onFailure(t -> LOGGER.warn("Unparsable number level format: {}, using 'EXACT' instead", format, t))
        .getOrElse(() -> parseExactLevelDef(prop));

    }

    private static final Pattern STRING_FORMAT_PATTERN =
        Pattern.compile("^PREFIX\\((?<num>\\d),(?<alphanum>A|9|A9)\\)$");

    private DynCollLevelDef<?> parseStringLevelDef(StacProperty prop) {
        String format = Option.of(prop.getDynamicCollectionFormat())
                .map(String::trim)
                .getOrElse("");

        return Try.of(() -> {
            Matcher matcher;
            if (isBlank(format)) {
                return parseExactLevelDef(prop);
            }
            else if ((matcher = STRING_FORMAT_PATTERN.matcher(format)).matches()) {
                Integer length = Try.of(() -> Integer.parseInt(matcher.group("num"))).getOrElse(1);
                String alphaNum = matcher.group("alphanum");
                boolean alpha = alphaNum.contains("A");
                boolean num = alphaNum.contains("9");

                List<StringPrefixSublevelDef> sublevels = List.range(1, length + 1)
                        .map(i -> new StringPrefixSublevelDef(i, alpha, num));

                return new StringPrefixLevelDef(prop, sublevels);
            }
            else {
                return parseExactLevelDef(prop);
            }
        })
        .onFailure(t -> LOGGER.warn("Unparsable string level format: {}, using 'EXACT' instead", format, t))
        .getOrElse(() -> parseExactLevelDef(prop));
    }

}
