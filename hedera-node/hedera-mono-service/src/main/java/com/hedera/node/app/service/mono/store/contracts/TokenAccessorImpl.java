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
package com.hedera.node.app.service.mono.store.contracts;

import static com.hedera.node.app.service.mono.store.models.NftId.fromGrpc;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.tokenIdFromEvmAddress;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmHederaKey;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmNftInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;

public class TokenAccessorImpl implements TokenAccessor {
    private final WorldLedgers trackingLedgers;

    public TokenAccessorImpl(final WorldLedgers trackingLedgers) {
        this.trackingLedgers = trackingLedgers;
    }

    @Override
    public Optional<EvmTokenInfo> evmInfoForToken(Address tokenId, byte[] ledgerId) {
        return trackingLedgers.evmInfoForToken(
                tokenIdFromEvmAddress(tokenId), ByteString.copyFrom(ledgerId));
    }

    @Override
    public Optional<EvmNftInfo> evmNftInfo(NftID target, byte[] ledgerId) {
        return trackingLedgers.evmNftInfo(target, ByteString.copyFrom(ledgerId));
    }

    @Override
    public boolean isTokenAddress(Address address) {
        return trackingLedgers.isTokenAddress(address);
    }

    @Override
    public boolean isFrozen(Address accountId, Address tokenId) {
        return trackingLedgers.isFrozen(
                accountIdFromEvmAddress(accountId), tokenIdFromEvmAddress(tokenId));
    }

    @Override
    public boolean defaultFreezeStatus(Address tokenId) {
        return trackingLedgers.defaultFreezeStatus(tokenIdFromEvmAddress(tokenId));
    }

    @Override
    public boolean defaultKycStatus(Address tokenId) {
        return trackingLedgers.defaultKycStatus(tokenIdFromEvmAddress(tokenId));
    }

    @Override
    public boolean isKyc(Address accountId, Address tokenId) {
        return trackingLedgers.isKyc(
                accountIdFromEvmAddress(accountId), tokenIdFromEvmAddress(tokenId));
    }

    @Override
    public Optional<List<CustomFee>> infoForTokenCustomFees(Address tokenId) {
        return trackingLedgers.infoForTokenCustomFees(tokenIdFromEvmAddress(tokenId));
    }

    @Override
    public TokenType typeOf(Address tokenId) {
        return trackingLedgers.typeOf(tokenIdFromEvmAddress(tokenId));
    }

    @Override
    public Optional<TokenInfo> infoForToken(Address tokenId, byte[] ledgerId) {
        return trackingLedgers.infoForToken(
                tokenIdFromEvmAddress(tokenId), ByteString.copyFrom(ledgerId));
    }

    @Override
    public EvmHederaKey keyOf(Address tokenId, Enum keyType) {
        return trackingLedgers.keyOf(tokenIdFromEvmAddress(tokenId), (TokenProperty) keyType);
    }

    @Override
    public String nameOf(Address tokenId) {
        return trackingLedgers.nameOf(tokenIdFromEvmAddress(tokenId));
    }

    @Override
    public String symbolOf(Address tokenId) {
        return trackingLedgers.symbolOf(tokenIdFromEvmAddress(tokenId));
    }

    @Override
    public long totalSupplyOf(Address tokenId) {
        return trackingLedgers.totalSupplyOf(tokenIdFromEvmAddress(tokenId));
    }

    @Override
    public int decimalsOf(Address tokenId) {
        return trackingLedgers.decimalsOf(tokenIdFromEvmAddress(tokenId));
    }

    @Override
    public long balanceOf(Address accountId, Address tokenId) {
        return trackingLedgers.balanceOf(
                accountIdFromEvmAddress(accountId), tokenIdFromEvmAddress(tokenId));
    }

    @Override
    public long staticAllowanceOf(Address ownerId, Address spenderId, Address tokenId) {
        return trackingLedgers.staticAllowanceOf(
                accountIdFromEvmAddress(ownerId),
                accountIdFromEvmAddress(spenderId),
                tokenIdFromEvmAddress(tokenId));
    }

    @Override
    public Address staticApprovedSpenderOf(NftID nftId) {
        return trackingLedgers.staticApprovedSpenderOf(fromGrpc(nftId));
    }

    @Override
    public boolean staticIsOperator(Address ownerId, Address operatorId, Address tokenId) {
        return trackingLedgers.staticIsOperator(
                accountIdFromEvmAddress(ownerId),
                accountIdFromEvmAddress(operatorId),
                tokenIdFromEvmAddress(tokenId));
    }

    @Override
    public Address ownerOf(NftID nftId) {
        return trackingLedgers.ownerOf(fromGrpc(nftId));
    }

    @Override
    public Address canonicalAddress(Address addressOrAlias) {
        return trackingLedgers.canonicalAddress(addressOrAlias);
    }

    @Override
    public String metadataOf(NftID nftId) {
        return trackingLedgers.metadataOf(fromGrpc(nftId));
    }
}
