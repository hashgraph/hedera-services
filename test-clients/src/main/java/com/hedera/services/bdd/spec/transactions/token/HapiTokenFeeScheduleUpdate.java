/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;

import com.google.common.base.MoreObjects;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.queries.token.HapiGetTokenInfo;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.services.usage.token.meta.ExtantFeeScheduleContext;
import com.hedera.services.usage.token.meta.FeeScheduleUpdateMeta;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiTokenFeeScheduleUpdate extends HapiTxnOp<HapiTokenFeeScheduleUpdate> {
    private static final TokenOpsUsage TOKEN_OPS_USAGE = new TokenOpsUsage();

    static final Logger log = LogManager.getLogger(HapiTokenFeeScheduleUpdate.class);

    private String token;

    private final List<Function<HapiApiSpec, CustomFee>> feeScheduleSuppliers = new ArrayList<>();

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenUpdate;
    }

    public HapiTokenFeeScheduleUpdate(String token) {
        this.token = token;
    }

    public HapiTokenFeeScheduleUpdate withCustom(Function<HapiApiSpec, CustomFee> supplier) {
        feeScheduleSuppliers.add(supplier);
        return this;
    }

    @Override
    protected HapiTokenFeeScheduleUpdate self() {
        return this;
    }

    @Override
    protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        try {
            final TokenInfo info = lookupInfo(spec, token, log, loggingOff);
            final FeeCalculator.ActivityMetrics metricsCalc =
                    (_txn, svo) -> usageEstimate(_txn, svo, info);
            return spec.fees()
                    .forActivityBasedOp(TokenFeeScheduleUpdate, metricsCalc, txn, numPayerKeys);
        } catch (Throwable ignore) {
            return HapiApiSuite.ONE_HBAR;
        }
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
        var id = TxnUtils.asTokenId(token, spec);
        final var opBody =
                spec.txns()
                        .<TokenFeeScheduleUpdateTransactionBody,
                                TokenFeeScheduleUpdateTransactionBody.Builder>
                                body(
                                        TokenFeeScheduleUpdateTransactionBody.class,
                                        b -> {
                                            b.setTokenId(id);
                                            if (!feeScheduleSuppliers.isEmpty()) {
                                                for (var supplier : feeScheduleSuppliers) {
                                                    b.addCustomFees(supplier.apply(spec));
                                                }
                                            }
                                        });
        return b -> b.setTokenFeeScheduleUpdate(opBody);
    }

    @Override
    protected List<Function<HapiApiSpec, Key>> defaultSigners() {
        List<Function<HapiApiSpec, Key>> signers = new ArrayList<>();
        signers.add(spec -> spec.registry().getKey(effectivePayer(spec)));
        signers.add(
                spec -> {
                    final var registry = spec.registry();
                    return registry.hasFeeScheduleKey(token)
                            ? registry.getFeeScheduleKey(token)
                            : Key.getDefaultInstance();
                });
        return signers;
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
        return spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls)::updateTokenFeeSchedule;
    }

    @Override
    protected void updateStateOf(HapiApiSpec spec) {
        /* No-op */
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("token", token);
    }

    private FeeData usageEstimate(TransactionBody txn, SigValueObj svo, TokenInfo info) {
        final var op = txn.getTokenFeeScheduleUpdate();
        final var baseMeta = new BaseTransactionMeta(txn.getMemoBytes().size(), 0);
        final var effectiveNow = txn.getTransactionID().getTransactionValidStart().getSeconds();
        final var bytesToReprNew = TOKEN_OPS_USAGE.bytesNeededToRepr(op.getCustomFeesList());
        final var bytesToReprOld = TOKEN_OPS_USAGE.bytesNeededToRepr(info.getCustomFeesList());

        final var ctx = new ExtantFeeScheduleContext(info.getExpiry().getSeconds(), bytesToReprOld);
        final var accumulator = new UsageAccumulator();
        final var opMeta = new FeeScheduleUpdateMeta(effectiveNow, bytesToReprNew);
        TOKEN_OPS_USAGE.feeScheduleUpdateUsage(suFrom(svo), baseMeta, opMeta, ctx, accumulator);

        return AdapterUtils.feeDataFrom(accumulator);
    }

    static TokenInfo lookupInfo(
            HapiApiSpec spec, String token, Logger scopedLog, boolean loggingOff) throws Throwable {
        HapiGetTokenInfo subOp = getTokenInfo(token).noLogging();
        Optional<Throwable> error = subOp.execFor(spec);
        if (error.isPresent()) {
            if (!loggingOff) {
                scopedLog.warn(
                        "Unable to look up current info for "
                                + HapiPropertySource.asTokenString(
                                        spec.registry().getTokenID(token)),
                        error.get());
            }
            throw error.get();
        }
        return subOp.getResponse().getTokenGetInfo().getTokenInfo();
    }
}
