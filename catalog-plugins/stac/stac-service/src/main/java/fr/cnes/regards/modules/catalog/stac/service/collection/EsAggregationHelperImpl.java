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
import fr.cnes.regards.modules.indexer.service.IndexAliasResolver;
import fr.cnes.regards.modules.indexer.service.Searches;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
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
import static fr.cnes.regards.modules.catalog.stac.domain.utils.OffsetDatetimeUtils.upperBound;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;

/**
 * Helper implementation to get Elasticsearch aggregations and date range queries
 *
 * @author gandrieu
 */
@Component
public class EsAggregationHelperImpl implements EsAggregationHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(EsAggregationHelperImpl.class);

    /**
     * Regular expression used to match dataset URNs within feature tags.
     */
    private static final String DATASET_REGEXP = "URN:AIP:DATASET.*";

    private static final IncludeExclude DATASET_ONLY = new IncludeExclude(DATASET_REGEXP, null);

    private final IEsRepository esRepository;

    private final IRuntimeTenantResolver tenantResolver;

    private final ProjectGeoSettings projectGeoSettings;

    public EsAggregationHelperImpl(IEsRepository esRepository,
                                   IRuntimeTenantResolver tenantResolver,
                                   ProjectGeoSettings projectGeoSettings) {
        this.esRepository = esRepository;
        this.tenantResolver = tenantResolver;
        this.projectGeoSettings = projectGeoSettings;
    }

    @Override
    public Aggregations getAggregationsFor(ICriterion criterion, List<AggregationBuilder> aggDefinitions, int limit) {
        SimpleSearchKey<AbstractEntity<?>> searchKey = searchDataKey();
        return esRepository.getAggregationsFor(searchKey, criterion, aggDefinitions.toJavaList(), limit);
    }

    @Override
    public Tuple2<OffsetDateTime, OffsetDateTime> dateRange(ICriterion criterion, String attrPath) {
        return trying(() -> {
            SimpleSearchKey<AbstractEntity<?>> searchKey = searchDataKey();
            OffsetDateTime dateTimeFrom = Option.of(esRepository.minDate(searchKey, criterion, attrPath))
                                                .getOrElse(lowestBound());
            OffsetDateTime dateTimeTo = Option.of(esRepository.maxDate(searchKey, criterion, attrPath))
                                              .getOrElse(upperBound());
            return Tuple.of(dateTimeFrom, dateTimeTo);
        }).onFailure(t -> info(LOGGER, "Failed to load min/max date for {}", attrPath, t))
          .getOrElse(() -> Tuple.of(lowestBound(), upperBound()));
    }

    @Override
    public Long getDatasetTotalCount() {
        return esRepository.count(searchDatasetKey(), ICriterion.all());
    }

    private SimpleSearchKey<AbstractEntity<?>> searchDatasetKey() {
        SimpleSearchKey<AbstractEntity<?>> result = Searches.onSingleEntity(EntityType.DATASET);
        result.setSearchIndex(IndexAliasResolver.resolveAliasName(tenantResolver.getTenant()));
        result.setCrs(projectGeoSettings.getCrs());
        return result;
    }

    @Override
    public Aggregations getDatasetAggregations(String aggregationName, ICriterion itemCriteria, Long size) {
        SimpleSearchKey<AbstractEntity<?>> searchKey = searchDataKey();
        AggregationBuilder termsAggBuilder = AggregationBuilders.terms(aggregationName)
                                                                .field(StaticProperties.FEATURE_TAGS + ".keyword")
                                                                .size(size.intValue())
                                                                .includeExclude(DATASET_ONLY);
        return esRepository.getAggregationsFor(searchKey, itemCriteria, Lists.newArrayList(termsAggBuilder), 1000);
    }

    private SimpleSearchKey<AbstractEntity<?>> searchDataKey() {
        SimpleSearchKey<AbstractEntity<?>> result = Searches.onSingleEntity(EntityType.DATA);
        result.setSearchIndex(IndexAliasResolver.resolveAliasName(tenantResolver.getTenant()));
        result.setCrs(projectGeoSettings.getCrs());
        return result;
    }
}
