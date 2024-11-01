open module com.swirlds.state.api.test.fixtures {

    requires transitive com.swirlds.state.api;
    requires transitive com.hedera.pbj.runtime;

    requires static transitive com.github.spotbugs.annotations;

    exports com.swirlds.state.test.fixtures;
}
