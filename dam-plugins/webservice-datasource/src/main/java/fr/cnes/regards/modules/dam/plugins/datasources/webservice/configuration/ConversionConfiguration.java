package fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import org.elasticsearch.common.Strings;

import java.util.Map;

/**
 * Holds conversion configuration, that describes how to convert retrieved GEOJson features into REGARDS data objects
 *
 * @author Raphaël Mechali
 */
public class ConversionConfiguration {

    /**
     * Model of produced data
     */
    @PluginParameter(name = DataSourcePluginConstants.MODEL_NAME_PARAM, label = "model name",
        description = "Associated data source model name")
    private String modelName;

    /**
     * Map of model attributes path to corresponding GEOJson field in webservice results
     */
    @PluginParameter(name = "attributeToJSonField", label = "Attribute path to JSon field", keylabel = "attributePath",
        description = "Links model attribute, by its path, to corresponding JSon field path in webservice results")
    private Map<String, String> attributeToJSonField;

    /**
     * Holds GEOJSon field path to report as data object thumbnail URL
     */
    @PluginParameter(name = "thumbnailURLPath", label = "thumbnail URL path", optional = true,
        description = "Path, in each feature from webservice results, to the thumbnail file URL")
    private String thumbnailURLPath;

    /**
     * Holds GEOJSon field path to report as data object raw data URL
     */
    @PluginParameter(name = "rawDataURLPath", label = "Raw data URL path", optional = true,
        description = "Path, in each feature from webservice results, to the raw data file URL")
    private String rawDataURLPath;

    /**
     * Holds GEOJSon field path to report as data object quicklook URL
     */
    @PluginParameter(name = "quicklookURLPath", label = "Quicklook URL path", optional = true,
        description = "Path, in each feature from webservice results, to the quicklook file URL")
    private String quicklookURLPath;

    /**
     * Total results count field name in results
     */
    @PluginParameter(name = "totalResultsField", label = "Page size field",
        description = "Name of the field, in results, exposing total results for request")
    private String totalResultsField;

    /**
     * Page size field name in results
     */
    @PluginParameter(name = "pageSizeField", label = "Total results field",
        description = "Name of the field, in results, exposing server page size")
    private String pageSizeField;

    /**
     * Constructor for reflexion instantiation
     */
    public ConversionConfiguration() {
    }

    /**
     * Full constructor for tests
     */
    public ConversionConfiguration(String modelName,
                                   Map<String, String> attributeToJSonField,
                                   String thumbnailURLPath,
                                   String rawDataURLPath,
                                   String quicklookURLPath,
                                   String totalResultsField,
                                   String pageSizeField) {
        this.modelName = modelName;
        this.attributeToJSonField = attributeToJSonField;
        this.thumbnailURLPath = thumbnailURLPath;
        this.rawDataURLPath = rawDataURLPath;
        this.quicklookURLPath = quicklookURLPath;
        this.totalResultsField = totalResultsField;
        this.pageSizeField = pageSizeField;
    }

    public String getModelName() {
        return modelName;
    }

    public Map<String, String> getAttributeToJSonField() {
        return attributeToJSonField;
    }

    public String getThumbnailURLPath() {
        return thumbnailURLPath;
    }

    public String getQuicklookURLPath() {
        return quicklookURLPath;
    }

    public String getRawDataURLPath() {
        return rawDataURLPath;
    }

    public String getTotalResultsField() {
        return totalResultsField;
    }

    public String getPageSizeField() {
        return pageSizeField;
    }

    /**
     * Checks current data validity. When it is invalid, it throws an exception.
     * That method should be used to perform plugin initialization
     *
     * @throws ModuleException when an invalid value is found
     */
    public void checkValidity() throws ModuleException {
        if (Strings.isNullOrEmpty(attributeToJSonField.get(StaticProperties.FEATURE_LABEL))) {
            throw new ModuleException(
                "Invalid webservice data source plugin configuration: features label path is missing");
        }
        if (Strings.isNullOrEmpty(attributeToJSonField.get(StaticProperties.FEATURE_PROVIDER_ID))) {
            throw new ModuleException(
                "Invalid webservice data source plugin configuration: features providerId path is missing");
        }
    }
}
