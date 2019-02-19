package fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration;

import com.google.common.base.Strings;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Holds webservice configuration, that describes OpenSearch webservice access with pagination feature
 *
 * @author Raphaël Mechali
 */
public class WebserviceConfiguration {

    @PluginParameter(name = "webserviceURL", label = "Webservice URL",
            description = "OpenSearch webservice URL")
    private String webserviceURL;

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
     * Constructor for reflexion instantiation
     */
    public WebserviceConfiguration() {
    }

    /**
     * Constructor for tests
     * @param webserviceURL -
     * @param pageIndexParam -
     * @param pageSizeParam -
     * @param lastUpdateParam -
     * @param startPageIndex -
     */
    public WebserviceConfiguration(String webserviceURL, String pageIndexParam, String pageSizeParam, String lastUpdateParam, int startPageIndex) {
        this.webserviceURL = webserviceURL;
        this.pageIndexParam = pageIndexParam;
        this.pageSizeParam = pageSizeParam;
        this.lastUpdateParam = lastUpdateParam;
        this.startPageIndex = startPageIndex;
    }

    public String getWebserviceURL() {
        return webserviceURL;
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

    /**
     * @return true when webservice URL is set
     */
    private boolean hasWebserviceURL() {
        return !Strings.isNullOrEmpty(this.webserviceURL);
    }

    /**
     * @return true when page index is set
     */
    private boolean hasPageIndexParam() {
        return Strings.isNullOrEmpty(this.pageIndexParam);
    }

    /**
     * @return true when page size param is set
     */
    private boolean hasPageSizeParam() {
        return Strings.isNullOrEmpty(this.pageSizeParam);
    }

    /**
     * Checks current data validity. When it is invalid, it throws an exception.
     * That method should be used to perform plugin initialization
     *
     * @throws ModuleException when an invalid value is found
     */
    public void checkValidity() throws ModuleException {
        if (!this.hasWebserviceURL()) {
            throw new ModuleException("Invalid webservice data source plugin configuration: URL is missing");
        }
        if (!this.hasPageIndexParam()) {
            throw new ModuleException("Invalid webservice data source plugin configuration: page index parameter name is missing");
        }
        if (!this.hasPageSizeParam()) {
            throw new ModuleException("Invalid webservice data source plugin configuration: page size parameter name is missing");
        }
        try {
            new URL(this.webserviceURL);
        } catch (MalformedURLException e) {
            throw new ModuleException("Invalid webservice data source plugin configuration: URL is malformed");
        }
    }

}
