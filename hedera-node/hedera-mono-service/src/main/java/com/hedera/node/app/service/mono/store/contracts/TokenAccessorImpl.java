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
import static com.hedera.node.app.service.mono.utils.EvmTokenUtil.convertToEvmKey;
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

    public TokenAccessorImpl(final WorldLedgers trackingLedgers) {
        this.trackingLedgers = trackingLedgers;
    }

    @Override
    public Optional<EvmTokenInfo> evmInfoForToken(final Address token, final ByteString ledgerId) {
        return trackingLedgers.evmInfoForToken(tokenIdFromEvmAddress(token), ledgerId);
    }

    @Override
    public Optional<EvmNftInfo> evmNftInfo(
            final Address token, final long serialNo, final ByteString ledgerId) {
        final var target =
                NftID.newBuilder()
                        .setSerialNumber(serialNo)
                        .setTokenID(tokenIdFromEvmAddress(token))
                        .build();
        return trackingLedgers.evmNftInfo(target, ledgerId);
    }

    @Override
    public boolean isTokenAddress(final Address address) {
        return trackingLedgers.isTokenAddress(address);
    }

    @Override
    public boolean isFrozen(final Address account, final Address token) {
        return trackingLedgers.isFrozen(
                accountIdFromEvmAddress(account), tokenIdFromEvmAddress(token));
    }

    @Override
    public boolean defaultFreezeStatus(final Address token) {
        return trackingLedgers.defaultFreezeStatus(tokenIdFromEvmAddress(token));
    }

    @Override
    public boolean defaultKycStatus(final Address token) {
        return trackingLedgers.defaultKycStatus(tokenIdFromEvmAddress(token));
    }

    @Override
    public boolean isKyc(final Address account, final Address token) {
        return trackingLedgers.isKyc(
                accountIdFromEvmAddress(account), tokenIdFromEvmAddress(token));
    }

    @Override
    public Optional<List<CustomFee>> infoForTokenCustomFees(final Address token) {
        return trackingLedgers.infoForTokenCustomFees(tokenIdFromEvmAddress(token));
    }

    @Override
    public TokenType typeOf(final Address token) {
        return trackingLedgers.typeOf(tokenIdFromEvmAddress(token));
    }

    @Override
    public EvmKey keyOf(final Address token, final TokenKeyType keyType) {
        final var key =
                trackingLedgers.keyOf(
                        tokenIdFromEvmAddress(token), TokenProperty.valueOf(keyType.name()));

        return convertToEvmKey(asKeyUnchecked(key));
    }

    @Override
    public String nameOf(final Address token) {
        return trackingLedgers.nameOf(tokenIdFromEvmAddress(token));
    }

    @Override
    public String symbolOf(final Address token) {
        return trackingLedgers.symbolOf(tokenIdFromEvmAddress(token));
    }

    @Override
    public long totalSupplyOf(final Address token) {
        return trackingLedgers.totalSupplyOf(tokenIdFromEvmAddress(token));
    }

    @Override
    public int decimalsOf(final Address token) {
        return trackingLedgers.decimalsOf(tokenIdFromEvmAddress(token));
    }

    @Override
    public long balanceOf(final Address account, final Address token) {
        return trackingLedgers.balanceOf(
                accountIdFromEvmAddress(account), tokenIdFromEvmAddress(token));
    }

    @Override
    public long allowanceOf(final Address owner, final Address spender, final Address token) {
        return trackingLedgers.staticAllowanceOf(
                accountIdFromEvmAddress(owner),
                accountIdFromEvmAddress(spender),
                tokenIdFromEvmAddress(token));
    }

    @Override
    public Address approvedSpenderOf(final Address nft, final long serialNo) {
        return trackingLedgers.staticApprovedSpenderOf(nftIdOf(nft, serialNo));
    }

    @Override
    public boolean isOperator(final Address owner, final Address operator, final Address token) {
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
    public Address canonicalAddress(final Address addressOrAlias) {
        return trackingLedgers.canonicalAddress(addressOrAlias);
    }

    @Override
    public String metadataOf(final Address nft, long serialNo) {
        return trackingLedgers.metadataOf(nftIdOf(nft, serialNo));
    }

    private NftId nftIdOf(final Address token, final long serialNo) {
        return fromGrpc(tokenIdFromEvmAddress(token), serialNo);
    }
}
