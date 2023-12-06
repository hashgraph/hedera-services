package com.swirlds.platform.config;

import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

public class Converters {
    private Converters() {
    }

    public static @NonNull TaskSchedulerType convertTaskSchedulerType(@NonNull final String value)
            throws IllegalArgumentException, NullPointerException {
        return TaskSchedulerType.valueOf(Objects.requireNonNull(value));
    }
}
