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

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.toPbj;
import static com.hedera.node.app.service.token.TokenService.NAME;
import static com.hedera.node.app.service.token.impl.schemas.V0530TokenSchema.AIRDROPS_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.swirlds.state.spi.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

public class ViewPendingAirdropOp extends UtilOp {
    private final String tokenName;
    private final String senderName;
    private final String receiverName;
    private final Consumer<AccountPendingAirdrop> observer;

    public ViewPendingAirdropOp(
            @NonNull final String tokenName,
            @NonNull final String senderName,
            @NonNull final String receiverName,
            @NonNull final Consumer<AccountPendingAirdrop> observer) {
        this.tokenName = requireNonNull(tokenName);
        this.receiverName = requireNonNull(receiverName);
        this.senderName = requireNonNull(senderName);
        this.observer = requireNonNull(observer);
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        final var state = spec.embeddedStateOrThrow();
        final var readableStates = state.getReadableStates(NAME);
        final ReadableKVState<PendingAirdropId, AccountPendingAirdrop> readableAirdropState =
                readableStates.get(AIRDROPS_KEY);
        final var pendingAirdropId = PendingAirdropId.newBuilder()
                .receiverId(toPbj(TxnUtils.asId(receiverName, spec)))
                .senderId(toPbj(TxnUtils.asId(senderName, spec)))
                .fungibleTokenType(toPbj(TxnUtils.asTokenId(tokenName, spec)))
                .build();
        final var accountPendingAirdrop = readableAirdropState.get(pendingAirdropId);
        observer.accept(accountPendingAirdrop);
        return false;
    }
}
