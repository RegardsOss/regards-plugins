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
package fr.cnes.regards.modules.catalog.femdriver.dto;

import fr.cnes.regards.modules.search.dto.SearchRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.HashSet;
import java.util.Set;

/**
 * POJO containig the list of recipients for the direct notification with rs-notifier and information to handle a search on catalog
 *
 * @author Stephane Cortine
 */
public class RecipientsSearchRequest {

    /**
     * Search parameters {@link SearchRequest} on the catalog
     */
    @Schema(description = "Search parameters on the catalog")
    private SearchRequest searchRequest;

    /**
     * List of recipients(business identifiers of plugin configurations
     * {@link fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration}) for the direct
     * notification
     */
    @Schema(description = "List of recipient(business identifiers) for direct notification")
    private Set<String> recipients = new HashSet<>();

    public SearchRequest getSearchRequest() {
        return searchRequest;
    }

    public void setSearchRequest(SearchRequest searchRequest) {
        this.searchRequest = searchRequest;
    }

    public Set<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(Set<String> recipients) {
        this.recipients = recipients;
    }
}
