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

package com.hedera.services.bdd.spec.utilops.embedded;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.protoToPbj;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTokenId;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

/**
 * An operation that allows the test author to directly mutate a token in an embedded state.
 */
public class MutateTokenOp extends UtilOp {
    private final String token;
    private final Consumer<Token.Builder> mutation;

    /**
     * Constructs the operation.
     * @param token the identifier of the token to mutate
     * @param mutation the mutation to apply to the token
     */
    public MutateTokenOp(@NonNull final String token, @NonNull final Consumer<Token.Builder> mutation) {
        this.token = requireNonNull(token);
        this.mutation = requireNonNull(mutation);
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        final var tokens = spec.embeddedTokensOrThrow();
        final var targetId = protoToPbj(asTokenId(token, spec), TokenID.class);
        final var token = requireNonNull(tokens.get(targetId));
        final var builder = token.copyBuilder();
        mutation.accept(builder);
        tokens.put(targetId, builder.build());
        spec.commitEmbeddedState();
        return false;
    }
}
