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
import fr.cnes.regards.framework.security.utils.jwt.JWTAuthentication;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.CollectionConfiguration;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.StacPropertyConfiguration;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.mapping.StacConfigurationDomainAccessor;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.link.LinkCreatorService;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.search.domain.plugin.*;
import io.vavr.control.Try;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Link;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.URI;
import java.util.List;

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

    public static final String PLUGIN_ID = "stac";

    @Autowired
    private StacConfigurationDomainAccessor propMapper;
    @Autowired
    private LinkCreatorService linkCreator;

    @PluginParameter(
            name = "stacTitle",
            label = "STAC title",
            description = "Title for the root STAC catalog.",
            optional = true)
    private String stacTitle;

    @PluginParameter(
            name = "stacDescription",
            label = "STAC description",
            description = "Description for the root STAC catalog.",
            optional = true)
    private String stacDescription;

    @PluginParameter(
            name = "rootStaticCollectionTitle",
            label = "STAC root static collection title",
            description = "Displayed label for the static collections root.",
            defaultValue = "static",
            optional = true)
    private String rootStaticCollectionTitle;

    @PluginParameter(
            name = "rootDynamicCollectionTitle",
            label = "STAC root dynamic collection title",
            description = "Displayed label for the dynamic collections root.",
            defaultValue = "dynamic",
            optional = true)
    private String rootDynamicCollectionTitle;

    @PluginParameter(
            name = "stacDatetimeProperty",
            label = "STAC datetime property",
            description = "Mandatory configuration for the datetime property, corresponding to the" +
                    " 'temporal' aspect of the STAC spec.")
    private StacPropertyConfiguration stacDatetimeProperty;

    @PluginParameter(
            name = "stacExtraProperties",
            label = "STAC extra properties",
            description = "List of other STAC properties to be mapped to model attributes.",
            optional = true)
    private List<StacPropertyConfiguration> stacExtraProperties = Lists.newArrayList();

    @PluginParameter(
            name = "stacCollectionDatasetProperties",
            label = "Dataset properties",
            description = "Configure STAC collection properties for selected datasets.")
    private List<CollectionConfiguration> stacCollectionDatasetProperties;



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

    /**
     * This plugin does not use the default search links on the <code>SearchEngineController</code>, but uses its own
     * endpoints.
     *
     * @return false
     */
    @Override
    public boolean useDefaultConfigurationLinks() {
        return false;
    }

    /**
     * Provide some links to the strategic endpoints, with relations similar to what other {@link ISearchEngine}
     * instances provide.
     *
     * @param searchEngineControllerClass unused
     * @param element unused
     * @return a list of usable links, among which one with "rel=stac" for the root of the STAC catalog.
     */
    @Override
    public List<Link> extraLinks(Class<?> searchEngineControllerClass, SearchEngineConfiguration element) {
        JWTAuthentication auth = (JWTAuthentication) SecurityContextHolder.getContext().getAuthentication();

        OGCFeatLinkCreator ogcFeatLinkCreator = linkCreator.makeOGCFeatLinkCreator(auth);
        SearchPageLinkCreator searchPageLinkCreator = linkCreator.makeSearchPageLinkCreator(auth, 0, ItemSearchBody.builder().limit(100).build());
        Try<String> collectionsLink = ogcFeatLinkCreator.createCollectionsLink().map(l -> l.getHref().toString());
        return io.vavr.collection.List.of(
                collectionsLink.map(href -> new Link(href, "search-collections")),
                collectionsLink.map(href -> new Link(href, "search-datasets")),
                searchPageLinkCreator.searchAll().map(URI::toString).map(href -> new Link(href, "search-objects")),
                ogcFeatLinkCreator.createRootLink().map(l -> l.getHref().toString()).map(href -> new Link(href, "stac"))
        )
        .flatMap(vl -> vl)
        .toJavaList();
    }
}
