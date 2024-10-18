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

package com.hedera.node.app.service.contract.impl.exec.gas;

import static com.hedera.hapi.node.base.SubType.DEFAULT;
import static com.hedera.hapi.node.base.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hedera.hapi.node.base.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hedera.hapi.node.base.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Enumerates the types of child transactions that can be dispatched to the HTS system contract.
 */
@SuppressWarnings("MissingJavadoc")
public enum DispatchType {
    @SuppressWarnings("MissingJavadoc")
    CRYPTO_CREATE(HederaFunctionality.CRYPTO_CREATE, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    CRYPTO_UPDATE(HederaFunctionality.CRYPTO_UPDATE, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    TRANSFER_HBAR(HederaFunctionality.CRYPTO_TRANSFER, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    TRANSFER_FUNGIBLE(HederaFunctionality.CRYPTO_TRANSFER, TOKEN_FUNGIBLE_COMMON),
    @SuppressWarnings("MissingJavadoc")
    TRANSFER_NFT(HederaFunctionality.CRYPTO_TRANSFER, TOKEN_NON_FUNGIBLE_UNIQUE),
    @SuppressWarnings("MissingJavadoc")
    TRANSFER_FUNGIBLE_CUSTOM_FEES(HederaFunctionality.CRYPTO_TRANSFER, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES),
    @SuppressWarnings("MissingJavadoc")
    TRANSFER_NFT_CUSTOM_FEES(HederaFunctionality.CRYPTO_TRANSFER, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES),
    @SuppressWarnings("MissingJavadoc")
    MINT_FUNGIBLE(HederaFunctionality.TOKEN_MINT, TOKEN_FUNGIBLE_COMMON),
    @SuppressWarnings("MissingJavadoc")
    MINT_NFT(HederaFunctionality.TOKEN_MINT, TOKEN_NON_FUNGIBLE_UNIQUE),
    @SuppressWarnings("MissingJavadoc")
    BURN_FUNGIBLE(HederaFunctionality.TOKEN_BURN, TOKEN_FUNGIBLE_COMMON),
    @SuppressWarnings("MissingJavadoc")
    DELETE(HederaFunctionality.TOKEN_DELETE, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    BURN_NFT(HederaFunctionality.TOKEN_BURN, TOKEN_NON_FUNGIBLE_UNIQUE),
    @SuppressWarnings("MissingJavadoc")
    ASSOCIATE(HederaFunctionality.TOKEN_ASSOCIATE_TO_ACCOUNT, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    DISSOCIATE(HederaFunctionality.TOKEN_DISSOCIATE_FROM_ACCOUNT, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    APPROVE(HederaFunctionality.CRYPTO_APPROVE_ALLOWANCE, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    DELETE_NFT_APPROVE(HederaFunctionality.CRYPTO_DELETE_ALLOWANCE, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    GRANT_KYC(HederaFunctionality.TOKEN_GRANT_KYC_TO_ACCOUNT, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    REVOKE_KYC(HederaFunctionality.TOKEN_REVOKE_KYC_FROM_ACCOUNT, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    PAUSE(HederaFunctionality.TOKEN_PAUSE, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    UNPAUSE(HederaFunctionality.TOKEN_UNPAUSE, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    FREEZE(HederaFunctionality.TOKEN_FREEZE_ACCOUNT, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    UNFREEZE(HederaFunctionality.TOKEN_UNFREEZE_ACCOUNT, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    WIPE_FUNGIBLE(HederaFunctionality.TOKEN_ACCOUNT_WIPE, TOKEN_FUNGIBLE_COMMON),
    @SuppressWarnings("MissingJavadoc")
    WIPE_NFT(HederaFunctionality.TOKEN_ACCOUNT_WIPE, TOKEN_NON_FUNGIBLE_UNIQUE),
    @SuppressWarnings("MissingJavadoc")
    UPDATE(HederaFunctionality.TOKEN_UPDATE, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    TOKEN_UPDATE_NFTS(HederaFunctionality.TOKEN_UPDATE_NFTS, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    UTIL_PRNG(HederaFunctionality.UTIL_PRNG, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    TOKEN_INFO(HederaFunctionality.TOKEN_GET_INFO, DEFAULT),
    @SuppressWarnings("MissingJavadoc")
    UPDATE_TOKEN_CUSTOM_FEES(HederaFunctionality.TOKEN_FEE_SCHEDULE_UPDATE, DEFAULT);

    private final HederaFunctionality functionality;
    private final SubType subtype;

    DispatchType(@NonNull final HederaFunctionality functionality, @NonNull final SubType subtype) {
        this.functionality = Objects.requireNonNull(functionality);
        this.subtype = Objects.requireNonNull(subtype);
    }

    @SuppressWarnings("MissingJavadoc")
    public HederaFunctionality functionality() {
        return functionality;
    }

    @SuppressWarnings("MissingJavadoc")
    public SubType subtype() {
        return subtype;
    }
}
