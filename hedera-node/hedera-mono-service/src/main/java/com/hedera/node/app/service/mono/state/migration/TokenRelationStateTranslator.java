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

package com.hedera.node.app.service.mono.state.migration;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides static methods for translating between {@link TokenRelation} and {@link  com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus} both ways.
 */
public final class TokenRelationStateTranslator {

    @NonNull
    /**
     * Translates a {@link  com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus} to a {@link TokenRelation}.
     * @param merkleTokenRelStatus - the {@link  com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus} to be translated
     * @return {@link TokenRelation}
     */
    public static TokenRelation tokenRelationFromMerkleTokenRelStatus(
            @NonNull final com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus merkleTokenRelStatus) {
        requireNonNull(merkleTokenRelStatus);
        final var builder = TokenRelation.newBuilder()
                .balance(merkleTokenRelStatus.getBalance())
                .frozen(merkleTokenRelStatus.isFrozen())
                .kycGranted(merkleTokenRelStatus.isKycGranted())
                .automaticAssociation(merkleTokenRelStatus.isAutomaticAssociation());

        final long prevToken = merkleTokenRelStatus.getPrev();
        if (prevToken > 0) {
            builder.previousToken(TokenID.newBuilder().tokenNum(prevToken).build());
        }

        final long nextToken = merkleTokenRelStatus.getNext();
        if (nextToken > 0) {
            builder.nextToken(TokenID.newBuilder().tokenNum(nextToken).build());
        }

        final long account = merkleTokenRelStatus.getKey().getHiOrderAsLong();
        if (account > 0) {
            builder.accountId(AccountID.newBuilder().accountNum(account).build());
        }

        final long token = merkleTokenRelStatus.getKey().getLowOrderAsLong();
        if (token > 0) {
            builder.tokenId(TokenID.newBuilder().tokenNum(token).build());
        }

        return builder.build();
    }

    public static TokenRelation tokenRelationFromOnDiskTokenRelStatus(@NonNull final OnDiskTokenRel onDiskTokenRel) {
        requireNonNull(onDiskTokenRel);
        final var builder = TokenRelation.newBuilder()
                .balance(onDiskTokenRel.getBalance())
                .frozen(onDiskTokenRel.isFrozen())
                .kycGranted(onDiskTokenRel.isKycGranted())
                .automaticAssociation(onDiskTokenRel.isAutomaticAssociation());

        final long prevToken = onDiskTokenRel.getPrev();
        if (prevToken > 0) {
            builder.previousToken(TokenID.newBuilder().tokenNum(prevToken).build());
        }

        final long nextToken = onDiskTokenRel.getNext();
        if (nextToken > 0) {
            builder.nextToken(TokenID.newBuilder().tokenNum(nextToken).build());
        }

        final long account = onDiskTokenRel.getKey().getHiOrderAsLong();
        if (account > 0) {
            builder.accountId(AccountID.newBuilder().accountNum(account).build());
        }

        final long token = onDiskTokenRel.getKey().getLowOrderAsLong();
        if (token > 0) {
            builder.tokenId(TokenID.newBuilder().tokenNum(token).build());
        }

        return builder.build();
    }

    @NonNull
    /**
     * Translates a {@link TokenRelation} to a{@link  com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus}.
     * @param tokenRelation - the {@link TokenRelation} to be translated
     * @return {@link  com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus}
     */
    public static com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus
            merkleTokenRelStatusFromTokenRelation(@NonNull TokenRelation tokenRelation) {
        requireNonNull(tokenRelation);

        com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus merkleTokenRelStatus =
                new com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus(
                        tokenRelation.balance(),
                        tokenRelation.frozen(),
                        tokenRelation.kycGranted(),
                        tokenRelation.automaticAssociation());

        final long accountNum;
        if (tokenRelation.accountId() != null && tokenRelation.accountId().accountNum() > 0) {
            accountNum = tokenRelation.accountId().accountNum();
        } else {
            accountNum = 0L;
        }

        final long tokenNum;
        if (tokenRelation.tokenId() != null && tokenRelation.tokenId().tokenNum() > 0) {
            tokenNum = tokenRelation.tokenId().tokenNum();
        } else {
            tokenNum = 0;
        }

        if (tokenNum > 0L || accountNum > 0L) {
            merkleTokenRelStatus.setKey(EntityNumPair.fromLongs(accountNum, tokenNum));
        }

        if (tokenRelation.previousToken() != null
                && tokenRelation.previousToken().tokenNum() > 0) {
            merkleTokenRelStatus.setPrev(tokenRelation.previousToken().tokenNum());
        }

        if (tokenRelation.nextToken() != null && tokenRelation.nextToken().tokenNum() > 0) {
            merkleTokenRelStatus.setNext(tokenRelation.nextToken().tokenNum());
        }

        return merkleTokenRelStatus;
    }
}
