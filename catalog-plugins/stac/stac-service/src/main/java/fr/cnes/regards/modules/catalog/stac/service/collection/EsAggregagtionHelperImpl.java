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
import io.vavr.control.Try;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Arrays;

import static fr.cnes.regards.modules.catalog.stac.domain.utils.OffsetDatetimeUtils.lowestBound;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.OffsetDatetimeUtils.uppestBound;

/**
 * TODO: EsAggregagtionHelperImpl description
 *
 * @author gandrieu
 */
@Component
public class EsAggregagtionHelperImpl implements EsAggregagtionHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(EsAggregagtionHelperImpl.class);

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


    /**
     * Add document type (Elasticsearch prior to version 6 type) into criterion
     */
    private static ICriterion addTypes(ICriterion criterion, String... types) {
        // Beware if crit is null
        criterion = criterion == null ? ICriterion.all() : criterion;
        // Then add type
        switch (types.length) {
            case 0:
                return criterion;
            case 1:
                return ICriterion.and(ICriterion.eq("type", types[0]), criterion);
            default:
                ICriterion orCrit = ICriterion
                        .or(Arrays.stream(types).map(type -> ICriterion.eq("type", type)).toArray(ICriterion[]::new));
                return ICriterion.and(orCrit, criterion);
        }

    }

    @Override
    public Aggregations getAggregationsFor(ICriterion criterion, List<AggregationBuilder> aggDefs) {
        SimpleSearchKey<AbstractEntity<?>> searchKey = searchKey();
        return esRepository.getAggregationsFor(searchKey, criterion, aggDefs.toJavaList());
    }

    @Override
    public Tuple2<OffsetDateTime, OffsetDateTime> dateRange(ICriterion criterion, String attrPath) {
        return Try.of(() -> {
            SimpleSearchKey<AbstractEntity<?>> searchKey = searchKey();
            OffsetDateTime dateTimeFrom = esRepository.minDate(searchKey, criterion, attrPath);
            OffsetDateTime dateTimeTo = esRepository.maxDate(searchKey, criterion, attrPath);
            return Tuple.of(dateTimeFrom, dateTimeTo);
        })
        .onFailure(t -> LOGGER.info("Failed to load min/max date for {}", attrPath, t))
        .getOrElse(() -> Tuple.of(lowestBound(), uppestBound()));
    }

    private SimpleSearchKey<AbstractEntity<?>> searchKey() {
        SimpleSearchKey<AbstractEntity<?>> result = Searches.onSingleEntity(EntityType.DATA);
        result.setSearchIndex(tenantResolver.getTenant());
        result.setCrs(projectGeoSettings.getCrs());
        return result;
    }

}
