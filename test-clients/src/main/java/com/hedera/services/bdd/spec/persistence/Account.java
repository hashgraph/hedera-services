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
package com.hedera.services.bdd.spec.persistence;

import static com.hedera.services.bdd.spec.persistence.Entity.UNUSED_KEY;
import static com.hedera.services.bdd.spec.persistence.SpecKey.RegistryForms.under;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountInfo;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoCreate;
import java.util.Optional;

public class Account {
    private static final Long UNSPECIFIED_BALANCE = null;
    private static final Integer DEFAULT_RECHARGE_WINDOW = 30;
    private static final Integer UNSPECIFIED_RECHARGE_WINDOW = null;
    static final String UNSPECIFIED_MEMO = null;

    private Long balance = UNSPECIFIED_BALANCE;
    private String memo = UNSPECIFIED_MEMO;
    private SpecKey key = UNUSED_KEY;
    private Integer rechargeWindow = UNSPECIFIED_RECHARGE_WINDOW;
    private boolean receiverSigRequired = false;
    private boolean recharging = false;

    public void registerWhatIsKnown(HapiSpec spec, String name, Optional<EntityId> entityId) {
        if (key == UNUSED_KEY) {
            throw new IllegalStateException(String.format("Account '%s' has no given key!", name));
        }
        key.registerWith(spec, under(name));
        if (recharging) {
            spec.registry().setRecharging(name, effBalance(spec));
            spec.registry().setRechargingWindow(name, effRechargeWindow());
        }
        if (receiverSigRequired) {
            spec.registry().saveSigRequirement(name, receiverSigRequired);
        }
        entityId.ifPresent(id -> spec.registry().saveAccountId(name, id.asAccount()));
    }

    public HapiQueryOp<HapiGetAccountInfo> existenceCheck(String name) {
        return getAccountInfo(name);
    }

    HapiTxnOp<HapiCryptoCreate> createOp(String name) {
        var op = cryptoCreate(name).key(name).advertisingCreation();
        if (balance != null) {
            op.balance(balance);
        }
        if (memo != null) {
            op.entityMemo(memo);
        }
        if (receiverSigRequired) {
            op.receiverSigRequired(true);
        }
        if (recharging) {
            op.withRecharging().rechargeWindow(effRechargeWindow());
        }
        return op;
    }

    public SpecKey getKey() {
        return key;
    }

    public void setKey(SpecKey key) {
        this.key = key;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }

    private long effBalance(HapiSpec spec) {
        return (balance == null) ? spec.setup().defaultBalance() : balance;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    private int effRechargeWindow() {
        return (rechargeWindow == null) ? DEFAULT_RECHARGE_WINDOW : rechargeWindow;
    }

    public Boolean getReceiverSigRequired() {
        return receiverSigRequired;
    }

    public void setReceiverSigRequired(Boolean receiverSigRequired) {
        this.receiverSigRequired = receiverSigRequired;
    }

    public Integer getRechargeWindow() {
        return rechargeWindow;
    }

    public void setRechargeWindow(Integer rechargeWindow) {
        this.rechargeWindow = rechargeWindow;
    }

    public boolean isRecharging() {
        return recharging;
    }

    public void setRecharging(boolean recharging) {
        this.recharging = recharging;
    }
}
