package com.swirlds.common.test.metrics.extensions;

import static com.swirlds.common.test.metrics.extensions.TestPhases.BAR;
import static com.swirlds.common.test.metrics.extensions.TestPhases.BAZ;
import static com.swirlds.common.test.metrics.extensions.TestPhases.FOO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.MetricConfig;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric.Config;
import com.swirlds.common.metrics.extensions.PhaseTimer;
import com.swirlds.common.metrics.extensions.PhaseTimerBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PhaseTimer Tests")
class PhaseTimerTests {

    // <T extends Metric> T getOrCreate(final MetricConfig<T, ?> config)

    @NonNull
    private Metrics buildMockMetrics(@NonNull final Function<MetricConfig<?, ?>, Metric> getOrCreateMetric) {
        final Metrics metrics = mock(Metrics.class);

        when(metrics.getOrCreate(any())).thenAnswer(invocation -> {
            final MetricConfig<?, ?> config = invocation.getArgument(0);
            return getOrCreateMetric.apply(config);
        });

        return metrics;
    }

    @Test
    @DisplayName("No Registered Metrics Test")
    void noRegisteredMetricsTest() {
        final Set<MetricConfig<?, ?>> registeredConfigs = new HashSet<>();
        final Metrics metrics = buildMockMetrics(config -> {
            registeredConfigs.add(config);
            return mock(Metric.class);
        });

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withMetrics(metrics)
                .build();
        final FakeTime time = new FakeTime();

        final PhaseTimer<TestPhases> timer = new PhaseTimerBuilder<>(platformContext, time, "test", TestPhases.class)
                .build();

        // No metrics should have been registered
        assertEquals(0, registeredConfigs.size());

        // Even though we haven't registered metrics, it shouldn't cause problems if we update the phase
        timer.activatePhase(FOO);
        timer.activatePhase(BAR);
        timer.activatePhase(BAZ);
        timer.activatePhase(BAZ);
        timer.activatePhase(BAR);
        timer.activatePhase(FOO);
    }

    @Test
    @DisplayName("Only Fractional Metrics Enabled Test")
    void onlyFractionalMetricsEnabledTest() {
        final Set<MetricConfig<?, ?>> registeredConfigs = new HashSet<>();
        final Metrics metrics = buildMockMetrics(config -> {
            registeredConfigs.add(config);
            return mock(Metric.class);
        });

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withMetrics(metrics)
                .build();
        final FakeTime time = new FakeTime();

        final PhaseTimer<TestPhases> timer = new PhaseTimerBuilder<>(platformContext, time, "test", TestPhases.class)
                .enableFractionalMetrics()
                .build();

        assertEquals(3, registeredConfigs.size());
        final Set<String> expectedNames = new HashSet<>(Set.of(
                "test_fraction_FOO",
                "test_fraction_BAR",
                "test_fraction_BAZ"));
        for (final MetricConfig<?, ?> config : registeredConfigs) {
            assertEquals("test", config.getCategory());
            assertEquals(FunctionGauge.Config.class, config.getClass());
            assertTrue(expectedNames.remove(config.getName()));
        }

        // Even though we haven't all metric types, it shouldn't cause problems if we update the phase
        timer.activatePhase(FOO);
        timer.activatePhase(BAR);
        timer.activatePhase(BAZ);
        timer.activatePhase(BAZ);
        timer.activatePhase(BAR);
        timer.activatePhase(FOO);
    }
}
