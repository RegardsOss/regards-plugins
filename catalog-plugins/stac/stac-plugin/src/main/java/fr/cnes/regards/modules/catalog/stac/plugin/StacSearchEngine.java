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

package fr.cnes.regards.modules.catalog.stac.plugin;

import com.google.common.collect.Lists;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.Spatial4jConfiguration;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.StacPropertyConfiguration;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.mapping.StacPropertyConfigurationToDomainPropertyMapper;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.search.domain.plugin.IEntityLinkBuilder;
import fr.cnes.regards.modules.search.domain.plugin.ISearchEngine;
import fr.cnes.regards.modules.search.domain.plugin.SearchContext;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants.PropertyName.DATETIME_PROPERTY_NAME;

@Plugin(
        id = StacSearchEngine.PLUGIN_ID,
        version = "1.0.0",
        description = "Allow usage of the STAC API",
        author = "REGARDS Team",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CSSI",
        url = "https://github.com/RegardsOss",
        markdown = "StacEnginePlugin.md"
)
@Data @AllArgsConstructor @NoArgsConstructor
public class StacSearchEngine implements ISearchEngine<Object, ItemSearchBody, Object, List<String>> {

    static final String PLUGIN_ID = "StacSearchEngine";

    @Autowired
    private StacPropertyConfigurationToDomainPropertyMapper propMapper;

    @PluginParameter(
            name = "stacDatetimeProperty",
            label = "STAC datetime property",
            description = "Mandatory configuration for the datetime property, corresponding to the" +
                    " 'temporal' aspect of the STAC spec.")
    private StacPropertyConfiguration stacDatetimeProperty;

    @PluginParameter(
            name = "stacExtraProperties",
            label = "STAC extra properties",
            description = "List of other STAC properties to be mapped to model attributes.")
    private List<StacPropertyConfiguration> stacExtraProperties = Lists.newArrayList();

    @PluginParameter(
            name = "spatial4jConfiguration",
            label = "Configuration for spatial4j",
            description = "This property configures the spatial4j library, allowing to compute bounding boxes from geometries."
    )
    private Spatial4jConfiguration spatial4jConfiguration;

    @Override
    public boolean supports(SearchType searchType) {
        return false;
    }

    @Override
    public ResponseEntity<Object> search(SearchContext context, ISearchEngine<?, ?, ?, ?> requestParser, IEntityLinkBuilder linkBuilder) throws ModuleException {
        return null;
    }

    @Override
    public ICriterion parse(SearchContext context) throws ModuleException {
        return null;
    }

    @Override
    public ResponseEntity<Object> getEntity(SearchContext context, IEntityLinkBuilder linkBuilder) throws ModuleException {
        return null;
    }

    public io.vavr.collection.List<StacProperty> getConfiguredProperties() {
        return propMapper.getConfiguredProperties(
            io.vavr.collection.List.ofAll(stacExtraProperties)
                .prepend(stacDatetimeProperty.withStacPropertyName(DATETIME_PROPERTY_NAME)));
    }
}
