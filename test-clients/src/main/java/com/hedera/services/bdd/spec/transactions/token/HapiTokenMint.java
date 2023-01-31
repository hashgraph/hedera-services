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
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenFeeScheduleUpdate.lookupInfo;
import static com.hedera.services.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
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
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.fee.SigValueObj;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiTokenMint extends HapiTxnOp<HapiTokenMint> {
    static final Logger log = LogManager.getLogger(HapiTokenMint.class);

    private long amount;
    private String token;
    private boolean rememberingNothing = false;
    private List<ByteString> metadata;
    private SubType subType;

    private TokenInfo info;

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenMint;
    }

    public HapiTokenMint(String token, long amount) {
        this.token = token;
        this.amount = amount;
        this.metadata = Collections.emptyList();
        this.subType = figureSubType();
    }

    public HapiTokenMint(String token, List<ByteString> metadata) {
        this.token = token;
        this.metadata = metadata;
        this.subType = figureSubType();
    }

    public HapiTokenMint(String token, List<ByteString> metadata, String txNamePrefix) {
        this.token = token;
        this.metadata = metadata;
        this.amount = 0;
    }

    public HapiTokenMint(String token, List<ByteString> metadata, long amount) {
        this.token = token;
        this.metadata = metadata;
        this.amount = amount;
        this.subType = figureSubType();
    }

    public HapiTokenMint rememberingNothing() {
        rememberingNothing = true;
        return this;
    }

    @Override
    protected HapiTokenMint self() {
        return this;
    }

    @Override
    protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        try {
            info = lookupInfo(spec, token, log, loggingOff);
        } catch (Throwable ignore) {

        }
        return spec.fees()
                .forActivityBasedOp(
                        HederaFunctionality.TokenMint,
                        subType,
                        this::usageEstimate,
                        txn,
                        numPayerKeys);
    }

    private FeeData usageEstimate(TransactionBody txn, SigValueObj svo) {
        UsageAccumulator accumulator = new UsageAccumulator();

        long lifetime = 0L;
        if (subType == SubType.TOKEN_NON_FUNGIBLE_UNIQUE) {
            lifetime =
                    info.getExpiry().getSeconds()
                            - txn.getTransactionID().getTransactionValidStart().getSeconds();
        }
        final var tokenMintMeta = TOKEN_OPS_USAGE_UTILS.tokenMintUsageFrom(txn, subType, lifetime);
        final var baseTransactionMeta = new BaseTransactionMeta(txn.getMemoBytes().size(), 0);
        TokenOpsUsage tokenOpsUsage = new TokenOpsUsage();
        tokenOpsUsage.tokenMintUsage(suFrom(svo), baseTransactionMeta, tokenMintMeta, accumulator);
        return AdapterUtils.feeDataFrom(accumulator);
    }

    private SubType figureSubType() {
        if (metadata.isEmpty()) {
            return SubType.TOKEN_FUNGIBLE_COMMON;
        } else {
            return SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
        }
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
        var tId = TxnUtils.asTokenId(token, spec);
        TokenMintTransactionBody opBody =
                spec.txns()
                        .<TokenMintTransactionBody, TokenMintTransactionBody.Builder>body(
                                TokenMintTransactionBody.class,
                                b -> {
                                    b.setToken(tId);
                                    b.setAmount(amount);
                                    b.addAllMetadata(metadata);
                                });
        return b -> b.setTokenMint(opBody);
    }

    @Override
    protected List<Function<HapiApiSpec, Key>> defaultSigners() {
        return List.of(
                spec -> spec.registry().getKey(effectivePayer(spec)),
                spec -> spec.registry().getSupplyKey(token));
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
        return spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls)::mintToken;
    }

    @Override
    public void updateStateOf(HapiApiSpec spec) throws Throwable {
        if (rememberingNothing || actualStatus != SUCCESS) {
            return;
        }
        lookupSubmissionRecord(spec);
        spec.registry().saveCreationTime(token, recordOfSubmission.getConsensusTimestamp());
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper =
                super.toStringHelper()
                        .add("token", token)
                        .add("amount", amount)
                        .add("metadata", metadata);
        return helper;
    }
}
