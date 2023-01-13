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
package com.hedera.node.app.service.evm.store.tokens;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmKey;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmNftInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenKeyType;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;

public interface TokenAccessor {
    Optional<EvmTokenInfo> evmInfoForToken(final Address token);

    Optional<EvmNftInfo> evmNftInfo(final Address nft, final long serialNo);

    boolean isTokenAddress(final Address address);

    boolean isFrozen(final Address account, final Address token);

    boolean defaultFreezeStatus(final Address token);

    boolean defaultKycStatus(final Address token);

    boolean isKyc(final Address account, final Address token);

    Optional<List<CustomFee>> infoForTokenCustomFees(final Address token);

    TokenType typeOf(final Address token);

    EvmKey keyOf(final Address tokenId, final TokenKeyType keyType);

    String nameOf(final Address token);

    String symbolOf(final Address token);

    long totalSupplyOf(final Address token);

    int decimalsOf(final Address token);

    long balanceOf(final Address account, final Address token);

    long staticAllowanceOf(final Address owner, final Address spender, final Address token);

    Address staticApprovedSpenderOf(final Address nft, long serialNo);

    boolean staticIsOperator(final Address owner, final Address operator, final Address token);

    Address ownerOf(final Address nft, long serialNo);

    Address canonicalAddress(final Address addressOrAlias);

    String metadataOf(final Address nft, long serialNo);

    byte[] ledgerId();
}
