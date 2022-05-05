/*
 * Copyright 2017-2022 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacException;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import org.springframework.data.util.Pair;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper class for generating EODag scripts
 */
public class EODagGenerator {

    // JINJA templates

    private static final JinjavaConfig config = JinjavaConfig.newBuilder().withTrimBlocks(true).withLstripBlocks(true).build();

    private static final Jinjava jinjava = new Jinjava(config);

    private static final String BASE_TEMPLATE = "fr/cnes/regards/modules/catalog/stac/service/collection/search/eodag/base_script.py";

    private static final String SINGLE_TEMPLATE = "fr/cnes/regards/modules/catalog/stac/service/collection/search/eodag/single_collection_script.py";

    private static final String MULTI_TEMPLATE = "fr/cnes/regards/modules/catalog/stac/service/collection/search/eodag/multi_collections_script.py";

    /**
     * Single collection script generation
     */
    public static void generateFromTemplate(PrintWriter writer, EODagInformation information, EODagParameters parameters) throws StacException {
        Map<String, Object> context = prepareContext(information, parameters);
        writer.println(jinjava.render(getBaseTemplate(), context));
        writer.println(jinjava.render(getSingleTemplate(), context));
    }

    /**
     * Multi collections script generation
     */
    public static void generateFromTemplate(PrintWriter writer, EODagInformation information, List<EODagParameters> collectionParameters) throws StacException {
        Map<String, Object> context = prepareContext(information, collectionParameters);
        writer.println(jinjava.render(getBaseTemplate(), context));
        writer.println(jinjava.render(getMultiTemplate(), context));
    }

    private static Map<String, Object> prepareContext(EODagInformation information, EODagParameters parameters) {
        Map<String, Object> context = Maps.newHashMap();
        context.put("info", information);
        context.put("parameters", parameters);
        if (parameters.hasExtras()) {
            context.put("query_parameters", parameters.getExtras());
        }
        return context;
    }

    private static Map<String, Object> prepareContext(EODagInformation information, List<EODagParameters> collectionParameters) {
        Map<String, Object> context = Maps.newHashMap();
        context.put("info", information);
        context.put("parameters", collectionParameters);
        return context;
    }

    private static String getBaseTemplate() throws StacException {
        try {
            return Resources.toString(Resources.getResource(BASE_TEMPLATE), Charsets.UTF_8);
        } catch (IOException e) {
            throw new StacException("Unable to load base template of python script", e, StacFailureType.JINJA_TEMPLATE_LOADING_FAILURE);
        }
    }

    private static String getSingleTemplate() throws StacException {
        try {
            return Resources.toString(Resources.getResource(SINGLE_TEMPLATE), Charsets.UTF_8);
        } catch (IOException e) {
            throw new StacException("Unable to load single template of python script", e, StacFailureType.JINJA_TEMPLATE_LOADING_FAILURE);
        }
    }

    private static String getMultiTemplate() throws StacException {
        try {
            return Resources.toString(Resources.getResource(MULTI_TEMPLATE), Charsets.UTF_8);
        } catch (IOException e) {
            throw new StacException("Unable to load multi template of python script", e, StacFailureType.JINJA_TEMPLATE_LOADING_FAILURE);
        }
    }

}
