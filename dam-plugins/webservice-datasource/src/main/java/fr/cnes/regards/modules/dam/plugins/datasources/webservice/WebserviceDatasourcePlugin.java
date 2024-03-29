package fr.cnes.regards.modules.dam.plugins.datasources.webservice;

import com.google.gson.Gson;
import fr.cnes.regards.framework.geojson.FeatureWithPropertiesCollection;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.notification.NotificationLevel;
import fr.cnes.regards.framework.notification.client.INotificationClient;
import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.modules.dam.domain.datasources.CrawlingCursor;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDataSourcePlugin;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration.ConversionConfiguration;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration.WebserviceConfiguration;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.reports.ConversionReport;
import fr.cnes.regards.modules.model.domain.ModelAttrAssoc;
import fr.cnes.regards.modules.model.service.IModelAttrAssocService;
import fr.cnes.regards.modules.templates.service.TemplateService;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Plugin to use an OpenSearch compliant webservice as a REGARDS datasource. Fetches data then converts it into REGARDS data to be be indexed.
 *
 * @author Raphaël Mechali
 */
@Plugin(id = "webservice-datasource",
        version = "0.4.0",
        description = "Extracts data objects from an OpenSearch webservice",
        author = "REGARDS Team",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CSSI",
        url = "https://github.com/RegardsOss")
