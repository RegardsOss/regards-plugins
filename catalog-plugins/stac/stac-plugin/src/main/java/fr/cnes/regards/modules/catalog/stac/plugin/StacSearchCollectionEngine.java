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
package fr.cnes.regards.modules.catalog.stac.plugin;

import com.google.common.collect.Lists;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.StacSimplePropertyConfiguration;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.StacSourcePropertyConfiguration;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.TemporalExtentConfiguration;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.search.domain.plugin.IEntityLinkBuilder;
import fr.cnes.regards.modules.search.domain.plugin.ISearchEngine;
import fr.cnes.regards.modules.search.domain.plugin.SearchContext;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Only used for STAC collection search configuration.
 * STAC collection search is based on {@link fr.cnes.regards.modules.dam.domain.entities.Dataset}
 *
 * @author Marc SORDI
 */
@Plugin(id = StacSearchCollectionEngine.PLUGIN_ID,
        version = "1.0.0",
        description = "Extend th STAC API for collection search",
        author = "REGARDS Team",
        contact = "regards@csgroup.eu.fr",
        license = "GPLv3",
        owner = "CS GROUP FRANCE",
        url = "https://github.com/RegardsOss",
        markdown = "StacSearchCollectionEngine.md")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StacSearchCollectionEngine implements ISearchEngine<Object, ItemSearchBody, Object, List<String>> {

    public static final String PLUGIN_ID = "stac-collection-search";

    private static final String BAD_REQUEST_MESSAGE = "Use STAC standard API to use this plugin";

    @PluginParameter(label = "STAC collection title", optional = true)
    private StacSourcePropertyConfiguration stacCollectionTitle;

    @PluginParameter(label = "STAC collection description")
    private StacSourcePropertyConfiguration stacCollectionDescription;

    @PluginParameter(label = "STAC collection keywords", optional = true)
    private StacSourcePropertyConfiguration stacCollectionKeywords;

    @PluginParameter(label = "STAC collection license")
    private StacSourcePropertyConfiguration stacCollectionLicense;

    @PluginParameter(label = "STAC collection providers", optional = true)
    private StacSourcePropertyConfiguration stacCollectionProviders;

    @PluginParameter(label = "STAC collection links", optional = true)
    private StacSourcePropertyConfiguration stacCollectionLinks;

    @PluginParameter(label = "STAC collection assets", optional = true)
    private StacSourcePropertyConfiguration stacCollectionAssets;

    @PluginParameter(label = "STAC temporal extent", optional = true)
    private TemporalExtentConfiguration temporalExtent;

    // TODO extent ... mapping or computed properties : mapping plus rapide! mais inexact en recherche!

    @PluginParameter(label = "STAC properties for collection summaries (strongly recommended)", optional = true)
    private List<StacSimplePropertyConfiguration> stacCollectionSummaries = Lists.newArrayList();

    @Override
    public boolean supports(SearchType searchType) {
        return false;
    }

    @Override
    public ResponseEntity<Object> search(SearchContext context,
                                         ISearchEngine<?, ?, ?, ?> requestParser,
                                         IEntityLinkBuilder linkBuilder) throws ModuleException {
        return ResponseEntity.badRequest().body(BAD_REQUEST_MESSAGE);
    }

    @Override
    public ICriterion parse(SearchContext context) throws ModuleException {
        return ICriterion.all();
    }

    @Override
    public ResponseEntity<Object> getEntity(SearchContext context, IEntityLinkBuilder linkBuilder)
        throws ModuleException {
        return ResponseEntity.badRequest().body(BAD_REQUEST_MESSAGE);
    }
}
