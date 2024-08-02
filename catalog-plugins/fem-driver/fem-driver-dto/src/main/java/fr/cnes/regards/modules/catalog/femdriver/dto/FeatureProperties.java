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
package fr.cnes.regards.modules.catalog.femdriver.dto;

import fr.cnes.regards.modules.model.dto.properties.IProperty;

import java.util.Set;

/**
 * @author sbinda
 */
public class FeatureProperties {

    public static final String TYPE = "FEATURE_PROPS";

    private Set<IProperty<?>> properties;

    public static FeatureProperties build(Set<IProperty<?>> properties) {
        FeatureProperties f = new FeatureProperties();
        f.properties = properties;
        return f;
    }

    public Set<IProperty<?>> getProperties() {
        return properties;
    }

    public void setProperties(Set<IProperty<?>> properties) {
        this.properties = properties;
    }
}
