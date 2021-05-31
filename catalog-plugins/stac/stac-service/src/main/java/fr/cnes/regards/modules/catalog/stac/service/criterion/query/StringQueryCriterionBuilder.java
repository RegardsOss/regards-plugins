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

package fr.cnes.regards.modules.catalog.stac.service.criterion.query;

import static fr.cnes.regards.modules.dam.domain.entities.criterion.IFeatureCriterion.contains;
import static fr.cnes.regards.modules.dam.domain.entities.criterion.IFeatureCriterion.eq;
import static fr.cnes.regards.modules.dam.domain.entities.criterion.IFeatureCriterion.regexp;
import static fr.cnes.regards.modules.indexer.domain.criterion.ICriterion.not;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.StringQueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;

/**
 * Criterion builder for a {@link StringQueryObject}
 */
public class StringQueryCriterionBuilder extends AbstractQueryObjectCriterionBuilder<StringQueryObject> {

    public StringQueryCriterionBuilder(String stacPropName) {
        super(stacPropName);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Option<ICriterion> buildCriterion(AttributeModel attr, List<StacProperty> properties,
            StringQueryObject queryObject) {
        return andAllPresent(Option.of(queryObject.getEq()).map(eq -> eq(attr, eq)),
                             Option.of(queryObject.getNeq()).map(neq -> not(eq(attr, neq))),
                             Option.of(queryObject.getStartsWith()).map(st -> regexp(attr, toStartRegexp(st))),
                             Option.of(queryObject.getEndsWith()).map(en -> regexp(attr, toEndRegexp(en))),
                             Option.of(queryObject.getContains()).map(en -> contains(attr, en)),
                             Option.of(queryObject.getIn())
                                     .flatMap(in -> in.map(d -> eq(attr, d)).reduceLeftOption(ICriterion::or)));
    }

    private String toEndRegexp(String en) {
        return ".*" + toCaseInsensitiveRegexp(en);
    }

    private String toStartRegexp(String st) {
        return toCaseInsensitiveRegexp(st) + ".*";
    }

    private String toCaseInsensitiveRegexp(String pattern) {
        return Stream.ofAll(pattern.toCharArray())
                .map(chr -> Character.isDigit(chr) ? Character.toString(chr)
                        : String.format("[%s%s]", Character.toLowerCase(chr), Character.toUpperCase(chr)))
                .foldLeft("", String::concat);
    }

}
