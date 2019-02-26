package fr.cnes.regards.modules.dam.plugins.datasources.webservice.reports;

import fr.cnes.regards.framework.oais.urn.DataType;
import fr.cnes.regards.modules.dam.domain.models.attributes.AttributeType;

/**
 * Holds a feature conversion error description for report.
 * It exposes messages builders through static methods
 *
 * @author Raphaël Mechali
 */
public class FeatureConversionError {

    /**
     * Is this error blocking feature conversion?
     */
    private final boolean blocking;
    private final String message;

    /**
     * Constructor
     *
     * @param blocking is this error blocking feature conversion?
     * @param message  error message
     */
    private FeatureConversionError(boolean blocking, String message) {
        this.blocking = blocking;
        this.message = message;
    }

    /**
     * Builds an error for a mandatory attribute not found (blocks feature conversion)
     *
     * @param attributeJsonPath JSON path of the attribute to convert
     * @param resultsJsonPath   JSON path in webservice results
     * @return built error with corresponding message
     */
    public static FeatureConversionError getMandatoryAttributeNotFoundError(String attributeJsonPath, String resultsJsonPath) {
        return new FeatureConversionError(true, String.format("mandatory attribute '%s' value was not found at path '%s'", attributeJsonPath, resultsJsonPath));
    }

    /**
     * Builds an error for an invalid input value (blocks feature conversion only when that attribute is mandatory)
     *
     * @param attributeJsonPath JSON path of the attribute to convert
     * @param type              type of the attribute to convert
     * @param mandatory         is attribute mandatory?
     * @param resultsJsonPath   JSON path in webservice results
     * @param resultsValue      found value in results, not null
     * @return built error with corresponding message
     */
    public static FeatureConversionError getValueNotConvertibleError(String attributeJsonPath, AttributeType type, boolean mandatory, String resultsJsonPath, Object resultsValue) {
        return new FeatureConversionError(mandatory, String.format("%s attribute '%s' value, of type %s, could not be converted from path '%s' value (%s)",
                mandatory ? "mandatory" : "optional", attributeJsonPath, type, resultsJsonPath, resultsValue));
    }

    /**
     * Builds an error for an invalid element on value path (blocks feature conversion only when that attribute is mandatory)
     *
     * @param attributeJsonPath JSON path of the attribute to convert
     * @param mandatory         is attribute to convert mandatory?
     * @param resultsJsonPath   JSON path in webservice results
     * @param errorLevel        level name, in JSON path, where the error occurred
     * @param resultsValue      found value in results, not null
     * @return built error with corresponding message
     */
    public static FeatureConversionError getInvalidValueOnPathError(String attributeJsonPath, boolean mandatory, String resultsJsonPath, String errorLevel, Object resultsValue) {
        return new FeatureConversionError(mandatory, String.format("%s attribute '%s' value could not be retrieved from path '%s' as element at '%s' (%s) is not a JSON object",
                mandatory ? "mandatory" : "optional", attributeJsonPath, resultsJsonPath, errorLevel, resultsValue));
    }

    /**
     * Returns a non blocking error corresponding to invalid URL for file
     *
     * @param fileURL         found file URL
     * @param fileType        corresponding file type
     * @param resultsJsonPath path in JSON results
     * @return corresponding error
     */
    public static FeatureConversionError getInvalidFileURLError(String fileURL, DataType fileType, String resultsJsonPath) {
        return new FeatureConversionError(false, String.format("%s file URL, '%s', found at path '%s', is invalid. File was discarded.", fileType, fileURL, resultsJsonPath));
    }

    /**
     * Returns a non blocking error corresponding to missing extending on URL for file
     *
     * @param fileURL         found file URL
     * @param fileType        corresponding file type
     * @param resultsJsonPath path in JSON results
     * @return corresponding error
     */
    public static FeatureConversionError getMimeTypeNotFoundError(String fileURL, DataType fileType, String resultsJsonPath) {
        return new FeatureConversionError(false, String.format("%s file MIME type could not be inferred from URL '%s', found at path '%s'. File was discarded.", fileType, fileURL, resultsJsonPath));
    }

    /**
     * Returns a non blocking error corresponding to missing extending on URL for file
     *
     * @param fileURL         found file URL
     * @param fileType        corresponding file type
     * @param resultsJsonPath path in JSON results
     * @param mimeType        found mime type
     * @return corresponding error
     */
    public static FeatureConversionError getInvalidImageMimeTypeFound(String fileURL, DataType fileType, String resultsJsonPath, String mimeType) {
        return new FeatureConversionError(false, String.format("%s file MIME type, '%s', is not a supported picture file type (URL '%s', found at path '%s'). File was discarded.", fileType, mimeType, fileURL, resultsJsonPath));
    }

    public boolean isBlocking() {
        return blocking;
    }

    public String getMessage() {
        return message;
    }
}
