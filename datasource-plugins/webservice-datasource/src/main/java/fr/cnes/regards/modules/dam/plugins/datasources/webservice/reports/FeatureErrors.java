package fr.cnes.regards.modules.dam.plugins.datasources.webservice.reports;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds a single feature errors
 *
 * @author Raphaël Mechali
 */
public class FeatureErrors {

    /**
     * Feature index
     */
    private int index;
    /**
     * Feature label if found
     */
    private String label;
    /**
     * Feature  provider Id if found
     */
    private String providerId;
    /**
     * Feature errors
     */
    private List<FeatureConversionError> errors = new ArrayList<>();

    /**
     * Constructor
     *
     * @param index      index in page
     * @param label      label if any could be found
     * @param providerId provider id if any could be found
     */
    public FeatureErrors(int index, String label, String providerId) {
        this.index = index;
        this.label = label;
        this.providerId = providerId;
    }

    public int getIndex() {
        return index;
    }

    public String getLabel() {
        return label;
    }

    public String getProviderId() {
        return providerId;
    }

    public List<FeatureConversionError> getErrors() {
        return errors;
    }

    /**
     * Adds a feature error
     *
     * @param error -
     */
    public void addError(FeatureConversionError error) {
        this.errors.add(error);
    }

}
