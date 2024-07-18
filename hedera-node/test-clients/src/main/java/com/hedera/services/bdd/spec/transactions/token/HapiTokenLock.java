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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiTokenLock extends HapiTxnOp<HapiTokenLock> {
    static final Logger log = LogManager.getLogger(HapiTokenLock.class);

    private final String account;
    private final String token;
    private long amount;
    private final List<Long> serialNumbers;
    private final SubType subType;
    private ByteString alias = ByteString.EMPTY;

    private boolean rememberingNothing = false;

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenLock;
    }

    public HapiTokenLock(final String token, final String account, final long amount) {
        this.token = token;
        this.account = account;
        this.amount = amount;
        this.serialNumbers = new ArrayList<>();
        this.subType = SubType.TOKEN_FUNGIBLE_COMMON;
    }

    public HapiTokenLock(final String token, final String account, final List<Long> serialNumbers) {
        this.token = token;
        this.account = account;
        this.serialNumbers = serialNumbers;
        this.subType = SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
    }

    public HapiTokenLock rememberingNothing() {
        rememberingNothing = true;
        return this;
    }

    @Override
    protected HapiTokenLock self() {
        return this;
    }

    /*  The constant fees will be modified later once new fee calculation model is implemented */
    @Override
    protected long feeFor(final HapiSpec spec, final Transaction txn, final int numPayerKeys) throws Throwable {
        return HapiSuite.ONE_HUNDRED_HBARS;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(final HapiSpec spec) throws Throwable {
        final var tId = TxnUtils.asTokenId(token, spec);
        final AccountID aId;
        if (!alias.isEmpty()) {
            aId = AccountID.newBuilder().setAlias(alias).build();
        } else {
            aId = TxnUtils.asId(account, spec);
        }
        final TokenLockTransactionBody opBody = spec.txns()
                .<TokenLockTransactionBody, TokenLockTransactionBody.Builder>body(TokenLockTransactionBody.class, b -> {
                    b.setToken(tId);
                    b.setAccount(aId);
                    b.setAmount(amount);
                    b.addAllSerialNumbers(serialNumbers);
                });
        return b -> b.setTokenLock(opBody);
    }

    protected List<Function<HapiSpec, Key>> defaultSigners(final Function<HapiSpec, String> effectivePayer) {
        final List<Function<HapiSpec, Key>> signers = new ArrayList<>();
        signers.add(spec -> spec.registry().getKey(effectivePayer.apply(spec)));
        signers.add(spec -> {
            try {
                return spec.registry().getLockKey(token);
            } catch (Exception ignore) {
                return Key.getDefaultInstance();
            }
        });
        return signers;
    }

    @Override
    protected void updateStateOf(final HapiSpec spec) throws Throwable {
        if (rememberingNothing || actualStatus != SUCCESS) {
            return;
        }
        lookupSubmissionRecord(spec);
        spec.registry().saveCreationTime(token, recordOfSubmission.getConsensusTimestamp());
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("token", token)
                .add("account", account)
                .add("amount", amount)
                .add("serialNumbers", serialNumbers);
    }
}
