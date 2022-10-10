/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

public final class TokenOpsValidator {
    /**
     * Validate the token operations mint/wipe/burn given the attributes of the transaction.
     *
     * @param nftCount The number of unique nfts to mint/wipe/burn.
     * @param fungibleCount The number of fungible common token to mint/wipe/burn.
     * @param areNftEnabled A boolean that specifies if NFTs are enabled in the network.
     * @param invalidTokenAmountResponse Respective response code for invalid token amount in
     *     mint/wipe/burn operations.
     * @param metaDataList either metadata of the nfts being minted or serialNumber list of the
     *     burn/wipe operations.
     * @param batchSizeCheck validation method to check if the batch size of requested nfts is
     *     valid.
     * @param nftMetaDataCheck validation method to check if the metadata of minted nft is valid.
     * @return The validity of the token operation.
     */
    public static ResponseCodeEnum validateTokenOpsWith(
            final int nftCount,
            final long fungibleCount,
            final boolean areNftEnabled,
            final ResponseCodeEnum invalidTokenAmountResponse,
            final List<ByteString> metaDataList,
            final IntFunction<ResponseCodeEnum> batchSizeCheck,
            Function<byte[], ResponseCodeEnum> nftMetaDataCheck) {
        var validity =
                validateCounts(
                        nftCount,
                        fungibleCount,
                        areNftEnabled,
                        invalidTokenAmountResponse,
                        batchSizeCheck);

        if (validity != OK) {
            return validity;
        }

        if (fungibleCount <= 0 && nftCount > 0) {
            return validateMetaData(metaDataList, nftMetaDataCheck);
        }
        return OK;
    }

    /**
     * Validate the token operations mint/wipe/burn given the attributes of the transaction.
     *
     * @param nftCount The number of unique nfts to mint/wipe/burn.
     * @param fungibleCount The number of fungible common token to mint/wipe/burn.
     * @param areNftEnabled A boolean that specifies if NFTs are enabled in the network.
     * @param invalidTokenAmountResponse Respective response code for invalid token amount in
     *     mint/wipe/burn operations.
     * @param serialNos either metadata of the nfts being minted or serialNumber list of the
     *     burn/wipe operations.
     * @param batchSizeCheck validation method to check if the batch size of requested nfts is
     *     valid.
     * @return The validity of the token operation.
     */
    public static ResponseCodeEnum validateTokenOpsWith(
            final int nftCount,
            final long fungibleCount,
            final boolean areNftEnabled,
            final ResponseCodeEnum invalidTokenAmountResponse,
            final List<Long> serialNos,
            final IntFunction<ResponseCodeEnum> batchSizeCheck) {
        var validity =
                validateCounts(
                        nftCount,
                        fungibleCount,
                        areNftEnabled,
                        invalidTokenAmountResponse,
                        batchSizeCheck);

        if (validity != OK) {
            return validity;
        }

        if (fungibleCount <= 0 && nftCount > 0) {
            return validateSerialNumbers(serialNos);
        }
        return OK;
    }

    private static ResponseCodeEnum validateCounts(
            final int nftCount,
            final long fungibleCount,
            final boolean areNftEnabled,
            final ResponseCodeEnum invalidTokenAmount,
            final IntFunction<ResponseCodeEnum> batchSizeCheck) {
        if (nftCount > 0 && !areNftEnabled) {
            return NOT_SUPPORTED;
        }

        boolean bothPresent = (fungibleCount > 0 && nftCount > 0);
        if (bothPresent) {
            return INVALID_TRANSACTION_BODY;
        }

        if (fungibleCount < 0) {
            return invalidTokenAmount;
        }

        if (fungibleCount <= 0 && nftCount > 0) {
            return batchSizeCheck.apply(nftCount);
        }
        return OK;
    }

    private static ResponseCodeEnum validateMetaData(
            final List<ByteString> metaDataList,
            Function<byte[], ResponseCodeEnum> nftMetaDataCheck) {
        for (var bytes : metaDataList) {
            var validity = nftMetaDataCheck.apply(bytes.toByteArray());
            if (validity != OK) {
                return validity;
            }
        }
        return OK;
    }

    private static ResponseCodeEnum validateSerialNumbers(final List<Long> serialNos) {
        for (var serialNum : serialNos) {
            if (serialNum <= 0) {
                return INVALID_NFT_ID;
            }
        }
        return OK;
    }

    private TokenOpsValidator() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
