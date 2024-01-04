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

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.merkle.internals.BitPackUtils;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.mono.utils.NftNumPair;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

public final class NftStateTranslator {

    @NonNull
    /**
     * Converts a {@link com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken} to {@link Nft}.
     * @param merkleUniqueToken the {@link com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken}
     * @return the {@link Nft} converted from the {@link com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken}
     */
    public static Nft nftFromMerkleUniqueToken(
            @NonNull final com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken merkleUniqueToken) {
        requireNonNull(merkleUniqueToken);
        final var builder = Nft.newBuilder();
        final var nftIdPair = merkleUniqueToken.getKey().asNftNumPair();
        builder.nftId(nftNumPairToNftID(nftIdPair));

        builder.ownerId(PbjConverter.toPbj(merkleUniqueToken.getOwner().toGrpcAccountId()))
                .spenderId(PbjConverter.toPbj(merkleUniqueToken.getSpender().toGrpcAccountId()))
                .mintTime(Timestamp.newBuilder()
                        .seconds(merkleUniqueToken.getCreationTime().getSeconds())
                        .nanos(merkleUniqueToken.getCreationTime().getNanos())
                        .build())
                .metadata(Bytes.wrap(merkleUniqueToken.getMetadata()));

        final var nftPrevIdPair = merkleUniqueToken.getPrev();
        builder.ownerPreviousNftId(nftNumPairToNftID(nftPrevIdPair));

        final var nftNextIdPair = merkleUniqueToken.getNext();
        builder.ownerNextNftId(nftNumPairToNftID(nftNextIdPair));

        return builder.build();
    }

    private static @NonNull NftID nftNumPairToNftID(@NonNull NftNumPair nftNumPair) {
        final var tokenTypeNumber = nftNumPair.tokenNum();
        final var serialNumber = nftNumPair.serialNum();
        return NftID.newBuilder()
                .tokenId(TokenID.newBuilder().tokenNum(tokenTypeNumber).build())
                .serialNumber(serialNumber)
                .build();
    }

    @NonNull
    /**
     * Converts a {@link com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken} to a {@link Nft}.
     *  @param tokenID the {@link NftID}
     *  @param tokenID the {@link ReadableNftStore}
     */
    public static com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken merkleUniqueTokenFromNft(
            @NonNull NftID tokenID, @NonNull ReadableNftStore readableNftStore) {
        requireNonNull(tokenID);
        requireNonNull(readableNftStore);
        final var optionalNFT = readableNftStore.get(tokenID);
        if (optionalNFT == null) {
            throw new IllegalArgumentException("NFT not found");
        }
        return merkleUniqueTokenFromNft(optionalNFT);
    }

    @NonNull
    /***
     * Converts a {@link Nft} to a {@link com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken}.
     * @param nft the {@link Nft} to convert
     * @return the {@link com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken}
     */
    public static com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken merkleUniqueTokenFromNft(
            @NonNull Nft nft) {
        requireNonNull(nft);
        final com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken merkleUniqueToken =
                new com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken();

        if (nft.hasNftId()) {
            merkleUniqueToken.setKey(EntityNumPair.fromNums(
                    EntityNum.fromLong(nft.nftId().tokenId().tokenNum()),
                    EntityNum.fromLong(nft.nftId().serialNumber())));
        }
        merkleUniqueToken.setOwner(EntityId.fromGrpcAccountId(PbjConverter.fromPbj(nft.ownerId())));
        merkleUniqueToken.setSpender(EntityId.fromGrpcAccountId(PbjConverter.fromPbj(nft.spenderId())));
        merkleUniqueToken.setPackedCreationTime(
                BitPackUtils.packedTime(nft.mintTime().seconds(), nft.mintTime().nanos()));
        merkleUniqueToken.setMetadata(nft.metadata().toByteArray());

        if (nft.hasOwnerPreviousNftId()) {
            merkleUniqueToken.setPrev(new NftNumPair(
                    nft.ownerPreviousNftId().tokenId().tokenNum(),
                    nft.ownerPreviousNftId().serialNumber()));
        }

        if (nft.hasOwnerNextNftId()) {
            merkleUniqueToken.setNext(new NftNumPair(
                    nft.ownerNextNftId().tokenId().tokenNum(),
                    nft.ownerNextNftId().serialNumber()));
        }

        return merkleUniqueToken;
    }
}
