package fr.cnes.regards.modules.dam.plugins.datasources.webservice;

import com.google.gson.Gson;
import feign.FeignException;
import feign.Target;
import feign.httpclient.ApacheHttpClient;
import fr.cnes.regards.framework.feign.FeignClientBuilder;
import fr.cnes.regards.framework.geojson.FeatureWithPropertiesCollection;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration.WebserviceConfiguration;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches an open search data source
 */
public class OpenSearchFetcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchFetcher.class);
    /**
     * URL query start char
     */
    private static final String queryStartMarker = "?";
    /**
     * URL next parameter separator
     */
    private static final String nextParameterMarker = "&";
    /**
     * URL parameter value separator
     */
    private static final String parameterValueSeparator = "=";
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
     * @param page       page
     * @param lastUpdate data last update date
     * @return built URL
     */
    public String getFetchURL(Pageable page, OffsetDateTime lastUpdate) {
        // 1 - Prepare parameters to add
        List<Pair<String, String>> addedParameters = new ArrayList<>();
        // 2.1 - page index
        int servicePageIndex = page.getPageNumber() + webserviceConfiguration.getStartPageIndex();
        addedParameters.add(Pair.of(webserviceConfiguration.getPageIndexParam(), String.valueOf(servicePageIndex)));
        // 2.2 - page size: from configuration when provided, from caller page otherwise
        int pageSize = this.webserviceConfiguration.getPagesSize() != null ? this.webserviceConfiguration.getPagesSize() : page.getPageSize();
        addedParameters.add(Pair.of(webserviceConfiguration.getPageSizeParam(), String.valueOf(pageSize)));
        // 3.3 - lastUpdate if any is required and user provided it
        String lastUpdateParam = webserviceConfiguration.getLastUpdateParam();
        if (lastUpdate != null && lastUpdateParam != null) {
            addedParameters.add(Pair.of(lastUpdateParam, DateTimeFormatter.ISO_DATE_TIME.format(lastUpdate)));
        }

        // 2 - prepare next parameter separator
        String webserviceURL = webserviceConfiguration.getWebserviceURL();
        String currentParameterSeparator = queryStartMarker; // by default: query delimiter
        if (webserviceURL.endsWith(queryStartMarker) || webserviceURL.endsWith(nextParameterMarker)) {
            // next parameter can be added without inserting any new separator
            currentParameterSeparator = "";
        } else if (webserviceURL.contains(queryStartMarker)) {
            // url  already contains a query, next parameter should be separated using "&"
            currentParameterSeparator = nextParameterMarker;
        }

        // 3 - Append parameters
        StringBuilder builtURL = new StringBuilder(webserviceURL);
        for (Pair<String, String> p : addedParameters) {
            builtURL.append(currentParameterSeparator).append(p.getKey()).append(parameterValueSeparator).append(p.getValue());
            currentParameterSeparator = nextParameterMarker;
        }
        return builtURL.toString();
    }

    /**
     * Fetches and returns features for current page
     *
     * @param page       page
     * @param lastUpdate data last update date
     * @return found features
     * @Throws DataSourceException when content could not be retrieved
     */
    public ResponseEntity<FeatureWithPropertiesCollection> fetchFeatures(Pageable page, OffsetDateTime lastUpdate) throws DataSourceException {
        lastPageURL = this.getFetchURL(page, lastUpdate);
        Target<GEOJsonWebservice> target = new Target.HardCodedTarget<>(GEOJsonWebservice.class, lastPageURL);
        ApacheHttpClient client = new ApacheHttpClient(httpClient);
        try {
            ResponseEntity<FeatureWithPropertiesCollection> lastRetrievedFeatures = FeignClientBuilder.build(target, client, gson).get();
            if (lastRetrievedFeatures.getStatusCode() != HttpStatus.OK) {
                throw new DataSourceException(String.format("Could not get features to convert from URL '%s' (returned code: %d)",
                        lastPageURL, lastRetrievedFeatures.getStatusCodeValue()));
            }
            return lastRetrievedFeatures;
        } catch (HttpClientErrorException | HttpServerErrorException | FeignException e) {
            throw new DataSourceException(String.format("Could not get features to convert from URL '%s' (HTTP error)", lastPageURL), e);
        }
    }

    public String getLastPageURL() {
        return lastPageURL;
    }

}
