module com.hedera.block.node {
//    requires transitive com.hedera.node.config;
    requires io.grpc;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;
    requires static java.compiler; // javax.annotation.processing.Generated
}
