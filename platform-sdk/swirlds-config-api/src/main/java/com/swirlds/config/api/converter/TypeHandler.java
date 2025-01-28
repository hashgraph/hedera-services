package com.swirlds.config.api.converter;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Interface for handling type conversions between strings and typed values.
 * Implementations of this interface provide methods to convert from a string to a specific type and vice versa.
 *
 * @param <T> the type of the value to be converted
 */
public interface TypeHandler<T> {

    /**
     * Converts the given string value to the specified type.
     *
     * @param value the string value to be converted
     * @return the converted value of the specified type
     * @throws IllegalArgumentException if the string value cannot be converted to the specified type
     */
    @Nullable
    T fromString(@NonNull String value) throws IllegalArgumentException;

    /**
     * Converts the given value of the specified type to a string.
     *
     * @param value the value to be converted to a string
     * @return the string representation of the value
     */
    @NonNull
    String toString(@NonNull T value);
}