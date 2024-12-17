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

package com.hedera.node.app.hapi.fees.usage.token;

import static com.hedera.node.app.hapi.fees.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

import com.hedera.node.app.hapi.fees.usage.QueryUsage;
import com.hedera.node.app.hapi.utils.fee.FeeBuilder;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import java.util.Optional;

public class TokenGetInfoUsage extends QueryUsage {
    private TokenGetInfoUsage(final Query query) {
        super(query.getTokenGetInfo().getHeader().getResponseType());
        addTb(BASIC_ENTITY_ID_SIZE);
        addRb(TOKEN_ENTITY_SIZES.fixedBytesInTokenRepr());
    }

    public static TokenGetInfoUsage newEstimate(final Query query) {
        return new TokenGetInfoUsage(query);
    }

    public TokenGetInfoUsage givenCurrentAdminKey(final Optional<Key> adminKey) {
        adminKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::addRb);
        return this;
    }

    public TokenGetInfoUsage givenCurrentWipeKey(final Optional<Key> wipeKey) {
        wipeKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::addRb);
        return this;
    }

    public TokenGetInfoUsage givenCurrentSupplyKey(final Optional<Key> supplyKey) {
        supplyKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::addRb);
        return this;
    }

    public TokenGetInfoUsage givenCurrentFreezeKey(final Optional<Key> freezeKey) {
        freezeKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::addRb);
        return this;
    }

    public TokenGetInfoUsage givenCurrentKycKey(final Optional<Key> kycKey) {
        kycKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::addRb);
        return this;
    }

    public TokenGetInfoUsage givenCurrentPauseKey(final Optional<Key> pauseKey) {
        pauseKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::addRb);
        return this;
    }

    public TokenGetInfoUsage givenCurrentMetadataKey(final Optional<Key> metadataKey) {
        metadataKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::addRb);
        return this;
    }

    public TokenGetInfoUsage givenCurrentMemo(final String memo) {
        addRb(memo.length());
        return this;
    }

    public TokenGetInfoUsage givenCurrentName(final String name) {
        addRb(name.length());
        return this;
    }

    public TokenGetInfoUsage givenCurrentSymbol(final String symbol) {
        addRb(symbol.length());
        return this;
    }

    public TokenGetInfoUsage givenCurrentlyUsingAutoRenewAccount() {
        addRb(BASIC_ENTITY_ID_SIZE);
        return this;
    }

    public TokenGetInfoUsage givenCurrentMetadata(final String metadata) {
        addRb(metadata.length());
        return this;
    }
}
