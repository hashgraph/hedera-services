package com.swirlds.config.api;

import com.swirlds.config.api.validation.ConfigPropertyConstraint;
import java.util.List;

/**
 * Represents a strongly typed configuration value with associated metadata.
 *
 * @param <T> the type of the configuration value
 */
public interface ConfigValue<T> {

    /**
     * Retrieves the value of the configuration property.
     *
     * @return the value of the configuration property
     */
    T getValue();

    /**
     * Retrieves the name of the configuration property.
     *
     * @return the name of the configuration property
     */
    String getPropertyName();

    /**
     * Retrieves the type of the configuration value.
     *
     * @return the class representing the type of the configuration value
     */
    TypeReference<T> getValueType();

    /**
     * Checks if the configuration property has any constraints.
     *
     * @return true if the configuration property has constraints, false otherwise
     */
    boolean hasConstraints();

    /**
     * Retrieves the list of constraints associated with the configuration property.
     *
     * @return a list of constraints associated with the configuration property
     */
    List<ConfigPropertyConstraint<T>> getConstraints();
}
