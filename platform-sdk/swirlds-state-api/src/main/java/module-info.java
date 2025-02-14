// SPDX-License-Identifier: Apache-2.0
module com.swirlds.state.api {
    exports com.swirlds.state;
    exports com.swirlds.state.spi;
    exports com.swirlds.state.lifecycle.info;
    exports com.swirlds.state.spi.metrics;
    exports com.swirlds.state.lifecycle;

    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires static transitive com.github.spotbugs.annotations;
}
