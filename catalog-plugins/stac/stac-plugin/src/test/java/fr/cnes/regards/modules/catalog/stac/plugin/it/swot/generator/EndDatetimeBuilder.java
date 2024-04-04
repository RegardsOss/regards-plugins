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

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generate STAC end_datetime property for timeline testing.
 * This generator is used to generate random data.
 * Look at {@link fr.cnes.regards.modules.catalog.stac.plugin.it.swot.TimelineIT}
 */
@Component
public class EndDatetimeBuilder implements RandomGeneratorBuilder<EndDatetimeBuilder.RandomEndDatetime> {

    @Override
    public String getFunctionName() {
        return "end";
    }

    @Override
    public RandomEndDatetime build(FunctionDescriptor fd) {
        return new RandomEndDatetime(fd);
    }

    static class RandomEndDatetime extends AbstractRandomGenerator<OffsetDateTime> {

        private static final String USAGE = "Function %s only support 1 argument : the relative start datetime property";

        private String startProperty;

        // In days
        private long duration;

        RandomEndDatetime(FunctionDescriptor fd) {
            super(fd);
        }

        @Override
        public void parseParameters() {
            if (fd.getParameterSize() == 2) {
                startProperty = fd.getParameter(0);
                duration = Long.parseLong(fd.getParameter(1));
            } else {
                throw new IllegalArgumentException(String.format(USAGE, fd.getFunctionName()));
            }
        }

        @Override
        public Optional<List<String>> getDependentProperties() {
            return Optional.of(Collections.singletonList(startProperty));
        }

        @Override
        public OffsetDateTime randomWithContext(Map<String, Object> context) {
            OffsetDateTime start = (OffsetDateTime) findValue(context, startProperty);
            return start.plusDays(duration).minusSeconds(1);
        }
    }
}
