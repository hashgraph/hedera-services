/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.hapi.fees.usage.crypto;

import com.hederahashgraph.api.proto.java.SubType;

public class CryptoTransferMeta {
    private int tokenMultiplier = 1;

    private final int numTokensInvolved;
    private final int numFungibleTokenTransfers;
    private final int numNftOwnershipChanges;

    private int customFeeTokensInvolved;
    private int customFeeHbarTransfers;
    private int customFeeTokenTransfers;

    public CryptoTransferMeta(
            int tokenMultiplier,
            int numTokensInvolved,
            int numFungibleTokenTransfers,
            int numNftOwnershipChanges) {
        this.tokenMultiplier = tokenMultiplier;
        this.numTokensInvolved = numTokensInvolved;
        this.numFungibleTokenTransfers = numFungibleTokenTransfers;
        this.numNftOwnershipChanges = numNftOwnershipChanges;
    }

    public int getTokenMultiplier() {
        return tokenMultiplier;
    }

    public int getNumTokensInvolved() {
        return numTokensInvolved;
    }

    public int getNumFungibleTokenTransfers() {
        return numFungibleTokenTransfers;
    }

    public void setTokenMultiplier(int tokenMultiplier) {
        this.tokenMultiplier = tokenMultiplier;
    }

    public void setCustomFeeTokensInvolved(final int customFeeTokensInvolved) {
        this.customFeeTokensInvolved = customFeeTokensInvolved;
    }

    public int getCustomFeeTokensInvolved() {
        return customFeeTokensInvolved;
    }

    public void setCustomFeeTokenTransfers(final int customFeeTokenTransfers) {
        this.customFeeTokenTransfers = customFeeTokenTransfers;
    }

    public int getCustomFeeTokenTransfers() {
        return customFeeTokenTransfers;
    }

    public void setCustomFeeHbarTransfers(final int customFeeHbarTransfers) {
        this.customFeeHbarTransfers = customFeeHbarTransfers;
    }

    public int getCustomFeeHbarTransfers() {
        return customFeeHbarTransfers;
    }

    public int getNumNftOwnershipChanges() {
        return numNftOwnershipChanges;
    }

    public SubType getSubType() {
        if (numNftOwnershipChanges != 0) {
            if (customFeeHbarTransfers > 0 || customFeeTokenTransfers > 0) {
                return SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
            }
            return SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
        }
        if (numFungibleTokenTransfers != 0) {
            if (customFeeHbarTransfers > 0 || customFeeTokenTransfers > 0) {
                return SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
            }
            return SubType.TOKEN_FUNGIBLE_COMMON;
        }
        return SubType.DEFAULT;
    }
}
