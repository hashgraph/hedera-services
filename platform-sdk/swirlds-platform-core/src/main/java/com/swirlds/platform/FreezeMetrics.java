package com.swirlds.platform;

import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;

import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Used for creating freeze metrics.
 */
public final class FreezeMetrics {

    private FreezeMetrics() {

    }

    /**
     * Register freeze metrics.
     *
     * @param metrics                   the metrics engine
     * @param freezeManager             the freeze manager
     * @param startUpEventFrozenManager the start up event frozen manager
     */
    public static void registerFreezeMetrics(
            @NonNull final Metrics metrics,
            @NonNull final FreezeManager freezeManager,
            @NonNull final StartUpEventFrozenManager startUpEventFrozenManager) {

        metrics.getOrCreate(new FunctionGauge.Config<>(
                INTERNAL_CATEGORY,
                "isEvFrozen",
                Boolean.class,
                () -> freezeManager.isEventCreationFrozen()
                        || startUpEventFrozenManager.isEventCreationPausedAfterStartUp())
                .withDescription("is event creation frozen")
                .withUnit("is event creation frozen"));

    }

}
