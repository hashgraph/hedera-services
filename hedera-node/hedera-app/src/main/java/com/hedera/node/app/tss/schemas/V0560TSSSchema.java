package com.hedera.node.app.tss.schemas;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.node.tss.TssMessageTransactionBody;
import com.hedera.hapi.node.tss.TssVoteTransactionBody;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;

/**
 * Schema for the TSS service.
 */
public class V0560TSSSchema extends Schema {
    private static final Logger log = LogManager.getLogger(V0560TSSSchema.class);
    public static final String TSS_MESSAGE_MAP_KEY = "TSS_MESSAGES";
    public static final String TSS_VOTE_MAP_KEY = "TSS_VOTES";
    /**
     * This will at most be equal to the number of nodes in the network.
     */
    private static final long MAX_TSS_MESSAGES = 65_536L;
    /**
     * This will at most be equal to the number of nodes in the network.
     */
    private static final long MAX_TSS_VOTES = 65_536L;

    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(56).patch(0).build();
    /**
     * Create a new instance
     */
    public V0560TSSSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.onDisk(TSS_MESSAGE_MAP_KEY, TssMessageMapKey.PROTOBUF, TssMessageTransactionBody.PROTOBUF, MAX_TSS_MESSAGES),
                StateDefinition.onDisk(TSS_VOTE_MAP_KEY, TssVoteMapKey.PROTOBUF, TssVoteTransactionBody.PROTOBUF, MAX_TSS_VOTES));
    }
}
