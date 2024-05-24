/*
 * Copyright 2017-2023 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.dam.plugins.datasources;

import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;

/**
 * Allows to fulfill a {@link fr.cnes.regards.modules.model.dto.properties.PropertyType#DATE_RANGE} property
 * from 2 {@link fr.cnes.regards.modules.model.dto.properties.PropertyType#DATE_ISO8601} properties at harvesting time.
 *
 * @author Marc SORDI
 */
public class DateRangeHistogramProperties {

    @PluginParameter(name = "source-property-path-for-lower-bound",
        label = "Source property path for date range LOWER bound")
    protected String lowerBoundPropertyPath;

    @PluginParameter(name = "source-property-path-for-upper-bound",
        label = "Source property path for date range UPPER bound")
    protected String upperBoundPropertyPath;

    @PluginParameter(name = "target-property-path",
        label = "Target date range property path")
    protected String targetPropertyPath;

    public String getLowerBoundPropertyPath() {
        return lowerBoundPropertyPath;
    }

    public void setLowerBoundPropertyPath(String lowerBoundPropertyPath) {
        this.lowerBoundPropertyPath = lowerBoundPropertyPath;
    }

    public String getUpperBoundPropertyPath() {
        return upperBoundPropertyPath;
    }

    public void setUpperBoundPropertyPath(String upperBoundPropertyPath) {
        this.upperBoundPropertyPath = upperBoundPropertyPath;
    }

    public String getTargetPropertyPath() {
        return targetPropertyPath;
    }

    public void setTargetPropertyPath(String targetPropertyPath) {
        this.targetPropertyPath = targetPropertyPath;
    }
}