public class WebserviceDatasourcePlugin implements IDataSourcePlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebserviceDatasourcePlugin.class);

    @PluginParameter(name = "webserviceConfiguration",
                     label = "Webservice configuration",
                     description = "Information about webservice used as data source")
    private WebserviceConfiguration webserviceConfiguration;

    @PluginParameter(name = "conversionConfiguration",
                     label = "Conversion configuration",
                     description = "Information to convert retrieved json results into REGARDS data objects")
    private ConversionConfiguration conversionConfiguration;

    /**
     * Indexation refresh rate in seconds
     */
    @PluginParameter(name = DataSourcePluginConstants.REFRESH_RATE,
                     defaultValue = "86400",
                     optional = true,
                     label = "refresh rate",
                     description = "Ingestion refresh rate in seconds (minimum delay between two consecutive indexations)")
    private Integer refreshRate;

    /**
     * Model attributes association service, used here to retrieve attributes associated with a model
     */
    @Autowired
    private IModelAttrAssocService modelAttrAssocService;

    /**
     * Notification client to notify conversion errors
     */
    @Autowired
    private INotificationClient notificationClient;

    /**
     * HTTP client for external API requests
     */
    @Autowired
    private HttpClient httpClient;

    /**
     * Gson request and response converter
     */
    @Autowired
    private Gson gson;

    /**
     * Templates service for conversion errors notification rendering
     */
    @Autowired
    private TemplateService templateService;

    /**
     * Delegate features converter
     **/
    private FeaturesConverter converter;

    /**
     * Delegate features fetcher
     */
    private OpenSearchFetcher fetcher;

    /**
     * Spring bean constructor
     */
    public WebserviceDatasourcePlugin() {
    }

    /**
     * Complete constructor for tests
     *
     * @param webserviceConfiguration -
     * @param conversionConfiguration -
     * @param refreshRate             -
     * @param modelAttrAssocService   -
     * @param notificationClient      -
     * @param httpClient              -
     * @param gson                    -
     */
    WebserviceDatasourcePlugin(WebserviceConfiguration webserviceConfiguration,
                               ConversionConfiguration conversionConfiguration,
                               Integer refreshRate,
                               IModelAttrAssocService modelAttrAssocService,
                               INotificationClient notificationClient,
                               HttpClient httpClient,
                               Gson gson) {
        this.webserviceConfiguration = webserviceConfiguration;
        this.conversionConfiguration = conversionConfiguration;
        this.refreshRate = refreshRate;
        this.modelAttrAssocService = modelAttrAssocService;
        this.notificationClient = notificationClient;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    /**
     * Checks module configuration before first run
     *
     * @throws ModuleException any configuration error
     */
    @PluginInit
    public void initialize() throws ModuleException {
        // check webservice configuration
        if (webserviceConfiguration == null) {
            throw new ModuleException("Missing webservice configuration");
        }
        webserviceConfiguration.checkValidity();

        // check conversion configuration
        if (conversionConfiguration == null) {
            throw new ModuleException("Missing conversion configuration");
        }
        conversionConfiguration.checkValidity();
        // Retrieve model to attributes associations
        List<ModelAttrAssoc> modelAttrAssocs;
        try {
            modelAttrAssocs = modelAttrAssocService.getModelAttrAssocs(conversionConfiguration.getModelName());
        } catch (Exception e) {
            // we catch all exceptions here just to add some context in logs
            throw new ModuleException(String.format(
                "Webservice data source plugin: cannot retrieve attributes for model %s. Was it deleted?",
                conversionConfiguration.getModelName()), e);
        }
        this.converter = new FeaturesConverter(conversionConfiguration, modelAttrAssocs);
        this.fetcher = new OpenSearchFetcher(webserviceConfiguration, httpClient, gson);
    }

    @Override
    public String getModelName() {
        return this.conversionConfiguration == null ? null : this.conversionConfiguration.getModelName();
    }

    @Override
    public int getRefreshRate() {
        return this.refreshRate;
    }

    /**
     * Converter delegate getter, opened for tests
     *
     * @return -
     */
    public FeaturesConverter getConverter() {
        return converter;
    }

    /**
     * Main implementation: retrieves elements and convert them, taking date in account if it is not null
     *
     * @param tenant Tenant
     * @param cursor cursor information
     * @param date   last crawling date, null when first crawling
     * @return converted results
     * @throws DataSourceException when conversion was not possible
     */
    @Override
    public List<DataObjectFeature> findAll(String tenant,
                                           CrawlingCursor cursor,
                                           OffsetDateTime lastIngestionDate,
                                           OffsetDateTime currentIngestionStartDate) throws DataSourceException {
        // A - pull web service resulting features for page (ignore date if not provided)
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format(
                "Webservice data source plugin: starting OpenSearch webservice source conversion at URL '%s', for page #%d (size %d)",
                webserviceConfiguration.getWebserviceURL(),
                cursor.getPosition(),
                cursor.getSize()));
        }

        ResponseEntity<FeatureWithPropertiesCollection> retrievedFeatures;
        try {
            retrievedFeatures = fetcher.fetchFeatures(cursor, lastIngestionDate);
        } catch (DataSourceException e) {
            // catch exception to notify administrator, then let it through
            notificationClient.notify(e.getMessage(),
                                      "Webservice data source plugin",
                                      NotificationLevel.ERROR,
                                      MimeTypeUtils.TEXT_PLAIN,
                                      DefaultRole.ADMIN);
            throw e;
        }

        // B - Convert each feature retrieved
        converter.convert(tenant, cursor, retrievedFeatures.getBody());

        // C - Notify if errors were encountered
        ConversionReport conversionReport = converter.getReport();
        if (conversionReport.hasErrors()) {
            NotificationLevel notificationLevel = conversionReport.getNotificationLevel();
            String notificationReport = conversionReport.buildNotificationReport(fetcher.getLastPageURL(),
                                                                                 templateService);
            if (notificationReport == null) {
                notificationReport = "No error report could be produced (inner plugin error)";
            }
            notificationClient.notify(notificationReport,
                                      "Webservice data source plugin",
                                      notificationLevel,
                                      MimeTypeUtils.TEXT_HTML,
                                      DefaultRole.ADMIN);
        }

        // D - Update cursor for next iteration
        Pair<List<DataObjectFeature>, Integer> convertedResults = converter.getConvertedResults();
        // compute the total number of pages to process and see if there are still features to convert
        int totalNumOfElements = convertedResults.getSecond();
        int totalNumOfPages = totalNumOfElements == 0 ?
            1 :
            (int) Math.ceil((double) totalNumOfElements / (double) cursor.getSize());
        cursor.setHasNext(cursor.getPosition() + 1 < totalNumOfPages);

        // return converted features
        return convertedResults.getFirst();
    }

}
