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
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.files.HederaFs;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.world.HederaWorldState;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.contract.process.CreateEvmTxProcessor;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.CommonUtils;
import org.apache.tuweni.bytes.Bytes;

import javax.inject.Inject;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.utils.EntityIdUtils.asContract;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_FILE_EMPTY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERIALIZATION_FAILED;

public class ContractCreateTransitionLogic implements TransitionLogic {

	private final HederaFs hfs;
	private final EntityIdSource ids;
	private final AccountStore accountStore;
	private final OptionValidator validator;
	private final HbarCentExchange exchange;
	private final TransactionContext txnCtx;
	private final HederaWorldState worldState;
	private final UsagePricesProvider usagePrices;
	private final GlobalDynamicProperties properties;
	private final TransactionRecordService recordService;

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	@Inject
	public ContractCreateTransitionLogic(
			HederaFs hfs,
			EntityIdSource ids,
			TransactionContext txnCtx,
			AccountStore accountStore,
			OptionValidator validator,
			HbarCentExchange exchange,
			HederaWorldState worldState,
			UsagePricesProvider usagePrices,
			GlobalDynamicProperties properties,
			TransactionRecordService recordService
	) {
		this.ids = ids;
		this.hfs = hfs;
		this.txnCtx = txnCtx;
		this.exchange = exchange;
		this.validator = validator;
		this.worldState = worldState;
		this.properties = properties;
		this.usagePrices = usagePrices;
		this.accountStore = accountStore;
		this.recordService = recordService;
	}

	@Override
	public void doStateTransition() {
		// TODO handle MAX_CONTRACT_STORAGE_EXCEEDED

		/* --- Translate from gRPC types --- */
		var contractCreateTxn = txnCtx.accessor().getTxn();
		var op = contractCreateTxn.getContractCreateInstance();
		final var senderId = Id.fromGrpcAccount(contractCreateTxn.getTransactionID().getAccountID());
		final var proxyAccount = op.hasProxyAccountID() ? Id.fromGrpcAccount(op.getProxyAccountID()) : Id.DEFAULT;
		var key = op.hasAdminKey() ?
				validator.attemptToDecodeOrThrow(op.getAdminKey(), SERIALIZATION_FAILED) :
				new JContractIDKey(asContract(senderId.asGrpcAccount()));

		/* --- Load the model objects --- */
		final var sender = accountStore.loadAccount(senderId);
		final var codeWithConstructorArgs = prepareCodeWithConstructorArguments(op);

		/* --- Do the business logic --- */
		final var newContractId = worldState.newContractId(sender.getId().asEvmAddress());
		final var txProcessor = new CreateEvmTxProcessor(
				exchange,
				worldState.updater(),
				usagePrices,
				properties,
				newContractId.asEvmAddress()
		);
		final var result = txProcessor.execute(
				sender,
				op.getGas(),
				op.getInitialBalance(),
				codeWithConstructorArgs,
				txnCtx.consensusTime()
		);
		/* In case the EVM runs into RE */
		validateFalse(result.isInvalid(), FAIL_INVALID, result.getInvalidReason());

		if (result.isSuccessful()) {
			worldState.addPropertiesFor(newContractId.asEvmAddress(), op.getMemo(), key, proxyAccount);
		} else {
			worldState.reclaimContractId();
		}

		/* --- Persist changes into state --- */
		worldState.persist();

		/* --- Externalise changes --- */
		recordService.externaliseCreateEvmTransaction(result.isSuccessful() ? newContractId : null, result);
	}


	@Override
	public void reclaimCreatedIds() {
		ids.reclaimProvisionalIds();
	}

	@Override
	public void resetCreatedIds() {
		ids.resetProvisionalIds();
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasContractCreateInstance;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
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

		return validator.memoCheck(op.getMemo());
	}

	private Bytes prepareCodeWithConstructorArguments(ContractCreateTransactionBody op) {
		var bytecodeSrc = op.getFileID();
		validateTrue(hfs.exists(bytecodeSrc), INVALID_FILE_ID);
		byte[] bytecode = hfs.cat(bytecodeSrc);
		validateFalse(bytecode.length == 0, CONTRACT_FILE_EMPTY);

		String contractByteCodeString = new String(bytecode);
		if (!op.getConstructorParameters().isEmpty()) {
			final var constructorParamsHexString = CommonUtils.hex(
					op.getConstructorParameters().toByteArray());
			contractByteCodeString += constructorParamsHexString;
		}
		return Bytes.fromHexString(contractByteCodeString);
	}
}
