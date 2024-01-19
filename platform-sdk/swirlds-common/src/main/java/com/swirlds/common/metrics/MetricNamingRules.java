/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.metrics;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is a simple rule engine for naming rules on the metric api.
 * It can be expended adding more {@code Rule} instances to the enum.
 * Each rule applies over a {@link MetricConfig} and determines if the metric offends the rule or not.
 * In case of offending, it is logged using {@code EXCEPTION#getMarker} so JRS validators will fail.
 */
public final class MetricNamingRules {
    private static final Logger log = LogManager.getLogger(MetricNamingRules.class);

    private static final class InstanceHolder {
        private static final MetricNamingRules INSTANCE = new MetricNamingRules();
    }

    private static class Constants {
        /**
         * It is preferred that we use only one normalized version of the unit
         */
        private static final Predicate<String> NON_NORMALIZED_UNITS =
                Pattern.compile(".*_+(sec|ms|millis|us|mb|gb|hz|round)$").asMatchPredicate();
        private static final Predicate<String> NON_ALPHA =
                Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*").asMatchPredicate().negate();
        /**
         * The list of all metric classes that are allowed not to define a unit
         */
        private static final List<Class<? extends Metric>> UNITLESS_METRICS = List.of();
        private static final Predicate<MetricConfig<?, ?>> ALL_METRICS = c -> true;
        private static final Predicate<MetricConfig<?, ?>> IS_NOT_UNITLESS_METRIC =
                c -> !UNITLESS_METRICS.contains(c.getResultClass());
    }

    private MetricNamingRules() {}

    public static @NonNull MetricNamingRules getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public <T extends Metric> void validate(@NonNull final MetricConfig<T, ?> config) {
        for (Rule rule : Rule.values()) {
            if (rule.isApplicable(config) && rule.isOffendedBy(config)) {
                log.error(
                        EXCEPTION.getMarker(),
                        "Rule:{} failed for metric(name:{},category:{},unit{}):{}",
                        rule,
                        config.getName(),
                        config.getCategory(),
                        config.getUnit(),
                        rule.getReason());
            }
        }
    }

    private enum Rule {
        INVALID_CHARS(/*Rule must contain valid chars*/
                mv -> Constants.NON_ALPHA.test(String.join(mv.name(), mv.category(), mv.unit())),
                Constants.ALL_METRICS,
                "Use a-z;A-Z;0-9 and _"),
        UNNORMALIZED_UNIT(/*Only one version for units is preferred*/
                mv -> Constants.NON_NORMALIZED_UNITS.test(mv.unit()),
                Constants.ALL_METRICS,
                "Use normalized version for unit: "
                        + "seconds|count|milliseconds|microseconds|bytes|megabytes|gigabytes|hertz|rounds"),
        UNIT_IS_MANDATORY(/*Some metrics require a mandatory unit*/
                mv -> mv.unit() == null || mv.unit().isBlank(),
                Constants.IS_NOT_UNITLESS_METRIC,
                "Metric should have a defined unit");

        private final Predicate<MetricValues> offendingPredicate;
        private final Predicate<MetricConfig<?, ?>> applicablePredicate;
        private final String reason;

        Rule(
                final Predicate<MetricValues> offendingPredicate,
                final Predicate<MetricConfig<?, ?>> applicablePredicate,
                final String reason) {
            this.offendingPredicate = offendingPredicate;
            this.applicablePredicate = applicablePredicate;
            this.reason = reason;
        }

        public boolean isOffendedBy(@NonNull final MetricConfig<?, ?> config) {
            return offendingPredicate.test(new MetricValues(config.getName(), config.getCategory(), config.getUnit()));
        }

        public boolean isApplicable(@NonNull final MetricConfig<?, ?> config) {
            return applicablePredicate.test(config);
        }

        public String getReason() {
            return reason;
        }

        private record MetricValues(String name, String category, String unit) {}
    }
}
