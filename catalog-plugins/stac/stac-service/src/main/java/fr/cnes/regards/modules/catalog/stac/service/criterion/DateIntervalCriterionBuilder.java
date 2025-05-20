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

package fr.cnes.regards.modules.catalog.stac.service.criterion;

import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.api.DateInterval;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagParameters;
import fr.cnes.regards.modules.dam.domain.entities.criterion.IFeatureCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.springframework.stereotype.Component;

import static fr.cnes.regards.modules.catalog.stac.domain.StacProperties.DATETIME_PROPERTY_NAME;

/**
 * Builds criteria for datetime fields.
 */
@Component
public class DateIntervalCriterionBuilder implements CriterionBuilder<DateInterval> {

    @Override
    public Option<ICriterion> buildCriterion(List<StacProperty> properties, DateInterval datetime) {
        Option<StacProperty> datetimeProperty = datetimeProperty(properties);

        if (datetime == null) {
            return Option.none();
        }
        return datetimeProperty.map(p -> {
            boolean singleDate = datetime.isSingleDate();
            return singleDate ?
                IFeatureCriterion.eq(p.getRegardsPropertyAccessor().getAttributeModel(), datetime.getFrom()) :
                IFeatureCriterion.between(p.getRegardsPropertyAccessor().getAttributeModel(),
                                          datetime.getFrom(),
                                          datetime.getTo());
        });
    }

    public Option<StacProperty> datetimeProperty(List<StacProperty> properties) {
        return properties.find(p -> DATETIME_PROPERTY_NAME.equals(p.getStacPropertyName()));
    }

    @Override
    public void computeEODagParameters(EODagParameters parameters,
                                       List<StacProperty> properties,
                                       DateInterval datetime) {
        if (datetime != null) {
            if (datetime.getFrom() != null) {
                parameters.setStart(OffsetDateTimeAdapter.format(datetime.getFrom()));
            }
            if (datetime.getTo() != null) {
                parameters.setEnd(OffsetDateTimeAdapter.format(datetime.getTo()));
            }
        }
    }
}
