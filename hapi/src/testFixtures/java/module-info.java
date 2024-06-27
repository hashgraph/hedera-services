module com.hedera.node.hapi.test.fixtures {
    exports com.hedera.node.hapi.fixtures;
    exports com.hederahashgraph.api.proto.java;

    requires transitive com.hedera.node.hapi;
    requires transitive com.google.protobuf;
    requires transitive com.hedera.pbj.runtime;
    requires transitive io.grpc.stub;
    requires transitive io.grpc;
    requires com.google.common;
    requires io.grpc.protobuf;
    requires static com.github.spotbugs.annotations;
}
