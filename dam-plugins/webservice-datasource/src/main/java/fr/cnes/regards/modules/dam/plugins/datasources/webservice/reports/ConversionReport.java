package fr.cnes.regards.modules.dam.plugins.datasources.webservice.reports;


import fr.cnes.regards.framework.notification.NotificationLevel;
import fr.cnes.regards.modules.templates.service.TemplateService;
import freemarker.template.TemplateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Holds conversion errors by feature. It produces a notification based on freemarker templating
 *
 * @author Raphaël Mechali
 */
public class ConversionReport {

    /**
     * Class logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ConversionReport.class);

    /**
     * Maps feature index to corresponding feature errors (bloking)
     */
    private final SortedMap<Integer, FeatureErrors> blockingErrors = new TreeMap<>();
    /**
     * Maps feature index to corresponding feature errors (non blocking)
     */
    private final SortedMap<Integer, FeatureErrors> nonBlockingErrors = new TreeMap<>();

    /**
     * Adds a feature conversion error
     *
     * @param featureIndex feature index
     * @param featureLabel feature label when found
     * @param providerId   feature provider id when found
     * @param error        error to store in report
     */
    public void addFeatureConversionError(int featureIndex, String featureLabel, String providerId, FeatureConversionError error) {
        // put the error in the right feature of the right map
        SortedMap<Integer, FeatureErrors> targetErrorsMap = error.isBlocking() ? blockingErrors : nonBlockingErrors;
        FeatureErrors targetFeature = targetErrorsMap.get(featureIndex);
        if (targetFeature == null) {
            targetFeature = new FeatureErrors(featureIndex, featureLabel, providerId);
            targetErrorsMap.put(featureIndex, targetFeature);
        }
        targetFeature.addError(error);
    }

    public SortedMap<Integer, FeatureErrors> getBlockingErrors() {
        return blockingErrors;
    }

    public SortedMap<Integer, FeatureErrors> getNonBlockingErrors() {
        return nonBlockingErrors;
    }

    /**
     * Is there a blocking error for feature as parameter
     *
     * @param featureIndex feature index
     * @return true when there is a blocking error
     */
    public boolean hasBlockingErrors(int featureIndex) {
        return this.blockingErrors.get(featureIndex) != null; // note: length necessary >= 1 since it could only be added with an initial error (addFeatureConversionError)
    }

    /**
     * Is there any blocking error in current report?
     *
     * @return true when there are blocking errors
     */
    public boolean hasBlockingErrors() {
        return !this.blockingErrors.isEmpty(); // note: each element errors count necessary >= 1
    }

    /**
     * Is there any non blocking error in current report?
     *
     * @return true when there are non blocking errors
     */
    public boolean hasNonBlockingErrors() {
        return !this.nonBlockingErrors.isEmpty(); // note: each element errors count necessary >= 1
    }

    /**
     * Is there any error in current report?
     *
     * @return true when there are errors
     */
    public boolean hasErrors() {
        return hasBlockingErrors() || hasNonBlockingErrors();
    }

    /**
     * Expresses reports errors as a notification level
     *
     * @return corresponding notification level or null
     */
    public NotificationLevel getNotificationLevel() {
        if (hasBlockingErrors()) {
            return NotificationLevel.ERROR;
        }
        if (hasNonBlockingErrors()) {
            return NotificationLevel.WARNING;
        }
        return null;
    }

    /**
     * Returns notification text for current report
     *
     * @param pageURL         page of the webservice for which the conversion report is emitted
     * @param templateService template service for rendering
     * @return produced textual report for notification or null if any error happened
     */
    public String buildNotificationReport(String pageURL, TemplateService templateService) {
        // create template values
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("pageURL", pageURL);
        dataModel.put("blockingErrors", blockingErrors.values());
        dataModel.put("nonBlockingErrors", nonBlockingErrors.values());
        try {
            return templateService.render(ReportTemplateConfiguration.CONVERSION_REPORT_KEY, dataModel);
        } catch (TemplateException e) {
            LOGGER.error("Error during notification template render", e);
        }
        return null;
    }

}
