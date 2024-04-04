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
package fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag;

/**
 * Common EODAG script information
 */
public class EODagInformation {

    /**
     * Portal name for documentation
     */
    private String portalName = "REGARDS HMI";

    /**
     * Project name ... i.e. tenant
     */
    private String projectName;

    /**
     * EODAG extension provider
     */
    private String provider = "EODAG provider";

    /**
     * STAC API search endpoint
     */
    private String stacSearchApi;

    /**
     * STAC API host
     */
    private String baseUri;

    /**
     * API key
     */
    private String apiKey;

    /**
     * Downloaded filename
     */
    private String filename;

    public String getBaseUri() {
        return baseUri;
    }

    public void setBaseUri(String baseUri) {
        this.baseUri = baseUri;
    }

    public String getPortalName() {
        return portalName;
    }

    public void setPortalName(String portalName) {
        this.portalName = portalName;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getStacSearchApi() {
        return stacSearchApi;
    }

    public void setStacSearchApi(String stacSearchApi) {
        this.stacSearchApi = stacSearchApi;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
