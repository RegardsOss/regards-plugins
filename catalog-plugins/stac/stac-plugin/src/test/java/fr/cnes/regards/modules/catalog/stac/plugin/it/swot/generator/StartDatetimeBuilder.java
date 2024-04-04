/*
 * Copyright 2017-2024 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.stac.plugin.it.swot.generator;

import fr.cnes.regards.framework.random.function.FunctionDescriptor;
import fr.cnes.regards.framework.random.generator.AbstractRandomGenerator;
import fr.cnes.regards.framework.random.generator.builder.RandomGeneratorBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

/**
 * Generate STAC start_datetime property for timeline testing.
 * This generator is used to generate random data.
 * Look at {@link fr.cnes.regards.modules.catalog.stac.plugin.it.swot.TimelineIT}
 */
@Component
public class StartDatetimeBuilder implements RandomGeneratorBuilder<StartDatetimeBuilder.RandomStartDatetime> {

    @Override
    public String getFunctionName() {
        return "start";
    }

    @Override
    public RandomStartDatetime build(FunctionDescriptor fd) {
        return new RandomStartDatetime(fd);
    }

    static class RandomStartDatetime extends AbstractRandomGenerator<OffsetDateTime> {

        private static final ZoneOffset REF_ZONE_OFFSET = ZoneOffset.UTC;

        private static final String EXPANDED_DATE = "T00:00:00";

        private static final String USAGE = "Function %s only support 2 arguments (see DateTimeFormatter.ISO_LOCAL_DATE)";

        private OffsetDateTime startInclusive;

        // Number of days to add at each step
        private long days;

        private OffsetDateTime current;

        RandomStartDatetime(FunctionDescriptor fd) {
            super(fd);
        }

        @Override
        public void parseParameters() {
            if (fd.getParameterSize() == 2) {
                TemporalAccessor ta1 = DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(getParameter(0));
                startInclusive = OffsetDateTime.of(LocalDateTime.from(ta1), REF_ZONE_OFFSET);
                days = Long.parseLong(fd.getParameter(1));
            } else {
                throw new IllegalArgumentException(String.format(USAGE, fd.getFunctionName()));
            }
        }

        private String getParameter(Integer position) {
            String param = fd.getParameter(position);
            return param.contains("T") ? param : param + EXPANDED_DATE;
        }

        @Override
        public OffsetDateTime random() {
            if (current == null) {
                // First call
                current = startInclusive;
                return current;
            }
            current = current.plusDays(days);
            return current;
        }
    }
}
