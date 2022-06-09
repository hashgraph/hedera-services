package com.hedera.services.store.contracts.precompile.impl;

/*-
 * ‌
 * Hedera Services Node
 *
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
 * ‍
 */

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;

import javax.inject.Provider;
import java.util.function.UnaryOperator;

import static com.hedera.services.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;

public class OwnerOfPrecompile extends ERCReadOnlyAbstractPrecompile {
	private NftId nftId;

	public OwnerOfPrecompile(
			final TokenID tokenId,
			final SyntheticTxnFactory syntheticTxnFactory,
			final WorldLedgers ledgers,
			final EncodingFacade encoder,
			final DecodingFacade decoder,
			final Provider<FeeCalculator> feeCalculator,
			final UsagePricesProvider resourceCosts,
			final StateView currentView,
			final PrecompilePricingUtils pricingUtils
	) {
		super(tokenId, syntheticTxnFactory, ledgers, encoder, decoder, feeCalculator, resourceCosts, currentView, pricingUtils);
	}

	@Override
	public long getGasRequirement(long blockTimestamp) {
		final var now = Timestamp.newBuilder().setSeconds(blockTimestamp).build();
		return pricingUtils.computeViewFunctionGas(now, getMinimumFeeInTinybars(now), feeCalculator, resourceCosts, currentView);
	}

	@Override
	public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		final var wrapper = decoder.decodeOwnerOf(input.slice(24));
		nftId = new NftId(tokenId.getShardNum(), tokenId.getRealmNum(), tokenId.getTokenNum(), wrapper.serialNo());
		return super.body(input, aliasResolver);
	}

	@Override
	public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
		final var nftsLedger = ledgers.nfts();
		validateTrueOrRevert(nftsLedger.contains(nftId), INVALID_TOKEN_NFT_SERIAL_NUMBER);
		final var owner = ledgers.ownerOf(nftId);
		final var priorityAddress = ledgers.canonicalAddress(owner);
		return encoder.encodeOwner(priorityAddress);
	}
}
