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

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.StringQueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.dam.domain.entities.criterion.IFeatureCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.StringMatchType;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;

import static fr.cnes.regards.modules.dam.domain.entities.criterion.IFeatureCriterion.*;
import static fr.cnes.regards.modules.indexer.domain.criterion.ICriterion.not;

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
        StringMatchType stringMatchType = IFeatureCriterion.parseStringMatchType(queryObject.getMatchType())
                .orElseGet(() -> StringMatchType.FULL_TEXT_SEARCH);
        return andAllPresent(Option.of(adaptCase(queryObject.getEq(), stringMatchType)).map(eq -> eq(attr, eq, stringMatchType)),
                             Option.of(adaptCase(queryObject.getNeq(), stringMatchType)).map(neq -> not(eq(attr, neq, stringMatchType))),
                             Option.of(adaptCase(queryObject.getStartsWith(), stringMatchType))
                                     .map(st -> regexp(attr, toStartRegexp(st), stringMatchType)),
                             Option.of(adaptCase(queryObject.getEndsWith(), stringMatchType))
                                     .map(en -> regexp(attr, toEndRegexp(en), stringMatchType)),
                             Option.of(adaptCase(queryObject.getContains(), stringMatchType))
                                     .map(en -> contains(attr, en, stringMatchType)),
                             Option.of(adaptCase(queryObject.getIn(), stringMatchType)).flatMap(
                                     in -> in.map(d -> eq(attr, d, stringMatchType))
                                             .reduceLeftOption(ICriterion::or)));
    }

    private String adaptCase(String text, StringMatchType stringMatchType) {
        if (text != null && StringMatchType.FULL_TEXT_SEARCH.equals(stringMatchType)) {
            return text.toLowerCase();
        } else {
            return text;
        }
    }

    private List<String> adaptCase(List<String> texts, StringMatchType stringMatchType) {
        if (texts != null && StringMatchType.FULL_TEXT_SEARCH.equals(stringMatchType)) {
            return texts.map(t -> t.toLowerCase()).toList();
        } else {
            return texts;
        }
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
