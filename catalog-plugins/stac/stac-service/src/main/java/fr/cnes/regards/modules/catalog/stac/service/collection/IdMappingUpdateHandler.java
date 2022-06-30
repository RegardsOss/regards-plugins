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
package fr.cnes.regards.modules.catalog.stac.service.collection;

import fr.cnes.regards.framework.amqp.ISubscriber;
import fr.cnes.regards.framework.amqp.domain.IHandler;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.dam.domain.entities.event.BroadcastEntityEvent;
import fr.cnes.regards.modules.dam.domain.entities.event.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * This handle init ID mapping for all tenants on startup.
 * <p>
 * {@link Profile} annotation allows to disable this handler for testing. Testing context has to call {@link IdMappingService} directly for building the
 * cache in its own lifecycle.
 */
@Component
@Profile("!noStacHandler")
public class IdMappingUpdateHandler
    implements IHandler<BroadcastEntityEvent>, ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    private IdMappingService idMappingService;

    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    @Autowired
    private ISubscriber subscriber;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        idMappingService.initOrUpdateCache();

        // Subscribe to Datasets and Collections creation event.
        subscriber.subscribeTo(BroadcastEntityEvent.class, this);
    }

    @Override
    public void handle(String tenant, BroadcastEntityEvent event) {
        try {
            runtimeTenantResolver.forceTenant(tenant);
            if (isDatasetOrCollectionCreationEvent(event))
                idMappingService.initOrUpdateCache(tenant);
        } finally {
            runtimeTenantResolver.clearTenant();
        }
    }

    private boolean isDatasetOrCollectionCreationEvent(BroadcastEntityEvent event) {
        return event.getEventType().equals(EventType.CREATE) && Arrays.stream(event.getAipIds())
                                                                      .anyMatch(urn -> urn.getEntityType()
                                                                                          .equals(EntityType.DATASET)
                                                                                       || urn.getEntityType()
                                                                                             .equals(EntityType.COLLECTION));
    }
}
