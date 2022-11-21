/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class HapiTokenUnfreeze extends HapiTxnOp<HapiTokenUnfreeze> {
    private String token;
    private String account;

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenUnfreezeAccount;
    }

    public HapiTokenUnfreeze(String token, String account) {
        this.token = token;
        this.account = account;
    }

    @Override
    protected HapiTokenUnfreeze self() {
        return this;
    }

    @Override
    protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        return spec.fees()
                .forActivityBasedOp(
                        HederaFunctionality.TokenUnfreezeAccount,
                        this::usageEstimate,
                        txn,
                        numPayerKeys);
    }

    private FeeData usageEstimate(TransactionBody txn, SigValueObj svo) {
        UsageAccumulator accumulator = new UsageAccumulator();
        final var tokenUnfreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenUnfreezeUsageFrom();
        final var baseTransactionMeta = new BaseTransactionMeta(txn.getMemoBytes().size(), 0);
        TokenOpsUsage tokenOpsUsage = new TokenOpsUsage();
        tokenOpsUsage.tokenUnfreezeUsage(
                suFrom(svo), baseTransactionMeta, tokenUnfreezeMeta, accumulator);
        return AdapterUtils.feeDataFrom(accumulator);
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
        var aId = TxnUtils.asId(account, spec);
        var tId = TxnUtils.asTokenId(token, spec);
        TokenUnfreezeAccountTransactionBody opBody =
                spec.txns()
                        .<TokenUnfreezeAccountTransactionBody,
                                TokenUnfreezeAccountTransactionBody.Builder>
                                body(
                                        TokenUnfreezeAccountTransactionBody.class,
                                        b -> {
                                            b.setAccount(aId);
                                            b.setToken(tId);
                                        });
        return b -> b.setTokenUnfreeze(opBody);
    }

    @Override
    protected List<Function<HapiApiSpec, Key>> defaultSigners() {
        return List.of(
                spec -> spec.registry().getKey(effectivePayer(spec)),
                spec -> spec.registry().getFreezeKey(token));
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
        return spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls)::unfreezeTokenAccount;
    }

    @Override
    protected void updateStateOf(HapiApiSpec spec) {}

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper =
                super.toStringHelper().add("token", token).add("account", account);
        return helper;
    }
}
