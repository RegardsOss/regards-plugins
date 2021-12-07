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
package fr.cnes.regards.modules.notifier.plugins;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import fr.cnes.regards.common.notifier.plugins.AbstractRabbitMQSender;
import fr.cnes.regards.framework.amqp.configuration.AmqpConstants;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.feature.dto.event.in.DisseminationAckEvent;
import fr.cnes.regards.modules.feature.dto.urn.FeatureUniformResourceName;
import fr.cnes.regards.modules.notifier.domain.NotificationRequest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Send acknowledge event related to a disseminated product
 * I.E. : Some REGARDS catalog, let's name it A, disseminate a product to another catalog, named B.
 * So this event is sent by catalog B to catalog A.
 *
 * @author Jacques Chirac
 */
@Plugin(author = "REGARDS Team", description = "Dissemination ACK sender", id = DisseminationAckSender.PLUGIN_ID,
        version = "1.0.0", contact = "regards@c-s.fr", license = "GPLv3", owner = "CNES",
        url = "https://regardsoss.github.io/")
public class DisseminationAckSender extends AbstractRabbitMQSender {

    public static final String RECIPIENT_TENANT_PARAM_NAME = "recipientTenant";

    public static final String PLUGIN_ID = "DisseminationAckSender";

    public static final String FEATURE_PATH_TO_URN = "urn";

    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    @PluginParameter(label = "Recipient tenant", name = RECIPIENT_TENANT_PARAM_NAME)
    private String recipientTenant;

    @Override
    public Collection<NotificationRequest> send(Collection<NotificationRequest> requestsToSend) {
        String tenantName = runtimeTenantResolver.getTenant();
        List<NotificationRequest> errors = new ArrayList<>();
        List<DisseminationAckEvent> toSend = new ArrayList<>();
        for (NotificationRequest request : requestsToSend) {
            // Check we can retrieve the URN, and it's valid field
            String urnAsString = request.getPayload().getAsJsonPrimitive(FEATURE_PATH_TO_URN).getAsString();
            if (FeatureUniformResourceName.isValidUrn(urnAsString)) {
                toSend.add(new DisseminationAckEvent(FeatureUniformResourceName.fromString(urnAsString), tenantName));
            } else {
                errors.add(request);
            }
        }
        Map<String, Object> headers = new HashMap<>();
        headers.put(AmqpConstants.REGARDS_TENANT_HEADER, recipientTenant);
        sendEvents(toSend, headers);
        return errors;
    }

    @Override
    public boolean isAckRequired() {
        return false;
    }
}
