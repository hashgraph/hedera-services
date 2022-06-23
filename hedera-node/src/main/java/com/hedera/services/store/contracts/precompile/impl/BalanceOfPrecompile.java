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

import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BalanceOfWrapper;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;

import java.util.function.UnaryOperator;

public class BalanceOfPrecompile extends AbstractReadOnlyPrecompile {
	private BalanceOfWrapper balanceWrapper;

	public BalanceOfPrecompile(
			final TokenID tokenId,
			final SyntheticTxnFactory syntheticTxnFactory,
			final WorldLedgers ledgers,
			final EncodingFacade encoder,
			final DecodingFacade decoder,
			final PrecompilePricingUtils pricingUtils) {
		super(tokenId, syntheticTxnFactory, ledgers, encoder, decoder, pricingUtils);
	}

	@Override
	public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		final var nestedInput = input.slice(24);
		balanceWrapper = decoder.decodeBalanceOf(nestedInput, aliasResolver);
		return super.body(input, aliasResolver);
	}

	@Override
	public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
		final var balance = ledgers.balanceOf(balanceWrapper.accountId(), tokenId);
		return encoder.encodeBalance(balance);
	}
}
