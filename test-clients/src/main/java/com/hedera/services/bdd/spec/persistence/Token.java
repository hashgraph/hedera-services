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
package com.hedera.services.bdd.spec.persistence;

import static com.hedera.services.bdd.spec.persistence.Account.UNSPECIFIED_MEMO;
import static com.hedera.services.bdd.spec.persistence.Entity.UNUSED_KEY;
import static com.hedera.services.bdd.spec.persistence.SpecKey.RegistryForms.asAdminKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.RegistryForms.asFreezeKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.RegistryForms.asKycKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.RegistryForms.asPauseKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.RegistryForms.asSupplyKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.RegistryForms.asWipeKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.adminKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.freezeKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.kycKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.pauseKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.supplyKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.wipeKeyFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenCreate;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Token {
    static final Logger log = LogManager.getLogger(Token.class);

    private static final String DEFAULT_SYMBOL = "S6T";
    private static final String DEFAULT_HEDERA_NAME = "SOMNOLENT";
    private static final String UNSPECIFIED_TREASURY = null;

    private boolean frozenByDefault = true;

    private SpecKey kycKey = UNUSED_KEY;
    private SpecKey wipeKey = UNUSED_KEY;
    private SpecKey adminKey = UNUSED_KEY;
    private SpecKey supplyKey = UNUSED_KEY;
    private SpecKey freezeKey = UNUSED_KEY;
    private SpecKey pauseKey = UNUSED_KEY;

    private String tokenName = DEFAULT_HEDERA_NAME;
    private String memo = UNSPECIFIED_MEMO;
    private String symbol = DEFAULT_SYMBOL;
    private String treasury = UNSPECIFIED_TREASURY;

    public void registerWhatIsKnown(HapiSpec spec, String name, Optional<EntityId> entityId) {
        if (kycKey != UNUSED_KEY) {
            kycKey.registerWith(spec, asKycKeyFor(name));
        }
        if (wipeKey != UNUSED_KEY) {
            wipeKey.registerWith(spec, asWipeKeyFor(name));
        }
        if (adminKey != UNUSED_KEY) {
            adminKey.registerWith(spec, asAdminKeyFor(name));
        }
        if (supplyKey != UNUSED_KEY) {
            supplyKey.registerWith(spec, asSupplyKeyFor(name));
        }
        if (freezeKey != UNUSED_KEY) {
            freezeKey.registerWith(spec, asFreezeKeyFor(name));
        }
        if (pauseKey != UNUSED_KEY) {
            pauseKey.registerWith(spec, asPauseKeyFor(name));
        }
        entityId.ifPresent(
                id -> {
                    spec.registry().saveName(name, this.tokenName);
                    spec.registry().saveSymbol(name, symbol);
                    spec.registry().saveTokenId(name, id.asToken());
                    if (treasury != UNSPECIFIED_TREASURY) {
                        spec.registry().saveTreasury(name, treasury);
                    }
                });
    }

    public HapiQueryOp<?> existenceCheck(String name) {
        return getTokenInfo(name);
    }

    HapiTxnOp<HapiTokenCreate> createOp(String name) {
        var op = tokenCreate(name).advertisingCreation().symbol(symbol).name(this.tokenName);

        if (treasury != UNSPECIFIED_TREASURY) {
            op.treasury(treasury);
        }
        if (memo != UNSPECIFIED_MEMO) {
            op.entityMemo(memo);
        }

        if (kycKey != UNUSED_KEY) {
            op.kycKey(kycKeyFor(name));
        }
        if (wipeKey != UNUSED_KEY) {
            op.wipeKey(wipeKeyFor(name));
        }
        if (adminKey != UNUSED_KEY) {
            op.adminKey(adminKeyFor(name));
        }
        if (freezeKey != UNUSED_KEY) {
            op.freezeKey(freezeKeyFor(name));
            if (frozenByDefault) {
                op.freezeDefault(true);
            }
        }
        if (supplyKey != UNUSED_KEY) {
            op.supplyKey(supplyKeyFor(name));
        }
        if (pauseKey != UNUSED_KEY) {
            op.pauseKey(pauseKeyFor(name));
        }

        return op;
    }

    public String getTreasury() {
        return treasury;
    }

    public void setTreasury(String treasury) {
        this.treasury = treasury;
    }

    public SpecKey getKycKey() {
        return kycKey;
    }

    public void setKycKey(SpecKey kycKey) {
        this.kycKey = kycKey;
    }

    public SpecKey getWipeKey() {
        return wipeKey;
    }

    public void setWipeKey(SpecKey wipeKey) {
        this.wipeKey = wipeKey;
    }

    public SpecKey getAdminKey() {
        return adminKey;
    }

    public void setAdminKey(SpecKey adminKey) {
        this.adminKey = adminKey;
    }

    public SpecKey getSupplyKey() {
        return supplyKey;
    }

    public void setSupplyKey(SpecKey supplyKey) {
        this.supplyKey = supplyKey;
    }

    public SpecKey getFreezeKey() {
        return freezeKey;
    }

    public void setFreezeKey(SpecKey freezeKey) {
        this.freezeKey = freezeKey;
    }

    public SpecKey getPauseKey() {
        return pauseKey;
    }

    public void setPauseKey(SpecKey pauseKey) {
        this.pauseKey = pauseKey;
    }

    public String getTokenName() {
        return tokenName;
    }

    public void setTokenName(String tokenName) {
        this.tokenName = tokenName;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public boolean isFrozenByDefault() {
        return frozenByDefault;
    }

    public void setFrozenByDefault(boolean frozenByDefault) {
        this.frozenByDefault = frozenByDefault;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }
}
