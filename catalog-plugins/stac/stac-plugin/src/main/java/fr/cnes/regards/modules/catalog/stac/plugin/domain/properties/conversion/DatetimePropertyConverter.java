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

package fr.cnes.regards.modules.catalog.stac.plugin.domain.properties.conversion;

import fr.cnes.regards.modules.catalog.stac.plugin.domain.properties.PropertyType;
import io.vavr.control.Try;

import java.time.format.DateTimeFormatter;

import static fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants.STAC_DEFAULT_FORMAT;

/**
 * REGARDS/STAC datetime converter, allowing to convert between date formats.
 */
public class DatetimePropertyConverter extends AbstractPropertyConverter<String, String> {

    private final DateTimeFormatter stacFormat;
    private final DateTimeFormatter regardsFormat;

    public DatetimePropertyConverter(String regardsFormat) {
        this(STAC_DEFAULT_FORMAT, regardsFormat);
    }

    public DatetimePropertyConverter(String stacFormat, String regardsFormat) {
        super(PropertyType.DATETIME);
        this.stacFormat = DateTimeFormatter.ofPattern(stacFormat);
        this.regardsFormat = DateTimeFormatter.ofPattern(regardsFormat);
    }

    @Override
    public Try<String> convertRegardsToStac(String regardsValue) {
        return convert(regardsValue, regardsFormat, stacFormat);
    }

    @Override
    public Try<String> convertStacToRegards(String stacValue) {
        return convert(stacValue, stacFormat, regardsFormat);
    }

    private Try<String> convert(String fromValue, DateTimeFormatter fromFormat, DateTimeFormatter toFormat) {
        return Try.of(() -> toFormat.format(fromFormat.parse(fromValue)));
    }

}
