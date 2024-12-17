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

package com.hedera.services.bdd.spec.transactions.token;

import static com.hedera.node.app.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.hedera.node.app.hapi.fees.usage.TxnUsageEstimator;
import com.hedera.node.app.hapi.fees.usage.token.TokenDissociateUsage;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.TokenCancelAirdropTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class HapiTokenCancelAirdrop extends HapiTxnOp<HapiTokenCancelAirdrop> {

    private final List<Function<HapiSpec, PendingAirdropId>> pendingAirdropIds;

    @SafeVarargs
    @SuppressWarnings("varargs")
    public HapiTokenCancelAirdrop(final Function<HapiSpec, PendingAirdropId>... pendingAirdropIds) {
        this.pendingAirdropIds = List.of(pendingAirdropIds);
    }

    @Override
    protected HapiTokenCancelAirdrop self() {
        return this;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenCancelAirdrop;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable {
        final var pendingAirdrops =
                pendingAirdropIds.stream().map(f -> f.apply(spec)).toList();
        final TokenCancelAirdropTransactionBody opBody = spec.txns()
                .<TokenCancelAirdropTransactionBody, TokenCancelAirdropTransactionBody.Builder>body(
                        TokenCancelAirdropTransactionBody.class, b -> b.addAllPendingAirdrops(pendingAirdrops));
        return builder -> builder.setTokenCancelAirdrop(opBody);
    }

    public static Function<HapiSpec, PendingAirdropId> pendingAirdrop(
            final String sender, final String receiver, final String token) {
        return spec -> {
            final var tokenID = TxnUtils.asTokenId(token, spec);
            final var senderID = TxnUtils.asId(sender, spec);
            final var receiverID = TxnUtils.asId(receiver, spec);
            return PendingAirdropId.newBuilder()
                    .setFungibleTokenType(tokenID)
                    .setSenderId(senderID)
                    .setReceiverId(receiverID)
                    .build();
        };
    }

    public static Function<HapiSpec, PendingAirdropId> pendingNFTAirdrop(
            final String sender, final String receiver, final String token, final long serialNum) {
        return spec -> {
            final var tokenID = TxnUtils.asTokenId(token, spec);
            final var senderID = TxnUtils.asId(sender, spec);
            final var receiverID = TxnUtils.asId(receiver, spec);
            return PendingAirdropId.newBuilder()
                    .setNonFungibleToken(NftID.newBuilder()
                            .setTokenID(tokenID)
                            .setSerialNumber(serialNum)
                            .build())
                    .setSenderId(senderID)
                    .setReceiverId(receiverID)
                    .build();
        };
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        return Stream.concat(
                        Stream.of(spec -> spec.registry().getKey(effectivePayer(spec))),
                        pendingAirdropIds.stream()
                                .map(pendingAirdropId -> (Function<HapiSpec, Key>) spec -> spec.registry()
                                        .getKey(spec.registry()
                                                .getAccountIdName(pendingAirdropId
                                                        .apply(spec)
                                                        .getSenderId()))))
                .toList();
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        return spec.fees()
                .forActivityBasedOp(HederaFunctionality.TokenCancelAirdrop, this::usageEstimate, txn, numPayerKeys);
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper().add("pendingAirdropIds", pendingAirdropIds);
    }

    private FeeData usageEstimate(final TransactionBody txn, final SigValueObj svo) {
        return TokenDissociateUsage.newEstimate(txn, new TxnUsageEstimator(suFrom(svo), txn, ESTIMATOR_UTILS))
                .get();
    }
}
