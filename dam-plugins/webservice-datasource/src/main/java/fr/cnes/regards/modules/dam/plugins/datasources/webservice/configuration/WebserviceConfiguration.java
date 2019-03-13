package fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * Holds webservice configuration, that describes OpenSearch webservice access with pagination feature
 *
 * @author Raphaël Mechali
 */
public class WebserviceConfiguration {

    @PluginParameter(name = "webserviceURL", label = "Webservice URL",
            description = "OpenSearch webservice URL")
    private String webserviceURL;

    @PluginParameter(name = "webserviceParameters", label = "Webservice query parameters", optional = true,
            description = "Webservice query parameters, restricting results set", keylabel = "parameter name")
    private Map<String, Object> webserviceParameters;

    @PluginParameter(name = "pageIndexParam", label = "Page index parameter",
            description = "Name of the parameter to use in query in order to specify the page index")
    private String pageIndexParam;

    @PluginParameter(name = "pageSizeParam", label = "Page size parameter",
            description = "Name of the parameter to use in query in order in order to specify the page size")
    private String pageSizeParam;

    /**
     * Data updated parameter name, optional as a customer request but omitting that parameter could be leading into performances issues
     */
    @PluginParameter(name = "lastUpdateParam", label = "Last update parameter", optional = true,
            description = "Name of the parameter to use in query in order to specify the results last update lower date (others should not be returned)")
    private String lastUpdateParam;

    /**
     * Server start page index (1 when not provided)
     */
    @PluginParameter(name = "startPageIndex", label = "Start page index", optional = true,
            defaultValue = "1", description = "Server start page index")
    private Integer startPageIndex;

    /**
     * Server page size (not mandatory, defaults to crawler provided size). Support for THEIA like servers
     * that send errors when requesting a first page larger than where they expect (twisted behavior...)
     */
    @PluginParameter(name = "pagesSize", label = "Page sizes", optional = true,
            description = "Server pages size (for servers sending errors when page size is too large)")
    private Integer pagesSize;

    /**
     * Constructor for reflexion instantiation
     */
    public WebserviceConfiguration() {
    }

    /**
     * Constructor for tests
     */
    public WebserviceConfiguration(String webserviceURL, String pageIndexParam, String pageSizeParam, String lastUpdateParam, int startPageIndex, Integer pagesSize, Map<String, Object> webserviceParameters) {
        this.webserviceURL = webserviceURL;
        this.pageIndexParam = pageIndexParam;
        this.pageSizeParam = pageSizeParam;
        this.lastUpdateParam = lastUpdateParam;
        this.startPageIndex = startPageIndex;
        this.pagesSize = pagesSize;
        this.webserviceParameters = webserviceParameters;
    }

    public String getWebserviceURL() {
        return webserviceURL;
    }

    public Map<String, Object> getWebserviceParameters() {
        return webserviceParameters;
    }

    public String getPageIndexParam() {
        return pageIndexParam;
    }

    public String getPageSizeParam() {
        return pageSizeParam;
    }

    public String getLastUpdateParam() {
        return lastUpdateParam;
    }

    public Integer getStartPageIndex() {
        return startPageIndex;
    }

    public Integer getPagesSize() {
        return pagesSize;
    }

    /**
     * Checks current data validity. When it is invalid, it throws an exception.
     * That method should be used to perform plugin initialization
     *
     * @throws ModuleException when an invalid value is found
     */
    public void checkValidity() throws ModuleException {
        try {
            new URL(this.webserviceURL);
        } catch (MalformedURLException e) {
            throw new ModuleException("Invalid webservice data source plugin configuration: URL is malformed");
        }
    }

}
