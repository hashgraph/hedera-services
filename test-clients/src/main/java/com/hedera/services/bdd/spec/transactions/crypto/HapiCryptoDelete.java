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
package com.hedera.services.bdd.spec.transactions.crypto;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static com.hedera.services.bdd.spec.queries.QueryUtils.lookUpAccountWithAlias;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.crypto.ReferenceType;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
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

public class HapiCryptoDelete extends HapiTxnOp<HapiCryptoDelete> {
    static final Logger log = LogManager.getLogger(HapiCryptoDelete.class);

    private String account;
    private String aliasKeySource = null;
    private boolean shouldPurge = false;
    private Optional<String> transferAccount = Optional.empty();
    private ReferenceType referenceType = ReferenceType.REGISTRY_NAME;

    public HapiCryptoDelete(String account) {
        this.account = account;
    }

    public HapiCryptoDelete(String reference, ReferenceType type) {
        this.referenceType = type;
        if (type == ReferenceType.ALIAS_KEY_NAME) {
            aliasKeySource = reference;
        } else {
            account = reference;
        }
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.CryptoDelete;
    }

    @Override
    protected HapiCryptoDelete self() {
        return this;
    }

    public HapiCryptoDelete transfer(String to) {
        transferAccount = Optional.of(to);
        return this;
    }

    public HapiCryptoDelete purging() {
        shouldPurge = true;
        return this;
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        return spec.fees()
                .forActivityBasedOp(
                        HederaFunctionality.CryptoDelete,
                        cryptoFees::getCryptoDeleteTxFeeMatrices,
                        txn,
                        numPayerKeys);
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable {
        AccountID target;

        if (referenceType == ReferenceType.REGISTRY_NAME) {
            target = TxnUtils.asId(account, spec);
        } else {
            account = lookUpAccountWithAlias(spec, aliasKeySource);
            target = asAccount(account);
        }

        CryptoDeleteTransactionBody opBody =
                spec.txns()
                        .<CryptoDeleteTransactionBody, CryptoDeleteTransactionBody.Builder>body(
                                CryptoDeleteTransactionBody.class,
                                b -> {
                                    transferAccount.ifPresent(
                                            a ->
                                                    b.setTransferAccountID(
                                                            spec.registry().getAccountID(a)));
                                    b.setDeleteAccountID(target);
                                });
        return b -> b.setCryptoDelete(opBody);
    }

    @Override
    protected void updateStateOf(HapiSpec spec) throws Throwable {
        if (actualStatus != SUCCESS) {
            return;
        }
        if (shouldPurge) {
            spec.registry().removeAccount(account);
            if (spec.registry().hasKey(account)) {
                spec.registry().removeKey(account);
            }
            if (spec.registry().hasSigRequirement(account)) {
                spec.registry().removeSigRequirement(account);
            }
            if (spec.registry().hasKey(aliasKeySource)) {
                final var lookedUpKey =
                        spec.registry().getKey(aliasKeySource).toByteString().toStringUtf8();
                spec.registry().removeAccount(lookedUpKey);
                spec.registry().removeKey(lookedUpKey);
            }
        }
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        List<Function<HapiSpec, Key>> deleteSigners = new ArrayList<>();
        deleteSigners.addAll(super.defaultSigners());
        deleteSigners.add(spec -> spec.registry().getKey(account));
        deleteSigners.add(
                spec ->
                        spec.registry()
                                .getKey(
                                        transferAccount.orElse(
                                                spec.setup().defaultTransferName())));
        return deleteSigners;
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiSpec spec) {
        return spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls)::cryptoDelete;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("account", account);
    }
}
