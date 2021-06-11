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

package fr.cnes.regards.modules.catalog.stac.service.collection;

import com.google.common.collect.Lists;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.indexer.dao.IEsRepository;
import fr.cnes.regards.modules.indexer.dao.spatial.ProjectGeoSettings;
import fr.cnes.regards.modules.indexer.domain.SimpleSearchKey;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.service.Searches;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.info;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.OffsetDatetimeUtils.lowestBound;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.OffsetDatetimeUtils.uppestBound;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;

/**
 * TODO: EsAggregagtionHelperImpl description
 *
 * @author gandrieu
 */
@Component
public class EsAggregagtionHelperImpl implements EsAggregationHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(EsAggregagtionHelperImpl.class);

    /**
     * Prefix to identify dataset tags
     */
    private static final String DATASET_REGEXP = "URN:AIP:DATASET.*";

    private static final IncludeExclude DATASET_ONLY = new IncludeExclude(DATASET_REGEXP, null);

    private final IEsRepository esRepository;

    private final IRuntimeTenantResolver tenantResolver;

    private final ProjectGeoSettings projectGeoSettings;

    public EsAggregagtionHelperImpl(IEsRepository esRepository, IRuntimeTenantResolver tenantResolver,
            ProjectGeoSettings projectGeoSettings) {
        this.esRepository = esRepository;
        this.tenantResolver = tenantResolver;
        this.projectGeoSettings = projectGeoSettings;
    }

    @Override
    public Aggregations getAggregationsFor(ICriterion criterion, List<AggregationBuilder> aggDefs) {
        SimpleSearchKey<AbstractEntity<?>> searchKey = searchKey();
        return esRepository.getAggregationsFor(searchKey, criterion, aggDefs.toJavaList());
    }

    @Override
    public Tuple2<OffsetDateTime, OffsetDateTime> dateRange(ICriterion criterion, String attrPath) {
        return trying(() -> {
            SimpleSearchKey<AbstractEntity<?>> searchKey = searchKey();
            OffsetDateTime dateTimeFrom = esRepository.minDate(searchKey, criterion, attrPath);
            OffsetDateTime dateTimeTo = esRepository.maxDate(searchKey, criterion, attrPath);
            return Tuple.of(dateTimeFrom, dateTimeTo);
        }).onFailure(t -> info(LOGGER, "Failed to load min/max date for {}", attrPath, t))
                .getOrElse(() -> Tuple.of(lowestBound(), uppestBound()));
    }

    @Override
    public Aggregations getDatasetAggregations(String aggregationName, ICriterion itemCriteria, int size) {
        SimpleSearchKey<AbstractEntity<?>> searchKey = searchKey();
        // TODO A voir, ajouter un critère pour filtrer les tags commençant par le préfixe des jeux
        // pour limiter la recherche aux objets liés à un jeu ou créer un nouveau champ dédié aux id de jeux
        // TODO gérer le suffixe keyword plus proprement
        AggregationBuilder termsAggBuilder = AggregationBuilders.terms(aggregationName)
                .field(StaticProperties.FEATURE_TAGS + ".keyword").size(size).includeExclude(DATASET_ONLY);
        return esRepository.getAggregationsFor(searchKey, itemCriteria, Lists.newArrayList(termsAggBuilder));
    }

    private SimpleSearchKey<AbstractEntity<?>> searchKey() {
        SimpleSearchKey<AbstractEntity<?>> result = Searches.onSingleEntity(EntityType.DATA);
        result.setSearchIndex(tenantResolver.getTenant());
        result.setCrs(projectGeoSettings.getCrs());
        return result;
    }
}
