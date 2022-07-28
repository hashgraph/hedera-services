/*
 * Copyright (C) 2020-2021 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hedera.services.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.fee.SigValueObj;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiTokenUnpause extends HapiTxnOp<HapiTokenUnpause> {
    static final Logger log = LogManager.getLogger(HapiTokenUnpause.class);

    private String token;

    public HapiTokenUnpause(String token) {
        this.token = token;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenPause;
    }

    @Override
    protected HapiTokenUnpause self() {
        return this;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(final HapiApiSpec spec) throws Throwable {
        var tId = TxnUtils.asTokenId(token, spec);
        TokenUnpauseTransactionBody opBody =
                spec.txns()
                        .<TokenUnpauseTransactionBody, TokenUnpauseTransactionBody.Builder>body(
                                TokenUnpauseTransactionBody.class,
                                b -> {
                                    b.setToken(tId);
                                });
        return b -> b.setTokenUnpause(opBody);
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(final HapiApiSpec spec) {
        return spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls)::unpauseToken;
    }

    @Override
    protected List<Function<HapiApiSpec, Key>> defaultSigners() {
        return List.of(
                spec -> spec.registry().getKey(effectivePayer(spec)),
                spec -> spec.registry().getPauseKey(token));
    }

    @Override
    protected long feeFor(final HapiApiSpec spec, final Transaction txn, final int numPayerKeys)
            throws Throwable {
        return spec.fees()
                .forActivityBasedOp(
                        HederaFunctionality.TokenUnpause, this::usageEstimate, txn, numPayerKeys);
    }

    private FeeData usageEstimate(TransactionBody txn, SigValueObj svo) {
        UsageAccumulator accumulator = new UsageAccumulator();
        final var tokenUnpauseMeta = TOKEN_OPS_USAGE_UTILS.tokenUnpauseUsageFrom();
        final var baseTransactionMeta = new BaseTransactionMeta(txn.getMemoBytes().size(), 0);
        TokenOpsUsage tokenOpsUsage = new TokenOpsUsage();
        tokenOpsUsage.tokenUnpauseUsage(
                suFrom(svo), baseTransactionMeta, tokenUnpauseMeta, accumulator);
        return AdapterUtils.feeDataFrom(accumulator);
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("token", token);
    }
}
