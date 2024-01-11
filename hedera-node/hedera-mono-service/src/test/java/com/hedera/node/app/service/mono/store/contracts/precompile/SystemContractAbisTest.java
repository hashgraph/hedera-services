/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.store.contracts.precompile;

import com.hedera.node.app.service.mono.store.contracts.precompile.impl.SystemContractAbis;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SoftAssertionsExtension.class)
class SystemContractAbisTest {

    @InjectSoftAssertions
    SoftAssertions softly;

    @Test
    @DisplayName("Confirm all values defined in SystemContractTypes are known to AbiConstants.java")
    void confirmAllSelectorsKnown() {
        final var allKnownABISelectors = EnumSet.allOf(SystemContractAbis.class).stream()
                .map(SystemContractAbis::selectorAsHex)
                .map(s -> s.substring(2)) // lose the `0x`
                .map(HexFormat::fromHexDigits)
                .toList();
        softly.assertThat(allAbiConstants).containsAll(allKnownABISelectors);
    }

    // From `mono/store/contracts/precompile/AbiConstants.java` via emacs:
    final List<Integer> allAbiConstants = List.of(
            AbiConstants.ABI_ID_CRYPTO_TRANSFER,
            AbiConstants.ABI_ID_CRYPTO_TRANSFER_V2,
            AbiConstants.ABI_ID_TRANSFER_TOKENS,
            AbiConstants.ABI_ID_TRANSFER_TOKEN,
            AbiConstants.ABI_ID_TRANSFER_NFTS,
            AbiConstants.ABI_ID_TRANSFER_NFT,
            AbiConstants.ABI_ID_MINT_TOKEN,
            AbiConstants.ABI_ID_MINT_TOKEN_V2,
            AbiConstants.ABI_ID_BURN_TOKEN,
            AbiConstants.ABI_ID_BURN_TOKEN_V2,
            AbiConstants.ABI_ID_DELETE_TOKEN,
            AbiConstants.ABI_ID_ASSOCIATE_TOKENS,
            AbiConstants.ABI_ID_ASSOCIATE_TOKEN,
            AbiConstants.ABI_ID_DISSOCIATE_TOKENS,
            AbiConstants.ABI_ID_DISSOCIATE_TOKEN,
            AbiConstants.ABI_ID_PAUSE_TOKEN,
            AbiConstants.ABI_ID_UNPAUSE_TOKEN,
            AbiConstants.ABI_ID_ALLOWANCE,
            AbiConstants.ABI_ID_APPROVE,
            AbiConstants.ABI_ID_APPROVE_NFT,
            AbiConstants.ABI_ID_SET_APPROVAL_FOR_ALL,
            AbiConstants.ABI_ID_GET_APPROVED,
            AbiConstants.ABI_ID_IS_APPROVED_FOR_ALL,
            AbiConstants.ABI_ID_TRANSFER_FROM,
            AbiConstants.ABI_ID_TRANSFER_FROM_NFT,
            AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN,
            AbiConstants.ABI_ID_ERC_NAME,
            AbiConstants.ABI_ID_ERC_SYMBOL,
            AbiConstants.ABI_ID_ERC_DECIMALS,
            AbiConstants.ABI_ID_ERC_TOTAL_SUPPLY_TOKEN,
            AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN,
            AbiConstants.ABI_ID_ERC_TRANSFER,
            AbiConstants.ABI_ID_ERC_TRANSFER_FROM,
            AbiConstants.ABI_ID_ERC_ALLOWANCE,
            AbiConstants.ABI_ID_ERC_APPROVE,
            AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL,
            AbiConstants.ABI_ID_ERC_GET_APPROVED,
            AbiConstants.ABI_ID_ERC_IS_APPROVED_FOR_ALL,
            AbiConstants.ABI_ID_ERC_OWNER_OF_NFT,
            AbiConstants.ABI_ID_ERC_TOKEN_URI_NFT,
            AbiConstants.ABI_ID_HRC_ASSOCIATE,
            AbiConstants.ABI_ID_HRC_DISSOCIATE,
            AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE,
            AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE_V2,
            AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_NFT,
            AbiConstants.ABI_ID_IS_FROZEN,
            AbiConstants.ABI_ID_FREEZE,
            AbiConstants.ABI_ID_UNFREEZE,
            AbiConstants.ABI_ID_UPDATE_TOKEN_INFO,
            AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V2,
            AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V3,
            AbiConstants.ABI_ID_UPDATE_TOKEN_KEYS,
            AbiConstants.ABI_ID_GET_TOKEN_KEY,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V2,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V3,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V2,
            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V3,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V2,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V3,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V2,
            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V3,
            AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO,
            AbiConstants.ABI_ID_GET_TOKEN_INFO,
            AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO,
            AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS,
            AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS,
            AbiConstants.ABI_ID_IS_KYC,
            AbiConstants.ABI_ID_GRANT_TOKEN_KYC,
            AbiConstants.ABI_ID_REVOKE_TOKEN_KYC,
            AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES,
            AbiConstants.ABI_ID_IS_TOKEN,
            AbiConstants.ABI_ID_GET_TOKEN_TYPE,
            AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO,
            AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO,
            AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO_V2);

    @NonNull
    String listAllSystemContractAbis() {
        final var allSystemContractABIs = EnumSet.allOf(SystemContractAbis.class);

        final var sb = new StringBuilder(2000);
        sb.append('\n');
        for (final var abi : allSystemContractABIs) {
            sb.append("%s (%d) %s[%d]: %s {%s}%n"
                    .formatted(
                            abi.selectorAsHex(),
                            abi.selector.toInt(),
                            abi.methodName,
                            abi.version,
                            abi.signature,
                            abi.decoderSignature));
        }

        return sb.toString();
    }
}
