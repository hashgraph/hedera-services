/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.codec;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;

public class TokenUpdateWrapper {
    private final TokenID tokenID;
    private final String name;
    private final String symbol;
    private final AccountID treasury;
    private final long maxSupply;
    private final String memo;
    private final List<TokenKeyWrapper> tokenKeys;
    private final TokenExpiryWrapper expiry;

    private TokenUpdateWrapper(
            TokenID tokenID,
            String name,
            String symbol,
            AccountID treasury,
            long maxSupply,
            String memo,
            List<TokenKeyWrapper> tokenKeys,
            TokenExpiryWrapper expiry) {
        this.tokenID = tokenID;
        this.name = name;
        this.symbol = symbol;
        this.treasury = treasury;
        this.maxSupply = maxSupply;
        this.memo = memo;
        this.tokenKeys = tokenKeys;
        this.expiry = expiry;
    }

    public static Builder builder() {
        return new Builder();
    }

    public TokenID getTokenID() {
        return tokenID;
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

    public long getMaxSupply() {
        return maxSupply;
    }

    public String getMemo() {
        return memo;
    }

    public List<TokenKeyWrapper> getTokenKeys() {
        return tokenKeys;
    }

    public TokenExpiryWrapper getExpiry() {
        return expiry;
    }

    public static class Builder {
        private TokenID tokenID;
        private String name;
        private String symbol;
        private AccountID treasury;
        private long maxSupply;
        private String memo;
        private List<TokenKeyWrapper> tokenKeys;
        private TokenExpiryWrapper expiry;

        public Builder setTokenID(TokenID tokenID) {
            this.tokenID = tokenID;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setSymbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder setTreasury(AccountID treasury) {
            this.treasury = treasury;
            return this;
        }

        public Builder setMaxSupply(long maxSupply) {
            this.maxSupply = maxSupply;
            return this;
        }

        public Builder setMemo(String memo) {
            this.memo = memo;
            return this;
        }

        public Builder setTokenKeys(List<TokenKeyWrapper> tokenKeys) {
            this.tokenKeys = tokenKeys;
            return this;
        }

        public Builder setExpiry(TokenExpiryWrapper expiry) {
            this.expiry = expiry;
            return this;
        }

        public TokenUpdateWrapper build() {
            return new TokenUpdateWrapper(
                    this.tokenID,
                    this.name,
                    this.symbol,
                    this.treasury,
                    this.maxSupply,
                    this.memo,
                    this.tokenKeys,
                    this.expiry);
        }
    }
}
