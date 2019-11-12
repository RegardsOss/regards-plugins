package fr.cnes.regards.modules.dam.plugins.datasources.webservice;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.elasticsearch.common.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;

import fr.cnes.regards.framework.geojson.Feature;
import fr.cnes.regards.framework.geojson.FeatureWithPropertiesCollection;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.oais.urn.DataType;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.configuration.ConversionConfiguration;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.reports.ConversionReport;
import fr.cnes.regards.modules.dam.plugins.datasources.webservice.reports.FeatureConversionError;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import fr.cnes.regards.modules.model.domain.ModelAttrAssoc;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.model.dto.properties.ObjectProperty;

/**
 * Performs conversion from a retrieved feature list into a Regards data object features
 *
 * @author Raphaël Mechali
 */
public class FeaturesConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeaturesConverter.class);

    /**
     * List of files data types that should be handled as images
     */
    private static final List<DataType> imagesDataTypes = Arrays.asList(DataType.QUICKLOOK_HD, DataType.QUICKLOOK_MD,
                                                                        DataType.QUICKLOOK_SD, DataType.THUMBNAIL);

    /**
     * Allowed MIME types types for images files
     */
    private static final List<String> imagesAllowedMimeTypes = Arrays
            .asList(MediaType.IMAGE_GIF_VALUE, MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE);

    /** Fixed value prefix: when used as first path character, it indicates that the net characters should be handled as a constant input */
    private static final String FIXED_VALUE_PREFIX = "#";

    /**
     * Computes if path as parameter should be handled as a fixed value
     * @param path path
     * @return true when path as parameter should be considered as a fixed input value, false otherwise
     */
    private static boolean isFixedValue(String path) {
        return path.startsWith(FIXED_VALUE_PREFIX);
    }

    /**
     * Computes fixed value for prefixed value as parameter
     * @param prefixedValue prefixed value (pre: there must be the prefix!)
     * @return fixed value found
     */
    private static String getFixedValue(String prefixedValue) {
        return prefixedValue.substring(FIXED_VALUE_PREFIX.length());
    }

    /**
     * Conversion configuration
     */
    private final ConversionConfiguration conversionConfiguration;

    /**
     * Path in GEOJson resulting feature to label value
     */
    private final String labelJSONPath;

    /**
     * Path in GEOJson resulting feature to label value
     */
    private final String providerIdJSONPath;

    /**
     * Map of target attribute model to source JSON field
     */
    private final Map<AttributeModel, String> attributeModelToPath = new HashMap<>();

    /**
     * Current page conversion errors report
     */
    private ConversionReport report;

    /**
     * Currently converted page
     */
    private Page<DataObjectFeature> convertedPage;

    /**
     * Constructor: initializes required runtime conversion data
     *
     * @param conversionConfiguration conversion configuration
     * @param modelAttrAssocs         model to
     * @throws ModuleException when any conversion blocker occurs
     */
    public FeaturesConverter(ConversionConfiguration conversionConfiguration, List<ModelAttrAssoc> modelAttrAssocs)
            throws ModuleException {
        this.conversionConfiguration = conversionConfiguration;
        // pack conversion data into runtime conversion data
        Map<String, String> attributesToPathCopy = new HashMap<>(conversionConfiguration.getAttributeToJSonField());
        // A - Extract mandatory fields for features (and remove them of the list of attributes to converts)
        // A.1 - extract label GeoJSON field (it cannot be null as it is checked at plugin init by ConversionConfiguration)
        labelJSONPath = attributesToPathCopy.remove(StaticProperties.FEATURE_LABEL);
        // A.2 - extract providerId GeoJSON field (it cannot be null as it is checked at plugin init by ConversionConfiguration)
        providerIdJSONPath = attributesToPathCopy.remove(StaticProperties.FEATURE_PROVIDER_ID);

        // B - Prepare the conversion map
        if (!attributesToPathCopy.isEmpty()) {
            if ((modelAttrAssocs != null) && !modelAttrAssocs.isEmpty()) {
                for (Map.Entry<String, String> attributeToPath : attributesToPathCopy.entrySet()) {
                    // B.2.a - retrieve attribute
                    Optional<ModelAttrAssoc> optionalAttribute = modelAttrAssocs.stream()
                            .filter(modelAttrAssoc -> modelAttrAssoc.getAttribute().getJsonPath()
                                    .equals(attributeToPath.getKey()))
                            .findFirst();
                    // B.2.b - store converter if attribute was found
                    if (optionalAttribute.isPresent()) {
                        attributeModelToPath.put(optionalAttribute.get().getAttribute(), attributeToPath.getValue());
                    } else {
                        // we catch all exceptions here just to add some context in logs
                        throw new ModuleException(String
                                .format("Webservice data source plugin: cannot retrieve attribute model %s. Please check plugin configuration",
                                        attributeToPath.getKey()));
                    }
                }
            }
        }
    }

    /**
     * Retrieves a mandatory field in collection or throws missing error
     *
     * @param featureCollection feature collection
     * @param fieldName         field to retrieve
     * @return found value
     * @throws DataSourceException when not found
     */
    private static int retrieveMandatoryIntField(FeatureWithPropertiesCollection featureCollection, String fieldName)
            throws DataSourceException {
        Object foundValue = featureCollection.getProperties().get(fieldName);
        if (foundValue == null) {
            throw new DataSourceException(
                    String.format("The field '%s' is missing in results page properties", fieldName));
        }
        if (foundValue instanceof Number) {
            return ((Number) foundValue).intValue();
        }
        try {
            return Integer.valueOf(foundValue.toString());
        } catch (NumberFormatException e) {
            throw new DataSourceException(
                    String.format("Field '%s' value cannot be parsed into a valid integer number", fieldName), e);
        }
    }

    public Page<DataObjectFeature> getConvertedPage() {
        return convertedPage;
    }

    public ConversionReport getReport() {
        return report;
    }

    /**
     * Converts GEOJSon feature collection as parameter into a list of REGARDS data object features
     *
     * @param tenant            tenant for the new feature (used to create URN)
     * @param page              page that was requested to external provider
     * @param featureCollection Feature collection to convert
     */
    public void convert(String tenant, Pageable page, FeatureWithPropertiesCollection featureCollection)
            throws DataSourceException {
        LOGGER.trace("Webservice data source plugin: Starting features conversion");
        List<Feature> webserviceFeatures = featureCollection.getFeatures();
        // Prepare a new report
        report = new ConversionReport();

        // Compute page data (breaks conversion if not found)
        int totalResults = retrieveMandatoryIntField(featureCollection, conversionConfiguration.getTotalResultsField());
        int pageSize = page.getPageSize();
        if (page.getPageNumber() == 0) {
            // Allowing page size setting changes only on first results page
            pageSize = retrieveMandatoryIntField(featureCollection, conversionConfiguration.getPageSizeField());
        }
        if (webserviceFeatures.isEmpty()) {
            // XXX - workaround for THEIA: total results is wrong, recompute it to let parent caller stop current indexation
            LOGGER.info(String
                    .format("Webservice data source plugin: No results found at page #%d, stopping indexation.",
                            page.getPageNumber()));
            totalResults = page.getPageNumber() * pageSize; // total count: sum of previous pages elements
        }

        LOGGER.trace(String.format("Webservice data source plugin: Found page size %d and total results %d", pageSize,
                                   totalResults));

        // Convert each element. Remove null elements (impossible conversions due to missing mandatory property
        List<DataObjectFeature> convertedFeatures = new ArrayList<>();
        for (int i = 0; i < webserviceFeatures.size(); i++) {
            DataObjectFeature convertedElement = this.convertFeature(tenant, webserviceFeatures.get(i), i);
            if (convertedElement != null) {
                convertedFeatures.add(convertedElement);
            }
        }
        convertedPage = new PageImpl<>(convertedFeatures, PageRequest.of(page.getPageNumber(), pageSize), totalResults);
        LOGGER.info(String.format("Finished converting page %d (converted %d / %d elements)", page.getPageNumber(),
                                  convertedFeatures.size(), webserviceFeatures.size()));
    }

    /**
     * Converts a feature or returns null if conversion is not possible. Note that the whole conversion alogrithm runs even after
     * encountering errors, so that the report hold all errors in feature.
     *
     * @param tenant       tenant for the new feature (used to create URN)
     * @param feature      source feature, to convert
     * @param featureIndex feature index (for logging and reports needs)
     * @return converted feature or null
     */
    private DataObjectFeature convertFeature(String tenant, Feature feature, int featureIndex) {
        // A - Extract mandatory attribute label (let delegate method report error if there is any)
        LOGGER.trace("Webservice data source plugin: Retrieve feature label");
        String labelValue = getStringAttributeValueByPath(feature, labelJSONPath, featureIndex, null, null,
                                                          StaticProperties.FEATURE_LABEL, true);

        // B - Extract mandatory attribute provider ID
        LOGGER.trace("Webservice data source plugin: Retrieve feature provider ID");
        String providerIDValue = getStringAttributeValueByPath(feature, providerIdJSONPath, featureIndex, labelValue,
                                                               null, StaticProperties.FEATURE_PROVIDER_ID, true);

        // C - Create feature, even when it is not valid, to avoid testing those points later (it will not be registered in such case)
        DataObjectFeature convertedFeature = new DataObjectFeature(tenant, providerIDValue, labelValue);

        // D - Retrieve geometry if provided
        convertedFeature.setGeometry(feature.getGeometry());

        // E - Convert other attributes
        LOGGER.trace(String.format("Webservice data source plugin: convert other attributes in feature %s(%s)",
                                   labelValue, providerIDValue));
        attributeModelToPath.forEach((key, value) -> this.convertAttribute(value, feature, key, convertedFeature,
                                                                           labelValue, providerIDValue, featureIndex));

        // F - convert files
        String quicklookURLPath = conversionConfiguration.getQuicklookURLPath();
        if (!Strings.isNullOrEmpty(quicklookURLPath)) {
            convertFile(quicklookURLPath, feature, convertedFeature, DataType.QUICKLOOK_SD, labelValue, providerIDValue,
                        featureIndex);
        }
        String rawDataURLPath = conversionConfiguration.getRawDataURLPath();
        if (!Strings.isNullOrEmpty(rawDataURLPath)) {
            convertFile(rawDataURLPath, feature, convertedFeature, DataType.RAWDATA, labelValue, providerIDValue,
                        featureIndex);
        }
        String thumbnailURLPath = conversionConfiguration.getThumbnailURLPath();
        if (!Strings.isNullOrEmpty(thumbnailURLPath)) {
            convertFile(thumbnailURLPath, feature, convertedFeature, DataType.THUMBNAIL, labelValue, providerIDValue,
                        featureIndex);
        }

        // accept conversion if no blocking error was found
        return report.hasBlockingErrors(featureIndex) ? null : convertedFeature;
    }

    /**
     * Converts attribute from source feature to target feature (out parameter). The conversion always produces an attribute, but it may write a blocking report entry that will
     * prevent feature conversion.
     *
     * @param sourceJSonPath       JSON path to use in source feature to retrieve value to convert
     * @param sourceFeature        Feature as returned by the webservice
     * @param targetAttributeModel Defines the target attribute to build
     * @param targetFeature        target feature (out parameter)
     * @param label                found label for target feature (maybe null), for reporting
     * @param providerId           found providerId for target feature (maybe null), for reporting
     * @param featureIndex         index in page, for reporting
     */
    private void convertAttribute(String sourceJSonPath, Feature sourceFeature, AttributeModel targetAttributeModel,
            DataObjectFeature targetFeature, String label, String providerId, int featureIndex) {
        LOGGER.trace(String.format("Webservice data source plugin: convert attribute from %s to %s(%s)", sourceJSonPath,
                                   targetAttributeModel.getJsonPath(), targetAttributeModel.getType()));

        // A - Retrieve source value (let delegate method add mandatory and path errors if any)
        Object sourceValue = this.getAttributeValueByPath(sourceFeature, sourceJSonPath, featureIndex, label,
                                                          providerId, targetAttributeModel.getJsonPath(),
                                                          !targetAttributeModel.isOptional());

        // B - Attempt value conversion (if it fails, initialize attribute with null but report the error)
        IProperty<?> convertedAttribute;
        try {
            convertedAttribute = IProperty.forType(targetAttributeModel.getType(), targetAttributeModel.getName(),
                                                   sourceValue);
        } catch (IllegalArgumentException | ClassCastException e) {
            // Conversion failed: build attribute with null value
            LOGGER.warn(String
                    .format("Webservice data source plugin: %s attribute value, of type %s, cannot be converted from %s (%s) in feature #%d %s(%s)",
                            targetAttributeModel.getJsonPath(), targetAttributeModel.getType(), sourceJSonPath,
                            sourceValue, featureIndex, label, providerId),
                        e);
            report.addFeatureConversionError(featureIndex, label, providerId, FeatureConversionError
                    .getValueNotConvertibleError(targetAttributeModel.getJsonPath(), targetAttributeModel.getType(),
                                                 !targetAttributeModel.isOptional(), sourceJSonPath, sourceValue));
            convertedAttribute = IProperty.forType(targetAttributeModel.getType(), targetAttributeModel.getName(),
                                                   null);
        }

        // C - add the converted attribute in or in fragment if any
        if (convertedAttribute.getValue() != null) {
            if (targetAttributeModel.hasFragment()) {
                // D.2.a - Retrieve or add corresponding fragment and property inside it
                String fragmentName = targetAttributeModel.getFragment().getName();
                ObjectProperty fragment = (ObjectProperty) targetFeature.getProperty(fragmentName);
                if (fragment == null) {
                    fragment = IProperty.buildObject(fragmentName);
                    targetFeature.addProperty(fragment);
                    LOGGER.trace(String.format("Webservice data source plugin: added fragment %s in feature",
                                               fragmentName));
                }
                fragment.addProperty(convertedAttribute);
                LOGGER.trace(String.format("Webservice data source plugin: added converted attribute %s in fragment %s",
                                           targetAttributeModel.getJsonPath(), fragmentName));
            } else {
                // D.2.b - Add attribute as entity root element (not in a fragment)
                targetFeature.addProperty(convertedAttribute);
                LOGGER.trace(String
                        .format("Webservice data source plugin: added converted attribute %s in feature properties",
                                targetAttributeModel.getJsonPath()));
            }
        }

    }

    /**
     * Converts a file from source feature to target feature (out parameter).
     *
     * @param fileURLSourcePath JSON path to use in source feature to retrieve file URL
     * @param sourceFeature     Feature as returned by the webservice
     * @param targetFeature     target feature (out parameter)
     * @param label             found label for target feature (maybe null), for reporting
     * @param providerId        found providerId for target feature (maybe null), for reporting
     * @param featureIndex      index in page, for reporting
     */
    private void convertFile(String fileURLSourcePath, Feature sourceFeature, DataObjectFeature targetFeature,
            DataType fileType, String label, String providerId, int featureIndex) {
        // extract file URL as optional value
        String fileURL = getStringAttributeValueByPath(sourceFeature, fileURLSourcePath, featureIndex, label,
                                                       providerId, fileType.toString(), false);
        if (fileURL == null) {
            return; // NO URL
        }
        try {
            new URL(fileURL);
        } catch (MalformedURLException e) {
            LOGGER.warn(String
                    .format("Webservice data source plugin: Found invalid URL '%s' at path '%s' while converting feature #%d-%s(%s) %s",
                            fileURL, fileURLSourcePath, featureIndex, label, providerId, fileType),
                        e);
            report.addFeatureConversionError(featureIndex, label, providerId, FeatureConversionError
                    .getInvalidFileURLError(fileURL, fileType, fileURLSourcePath));
            return; // Invalid URL
        }

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (imagesDataTypes.contains(fileType)) {
            // Retrieve file type from URL
            String inferredMimeType = URLConnection.guessContentTypeFromName(fileURL);
            if (inferredMimeType == null) {
                // refuse converting file as pictures MIME type must be restricted to REGARDS supported ones
                LOGGER.warn(String
                        .format("Webservice data source plugin: Could not guess mime type from URL '%s' at path '%s' while converting feature #%d-%s(%s) %s",
                                fileURL, fileURLSourcePath, featureIndex, label, providerId, fileType));
                report.addFeatureConversionError(featureIndex, label, providerId, FeatureConversionError
                        .getMimeTypeNotFoundError(fileURL, fileType, fileURLSourcePath));
                return;
            } else if (!imagesAllowedMimeTypes.contains(inferredMimeType)) {
                LOGGER.warn(String
                        .format("Webservice data source plugin: Found a not supported MIME type '%s' for URL '%s' at path '%s' while converting feature #%d-%s(%s) %s",
                                inferredMimeType, fileURL, fileURLSourcePath, featureIndex, label, providerId,
                                fileType));
                report.addFeatureConversionError(featureIndex, label, providerId, FeatureConversionError
                        .getInvalidImageMimeTypeFound(fileURL, fileType, fileURLSourcePath, inferredMimeType));
                return;
            }
            mediaType = MediaType.parseMediaType(inferredMimeType);
        }
        // valid URL and MIME types, build the file.
        // Create file name: attempt to keep only last the part of URL
        String[] pathElements = fileURL.split("/");
        String filename = pathElements[pathElements.length - 1];
        // append file in feature
        targetFeature.getFiles()
                .put(fileType, DataFile.build(fileType, filename, fileURL, mediaType, Boolean.TRUE, Boolean.TRUE));
    }

    /**
     * Simple delegate for string features parsed separately
     *
     * @param sourceFeature       source feature, where the value should be retrieved
     * @param path                path in feature properties (should not start with properties)
     * @param featureIndex        feature index in features list (for report)
     * @param label               feature label (for report)
     * @param targetAttributePath target attribute JSON path
     * @param mandatory           is attribute mandatory?
     * @return found value or null
     */
    private String getStringAttributeValueByPath(Feature sourceFeature, String path, int featureIndex, String label,
            String providerId, String targetAttributePath, boolean mandatory) {
        Object valueByPath = getAttributeValueByPath(sourceFeature, path, featureIndex, label, providerId,
                                                     targetAttributePath, mandatory);
        return valueByPath == null ? null : valueByPath.toString();
    }

    /**
     * Returns a value by its path in feature properties. It adds reports errors when:
     * - Value is mandatory but wasnt found
     * - An element that isn't a map is used as parent in current path
     *
     * @param sourceFeature       source feature, where the value should be retrieved
     * @param path                path in feature properties (should not start with properties)
     * @param featureIndex        feature index in features list (for report)
     * @param label               feature label (for report)
     * @param providerId          feature provider ID (for report)
     * @param targetAttributePath target attribute JSON path
     * @param mandatory           is attribute mandatory?
     * @return found value or null
     */
    private Object getAttributeValueByPath(Feature sourceFeature, String path, int featureIndex, String label,
            String providerId, String targetAttributePath, boolean mandatory) {
        // 1. Is path a fixed value?
        if (isFixedValue(path)) {
            // 1.a - yes: exit using user input as constant value
            return getFixedValue((path));
        }
        // 1.b - Not a fixed value, extract value from source feature using path
        Object currentValue = null;
        List<String> pathElements = Arrays.asList(path.split("\\."));
        for (int i = 0; i < pathElements.size(); i++) {
            String currentPathElement = pathElements.get(i);
            if (i == 0) {
                // initialization: search in properties
                currentValue = sourceFeature.getProperties().get(currentPathElement);
            } else if (currentValue != null) {
                // loop case: check previous element is a map before search lower value
                if (currentValue instanceof Map) {
                    currentValue = ((Map) currentValue).get(currentPathElement);
                } else {
                    // error case: it was not possible to get in sub level
                    LOGGER.warn(String
                            .format("When converting feature at %d, attribute %s value could not be extracted from path '%s' as value at '%s' (%s) is not a Json object.",
                                    featureIndex, targetAttributePath, path, pathElements.get(i - 1),
                                    currentValue.toString()));
                    report.addFeatureConversionError(featureIndex, label, providerId, FeatureConversionError
                            .getInvalidValueOnPathError(targetAttributePath, mandatory, path, pathElements.get(i - 1),
                                                        currentValue));
                    return null;
                }
            }
        }
        if (mandatory && (currentValue == null)) {
            report.addFeatureConversionError(featureIndex, label, providerId, FeatureConversionError
                    .getMandatoryAttributeNotFoundError(targetAttributePath, path));
        }
        return currentValue; // return last found value
    }

}
