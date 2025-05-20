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

package fr.cnes.regards.modules.catalog.stac.plugin;

import com.google.common.collect.Lists;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Relation;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.*;
import fr.cnes.regards.modules.catalog.stac.plugin.configuration.mapping.ConfigurationAccessorFactoryImpl;
import fr.cnes.regards.modules.catalog.stac.rest.link.LinkCreatorService;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.search.domain.plugin.*;
import io.vavr.control.Option;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Link;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

@Plugin(id = StacSearchEngine.PLUGIN_ID,
        version = "1.0.0",
        description = "Allow usage of the STAC API",
        author = "REGARDS Team",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CSSI",
        url = "https://github.com/RegardsOss",
        markdown = "StacSearchEngine.md")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StacSearchEngine implements ISearchEngine<Object, ItemSearchBody, Object, List<String>> {

    public static final String PLUGIN_ID = "stac";

    @Autowired
    private ConfigurationAccessorFactoryImpl propMapper;

    @Autowired
    private LinkCreatorService linkCreator;

    @PluginParameter(name = "stac-api-title",
                     label = "STAC title",
                     description = "Title for the root STAC catalog.",
                     optional = true)
    private String stacTitle;

    @PluginParameter(name = "stac-api-description",
                     label = "STAC description",
                     description = "Description for the root STAC catalog.",
                     optional = true)
    private String stacDescription;

    @PluginParameter(name = "stac-api-root-static-collection-title",
                     label = "STAC root static collection title",
                     description = "Displayed label for the static collections root.",
                     defaultValue = "static",
                     optional = true)
    private String rootStaticCollectionTitle;

    @PluginParameter(name = "stac-api-root-dynamic-collection-title",
                     label = "STAC root dynamic collection title",
                     description = "Displayed label for the dynamic collections root.",
                     defaultValue = "dynamic",
                     optional = true)
    private String rootDynamicCollectionTitle;

    @PluginParameter(name = "stac-api-datetime-property",
                     label = "STAC datetime property",
                     description = "Mandatory configuration for the datetime property, corresponding to the"
                                   + " 'temporal' aspect of the STAC spec.")
    private StacDatetimePropertyConfiguration stacDatetimeProperty;

    @PluginParameter(name = "stac-api-links-property", label = "STAC links property", optional = true)
    private StacSourcePropertyConfiguration stacLinksProperty;

    @PluginParameter(name = "stac-api-assets-property", label = "STAC assets property", optional = true)
    private StacSourcePropertyConfiguration stacAssetsProperty;

    @PluginParameter(name = "stac-properties",
                     label = "STAC properties",
                     description = "List of STAC properties to be mapped to product properties.",
                     optional = true)
    private List<StacPropertyConfiguration> stacExtraProperties = Lists.newArrayList();

    @PluginParameter(name = "stac-collection-dataset-properties",
                     label = "STAC dataset properties",
                     description = "Configure STAC collection properties for selected datasets.")
    private List<CollectionConfiguration> stacCollectionDatasetProperties;

    @PluginParameter(name = "eodag-properties",
                     label = "EODAG properties for STAC script generation",
                     description = "EODAG configuration to be injected in python script template",
                     optional = true)
    private EODAGConfiguration eodagConfiguration;

    @PluginParameter(name = "histogram-property-path",
                     label = "Histogram JSON property path",
                     description = "Fully qualified property path from data model",
                     optional = true)
    private String histogramPropertyPath;

    @PluginParameter(name = "enable-human-readable-ids",
                     label = "Enable human-readable IDs",
                     description = "Enable human-readable IDs",
                     optional = true,
                     defaultValue = "false")
    private boolean enableHumanReadableIds;

    @PluginParameter(name = "disable-auth-param",
                     label = "Disable authentication parameter",
                     description = "Disable authentication parameter in links",
                     optional = true,
                     defaultValue = "false")
    private boolean disableAuthParam;

    @PluginParameter(name = "use-collection-configuration",
                     label = "Use collection configuration",
                     description = "Use collection configuration for collection instead of this configuration",
                     optional = true,
                     defaultValue = "false")
    private boolean useCollectionConfiguration;

    @Override
    public boolean supports(SearchType searchType) {
        return false;
    }

    @Override
    public ResponseEntity<Object> search(SearchContext context,
                                         ISearchEngine<?, ?, ?, ?> requestParser,
                                         IEntityLinkBuilder linkBuilder) throws ModuleException {
        return null;
    }

    @Override
    public ICriterion parse(SearchContext context) {
        return null;
    }

    @Override
    public ResponseEntity<Object> getEntity(SearchContext context, IEntityLinkBuilder linkBuilder) {
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
     * @param element                     unused
     * @return a list of usable links, among which one with "rel=stac" for the root of the STAC catalog.
     */
    @Override
    public List<Link> extraLinks(Class<?> searchEngineControllerClass, SearchEngineConfiguration element) {
        OGCFeatLinkCreator ogcFeatLinkCreator = linkCreator.makeOGCFeatLinkCreator(true, null);
        SearchPageLinkCreator searchPageLinkCreator = linkCreator.makeSearchPageLinkCreator(0,
                                                                                            ItemSearchBody.builder()
                                                                                                          .limit(100)
                                                                                                          .build(),
                                                                                            true,
                                                                                            null);
        Option<String> collectionsLink = ogcFeatLinkCreator.createCollectionsLink(Relation.DATA)
                                                           .map(l -> l.href().toString());
        return io.vavr.collection.List.of(collectionsLink.map(href -> Link.of(href, "search-collections")),
                                          collectionsLink.map(href -> Link.of(href, "search-datasets")),
                                          searchPageLinkCreator.searchAll()
                                                               .map(URI::toString)
                                                               .map(href -> Link.of(href, "search-objects")),
                                          ogcFeatLinkCreator.createLandingPageLink(Relation.ROOT)
                                                            .map(l -> l.href().toString())
                                                            .map(href -> Link.of(href, "search")))
                                      .flatMap(vl -> vl)
                                      .toJavaList();
    }

}
