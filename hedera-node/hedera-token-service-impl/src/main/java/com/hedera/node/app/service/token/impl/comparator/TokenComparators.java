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

package com.hedera.node.app.service.token.impl.comparator;

import static com.hedera.hapi.util.HapiUtils.ACCOUNT_ID_COMPARATOR;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.state.token.Account;
import java.util.Comparator;
import java.util.Objects;

/**
 * Utility class for token-related comparators.
 */
public final class TokenComparators {
    private TokenComparators() {
        throw new IllegalStateException("Utility Class");
    }

    /**
     * Comparator for {@link Account} objects.
     */
    public static final Comparator<Account> ACCOUNT_COMPARATOR =
            Comparator.comparing(Account::accountId, ACCOUNT_ID_COMPARATOR);
    /**
     * Comparator for {@link AccountAmount} objects.
     */
    public static final Comparator<AccountAmount> ACCOUNT_AMOUNT_COMPARATOR =
            Comparator.comparing(AccountAmount::accountID, ACCOUNT_ID_COMPARATOR);
    /**
     * Comparator for {@link TokenID} objects.
     */
    public static final Comparator<TokenID> TOKEN_ID_COMPARATOR = Comparator.comparingLong(TokenID::tokenNum);

    /**
     * Comparator for {@link NftID} objects.
     */
    public static final Comparator<NftID> NFT_ID_COMPARATOR =
            Comparator.comparing(NftID::tokenIdOrThrow, TOKEN_ID_COMPARATOR).thenComparingLong(NftID::serialNumber);
    /**
     * Comparator for {@link PendingAirdropId} objects.
     */
    public static final Comparator<PendingAirdropId> PENDING_AIRDROP_ID_COMPARATOR = Comparator.comparing(
                    PendingAirdropId::receiverIdOrThrow, ACCOUNT_ID_COMPARATOR)
            .thenComparing(PendingAirdropId::senderIdOrThrow, ACCOUNT_ID_COMPARATOR)
            .thenComparing(PendingAirdropId::tokenReference, (a, b) -> {
                final var ordinalComparison =
                        Integer.compare(a.kind().protoOrdinal(), b.kind().protoOrdinal());
                if (ordinalComparison != 0) {
                    return ordinalComparison;
                } else {
                    if (a.kind() == PendingAirdropId.TokenReferenceOneOfType.FUNGIBLE_TOKEN_TYPE) {
                        return TOKEN_ID_COMPARATOR.compare(a.as(), b.as());
                    } else {
                        return NFT_ID_COMPARATOR.compare(a.as(), b.as());
                    }
                }
            });
    /**
     * Comparator for {@link TokenTransferList} objects.
     */
    public static final Comparator<TokenTransferList> TOKEN_TRANSFER_LIST_COMPARATOR =
            (o1, o2) -> Objects.compare(o1.token(), o2.token(), TOKEN_ID_COMPARATOR);
    /**
     * Comparator for {@link NftTransfer} objects.
     */
    public static final Comparator<NftTransfer> NFT_TRANSFER_COMPARATOR = Comparator.comparing(
                    NftTransfer::senderAccountID, ACCOUNT_ID_COMPARATOR)
            .thenComparing(NftTransfer::receiverAccountID, ACCOUNT_ID_COMPARATOR)
            .thenComparing(NftTransfer::serialNumber);
}
