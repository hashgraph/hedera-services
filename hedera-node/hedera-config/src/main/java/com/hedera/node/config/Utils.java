package com.hedera.node.config;

import com.swirlds.common.config.reflection.ConfigReflectionUtils;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Utilities for working with config. Ideally this class would not exist, but such functionality would be built into
 * {@link Configuration} itself.
 */
public final class Utils {
    private Utils() {
        // Do not instantiate
        throw new UnsupportedOperationException("Utility Class");
    }

    public static SortedMap<String, Object> networkProperties(@NonNull final Configuration configuration) {
        // Use reflection to get all fields annotated with @NetworkProperty. We do **not** want to actually use
        // reflection for this, it would be much better for there to be an annotation processor used by the config
        // system, and we would have some way to get this list from generated code instead of reflection.
        final var recordProperties = new TreeMap<String, Object>();
        configuration.getConfigDataTypes().forEach(configDataType -> {
            final var propertyNamePrefix = ConfigReflectionUtils.getNamePrefixForConfigDataRecord(configDataType);
            Arrays.stream(configDataType.getRecordComponents())
                    .filter(component -> component.getAnnotation(NetworkProperty.class) != null)
                    .forEach(component -> {
                final var name = ConfigReflectionUtils.getPropertyNameForConfigDataProperty(propertyNamePrefix, component);
                final var configData = configuration.getConfigData(configDataType);
                try {
                    recordProperties.put(name, component.getAccessor().invoke(configData));
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            });
        });

        return Collections.unmodifiableSortedMap(recordProperties);
    }
}
