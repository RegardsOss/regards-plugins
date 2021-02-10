package fr.cnes.regards.modules.catalog.stac.service.RegardsStacCollectionConverter;

import com.google.common.collect.Maps;
import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.exception.EntityOperationForbiddenException;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.feature.CollectionFeature;
import fr.cnes.regards.modules.dam.domain.entities.feature.EntityFeature;
import fr.cnes.regards.modules.indexer.domain.aggregation.QueryableAttribute;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.CatalogSearchService;
import fr.cnes.regards.modules.search.service.CollectionWithStats;
import fr.cnes.regards.modules.search.service.SearchException;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.elasticsearch.search.aggregations.bucket.range.ParsedDateRange;
import org.elasticsearch.search.aggregations.metrics.stats.ParsedStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;

@Component
public class RegardsStacCollectionConverterImpl implements IRegardsStacCollectionConverter {

    @Autowired
    private CatalogSearchService catalogSearchService;

    @Override
    public Try<Collection> convertRequest(String urn) {

        UniformResourceName resourceName = UniformResourceName.fromString(urn);

        CollectionWithStats damCollection = null;
        if (resourceName.getEntityType().equals(EntityType.DATASET) ||
                resourceName.getEntityType().equals(EntityType.COLLECTION)) {
            try {
                List<QueryableAttribute> creationDate =
                        List.of(new QueryableAttribute("creationDate", null,true,0,false));
                damCollection = catalogSearchService.getCollectionWithDataObjectsStats(resourceName, SearchType.DATAOBJECTS, creationDate.toJavaList());
            } catch (EntityOperationForbiddenException | EntityNotFoundException | SearchException e) {
                return Try.failure(e);
            }
        }else {

            return Try.success(null);
        }
        Provider provider = new Provider("", "", null, List.empty());
        // At the moment, we'd choose to to setup the extent with maximal values
        Extent extent = new Extent(new Extent.Spatial(List.of(new BBox(-180,-90,180,90))),
                new Extent.Temporal(List.of(new Tuple2<>(Option.of((OffsetDateTime)((ParsedDateRange)damCollection.getAggregationList().get(0)).getBuckets().get(0).getFrom()),
                        Option.of((OffsetDateTime)((ParsedDateRange)damCollection.getAggregationList().get(0)).getBuckets().get(0).getFrom())))));
        Collection collection = new Collection("1.0.0-beta2",
                List.empty(),
                damCollection.getCollection().getLabel(),
                damCollection.getCollection().getId().toString(),
                damCollection.getCollection().getModel().getDescription(),
                List.empty(),
                List.empty(),
                "",
                List.of(provider), extent, HashMap.of(Map.entry("",new Object())));

        return Try.success(collection);
    }

}
