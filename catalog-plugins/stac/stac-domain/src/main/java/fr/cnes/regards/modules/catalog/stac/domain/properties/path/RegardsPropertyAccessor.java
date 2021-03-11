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

package fr.cnes.regards.modules.catalog.stac.domain.properties.path;

import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import io.vavr.control.Try;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.With;

import java.util.function.Function;

/**
 * Represents a path to a value
 */
@Data @With @AllArgsConstructor
public class RegardsPropertyAccessor {

    /** Name of the REGARDS feature property */
    String regardsAttributeName;

    /** The corresponding attribute model */
    AttributeModel attributeModel;

    /** How to get the Regards property value from a DataObject */
    Function<DataObject, Try<?>> extractValueFn;

    /** Explicity giving the property type */
    Class<?> valueType;

    @SuppressWarnings("unchecked")
    public <T> Function<DataObject, Try<T>> getGenericExtractValueFn() {
        return t -> ((Try<T>)extractValueFn.apply(t));
    }

    @SuppressWarnings("unchecked")
    public <T> Class<T> getGenericValueType() {
        return (Class<T>) valueType;
    }

}
