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
    exports com.swirlds.base.concurrent;

    requires static com.github.spotbugs.annotations;
}
