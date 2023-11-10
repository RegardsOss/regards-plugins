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
package fr.cnes.regards.modules.notifier.plugins;

import com.google.gson.JsonPrimitive;
import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.amqp.configuration.AmqpConstants;
import fr.cnes.regards.framework.amqp.event.IEvent;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.oais.urn.OaisUniformResourceName;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.feature.dto.urn.FeatureUniformResourceName;
import fr.cnes.regards.modules.notifier.domain.NotificationRequest;
import fr.cnes.regards.modules.notifier.domain.plugin.IRecipientNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Send acknowledge event related to a disseminated product
 * I.E. : Some REGARDS catalog, let's name it A, disseminate a product to another catalog, named B.
 * So this event is sent by catalog B to catalog A.
 *
 * @author Michaël NGUYEN
 */
@Plugin(author = "REGARDS Team",
        description = "Dissemination ACK sender",
        id = DisseminationAckSender.PLUGIN_ID,
        version = "2.0.0",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CNES",
        url = "https://regardsoss.github.io/")
public class DisseminationAckSender implements IRecipientNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisseminationAckSender.class);

    @Autowired
    private IPublisher publisher;

    public static final String PLUGIN_ID = "DisseminationAckSender";

    public static final String FEATURE_EXCHANGE_PARAM_NAME = "featureDisseminationExchange";

    public static final String AIP_EXCHANGE_PARAM_NAME = "aipDisseminationExchange";

    public static final String FEATURE_QUEUE_PARAM_NAME = "featureDisseminationQueueName";

    public static final String AIP_QUEUE_PARAM_NAME = "aipDisseminationQueueName";

    public static final String SENDER_LABEL_PARAM_NAME = "senderLabel";

    /**
     * Path to feature origin urn in notification payload (used for GeoJson notified products).
     */
    public static final String FEATURE_PATH_TO_URN = "urn";

    /**
     * Path to feature origin urn in notification payload (used for AIP notified products).
     */
    public static final String FEATURE_PATH_TO_ORIGIN_URN = "originUrn";

    @PluginParameter(label = "RabbitMQ exchange name for features dissemination",
                     name = FEATURE_EXCHANGE_PARAM_NAME,
                     optional = true)
    private String featureExchange;

    @PluginParameter(label = "RabbitMQ exchange name for aip dissemination",
                     name = AIP_EXCHANGE_PARAM_NAME,
                     optional = true)
    private String aipExchange;

    @PluginParameter(label = "Sender label",
                     name = SENDER_LABEL_PARAM_NAME,
                     description = "Acknowledge sender label. Used by destination system to identify the current system")
    private String senderLabel;

    @PluginParameter(label = "RabbitMQ queue name", name = FEATURE_QUEUE_PARAM_NAME, optional = true)
    private String featureQueueName;

    @PluginParameter(label = "RabbitMQ queue name", name = AIP_QUEUE_PARAM_NAME, optional = true)
    private String aipQueueName;

    /**
     * Check configuration before start. One cohesive couple exchange-queue must be defined
     *
     * @throws ModuleException amqp configuration error
     */
    @PluginInit
    public void initialize() throws ModuleException {
        boolean correctFeatureDisseminationConfig = featureExchange != null && featureQueueName != null;
        boolean correctAipDisseminationConfig = aipExchange != null && aipQueueName != null;
        if (!correctFeatureDisseminationConfig && !correctAipDisseminationConfig) {
            throw new ModuleException("Bad configuration, missing cohesive exchange and queue names");
        }
    }

    @Override
    public Collection<NotificationRequest> send(Collection<NotificationRequest> requestsToSend) {
        List<NotificationRequest> errors = new ArrayList<>();
        List<String> urnsAsStrings = new ArrayList<>();

        for (NotificationRequest request : requestsToSend) {
            Optional<String> urnAsString = computeUrnString(request);
            if (urnAsString.isPresent() && isValidUrn(urnAsString.get())) {
                urnsAsStrings.add(urnAsString.get());
            } else {
                errors.add(request);
            }
        }

        //Events are sent to different exchange and queues, depending on if they are for feature or aip dissemination
        //So we have to differentiate the urns to send two distinct lists of events
        List<String> featureUrns = urnsAsStrings.stream().filter(FeatureUniformResourceName::isValidUrn).toList();
        List<String> aipUrns = urnsAsStrings.stream().filter(OaisUniformResourceName::isValidUrn).toList();

        if (!featureUrns.isEmpty()) {
            sendFeatureEvents(featureUrns);
        }
        if (!aipUrns.isEmpty()) {
            sendAipEvents(aipUrns);
        }

        return errors;
    }

    private void sendFeatureEvents(List<String> featureUrns) {
        //DisseminationAckEvent from fem
        List<fr.cnes.regards.modules.feature.dto.event.in.DisseminationAckEvent> featuresToSend = featureUrns.stream()
                                                                                                             .map(urn -> new fr.cnes.regards.modules.feature.dto.event.in.DisseminationAckEvent(
                                                                                                                 urn,
                                                                                                                 senderLabel))
                                                                                                             .toList();

        //We get the tenant name from the first urn we find
        String featureRecipientTenant = UniformResourceName.fromString(featureUrns.get(0)).getTenant();
        Map<String, Object> headers = new HashMap<>();
        headers.put(AmqpConstants.REGARDS_TENANT_HEADER, featureRecipientTenant);
        sendEvents(featureExchange, featureQueueName, featuresToSend, headers);
    }

    private void sendAipEvents(List<String> aipUrns) {
        //DisseminationAckEvent from ingest
        List<fr.cnes.regards.modules.ingest.dto.request.event.DisseminationAckEvent> aipsToSend = aipUrns.stream()
                                                                                                         .map(urn -> new fr.cnes.regards.modules.ingest.dto.request.event.DisseminationAckEvent(
                                                                                                             urn,
                                                                                                             senderLabel))
                                                                                                         .toList();

        //We get the tenant from the first urn we find
        String aipRecipientTenant = UniformResourceName.fromString(aipUrns.get(0)).getTenant();
        Map<String, Object> headers = new HashMap<>();
        headers.put(AmqpConstants.REGARDS_TENANT_HEADER, aipRecipientTenant);
        sendEvents(aipExchange, aipQueueName, aipsToSend, headers);
    }

    private Optional<String> computeUrnString(NotificationRequest request) {
        // Check we can retrieve the URN, and it's valid field
        // The request playload can be :
        // - A Feature notification from FEM
        // - An AIP notification from INGEST
        // Retrieve urn from urn property (from FEM Feature format) or null if notification is not a FEM feature
        JsonPrimitive featureUrnNode = request.getPayload().getAsJsonPrimitive(FEATURE_PATH_TO_URN);
        // Retrieve urn from originUrn property (from AIP Feature format) or null if notification is not an
        // INGEST AIP
        JsonPrimitive featureOriginUrnNode = request.getPayload().getAsJsonPrimitive(FEATURE_PATH_TO_ORIGIN_URN);
        if (featureUrnNode != null && featureUrnNode.getAsString() != null) {
            return Optional.of(featureUrnNode.getAsString());
        } else if (featureOriginUrnNode != null && featureOriginUrnNode.getAsString() != null) {
            return Optional.of(featureOriginUrnNode.getAsString());
        } else {
            LOGGER.error("Unable to find urn in provided feature in order to send feature update request.");
            return Optional.empty();
        }
    }

    private static boolean isValidUrn(String urn) {
        return (FeatureUniformResourceName.isValidUrn(urn) || OaisUniformResourceName.isValidUrn(urn));
    }

    public <T extends IEvent> Set<NotificationRequest> sendEvents(String exchange,
                                                                  String queueName,
                                                                  List<T> toSend,
                                                                  Map<String, Object> headers) {
        this.publisher.broadcastAll(exchange,
                                    Optional.ofNullable(queueName),
                                    Optional.empty(),
                                    Optional.empty(),
                                    0,
                                    toSend,
                                    headers);

        // if there is an issue with amqp then none of the message will be sent
        return Collections.emptySet();
    }

    @Override
    public String getRecipientLabel() {
        return null;
    }

    @Override
    public boolean isAckRequired() {
        return false;
    }
}
