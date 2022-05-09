package com.hedera.services.txns.contract;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.CreateEvmTxProcessor;
import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.files.HederaFs;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.HederaMutableWorldState;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.CommonUtils;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.tuweni.bytes.Bytes;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.ledger.accounts.ContractCustomizer.fromHapiCreation;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_FILE_EMPTY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERIALIZATION_FAILED;

@Singleton
public class ContractCreateTransitionLogic implements TransitionLogic {
	public static final JContractIDKey STANDIN_CONTRACT_ID_KEY = new JContractIDKey(0, 0, 0);

	private final HederaFs hfs;
	private final AccountStore accountStore;
	private final OptionValidator validator;
	private final TransactionContext txnCtx;
	private final HederaMutableWorldState worldState;
	private final TransactionRecordService recordService;
	private final CreateEvmTxProcessor evmTxProcessor;
	private final GlobalDynamicProperties properties;
	private final SigImpactHistorian sigImpactHistorian;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;

	@Inject
	public ContractCreateTransitionLogic(
			final HederaFs hfs,
			final TransactionContext txnCtx,
			final AccountStore accountStore,
			final OptionValidator validator,
			final HederaWorldState worldState,
			final TransactionRecordService recordService,
			final CreateEvmTxProcessor evmTxProcessor,
			final GlobalDynamicProperties properties,
			final SigImpactHistorian sigImpactHistorian,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts
	) {
		this.hfs = hfs;
		this.txnCtx = txnCtx;
		this.validator = validator;
		this.worldState = worldState;
		this.accountStore = accountStore;
		this.recordService = recordService;
		this.sigImpactHistorian = sigImpactHistorian;
		this.evmTxProcessor = evmTxProcessor;
		this.properties = properties;
		this.accounts = accounts;
	}

	@Override
	public void doStateTransition() {
		// --- Translate from gRPC types ---
		var contractCreateTxn = txnCtx.accessor().getTxn();
		var op = contractCreateTxn.getContractCreateInstance();
		final var senderId = Id.fromGrpcAccount(contractCreateTxn.getTransactionID().getAccountID());
		var key = op.hasAdminKey()
				? validator.attemptToDecodeOrThrow(op.getAdminKey(), SERIALIZATION_FAILED)
				: STANDIN_CONTRACT_ID_KEY;
		// Standardize immutable contract key format; c.f. https://github.com/hashgraph/hedera-services/issues/3037
		if (key.isEmpty()) {
			key = STANDIN_CONTRACT_ID_KEY;
		}

		// --- Load the model objects ---
		final var sender = accountStore.loadAccount(senderId);
		final var consensusTime = txnCtx.consensusTime();
		final var codeWithConstructorArgs = prepareCodeWithConstructorArguments(op);
		final var expiry = consensusTime.getEpochSecond() + op.getAutoRenewPeriod().getSeconds();
		final var newContractAddress = worldState.newContractAddress(sender.getId().asEvmAddress());

		// --- Do the business logic ---
		worldState.setHapiSenderCustomizer(fromHapiCreation(key, consensusTime, op));
		TransactionProcessingResult result;
		try {
			result = evmTxProcessor.execute(
					sender,
					newContractAddress,
					op.getGas(),
					op.getInitialBalance(),
					codeWithConstructorArgs,
					consensusTime,
					expiry);
		} finally {
			worldState.resetHapiSenderCustomizer();
		}

		// --- Persist changes into state ---
		final var createdContracts = worldState.getCreatedContractIds();
		result.setCreatedContracts(createdContracts);

		if (!result.isSuccessful()) {
			worldState.reclaimContractId();
		}

		// --- Externalise changes
		for (final var createdContract : createdContracts) {
			sigImpactHistorian.markEntityChanged(createdContract.getContractNum());
		}
		if (result.isSuccessful()) {
			final var newEvmAddress = newContractAddress.toArrayUnsafe();
			final var newContractId = contractIdFromEvmAddress(newEvmAddress);
			sigImpactHistorian.markEntityChanged(newContractId.getContractNum());
			txnCtx.setTargetedContract(newContractId);
			recordService.externalizeSuccessfulEvmCreate(result, newEvmAddress);
		} else {
			recordService.externalizeUnsuccessfulEvmCreate(result);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasContractCreateInstance;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
	}

	public ResponseCodeEnum validate(TransactionBody contractCreateTxn) {
		var op = contractCreateTxn.getContractCreateInstance();

		if (!op.hasAutoRenewPeriod() || op.getAutoRenewPeriod().getSeconds() < 1) {
			return INVALID_RENEWAL_PERIOD;
		}
		if (!validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
			return AUTORENEW_DURATION_NOT_IN_RANGE;
		}
		if (op.getGas() < 0) {
			return CONTRACT_NEGATIVE_GAS;
		}
		if (op.getInitialBalance() < 0) {
			return CONTRACT_NEGATIVE_VALUE;
		}
		if (op.getGas() > properties.maxGas()) {
			return MAX_GAS_LIMIT_EXCEEDED;
		}
		if (op.hasProxyAccountID()) {
			return PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
		}
		if (!validator.isValidStakedIdIfPresent(op.getStakedAccountId(), op.getStakedNodeId(), accounts.get())) {
			return INVALID_STAKING_ID;
		}
		return validator.memoCheck(op.getMemo());
	}

	Bytes prepareCodeWithConstructorArguments(ContractCreateTransactionBody op) {
		var bytecodeSrc = op.getFileID();
		validateTrue(hfs.exists(bytecodeSrc), INVALID_FILE_ID);
		byte[] bytecode = hfs.cat(bytecodeSrc);
		validateFalse(bytecode.length == 0, CONTRACT_FILE_EMPTY);

		var contractByteCodeString = new String(bytecode);
		if (!op.getConstructorParameters().isEmpty()) {
			final var constructorParamsHexString = CommonUtils.hex(op.getConstructorParameters().toByteArray());
			contractByteCodeString += constructorParamsHexString;
		}
		try {
			return Bytes.fromHexString(contractByteCodeString);
		} catch (IllegalArgumentException e) {
			throw new InvalidTransactionException(ResponseCodeEnum.ERROR_DECODING_BYTESTRING);
		}
	}
}
