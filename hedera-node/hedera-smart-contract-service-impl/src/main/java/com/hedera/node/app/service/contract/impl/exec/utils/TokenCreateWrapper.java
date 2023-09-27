/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.utils;

import com.hedera.hapi.node.base.AccountID;
import java.util.List;

public class TokenCreateWrapper {
    private final boolean isFungible;
    private final String name;
    private final String symbol;
    private final AccountID treasury;
    private final boolean isSupplyTypeFinite;
    private final long initSupply;
    private final int decimals;
    private final long maxSupply;
    private final String memo;
    private final boolean isFreezeDefault;
    private final List<TokenKeyWrapper> tokenKeys;
    private final TokenExpiryWrapper expiry;

    public TokenCreateWrapper(
            final boolean isFungible,
            final String tokenName,
            final String tokenSymbol,
            final AccountID tokenTreasury,
            final String memo,
            final Boolean isSupplyTypeFinite,
            final long initSupply,
            final int decimals,
            final long maxSupply,
            final Boolean isFreezeDefault,
            final List<TokenKeyWrapper> tokenKeys,
            final TokenExpiryWrapper tokenExpiry) {
        this.isFungible = isFungible;
        this.name = tokenName;
        this.symbol = tokenSymbol;
        this.treasury = tokenTreasury;
        this.memo = memo;
        this.isSupplyTypeFinite = isSupplyTypeFinite;
        this.initSupply = initSupply;
        this.decimals = decimals;
        this.maxSupply = maxSupply;
        this.isFreezeDefault = isFreezeDefault;
        this.tokenKeys = tokenKeys;
        this.expiry = tokenExpiry;
    }

    public boolean isFungible() {
        return isFungible;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public AccountID getTreasury() {
        return treasury;
    }

    public boolean isSupplyTypeFinite() {
        return isSupplyTypeFinite;
    }

    public long getInitSupply() {
        return initSupply;
    }

    public int getDecimals() {
        return decimals;
    }

    public long getMaxSupply() {
        return maxSupply;
    }

    public String getMemo() {
        return memo;
    }

    public boolean isFreezeDefault() {
        return isFreezeDefault;
    }

    public List<TokenKeyWrapper> getTokenKeys() {
        return tokenKeys;
    }

    public TokenExpiryWrapper getExpiry() {
        return expiry;
    }
}
