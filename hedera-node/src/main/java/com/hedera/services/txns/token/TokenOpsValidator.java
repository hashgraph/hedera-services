package com.hedera.services.txns.token;


/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public final class TokenOpsValidator {
	/**
	 * Validate the token operations mint/wipe/burn  given the attributes of the transaction.
	 * @param nftCount
	 *			The number of unique nfts to mint/wipe/burn.
	 * @param fungibleCount
	 * 			The number of fungible common token to mint/wipe/burn.
	 * @param areNftEnabled
	 * 			A boolean that specifies if NFTs are enabled in the network.
	 * @param invalidTokenAmount
	 * 			Respective response code for invalid token amount in mint/wipe/burn operations.
	 * @param metaDataList
	 * 			metadata of the nfts  being minted / serialNumber list of the burn/wipe operations.
	 * @param batchSizeCheck
	 * 			validation method to check if the batch size of requested nfts is valid.
	 * @param nftMetaDataCheck
	 * 			validation method to check if the metadata of minted nft is valid.
	 * @return
	 * 			The validity of the token operation.
	 */
	public static ResponseCodeEnum validateTokenCountsWith(
			final int nftCount,
			final long fungibleCount,
			final boolean areNftEnabled,
			final ResponseCodeEnum invalidTokenAmount,
			final List<Object> metaDataList,
			final IntFunction<ResponseCodeEnum> batchSizeCheck,
			@Nullable Function<byte[], ResponseCodeEnum> nftMetaDataCheck)  {

		if (nftCount > 0 && !areNftEnabled) {
			return NOT_SUPPORTED;
		}

		boolean bothPresent = (fungibleCount > 0 && nftCount > 0);
		boolean nonePresent = (fungibleCount <= 0 && nftCount == 0);
		if (nonePresent) {
			return invalidTokenAmount;
		}
		if (bothPresent) {
			return INVALID_TRANSACTION_BODY;
		}

		if (fungibleCount <= 0 && nftCount > 0) {
			/* validate the nft data */
			var validity = batchSizeCheck.apply(nftCount);
			if (validity != OK) {
				return validity;
			}
			if (nftMetaDataCheck != null) {
				for (Object bytes : metaDataList) {
					validity = nftMetaDataCheck.apply( ((ByteString) bytes).toByteArray());
					if (validity != OK) {
						return validity;
					}
				}
			} else {
				for (Object serialNum : metaDataList) {
					if ((long) serialNum <= 0) {
						return INVALID_NFT_ID;
					}
				}
			}
		}
		return OK;
	}

	private TokenOpsValidator()  {
		throw new UnsupportedOperationException("Utility Class");
	}
}
