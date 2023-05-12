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

package com.hedera.services.bdd.spec.utilops;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountBalance;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BalanceSnapshot extends UtilOp {
    private static final Logger log = LogManager.getLogger(BalanceSnapshot.class);

    private String account;
    private String snapshot;
    private Optional<Function<HapiSpec, String>> snapshotFn = Optional.empty();
    private Optional<String> payer = Optional.empty();
    private boolean aliased = false;

    public BalanceSnapshot(String account, String snapshot) {
        this.account = account;
        this.snapshot = snapshot;
    }

    public BalanceSnapshot(String account, Function<HapiSpec, String> fn) {
        this.account = account;
        this.snapshotFn = Optional.of(fn);
    }

    public BalanceSnapshot payingWith(String account) {
        payer = Optional.of(account);
        return this;
    }

    public BalanceSnapshot accountIsAlias() {
        aliased = true;
        return this;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) {
        snapshot = snapshotFn.map(fn -> fn.apply(spec)).orElse(snapshot);

        HapiGetAccountBalance delegate = aliased
                ? QueryVerbs.getAutoCreatedAccountBalance(account).logged()
                : QueryVerbs.getAccountBalance(account).logged();
        payer.ifPresent(delegate::payingWith);
        Optional<Throwable> error = delegate.execFor(spec);
        if (error.isPresent()) {
            log.error("Failed to take balance snapshot for '{}'!", account);
            return false;
        }
        long balance = delegate.getResponse().getCryptogetAccountBalance().getBalance();

        spec.registry().saveBalanceSnapshot(snapshot, balance);
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("snapshot", snapshot)
                .add("account", account)
                .toString();
    }
}
