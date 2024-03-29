package fr.cnes.regards.modules.dam.plugins.datasources.webservice;

import com.google.gson.Gson;
import feign.FeignException;
import feign.Target;
import feign.httpclient.ApacheHttpClient;
import fr.cnes.regards.framework.feign.ExternalTarget;
import fr.cnes.regards.framework.feign.FeignClientBuilder;
import fr.cnes.regards.framework.geojson.FeatureWithPropertiesCollection;
import fr.cnes.regards.modules.dam.domain.datasources.CrawlingCursor;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration.WebserviceConfiguration;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.HttpClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fetches an open search data source
 */
public class OpenSearchFetcher {

    /**
     * URL query start char
     */
    private static final String QUERY_START_MARKER = "?";

    /**
     * URL next parameter separator
     */
    private static final String NEXT_PARAMETER_MARKER = "&";

    /**
     * URL parameter value separator
     */
    private static final String PARAMETER_VALUE_SEPARATOR = "=";

    /**
     * OpenSearch webservice configuration
     **/
    private final WebserviceConfiguration webserviceConfiguration;

    /**
     * HTTP client
     */
    private final HttpClient httpClient;

    /**
     * JSON converter
     */
    private final Gson gson;

    /**
     * Stores fetched page URL (used for logging/reporting needs)
     */
    private String lastPageURL;

    /**
     * Constructor
     *
     * @param webserviceConfiguration OpenSearch webservice configuration
     * @param httpClient              HTTP client
     * @param gson                    JSON converter
     */
    public OpenSearchFetcher(WebserviceConfiguration webserviceConfiguration, HttpClient httpClient, Gson gson) {
        this.webserviceConfiguration = webserviceConfiguration;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    /**
     * Builds webservice URL for page and date as parameters (public for unit tests)
     *
     * @param cursor     cursor
     * @param lastUpdate data last update date
     * @return built URL
     */
    public String getFetchURL(CrawlingCursor cursor, OffsetDateTime lastUpdate) {
        // 1 - Prepare parameters to add
        List<Pair<String, String>> addedParameters = new ArrayList<>();
        // 2.1 - page index
        int servicePageIndex = cursor.getPosition() + webserviceConfiguration.getStartPageIndex();
        addedParameters.add(Pair.of(webserviceConfiguration.getPageIndexParam(), String.valueOf(servicePageIndex)));
        // 2.2 - page size: from configuration when provided, from caller page otherwise
        int pageSize = this.webserviceConfiguration.getPagesSize() != null ?
            this.webserviceConfiguration.getPagesSize() :
            cursor.getSize();
        addedParameters.add(Pair.of(webserviceConfiguration.getPageSizeParam(), String.valueOf(pageSize)));
        // 3.3 - lastUpdate if any is required and user provided it
        String lastUpdateParam = webserviceConfiguration.getLastUpdateParam();
        if (lastUpdate != null && lastUpdateParam != null) {
            addedParameters.add(Pair.of(lastUpdateParam, DateTimeFormatter.ISO_INSTANT.format(lastUpdate)));
        }

        // 2 - prepare next parameter separator
        String webserviceURL = webserviceConfiguration.getWebserviceURL();
        String currentParameterSeparator = QUERY_START_MARKER; // by default: query delimiter
        if (webserviceURL.endsWith(QUERY_START_MARKER) || webserviceURL.endsWith(NEXT_PARAMETER_MARKER)) {
            // next parameter can be added without inserting any new separator
            currentParameterSeparator = "";
        } else if (webserviceURL.contains(QUERY_START_MARKER)) {
            // url  already contains a query, next parameter should be separated using "&"
            currentParameterSeparator = NEXT_PARAMETER_MARKER;
        }

        // 3 - Append parameters
        StringBuilder builtURL = new StringBuilder(webserviceURL);
        for (Pair<String, String> p : addedParameters) {
            builtURL.append(currentParameterSeparator)
                    .append(p.getKey())
                    .append(PARAMETER_VALUE_SEPARATOR)
                    .append(p.getValue());
            currentParameterSeparator = NEXT_PARAMETER_MARKER;
        }
        // 4 - Append configuration parameters
        Map<String, Object> webserviceParameters = webserviceConfiguration.getWebserviceParameters();
        if (webserviceParameters != null) {
            for (Map.Entry<String, Object> parameter : webserviceParameters.entrySet()) {
                if (parameter.getValue() != null) {
                    builtURL.append(currentParameterSeparator)
                            .append(parameter.getKey())
                            .append(PARAMETER_VALUE_SEPARATOR)
                            .append(parameter.getValue());
                    currentParameterSeparator = NEXT_PARAMETER_MARKER;
                }
            }
        }
        return builtURL.toString();
    }

    /**
     * Fetches and returns features for current page
     *
     * @param cursor     cursor
     * @param lastUpdate data last update date
     * @return found features
     * @throws DataSourceException when content could not be retrieved
     */
    public ResponseEntity<FeatureWithPropertiesCollection> fetchFeatures(CrawlingCursor cursor,
                                                                         OffsetDateTime lastUpdate)
        throws DataSourceException {
        lastPageURL = this.getFetchURL(cursor, lastUpdate);
        Target<GEOJsonWebservice> target = new ExternalTarget<>(GEOJsonWebservice.class, lastPageURL, null);
        ApacheHttpClient client = new ApacheHttpClient(httpClient);
        try {
            ResponseEntity<FeatureWithPropertiesCollection> lastRetrievedFeatures = FeignClientBuilder.build(target,
                                                                                                             client,
                                                                                                             gson)
                                                                                                      .get();
            if (lastRetrievedFeatures.getStatusCode() != HttpStatus.OK) {
                throw new DataSourceException(String.format(
                    "Could not get features to convert from URL '%s' (returned code: %d)",
                    lastPageURL,
                    lastRetrievedFeatures.getStatusCodeValue()));
            }
            return lastRetrievedFeatures;
        } catch (HttpClientErrorException | HttpServerErrorException | FeignException e) {
            throw new DataSourceException(String.format("Could not get features to convert from URL '%s' (HTTP error)",
                                                        lastPageURL), e);
        }
    }

    public String getLastPageURL() {
        return lastPageURL;
    }

}
