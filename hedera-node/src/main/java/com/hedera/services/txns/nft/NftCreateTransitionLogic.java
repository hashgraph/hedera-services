package com.hedera.services.txns.nft;

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
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.merkle.MerkleNftOwnership;
import com.hedera.services.store.nft.NftStore;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.NftCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.state.merkle.MerkleNftOwnership.NUM_NFT_SERIAL_NO_BYTES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class NftCreateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(NftCreateTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

	private final NftStore store;
	private final HederaLedger ledger;
	private final TransactionContext txnCtx;
	private final OptionValidator validator;

	public NftCreateTransitionLogic(
			NftStore store,
			HederaLedger ledger,
			OptionValidator validator,
			TransactionContext txnCtx
	) {
		this.store = store;
		this.ledger = ledger;
		this.txnCtx = txnCtx;
		this.validator = validator;
	}

	@Override
	public void doStateTransition() {
		try {
			transitionFor(txnCtx.accessor().getTxn().getNftCreate());
		} catch (Exception e) {
			log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxn4Log(), e);
			txnCtx.setStatus(FAIL_INVALID);
		}
	}

	private void transitionFor(NftCreateTransactionBody op) {
		var result = store.createProvisionally(op, txnCtx.activePayer(), txnCtx.consensusTime().getEpochSecond());
		if (result.getStatus() != OK) {
			abortWith(result.getStatus());
			return;
		}

		var created = result.getCreated().get();
		var treasury = op.getTreasury();
		var status = OK;
		status = store.associate(treasury, List.of(created));
		if (status != OK) {
			abortWith(status);
			return;
		}

		var origSerialNos = IntStream.range(0, op.getSerialNoCount())
				.mapToObj(i -> "SN" + i)
				.map(NftCreateTransitionLogic::asSerialNo)
				.collect(Collectors.toList());
		status = store.mint(created, origSerialNos);
		if (status != OK) {
			abortWith(status);
			return;
		}

		store.commitCreation();
		txnCtx.setCreated(created);
		txnCtx.setStatus(SUCCESS);
	}

	public static ByteString asSerialNo(String shortUtf8) {
		int used = shortUtf8.length();
		byte[] bytes = new byte[NUM_NFT_SERIAL_NO_BYTES];
		System.arraycopy(shortUtf8.getBytes(), 0, bytes, 0, used);
		Arrays.fill(bytes, used, NUM_NFT_SERIAL_NO_BYTES, " ".getBytes()[0]);
		return ByteString.copyFrom(bytes);
	}

	private void abortWith(ResponseCodeEnum cause) {
		if (store.isCreationPending()) {
			store.rollbackCreation();
		}
		ledger.dropPendingNftChanges();
		txnCtx.setStatus(cause);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasNftCreate;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		var validity = OK;
		NftCreateTransactionBody op = txnBody.getNftCreate();
		return validity;
	}
}
