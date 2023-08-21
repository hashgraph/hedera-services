package com.swirlds.common.metrics.extensions;

import static com.swirlds.common.units.TimeUnit.UNIT_MICROSECONDS;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.units.TimeUnit;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Set;

/**
 * A fluent style builder for a {@link PhaseTimer}.
 */
public class PhaseTimerBuilder<T extends Enum<T>> {

    private final PlatformContext platformContext;
    private final Time time;
    private final Set<T> phases;
    private final String metricsCategory;

    private String metricsNamePrefix;
    private T initialPhase;
    private boolean fractionMetricsEnabled = true;
    private boolean absoluteTimeMetricsEnabled = true;
    private TimeUnit absoluteTimeUnit = UNIT_MICROSECONDS;

    /**
     * Create a new {@link PhaseTimerBuilder} instance.
     *
     * @param platformContext the platform context
     * @param time            the time provider
     * @param metricsCategory the metrics category
     * @param phases          the set of phases that the phase timer will be tracking
     */
    public PhaseTimerBuilder(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final String metricsCategory,
            @NonNull final Set<T> phases) { // TODO can we pass an enum class directly?

        this.platformContext = Objects.requireNonNull(platformContext);
        this.time = Objects.requireNonNull(time);
        this.metricsCategory = Objects.requireNonNull(metricsCategory);
        this.phases = Objects.requireNonNull(phases);
    }

    /**
     * Build the {@link PhaseTimer} instance.
     *
     * @return the {@link PhaseTimer} instance
     */
    @NonNull
    public PhaseTimer<T> build() {
        return new PhaseTimer<>(this);
    }

    /**
     * Set the prefix for the metrics names created by this object. If not set, a default is generated.
     *
     * @param metricsNamePrefix the prefix for the metrics names created by this object
     * @return this
     */
    @NonNull
    public PhaseTimerBuilder<T> setMetricsNamePrefix(@NonNull final String metricsNamePrefix) {
        this.metricsNamePrefix = Objects.requireNonNull(metricsNamePrefix);
        return this;
    }

    /**
     * Set the initial phase. If not set, the phase with ordinal 0 is used.
     *
     * @param initialPhase the initial phase
     * @return this
     */
    @NonNull
    public PhaseTimerBuilder<T> setInitialPhase(@NonNull final T initialPhase) {
        this.initialPhase = initialPhase;
        return this;
    }

    /**
     * Disable fractional networks. Enabled by default.
     *
     * @return this
     */
    @NonNull
    public PhaseTimerBuilder<T> disableFractionalMetrics() {
        this.fractionMetricsEnabled = false;
        return this;
    }

    /**
     * Disable absolute time metrics. Enabled by default.
     *
     * @return this
     */
    @NonNull
    public PhaseTimerBuilder<T> disableAbsoluteTimeMetrics() {
        this.absoluteTimeMetricsEnabled = false;
        return this;
    }

    /**
     * Set the unit for absolute time metrics. If not set, microseconds are used.
     *
     * @param absoluteTimeUnit the unit for absolute time metrics
     * @return this
     */
    @NonNull
    public PhaseTimerBuilder<T> setAbsoluteTimeUnit(@NonNull final TimeUnit absoluteTimeUnit) {
        this.absoluteTimeUnit = Objects.requireNonNull(absoluteTimeUnit);
        return this;
    }

    /**
     * Get the platform context.
     *
     * @return the platform context
     */
    @NonNull
    PlatformContext getPlatformContext() {
        return platformContext;
    }

    /**
     * Get the time provider.
     *
     * @return the time provider
     */
    @NonNull
    Time getTime() {
        return time;
    }

    /**
     * Get the set of phases.
     *
     * @return the set of phases
     */
    @NonNull
    Set<T> getPhases() {
        return phases;
    }

    /**
     * Get the metrics category.
     *
     * @return the metrics category
     */
    @NonNull
    String getMetricsCategory() {
        return metricsCategory;
    }

    /**
     * Get the metrics name prefix.
     *
     * @return the metrics name prefix
     */
    @NonNull
    String getMetricsNamePrefix() {
        if (metricsNamePrefix == null) {
            // No metrics prefix provided, generate a default.
            return "TODO"; // TODO
        }
        return metricsNamePrefix;
    }

    /**
     * Get the initial phase.
     *
     * @return the initial phase
     */
    T getInitialPhase() {
        if (initialPhase == null) {
            return initialPhase;
        }
        return null; // TODO return ordinal 0
    }

    /**
     * Get whether fractional metrics are enabled.
     *
     * @return whether fractional metrics are enabled
     */
    boolean areFractionMetricsEnabled() {
        return fractionMetricsEnabled;
    }

    /**
     * Get whether absolute time metrics are enabled.
     *
     * @return whether absolute time metrics are enabled
     */
    boolean areAbsoluteTimeMetricsEnabled() {
        return absoluteTimeMetricsEnabled;
    }

    /**
     * Get the unit for absolute time metrics.
     *
     * @return the unit for absolute time metrics
     */
    @NonNull
    TimeUnit getAbsoluteTimeUnit() {
        return absoluteTimeUnit;
    }
}
