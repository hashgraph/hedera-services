module com.swirlds.base {
    exports com.swirlds.base;
    exports com.swirlds.base.function;
    exports com.swirlds.base.state;
    exports com.swirlds.base.time;
    exports com.swirlds.base.units;
    exports com.swirlds.base.utility;
    exports com.swirlds.base.context;
    exports com.swirlds.base.context.internal to
            com.swirlds.base.test.fixtures,
            com.swirlds.logging;
    exports com.swirlds.base.internal to
            com.swirlds.base.test.fixtures,
            com.swirlds.metrics.api,
            com.swirlds.config.api,
            com.swirlds.config.api.test.fixtures,
            com.swirlds.config.impl,
            com.swirlds.config.exceptions,
            com.swirlds.config.extensions.test.fixtures,
            com.swirlds.logging,
            com.swirlds.logging.test.fixtures;

    requires static com.github.spotbugs.annotations;
}
