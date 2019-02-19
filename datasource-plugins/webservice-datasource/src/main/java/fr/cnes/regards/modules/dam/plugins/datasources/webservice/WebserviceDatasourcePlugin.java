package fr.cnes.regards.modules.dam.plugins.datasources.webservice;

import com.google.gson.Gson;
import fr.cnes.regards.framework.geojson.FeatureWithPropertiesCollection;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDataSourcePlugin;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.dam.domain.models.ModelAttrAssoc;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration.ConversionConfiguration;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration.WebserviceConfiguration;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.reports.ConversionReport;
import fr.cnes.regards.modules.dam.service.models.IModelAttrAssocService;
import fr.cnes.regards.modules.notification.client.INotificationClient;
import fr.cnes.regards.modules.notification.domain.NotificationLevel;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Plugin to use an OpenSearch compliant webservice as a REGARDS datasource. Fetches data then converts it into REGARDS data to be be indexed.
 *
 * @author Raphaël Mechali
 */
@Plugin(id = "webservice-datasource", version = "1.0-SNAPSHOT", description = "Extracts data objects from an OpenSearch webservice",
        author = "REGARDS Team", contact = "regards@c-s.fr", licence = "GPLv3.0", owner = "CSSI",
        url = "https://github.com/RegardsOss")
public class WebserviceDatasourcePlugin implements IDataSourcePlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebserviceDatasourcePlugin.class);

    @PluginParameter(name = "webserviceConfiguration", label = "Webservice configuration",
            description = "Information about webservice used as data source")
    private WebserviceConfiguration webserviceConfiguration;

    @PluginParameter(name = "conversionConfiguration", label = "Conversion configuration",
            description = "Information to convert retrieved json results into REGARDS data objects")
    private ConversionConfiguration conversionConfiguration;

    /**
     * Indexation refresh rate in seconds
     */
    @PluginParameter(name = DataSourcePluginConstants.REFRESH_RATE, defaultValue = "86400", optional = true,
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
     * HTTP client for external request
     */
    @Autowired
    private HttpClient httpClient;

    /**
     * Gson request and response converter
     */
    @Autowired
    private Gson gson;

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
    WebserviceDatasourcePlugin(WebserviceConfiguration webserviceConfiguration, ConversionConfiguration conversionConfiguration, Integer refreshRate, IModelAttrAssocService modelAttrAssocService, INotificationClient notificationClient, HttpClient httpClient, Gson gson) {
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
            throw new ModuleException(
                    String.format("Webservice data source plugin: cannot retrieve attributes for model %s. Was it deleted?", conversionConfiguration.getModelName()), e);
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
     * Main implementation: retrieves elements and convert them, taking date in account if it is not null
     *
     * @param tenant Tenant
     * @param page   page information (crawl process advancement)
     * @param date   last crawling date, null when first crawling
     * @return converted page
     * @throws DataSourceException
     */
    @Override
    public Page<DataObjectFeature> findAll(String tenant, Pageable page, OffsetDateTime date) throws DataSourceException {
        // A - pull web service resulting features for page (ignore date if not provided)
        /// ResponseEntity<FeatureWithPropertiesCollection> codeFeatures = this.getWebserviceFeatures(page, date);
        LOGGER.info(String.format("Webservice data source plugin: starting OpenSearch webservice source conversion at URL '%s', for page #%d (size %d)", webserviceConfiguration.getWebserviceURL(), page.getPageNumber(), page.getPageSize()));

        ResponseEntity<FeatureWithPropertiesCollection> retrievedFeatures;
        try {
            retrievedFeatures = fetcher.fetchFeatures(page, date);
        } catch (DataSourceException e) {
            // catch exception to notify administrator, then let it through
            notificationClient.notify(e.getMessage(), "Webservice data source plugin", NotificationLevel.ERROR, DefaultRole.ADMIN);
            throw e;
        }


        // B - Convert each feature retrieved
        converter.convert(tenant, page, retrievedFeatures.getBody());

        // C - Notify if errors were encountered
        ConversionReport conversionReport = converter.getReport();
        if (conversionReport.hasErrors()) {
            NotificationLevel notificationLevel = conversionReport.getNotificationLevel();
            String notificationReport = conversionReport.buildNotificationReport(fetcher.getLastPageURL());
            if (notificationReport == null) {
                notificationReport = "No error report could be produced (inner plugin error)";
            }
            notificationClient.notify(notificationReport, "Webservice data source plugin", notificationLevel, DefaultRole.ADMIN);
        }

        return converter.getConvertedPage();
    }


    @Override
    public Page<DataObjectFeature> findAll(String tenant, Pageable page) throws DataSourceException {
        // delegate to the dated mehtod
        return this.findAll(tenant, page, null);
    }

}
