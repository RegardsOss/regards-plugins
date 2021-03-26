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

package fr.cnes.regards.modules.catalog.stac.domain.properties;

import com.google.common.annotations.VisibleForTesting;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModelBuilder;
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

    /**
     * <b>BEWARE</b>
     * Use with care this factory method. It is meant to be used in tests and
     * for build StacProperties in very specific contexts. It is mainly meant
     * to be used in tests and where a StacProperty instance is required but
     * has not been configured by the user.
     *
     * Outside of tests, unless you know exactly why you should use this,
     * you should prefer using an instance of RegardsPropertyAccessorFactory.
     */
    @VisibleForTesting
    public static RegardsPropertyAccessor accessor(String name, StacPropertyType sPropType, Object value) {
        AttributeModel attr = AttributeModelBuilder.build(name, sPropType.getPropertyType(), "").get();
        return new RegardsPropertyAccessor(
                name, attr, d -> Try.success(value), value.getClass()
        );
    }
}
