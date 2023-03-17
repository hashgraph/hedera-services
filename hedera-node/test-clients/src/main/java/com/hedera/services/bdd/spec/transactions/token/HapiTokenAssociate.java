/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import static java.util.stream.Collectors.toList;

import com.google.common.base.MoreObjects;
import com.hedera.node.app.hapi.fees.usage.TxnUsageEstimator;
import com.hedera.node.app.hapi.fees.usage.token.TokenAssociateUsage;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.queries.contract.HapiGetContractInfo;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountInfo;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class HapiTokenAssociate extends HapiTxnOp<HapiTokenAssociate> {
    static final Logger log = LogManager.getLogger(HapiTokenAssociate.class);

    private String account;
    private List<String> tokens = new ArrayList<>();

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenAssociateToAccount;
    }

    public HapiTokenAssociate(String account, String... tokens) {
        this.account = account;
        this.tokens.addAll(List.of(tokens));
    }

    public HapiTokenAssociate(String account, List<String> tokens) {
        this.account = account;
        this.tokens.addAll(tokens);
    }

    @Override
    protected HapiTokenAssociate self() {
        return this;
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        try {
            final long expiry = lookupExpiry(spec);
            FeeCalculator.ActivityMetrics metricsCalc = (_txn, svo) -> {
                var estimate = TokenAssociateUsage.newEstimate(
                                _txn, new TxnUsageEstimator(suFrom(svo), _txn, ESTIMATOR_UTILS))
                        .givenCurrentExpiry(expiry);
                return estimate.get();
            };
            return spec.fees()
                    .forActivityBasedOp(HederaFunctionality.TokenAssociateToAccount, metricsCalc, txn, numPayerKeys);
        } catch (Throwable ignore) {
            return 100_000_000L;
        }
    }

    private long lookupExpiry(HapiSpec spec) throws Throwable {
        if (!spec.registry().hasContractId(account)) {
            HapiGetAccountInfo subOp = getAccountInfo(account).noLogging();
            Optional<Throwable> error = subOp.execFor(spec);
            if (error.isPresent()) {
                if (!loggingOff) {
                    String message =
                            String.format(
                                    "Unable to look up current info for %s",
                                    HapiPropertySource.asAccountString(
                                            spec.registry().getAccountID(account)));
                    log.warn(message, error.get());
                }
                throw error.get();
            }
            return subOp.getResponse()
                    .getCryptoGetInfo()
                    .getAccountInfo()
                    .getExpirationTime()
                    .getSeconds();
        } else {
            HapiGetContractInfo subOp = getContractInfo(account).noLogging();
            Optional<Throwable> error = subOp.execFor(spec);
            if (error.isPresent()) {
                if (!loggingOff) {
                    String message =
                            String.format(
                                    "Unable to look up current info for %s",
                                    HapiPropertySource.asContractString(
                                            spec.registry().getContractId(account)));
                    log.warn(message, error.get());
                }
                throw error.get();
            }
            return subOp.getResponse()
                    .getContractGetInfo()
                    .getContractInfo()
                    .getExpirationTime()
                    .getSeconds();
        }
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable {
        var aId = TxnUtils.asId(account, spec);
        TokenAssociateTransactionBody opBody = spec.txns()
                .<TokenAssociateTransactionBody, TokenAssociateTransactionBody.Builder>body(
                        TokenAssociateTransactionBody.class, b -> {
                            b.setAccount(aId);
                            b.addAllTokens(tokens.stream()
                                    .map(lit -> TxnUtils.asTokenId(lit, spec))
                                    .collect(toList()));
                        });
        return b -> b.setTokenAssociate(opBody);
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        return List.of(spec -> spec.registry().getKey(effectivePayer(spec)), spec -> spec.registry()
                .getKey(account));
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiSpec spec) {
        return spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls)::associateTokens;
    }

    @Override
    protected void updateStateOf(HapiSpec spec) {
        if (actualStatus != SUCCESS) {
            return;
        }
        var registry = spec.registry();
        tokens.forEach(token -> registry.saveTokenRel(account, token));
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper =
                super.toStringHelper().add("account", account).add("tokens", tokens);
        return helper;
    }
}
