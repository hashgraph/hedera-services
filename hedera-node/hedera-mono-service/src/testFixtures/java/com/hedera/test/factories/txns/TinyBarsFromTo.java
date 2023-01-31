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
package com.hedera.test.factories.txns;

import static com.hedera.test.utils.IdUtils.asAccount;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;

public class TinyBarsFromTo {
    private final String payer;
    private final String payee;
    private final long amount;
    private final boolean payerIsAlias;
    private final boolean payeeIsAlias;
    private final boolean isApproval;

    private TinyBarsFromTo(String payer, String payee, long amount) {
        this(payer, payee, amount, false, false, false);
    }

    private TinyBarsFromTo(
            String payer, String payee, long amount, boolean payerIsAlias, boolean payeeIsAlias) {
        this(payer, payee, amount, payerIsAlias, payeeIsAlias, false);
    }

    private TinyBarsFromTo(
            String payer,
            String payee,
            long amount,
            boolean payerIsAlias,
            boolean payeeIsAlias,
            boolean isApproval) {
        this.payer = payer;
        this.payee = payee;
        this.amount = amount;
        this.payerIsAlias = payerIsAlias;
        this.payeeIsAlias = payeeIsAlias;
        this.isApproval = isApproval;
    }

    public static TinyBarsFromTo tinyBarsFromTo(String payer, String payee, long amount) {
        return new TinyBarsFromTo(payer, payee, amount);
    }

    public static TinyBarsFromTo tinyBarsFromAccountToAlias(
            String payer, String payee, long amount) {
        return new TinyBarsFromTo(payer, payee, amount, false, true);
    }

    public static TinyBarsFromTo tinyBarsFromAliasToAlias(String payer, String payee, long amount) {
        return new TinyBarsFromTo(payer, payee, amount, true, true);
    }

    public static TinyBarsFromTo approvedTinyBarsFromTo(String payer, String payee, long amount) {
        return new TinyBarsFromTo(payer, payee, amount, false, false, true);
    }

    public String getPayer() {
        return payer;
    }

    public String getPayee() {
        return payee;
    }

    public long getAmount() {
        return amount;
    }

    public boolean isApproval() {
        return isApproval;
    }

    public AccountID payerId() {
        return payerIsAlias
                ? AccountID.newBuilder().setAlias(ByteString.copyFromUtf8(payer)).build()
                : asAccount(payer);
    }

    public AccountID payeeId() {
        return payeeIsAlias
                ? AccountID.newBuilder().setAlias(ByteString.copyFromUtf8(payee)).build()
                : asAccount(payee);
    }
}
