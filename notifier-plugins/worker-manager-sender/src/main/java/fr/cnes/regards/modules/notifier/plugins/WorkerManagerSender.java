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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import fr.cnes.regards.common.notifier.plugins.AbstractRabbitMQSender;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.notifier.domain.NotificationRequest;
import fr.cnes.regards.modules.workermanager.dto.events.RawMessageBuilder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The purpose of this plugin is to send worker manager processing requests through rs-notifier microservice.
 *
 * @author Iliana Ghazali
 **/
@Plugin(author = "REGARDS Team",
        description = "The purpose of this plugin is to send worker manager processing requests.",
        id = WorkerManagerSender.PLUGIN_ID, version = "1.0.0", contact = "regards@c-s.fr", license = "GPLv3",
        owner = "CNES", url = "https://regardsoss.github.io/")
public class WorkerManagerSender extends AbstractRabbitMQSender {

    //@formatter:off
    @PluginParameter(
            label = "Recipient acknowledgment",
            description = "When value is True, the recipient will send back an acknowledgment.",
            name = ACK_REQUIRED_PARAM_NAME,
            optional = true,
            defaultValue = "false")
    private boolean ackRequired;

    @PluginParameter(
            label = "Pattern to name the session on the dashboard (optional).",
            name = WS_SESSION_NAME_PATTERN_NAME,
            description = "If the parameter is not filled, the session will be named with the metadata of "
                    + "the requests received. If a value is provided, the session will be named the following way "
                    + "\"^\\{<jsonPathToAccessProductType>\\}-#day(.*)\""
                    + ", where the parameter <jsonPathToAccessProductType> has to be replaced with the json path to access the product type. "
                    + "The RegExp could be for instance : \"^\\{properties.data.type\\}-#day-foo\". "
                    + "The parameter properties.data.type will be replaced with the corresponding product type and #day with the ISO_LOCAL_DATE "
                    + "formatted current date. Note that any additional parameters can be provided after #day to help "
                    + "identifying the session names on the dashboard. If the pattern is not found, the "
                    + "session will be named with the pattern " + SESSION_NAME_PATTERN_ERROR + ". In that "
                    + "case, check if the path to access the product type is valid and if the regexp match the "
                    + "pattern mentioned before.",
            optional = true)
    private String sessionNamePattern;

    @PluginParameter(
            label = "Type of products processed",
            name = WS_CONTENT_TYPE_NAME)
    private String contentType;

    @PluginParameter(
            label = "Recipient tenant (optional)",
            name = RECIPIENT_TENANT_NAME,
            description = "Specify the recipient tenant in case it is different from the one sending the messages.",
            optional = true)
    private String recipientTenant;
    //@formatter:on

    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    public static final String PLUGIN_ID = "WorkerManagerSend";

    /**
     * Plugin parameters names
     */
    public static final String ACK_REQUIRED_PARAM_NAME = "ackRequired";

    public static final String WS_SESSION_NAME_PATTERN_NAME = "sessionPattern";

    public static final String WS_CONTENT_TYPE_NAME = "contentType";

    public static final String RECIPIENT_TENANT_NAME = "recipientTenant";

    /**
     * RegExp to build the session name
     */

    public static final String WS_SESSION_PATTERN = "^\\{(.+)\\}-(#day)(.*)$";

    public static final String DEFAULT_SESSION_OWNER_TOKEN = "REGARDS-";

    public static final String SESSION_NAME_PATTERN_ERROR = "{sessionNamePatternError}-#day";

    public static final String SESSION_OWNER_METADATA_PATH = "sessionOwner";

    public static final String SESSION_METADATA_PATH = "session";

    private static final Configuration JSON_PATH_CONFIGURATION = Configuration.builder()
            .jsonProvider(new GsonJsonProvider()).options(Option.SUPPRESS_EXCEPTIONS).build();

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerManagerSender.class);

    @Override
    public Collection<NotificationRequest> send(Collection<NotificationRequest> requestsToProcess) {
        List<Message> messagesToSend = buildWorkerManagerSenderMessages(requestsToProcess);
        // headers are empty hashmap because they are already contained in Message.messageProperties
        return sendEvents(messagesToSend, new HashMap<>());
    }

    @Override
    public boolean isAckRequired() {
        return this.ackRequired;
    }

    private List<Message> buildWorkerManagerSenderMessages(Collection<NotificationRequest> requestsToProcess) {
        List<Message> messagesToSend = new ArrayList<>();
        String tenantName = recipientTenant == null ? runtimeTenantResolver.getTenant() : recipientTenant;

        for (NotificationRequest notificationRequest : requestsToProcess) {
            JsonObject feature = notificationRequest.getPayload();
            String sessionOwnerName;
            String sessionName;

            // if the session pattern is null fill sessionOwnerName and session with the request metadata
            // else use the pattern
            if (sessionNamePattern == null) {
                DocumentContext metadataContext = JsonPath.using(JSON_PATH_CONFIGURATION)
                        .parse(notificationRequest.getMetadata());
                sessionOwnerName = ((JsonPrimitive) metadataContext.read(SESSION_OWNER_METADATA_PATH)).getAsString();
                sessionName = ((JsonPrimitive) metadataContext.read(SESSION_METADATA_PATH)).getAsString();
            } else {
                sessionOwnerName = DEFAULT_SESSION_OWNER_TOKEN + tenantName;
                sessionName = getSessionNameFromPattern(feature);
            }
            messagesToSend.add(RawMessageBuilder.build(tenantName, contentType, sessionOwnerName, sessionName,
                                                       UUID.randomUUID().toString(),
                                                       feature.toString().getBytes(StandardCharsets.UTF_8)));
        }
        return messagesToSend;
    }

    private String getSessionNameFromPattern(JsonObject feature) {
        JsonElement featureType = null;
        String currentDate = OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // check if the feature type can be retrieved from the WS_SESSION_PATTERN
        Pattern pattern = Pattern.compile(WS_SESSION_PATTERN);
        Matcher matcher = pattern.matcher(sessionNamePattern);
        if (matcher.find()) {
            // access the feature type from json path
            String jsonPathToAccessFeatureType = matcher.group(1);
            featureType = JsonPath.using(JSON_PATH_CONFIGURATION).parse(feature).read(jsonPathToAccessFeatureType);
        }

        // if the property was not found, replace the session with the default session name
        if (featureType == null || !featureType.isJsonPrimitive()) {
            String defaultSessionName = SESSION_NAME_PATTERN_ERROR.replaceAll(WS_SESSION_PATTERN,
                                                                              String.format("$1-%s", currentDate));
            LOGGER.warn("The pattern configured in {} \"{}\" has an invalid pattern. Check if : \n "
                                + "- The RegExp is valid and follow the pattern \"{}\" \n - The JsonPath to access the "
                                + "feature type is valid.\nThe session will be named by default: \"{}\".",
                        sessionNamePattern, WS_SESSION_NAME_PATTERN_NAME, WS_SESSION_PATTERN, defaultSessionName);
            return defaultSessionName;
        } else {
            return sessionNamePattern.replaceAll(WS_SESSION_PATTERN,
                                                 String.format("%s-%s$3", featureType.getAsString(), currentDate));
        }
    }
}
