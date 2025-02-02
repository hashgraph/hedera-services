package com.hedera.node.app.service.contract.impl.schemas;

import com.hedera.hapi.node.base.LambdaID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.lambda.LambdaState;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Set;

public class V061ContractSchema extends Schema {
    private static final int MAX_LAMBDA_STATES = 1_000_000_000;

    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(61).build();

    public static final String LAMBDA_STATES_KEY = "LAMBDA_STATES";

    public V061ContractSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.onDisk(LAMBDA_STATES_KEY, LambdaID.PROTOBUF, LambdaState.PROTOBUF, MAX_LAMBDA_STATES));
    }
}
