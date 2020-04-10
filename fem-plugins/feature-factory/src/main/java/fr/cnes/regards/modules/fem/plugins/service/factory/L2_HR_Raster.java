/*
 * Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.fem.plugins.service.factory;

import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;

/**
 * @author kevin
 *
 */
public class L2_HR_Raster extends AbstractFeatureFactory {

    @Override
    public Feature createFeature(String fileName, String regex, String model) {
        Feature toCreate = super.createFeature(fileName, regex, model);
        toCreate.addProperty(IProperty.forType(PropertyType.STRING, GRANULE_TYPE, FULL_SWATH_SCENE));
        toCreate.addProperty(IProperty.forType(PropertyType.STRING, TYPE, matcher.group(1)));
        toCreate.addProperty(IProperty.forType(PropertyType.STRING, CYCLE_NUMBER, matcher.group(2)));
        toCreate.addProperty(IProperty.forType(PropertyType.STRING, PASS_NUMBER, matcher.group(3)));
        toCreate.addProperty(IProperty.forType(PropertyType.DATE_ISO8601, UTC_START_DATE, parseDate(matcher.group(4))));
        toCreate.addProperty(IProperty.forType(PropertyType.DATE_ISO8601, UTC_END_DATE, parseDate(matcher.group(5))));
        toCreate.addProperty(IProperty.forType(PropertyType.STRING, CRID, matcher.group(6)));
        toCreate.addProperty(IProperty.forType(PropertyType.STRING, PRODUCT_COUNTER, matcher.group(7)));

        return toCreate;
    }

}
