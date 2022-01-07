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
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.files.HederaFs;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.HederaMutableWorldState;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.CommonUtils;
import org.apache.tuweni.bytes.Bytes;

import javax.inject.Inject;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.contractParsedFromSolidityAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_FILE_EMPTY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERIALIZATION_FAILED;

public class ContractCreateTransitionLogic implements TransitionLogic {
	private static final JContractIDKey STANDIN_CONTRACT_ID_KEY = new JContractIDKey(0, 0, 0);

	private final HederaFs hfs;
	private final AccountStore accountStore;
	private final OptionValidator validator;
	private final TransactionContext txnCtx;
	private final HederaMutableWorldState worldState;
	private final TransactionRecordService recordService;
	private final CreateEvmTxProcessor evmTxProcessor;
	private final HederaLedger hederaLedger;
	private final GlobalDynamicProperties properties;
	private final SigImpactHistorian sigImpactHistorian;

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	@Inject
	public ContractCreateTransitionLogic(
			final HederaFs hfs,
			final TransactionContext txnCtx,
			final AccountStore accountStore,
			final OptionValidator validator,
			final HederaWorldState worldState,
			final TransactionRecordService recordService,
			final CreateEvmTxProcessor evmTxProcessor,
			final HederaLedger hederaLedger,
			final GlobalDynamicProperties properties,
			final SigImpactHistorian sigImpactHistorian
	) {
		this.hfs = hfs;
		this.txnCtx = txnCtx;
		this.validator = validator;
		this.worldState = worldState;
		this.accountStore = accountStore;
		this.recordService = recordService;
		this.sigImpactHistorian = sigImpactHistorian;
		this.evmTxProcessor = evmTxProcessor;
		this.hederaLedger = hederaLedger;
		this.properties = properties;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		var contractCreateTxn = txnCtx.accessor().getTxn();
		var op = contractCreateTxn.getContractCreateInstance();
		final var senderId = Id.fromGrpcAccount(contractCreateTxn.getTransactionID().getAccountID());
		final var proxyAccount = op.hasProxyAccountID() ? Id.fromGrpcAccount(op.getProxyAccountID()) : Id.DEFAULT;
		var key = op.hasAdminKey()
				? validator.attemptToDecodeOrThrow(op.getAdminKey(), SERIALIZATION_FAILED)
				: STANDIN_CONTRACT_ID_KEY;

		/* --- Load the model objects --- */
		final var sender = accountStore.loadAccount(senderId);
		final var codeWithConstructorArgs = prepareCodeWithConstructorArguments(op);
		long expiry = RequestBuilder.getExpirationTime(txnCtx.consensusTime(), op.getAutoRenewPeriod()).getSeconds();

		/* --- Do the business logic --- */
		final var newContractAddress = worldState.newContractAddress(sender.getId().asEvmAddress());
		final var result = evmTxProcessor.execute(
				sender,
				newContractAddress,
				op.getGas(),
				op.getInitialBalance(),
				codeWithConstructorArgs,
				txnCtx.consensusTime(),
				expiry);

		/* --- Persist changes into state --- */
		final var createdContracts = worldState.persistProvisionalContractCreations();
		result.setCreatedContracts(createdContracts);

		if (result.isSuccessful()) {
			/* --- Create customizer for the newly created contract --- */
			final var account = accountParsedFromSolidityAddress(newContractAddress);
			if (key == STANDIN_CONTRACT_ID_KEY) {
				key = new JContractIDKey(account.getShardNum(), account.getRealmNum(), account.getAccountNum());
			}
			final var customizer = new HederaAccountCustomizer()
					.key(key)
					.memo(op.getMemo())
					.proxy(proxyAccount.asEntityId())
					.expiry(expiry)
					.autoRenewPeriod(op.getAutoRenewPeriod().getSeconds())
					.isSmartContract(true);
			hederaLedger.customizePotentiallyDeleted(account, customizer);
		} else {
			worldState.reclaimContractId();
		}
		/* --- Customize sponsored Accounts */
		worldState.customizeSponsoredAccounts();

		/* --- Externalise changes --- */
		for (final var createdContract : createdContracts) {
			sigImpactHistorian.markEntityChanged(createdContract.getContractNum());
		}
		if (result.isSuccessful()) {
			final var newContractId = contractParsedFromSolidityAddress(newContractAddress.toArray());
			sigImpactHistorian.markEntityChanged(newContractId.getContractNum());
			txnCtx.setCreated(newContractId);
		}
		recordService.externaliseEvmCreateTransaction(result);
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
		if (op.getGas() > properties.maxGas()) {
			return MAX_GAS_LIMIT_EXCEEDED;
		}
		return validator.memoCheck(op.getMemo());
	}

	Bytes prepareCodeWithConstructorArguments(ContractCreateTransactionBody op) {
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
		try {
			return Bytes.fromHexString(contractByteCodeString);
		} catch (IllegalArgumentException e) {
			throw new InvalidTransactionException(ResponseCodeEnum.ERROR_DECODING_BYTESTRING);
		}
	}
}
