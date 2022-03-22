package com.hedera.services.txns.ethereum;

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
import com.hedera.services.txns.PreFetchableTransition;
import com.hedera.services.txns.contract.ContractCallTransitionLogic;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.math.BigInteger;
import java.util.function.Function;
import java.util.function.Predicate;

public class EthereumTransitionLogic implements PreFetchableTransition {
	private static final Logger log = LogManager.getLogger(EthereumTransitionLogic.class);

	private static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(100_000_000);

	private final TransactionContext txnCtx;
	private final ExpandHandleSpanMapAccessor spanMapAccessor;
	private final ContractCallTransitionLogic contractCallTransitionLogic;

	@Inject
	public EthereumTransitionLogic(
			final TransactionContext txnCtx,
			final ExpandHandleSpanMapAccessor spanMapAccessor,
			final ContractCallTransitionLogic contractCallTransitionLogic
	) {
		this.txnCtx = txnCtx;
		this.spanMapAccessor = spanMapAccessor;
		this.contractCallTransitionLogic = contractCallTransitionLogic;
	}

	@Override
	public void doStateTransition() {
		var syntheticTxBody = spanMapAccessor.getEthTxBodyMeta(txnCtx.accessor());
		var txBody = txnCtx.accessor().getTxn();


		if (syntheticTxBody.hasContractCall()) {
			contractCallTransitionLogic.doStateTransitionOperation(
					syntheticTxBody,
					EntityNum.fromAccountId(txBody.getEthereumTransaction().getSenderId()).toId());
			//FIXME add gas, amount, callData to the TransactionRecord.
		}
		//FIXME ContractCreate
		//TODO when processing token and topic calls add a new child tx record.
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasEthereumTransaction;
	}

	@Override
	public ResponseCodeEnum validateSemantics(TxnAccessor accessor) {
		var txBody = spanMapAccessor.getEthTxBodyMeta(accessor);
		//FIXME
		// validate transactionData is set
		// validate senderId is set

		if (txBody == null) {
			// in submit precheck, not consensus precheck.  Don't validate synthetic TXes.
			return ResponseCodeEnum.OK;
		} else if (txBody.hasContractCall()) {
			//FIXME eth specific checks
			// does sender match the extracted signature?
			// is the nonce valid?
			// is the chainID correct?

			return contractCallTransitionLogic.semanticCheck().apply(txBody);
		} else { //FIXME
			return ResponseCodeEnum.NOT_SUPPORTED; //FIXME create proper protobuf response code
		}
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		// since we override `validateSemantics` this is to hedge against this accidentally being called
		// and ensure we always fail if we depend on this overridden path.
		return ignore -> ResponseCodeEnum.NOT_SUPPORTED;
	}

	@Override
	public void preFetch(final TxnAccessor accessor) {
		var ethTxData = spanMapAccessor.getEthTxDataMeta(accessor);
		spanMapAccessor.setEthTxSigsMeta(accessor, ethTxData.extractSignatures());

		//TODO short circuit direct calls to tokens and topics
		if (ethTxData.to() != null) {
			var op = ContractCallTransactionBody.newBuilder()
					.setFunctionParameters(
							ByteString.copyFrom(ethTxData.data(), ethTxData.callDataStart(),
									ethTxData.callDataLength()))
					.setGas(ethTxData.gasLimit())
					.setAmount(ethTxData.value().divide(WEIBARS_TO_TINYBARS).longValueExact())
					.setContractID(EntityIdUtils.contractIdFromEvmAddress(ethTxData.to())).build();
			var txBody = TransactionBody.newBuilder().setContractCall(op).build();
			spanMapAccessor.setEthTxBodyMeta(accessor, txBody);
			contractCallTransitionLogic.preFetchOperation(op);
		}
	}
}

