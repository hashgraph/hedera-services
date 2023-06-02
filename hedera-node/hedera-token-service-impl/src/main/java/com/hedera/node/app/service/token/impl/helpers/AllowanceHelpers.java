/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.helpers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.NftAllowance;
import java.util.List;

public class AllowanceHelpers {
    private AllowanceHelpers() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Each serial number in an {@code NftAllowance} is considered as an allowance.
     *
     * @param nftAllowances a list of NFT individual allowances
     * @return the number of mentioned serial numbers
     */
    public static int aggregateNftAllowances(List<NftAllowance> nftAllowances) {
        int nftAllowancesTotal = 0;
        for (var allowances : nftAllowances) {
            var serials = allowances.serialNumbers();
            if (!serials.isEmpty()) {
                nftAllowancesTotal += serials.size();
            } else {
                nftAllowancesTotal++;
            }
        }
        return nftAllowancesTotal;
    }

    public static int countSerials(final List<NftAllowance> nftAllowancesList) {
        int totalSerials = 0;
        for (var allowance : nftAllowancesList) {
            totalSerials += allowance.serialNumbers().size();
        }
        return totalSerials;
    }

    /**
     * Checks if the total allowances of an account will exceed the limit after applying this
     * transaction. This limit doesn't include number of serials for nfts, since they are not stored
     * on account. The limit includes number of crypto allowances, number of fungible token
     * allowances and number of approvedForAll Nft allowances on owner account
     *
     * @param owner The Account to validate the allowances limit on.
     * @param allowanceMaxAccountLimit maximum number of allowances an Account can have.
     */
    public static void validateAllowanceLimit(final Account owner, final int allowanceMaxAccountLimit) {
        final var totalAllowances = owner.cryptoAllowancesOrElse(emptyList()).size()
                + owner.tokenAllowancesOrElse(emptyList()).size()
                + owner.approveForAllNftAllowancesOrElse(emptyList()).size();
        validateFalse(totalAllowances > allowanceMaxAccountLimit, MAX_ALLOWANCES_EXCEEDED);
    }

    /**
     * Checks the owner of token is treasury or the owner id given in allowance. If not, considers
     * as an invalid owner and returns false.
     *
     * @param nft given nft
     * @param ownerNum owner given in allowance
     * @param token token for which nft belongs to
     * @return whether the owner is valid
     */
    public static boolean validateOwner(final Nft nft, final long ownerNum, final Token token) {
        final var listedOwner = nft.ownerNumber();
        return listedOwner == 0 ? ownerNum == token.treasuryAccountNumber() : listedOwner == ownerNum;
    }
}
