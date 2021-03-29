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

import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.indexer.dao.IEsRepository;
import fr.cnes.regards.modules.indexer.dao.spatial.ProjectGeoSettings;
import fr.cnes.regards.modules.indexer.domain.SimpleSearchKey;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.service.Searches;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.stats.Stats;

import java.time.OffsetDateTime;

import static fr.cnes.regards.modules.catalog.stac.domain.utils.OffsetDatetimeUtils.*;

/**
 * TODO: EsAggregagtionHelperImpl description
 *
 * @author gandrieu
 */
public class EsAggregagtionHelperImpl implements EsAggregagtionHelper {


    private final IEsRepository esRepository;
    private final IRuntimeTenantResolver tenantResolver;
    private final ProjectGeoSettings projectGeoSettings;

    public EsAggregagtionHelperImpl(
            IEsRepository esRepository,
            IRuntimeTenantResolver tenantResolver,
            ProjectGeoSettings projectGeoSettings
    ) {
        this.esRepository = esRepository;
        this.tenantResolver = tenantResolver;
        this.projectGeoSettings = projectGeoSettings;
    }

    @Override
    public Aggregations getAggregationsFor(ICriterion criterion, List<AggregationBuilder> aggDefs) {
        return esRepository.getAggregationsFor(searchKey(), criterion, aggDefs.toJavaList());
    }

    @Override
    public Tuple2<OffsetDateTime, OffsetDateTime> dateRange(ICriterion criterion, String regardsAttributePath) {

        return Try.of(() -> {
            List<AggregationBuilder> aggBuilders = List.of(AggregationBuilders.dateRange(regardsAttributePath).field(regardsAttributePath));
            return esRepository.getAggregationsFor(searchKey(), criterion, javaList(aggBuilders));
        })
        .map(aggs -> {
            Option<Stats> parsedStats = Try.of(() -> aggs.get(regardsAttributePath))
                    .map(Stats.class::cast)
                    .toOption();
            Option<OffsetDateTime> dateTimeFrom = extractTemporalBound(parsedStats.map(Stats::getMin));
            Option<OffsetDateTime> dateTimeTo = extractTemporalBound(parsedStats.map(Stats::getMax));
            return Tuple.of(
                dateTimeFrom.getOrElse(() -> lowestBound()),
                dateTimeTo.getOrElse(() -> uppestBound())
            );
        })
        .getOrElse(() -> Tuple.of(lowestBound(), uppestBound()));
    }

    public java.util.List<AggregationBuilder> javaList(List<AggregationBuilder> aggs) {
        return aggs.toJavaList();
    }


    private SimpleSearchKey<AbstractEntity<?>> searchKey() {
        SimpleSearchKey<AbstractEntity<?>> result = Searches.onSingleEntity(EntityType.DATA);
        result.setSearchIndex(tenantResolver.getTenant());
        result.setCrs(projectGeoSettings.getCrs());
        return result;
    }

}
