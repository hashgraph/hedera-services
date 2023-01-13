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

import static com.hedera.node.app.service.mono.state.merkle.MerkleToken.convertToEvmKey;
import static com.hedera.node.app.service.mono.store.models.NftId.fromGrpc;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.tokenIdFromEvmAddress;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmKey;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmNftInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenKeyType;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hederahashgraph.api.proto.java.NftID;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;

public class TokenAccessorImpl implements TokenAccessor {
    private final WorldLedgers trackingLedgers;
    private final ByteString ledgerId;

    public TokenAccessorImpl(final WorldLedgers trackingLedgers, ByteString ledgerId) {
        this.trackingLedgers = trackingLedgers;
        this.ledgerId = ledgerId;
    }

    @Override
    public Optional<EvmTokenInfo> evmInfoForToken(final Address token) {
        return trackingLedgers.evmInfoForToken(tokenIdFromEvmAddress(token), ledgerId);
    }

    @Override
    public Optional<EvmNftInfo> evmNftInfo(final Address token, long serialNo) {
        final var target =
                NftID.newBuilder()
                        .setSerialNumber(serialNo)
                        .setTokenID(tokenIdFromEvmAddress(token))
                        .build();
        return trackingLedgers.evmNftInfo(target, ledgerId);
    }

    @Override
    public boolean isTokenAddress(Address address) {
        return trackingLedgers.isTokenAddress(address);
    }

    @Override
    public boolean isFrozen(Address account, Address token) {
        return trackingLedgers.isFrozen(
                accountIdFromEvmAddress(account), tokenIdFromEvmAddress(token));
    }

    @Override
    public boolean defaultFreezeStatus(Address token) {
        return trackingLedgers.defaultFreezeStatus(tokenIdFromEvmAddress(token));
    }

    @Override
    public boolean defaultKycStatus(Address token) {
        return trackingLedgers.defaultKycStatus(tokenIdFromEvmAddress(token));
    }

    @Override
    public boolean isKyc(Address account, Address token) {
        return trackingLedgers.isKyc(
                accountIdFromEvmAddress(account), tokenIdFromEvmAddress(token));
    }

    @Override
    public Optional<List<CustomFee>> infoForTokenCustomFees(Address token) {
        return trackingLedgers.infoForTokenCustomFees(tokenIdFromEvmAddress(token));
    }

    @Override
    public TokenType typeOf(Address token) {
        return trackingLedgers.typeOf(tokenIdFromEvmAddress(token));
    }

    @Override
    public EvmKey keyOf(Address token, TokenKeyType keyType) {
        final var key =
                trackingLedgers.keyOf(
                        tokenIdFromEvmAddress(token), TokenProperty.valueOf(keyType.name()));

        return convertToEvmKey(asKeyUnchecked(key));
    }

    @Override
    public String nameOf(Address token) {
        return trackingLedgers.nameOf(tokenIdFromEvmAddress(token));
    }

    @Override
    public String symbolOf(Address token) {
        return trackingLedgers.symbolOf(tokenIdFromEvmAddress(token));
    }

    @Override
    public long totalSupplyOf(Address token) {
        return trackingLedgers.totalSupplyOf(tokenIdFromEvmAddress(token));
    }

    @Override
    public int decimalsOf(Address token) {
        return trackingLedgers.decimalsOf(tokenIdFromEvmAddress(token));
    }

    @Override
    public long balanceOf(Address account, Address token) {
        return trackingLedgers.balanceOf(
                accountIdFromEvmAddress(account), tokenIdFromEvmAddress(token));
    }

    @Override
    public long staticAllowanceOf(Address owner, Address spender, Address token) {
        return trackingLedgers.staticAllowanceOf(
                accountIdFromEvmAddress(owner),
                accountIdFromEvmAddress(spender),
                tokenIdFromEvmAddress(token));
    }

    @Override
    public Address staticApprovedSpenderOf(final Address nft, long serialNo) {
        return trackingLedgers.staticApprovedSpenderOf(nftIdOf(nft, serialNo));
    }

    @Override
    public boolean staticIsOperator(Address owner, Address operator, Address token) {
        return trackingLedgers.staticIsOperator(
                accountIdFromEvmAddress(owner),
                accountIdFromEvmAddress(operator),
                tokenIdFromEvmAddress(token));
    }

    @Override
    public Address ownerOf(final Address nft, long serialNo) {
        return trackingLedgers.ownerOf(nftIdOf(nft, serialNo));
    }

    @Override
    public Address canonicalAddress(Address addressOrAlias) {
        return trackingLedgers.canonicalAddress(addressOrAlias);
    }

    @Override
    public String metadataOf(final Address nft, long serialNo) {
        return trackingLedgers.metadataOf(nftIdOf(nft, serialNo));
    }

    @Override
    public byte[] ledgerId() {
        return ledgerId.toByteArray();
    }

    private NftId nftIdOf(Address token, long serialNo) {
        return fromGrpc(tokenIdFromEvmAddress(token), serialNo);
    }
}
