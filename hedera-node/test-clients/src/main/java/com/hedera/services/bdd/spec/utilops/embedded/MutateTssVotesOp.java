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

import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_VOTE_MAP_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.swirlds.state.spi.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

public class MutateTssVotesOp extends UtilOp {
    private final Consumer<WritableKVState<TssVoteMapKey, TssVoteTransactionBody>> mutation;

    public MutateTssVotesOp(@NonNull final Consumer<WritableKVState<TssVoteMapKey, TssVoteTransactionBody>> mutation) {
        this.mutation = requireNonNull(mutation);
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        requireNonNull(spec);
        final var state = spec.embeddedStateOrThrow();
        mutation.accept(state.getWritableStates(TssBaseService.NAME).get(TSS_VOTE_MAP_KEY));
        spec.commitEmbeddedState();
        return false;
    }
}
