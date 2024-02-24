/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.node.app.hapi.fees.usage.TxnUsageEstimator;
import com.hedera.node.app.hapi.fees.usage.token.TokenUpdateUsage;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenUpdateNftTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiTokenNftsUpdate extends HapiTxnOp<HapiTokenNftsUpdate> {
    static final Logger log = LogManager.getLogger(HapiTokenNftsUpdate.class);
    private Optional<List<Long>> serialNumbers;
    private String token;
    private final SubType subType;

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenUpdateNft;
    }

    public HapiTokenNftsUpdate(final String token, final String metadata, final List<Long> serialNumbers) {
        this.token = token;
        this.metadata = Optional.of(metadata);
        this.serialNumbers = Optional.of(serialNumbers);
        this.subType = SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
    }

    public HapiTokenNftsUpdate metadata(String metadata) {
        this.metadata = Optional.of(metadata);
        return this;
    }

    public HapiTokenNftsUpdate serialNumbers(List<Long> serialNumbers) {
        this.serialNumbers = Optional.of(serialNumbers);
        return this;
    }

    @Override
    protected HapiTokenNftsUpdate self() {
        return this;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(final HapiSpec spec) throws Throwable {
        var txnId = TxnUtils.asTokenId(token, spec);
        final TokenUpdateNftTransactionBody opBody = spec.txns()
                .<TokenUpdateNftTransactionBody, TokenUpdateNftTransactionBody.Builder>body(
                        TokenUpdateNftTransactionBody.class, b -> {
                            b.setToken(txnId);
                            var metadataValue = BytesValue.newBuilder()
                                    .setValue(ByteString.copyFrom(
                                            metadata.orElseThrow().getBytes()))
                                    .build();
                            metadata.ifPresent(s -> b.setMetadata(metadataValue));
                            b.addAllSerialNumbers(serialNumbers.orElse(Collections.emptyList()));
                        });
        return b -> b.setTokenUpdateNft(opBody);
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiSpec spec) {
        return spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls)::updateNfts;
    }

    @Override
    protected void updateStateOf(HapiSpec spec) {
        if (actualStatus != SUCCESS) {
            return;
        }
        var registry = spec.registry();
        registry.saveMetadata(token, metadata.orElse(""));
        metadata.ifPresent(m -> registry.saveMetadata(token, m));
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("token", token);
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        try {
            final TokenInfo info = HapiTokenFeeScheduleUpdate.lookupInfo(spec, token, log, loggingOff);
            FeeCalculator.ActivityMetrics metricsCalc = (_txn, svo) -> {
                var estimate =
                        TokenUpdateUsage.newEstimate(_txn, new TxnUsageEstimator(suFrom(svo), _txn, ESTIMATOR_UTILS));
                estimate.givenCurrentExpiry(info.getExpiry().getSeconds());
                if (info.hasMetadataKey()) {
                    estimate.givenCurrentFreezeKey(Optional.of(info.getMetadataKey()));
                }
                return estimate.get();
            };
            return spec.fees().forActivityBasedOp(HederaFunctionality.TokenUpdateNft, metricsCalc, txn, numPayerKeys);
        } catch (Throwable t) {
            log.warn("Couldn't estimate usage", t);
            return HapiSuite.ONE_HBAR;
        }
    }
}
