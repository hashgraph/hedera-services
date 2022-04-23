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

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.files.HederaFs;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.PreFetchableTransition;
import com.hedera.services.txns.contract.ContractCallTransitionLogic;
import com.hedera.services.txns.contract.ContractCreateTransitionLogic;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.codec.DecoderException;
import org.bouncycastle.util.encoders.Hex;

import javax.inject.Inject;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static com.hedera.services.ledger.properties.AccountProperty.PROXY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_FILE_EMPTY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;

public class EthereumTransitionLogic implements PreFetchableTransition {

	private static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);

	private final TransactionContext txnCtx;
	private final ExpandHandleSpanMapAccessor spanMapAccessor;
	private final ContractCallTransitionLogic contractCallTransitionLogic;
	private final ContractCreateTransitionLogic contractCreateTransitionLogic;
	private final TransactionRecordService recordService;
	private final AliasManager aliasManager;
	private final HederaFs hfs;
	private final byte[] chainId;
	private final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	private final GlobalDynamicProperties globalDynamicProperties;

	@Inject
	public EthereumTransitionLogic(
			final TransactionContext txnCtx,
			final ExpandHandleSpanMapAccessor spanMapAccessor,
			final ContractCallTransitionLogic contractCallTransitionLogic,
			final ContractCreateTransitionLogic contractCreateTransitionLogic,
			final TransactionRecordService recordService,
			final HederaFs hfs,
			final GlobalDynamicProperties globalDynamicProperties,
			final AliasManager aliasManager,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
		this.txnCtx = txnCtx;
		this.spanMapAccessor = spanMapAccessor;
		this.contractCallTransitionLogic = contractCallTransitionLogic;
		this.contractCreateTransitionLogic = contractCreateTransitionLogic;
		this.recordService = recordService;
		this.hfs = hfs;
		this.chainId = Integers.toBytes(globalDynamicProperties.getChainId());
		this.aliasManager = aliasManager;
		this.accountsLedger = accountsLedger;
		this.globalDynamicProperties = globalDynamicProperties;
	}

	@Override
	public void doStateTransition() {
		var syntheticTxBody = getOrCreateTransactionBody(txnCtx.accessor());
		EthTxData ethTxData = spanMapAccessor.getEthTxDataMeta(txnCtx.accessor());
		maybeUpdateCallData(txnCtx.accessor(), ethTxData, txnCtx.accessor().getTxn().getEthereumTransaction());
		var ethTxSigs = getOrCreateEthSigs(txnCtx.accessor(), ethTxData);

		var callingAccount = aliasManager.lookupIdBy(ByteString.copyFrom(ethTxSigs.address()));

		if (syntheticTxBody.hasContractCall()) {
			contractCallTransitionLogic.doStateTransitionOperation(syntheticTxBody, callingAccount.toId(), true);
		} else if (syntheticTxBody.hasContractCreateInstance()) {
			final var synthOp =
					addInheritablePropertiesToContractCreate(syntheticTxBody.getContractCreateInstance(), callingAccount.toGrpcAccountId());
			syntheticTxBody = TransactionBody.newBuilder().setContractCreateInstance(synthOp).build();
			spanMapAccessor.setEthTxBodyMeta(txnCtx.accessor(), syntheticTxBody);
			contractCreateTransitionLogic.doStateTransitionOperation(syntheticTxBody, callingAccount.toId(), true);
		}
		recordService.updateForEvmCall(ethTxData, callingAccount.toEntityId());
	}

	private TransactionBody getOrCreateTransactionBody(final TxnAccessor txnCtx) {
		var txBody = spanMapAccessor.getEthTxBodyMeta(txnCtx);
		if (txBody == null) {
			txBody = createSyntheticTransactionBody(spanMapAccessor.getEthTxDataMeta(txnCtx));
			spanMapAccessor.setEthTxBodyMeta(txnCtx, txBody);
		}
		return txBody;
	}

	private EthTxSigs getOrCreateEthSigs(final TxnAccessor txnCtx, EthTxData ethTxData) {
		var ethTxSigs = spanMapAccessor.getEthTxSigsMeta(txnCtx);
		if (ethTxSigs == null) {
			ethTxSigs = EthTxSigs.extractSignatures(ethTxData);
			spanMapAccessor.setEthTxSigsMeta(txnCtx, ethTxSigs);
		}
		return ethTxSigs;
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasEthereumTransaction;
	}

	@Override
	public ResponseCodeEnum validateSemantics(TxnAccessor accessor) {
		var ethTxData = spanMapAccessor.getEthTxDataMeta(accessor);
		var txBody = getOrCreateTransactionBody(accessor);

		if (ethTxData.chainId().length == 0 || Arrays.compare(chainId, ethTxData.chainId()) != 0) {
			return ResponseCodeEnum.WRONG_CHAIN_ID;
		}

		if (accessor.getExpandedSigStatus() == ResponseCodeEnum.OK) {
			// this is not precheck, so do more involved checks
			maybeUpdateCallData(accessor, ethTxData, accessor.getTxn().getEthereumTransaction());
			var ethTxSigs = getOrCreateEthSigs(txnCtx.accessor(), ethTxData);
			var callingAccount = aliasManager.lookupIdBy(ByteString.copyFrom(ethTxSigs.address()));
			if (callingAccount == null) {
				return ResponseCodeEnum.INVALID_ACCOUNT_ID; 
			}

			var accountNonce = (long) accountsLedger.get(callingAccount.toGrpcAccountId(), AccountProperty.ETHEREUM_NONCE);
			if (ethTxData.nonce() != accountNonce) {
				return ResponseCodeEnum.WRONG_NONCE;
			}
		}

		if (txBody.hasContractCall()) {
			return contractCallTransitionLogic.semanticCheck().apply(txBody);
		} else if (txBody.hasContractCreateInstance()) {
			return contractCreateTransitionLogic.semanticCheck().apply(txBody);
		} else {
			// This should only happen when we update the createSyntheticTransactionBody
			// and then forget to update this code.
			return ResponseCodeEnum.FAIL_INVALID;
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

		EthereumTransactionBody op = accessor.getTxn().getEthereumTransaction();
		ethTxData = maybeUpdateCallData(accessor, ethTxData, op);

		var ethTxSigs = EthTxSigs.extractSignatures(ethTxData);
		spanMapAccessor.setEthTxSigsMeta(accessor, ethTxSigs);

		TransactionBody txBody = createSyntheticTransactionBody(ethTxData);

		spanMapAccessor.setEthTxBodyMeta(accessor, txBody);
		if (txBody.hasContractCall()) {
			contractCallTransitionLogic.preFetchOperation(txBody.getContractCall());
		}
	}

	private EthTxData maybeUpdateCallData(final TxnAccessor accessor, EthTxData ethTxData,
			final EthereumTransactionBody op) {
		if ((ethTxData.callData() == null || ethTxData.callData().length == 0) && op.hasCallData()) {
			var callDataFileId = op.getCallData();
			validateTrue(hfs.exists(callDataFileId), INVALID_FILE_ID);
			byte[] callDataFile = Hex.decode(hfs.cat(callDataFileId));
			validateFalse(callDataFile.length == 0, CONTRACT_FILE_EMPTY);
			ethTxData = ethTxData.replaceCallData(callDataFile);
			spanMapAccessor.setEthTxDataMeta(accessor, ethTxData);
		}
		return ethTxData;
	}

	private TransactionBody createSyntheticTransactionBody(EthTxData ethTxData) {
		// maybe we could short circuit direct calls to tokens and topics?  maybe not
		if (ethTxData.to() != null && ethTxData.to().length != 0) {
			var synthOp = ContractCallTransactionBody.newBuilder()
					.setFunctionParameters(ByteString.copyFrom(ethTxData.callData()))
					.setGas(ethTxData.gasLimit())
					.setAmount(ethTxData.value().divide(WEIBARS_TO_TINYBARS).longValueExact())
					.setContractID(EntityIdUtils.contractIdFromEvmAddress(ethTxData.to())).build();
			return TransactionBody.newBuilder().setContractCall(synthOp).build();
		} else {
			final var autoRenewPeriod =
					Duration.newBuilder().setSeconds(globalDynamicProperties.minAutoRenewDuration()).build();
			var synthOp = ContractCreateTransactionBody.newBuilder()
					.setInitcode(ByteString.copyFrom(ethTxData.callData()))
					.setGas(ethTxData.gasLimit())
					.setInitialBalance(ethTxData.value().divide(WEIBARS_TO_TINYBARS).longValueExact())
					.setAutoRenewPeriod(autoRenewPeriod);
			return TransactionBody.newBuilder().setContractCreateInstance(synthOp).build();
		}
	}

	public ContractCreateTransactionBody addInheritablePropertiesToContractCreate(
			final ContractCreateTransactionBody contractCreateTxnBody,
			final AccountID sender
	) {
		final var synthOp = ContractCreateTransactionBody
				.newBuilder(contractCreateTxnBody)
				.setAdminKey(attemptToDecodeOrThrow((JKey) accountsLedger.get(sender, KEY)))
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds((long) accountsLedger.get(sender, AUTO_RENEW_PERIOD)));
		final var senderMemo = (String) accountsLedger.get(sender, MEMO);
		if (senderMemo != null) {
			synthOp.setMemo(senderMemo);
		}
		final var senderProxy = (EntityId) accountsLedger.get(sender, PROXY);
		if (senderProxy != null && !senderProxy.equals(EntityId.MISSING_ENTITY_ID)) {
			synthOp.setProxyAccountID(senderProxy.toGrpcAccountId());
		}
		return synthOp.build();
	}

	private Key attemptToDecodeOrThrow(final JKey key) {
		try {
			return JKey.mapJKey(key);
		} catch (DecoderException e) {
			throw new InvalidTransactionException(ResponseCodeEnum.SERIALIZATION_FAILED);
		}
	}
}

