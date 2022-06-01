package com.hedera.services.bdd.spec.transactions;

/*-
 * ‌
 * Hedera Services Test Clients
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
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.crypto.ReferenceType;
import com.hedera.services.bdd.spec.transactions.consensus.HapiMessageSubmit;
import com.hedera.services.bdd.spec.transactions.consensus.HapiTopicCreate;
import com.hedera.services.bdd.spec.transactions.consensus.HapiTopicDelete;
import com.hedera.services.bdd.spec.transactions.consensus.HapiTopicUpdate;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCall;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCreate;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractDelete;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractUpdate;
import com.hedera.services.bdd.spec.transactions.contract.HapiEthereumCall;
import com.hedera.services.bdd.spec.transactions.contract.HapiEthereumContractCreate;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoApproveAllowance;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoCreate;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoDelete;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoDeleteAllowance;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoUpdate;
import com.hedera.services.bdd.spec.transactions.file.HapiFileAppend;
import com.hedera.services.bdd.spec.transactions.file.HapiFileCreate;
import com.hedera.services.bdd.spec.transactions.file.HapiFileDelete;
import com.hedera.services.bdd.spec.transactions.file.HapiFileUpdate;
import com.hedera.services.bdd.spec.transactions.network.HapiUncheckedSubmit;
import com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleCreate;
import com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleDelete;
import com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleSign;
import com.hedera.services.bdd.spec.transactions.system.HapiFreeze;
import com.hedera.services.bdd.spec.transactions.system.HapiSysDelete;
import com.hedera.services.bdd.spec.transactions.system.HapiSysUndelete;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenAssociate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenBurn;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenCreate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenDelete;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenDissociate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenFeeScheduleUpdate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenFreeze;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenKycGrant;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenKycRevoke;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenMint;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenPause;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenUnfreeze;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenUnpause;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenUpdate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenWipe;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransferList;
import org.ethereum.core.CallTransaction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiApiSuite.GENESIS;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.CONSTRUCTOR;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.getResourcePath;
import static org.apache.commons.lang3.StringUtils.EMPTY;

public class TxnVerbs {
	/* CRYPTO */
	public static HapiCryptoCreate cryptoCreate(String account) {
		return new HapiCryptoCreate(account);
	}

	public static HapiCryptoDelete cryptoDelete(String account) {
		return new HapiCryptoDelete(account);
	}

	public static HapiCryptoDelete cryptoDeleteAliased(final String alias) {
		return new HapiCryptoDelete(alias, ReferenceType.ALIAS_KEY_NAME);
	}

	@SafeVarargs
	public static HapiCryptoTransfer sortedCryptoTransfer(Function<HapiApiSpec, TransferList>... providers) {
		return new HapiCryptoTransfer(true, providers);
	}

	@SafeVarargs
	public static HapiCryptoTransfer cryptoTransfer(Function<HapiApiSpec, TransferList>... providers) {
		return new HapiCryptoTransfer(providers);
	}

	public static HapiCryptoTransfer cryptoTransfer(BiConsumer<HapiApiSpec, CryptoTransferTransactionBody.Builder> def) {
		return new HapiCryptoTransfer(def);
	}

	public static HapiCryptoTransfer cryptoTransfer(TokenMovement... sources) {
		return new HapiCryptoTransfer(sources);
	}

	public static HapiCryptoUpdate cryptoUpdate(String account) {
		return new HapiCryptoUpdate(account);
	}

	public static HapiCryptoUpdate cryptoUpdateAliased(final String alias) {
		return new HapiCryptoUpdate(alias, ReferenceType.ALIAS_KEY_NAME);
	}

	public static HapiCryptoApproveAllowance cryptoApproveAllowance() {
		return new HapiCryptoApproveAllowance();
	}

	public static HapiCryptoDeleteAllowance cryptoDeleteAllowance() {
		return new HapiCryptoDeleteAllowance();
	}

	/* CONSENSUS */
	public static HapiTopicCreate createTopic(String topic) {
		return new HapiTopicCreate(topic);
	}

	public static HapiTopicDelete deleteTopic(String topic) {
		return new HapiTopicDelete(topic);
	}

	public static HapiTopicDelete deleteTopic(Function<HapiApiSpec, TopicID> topicFn) {
		return new HapiTopicDelete(topicFn);
	}

	public static HapiTopicUpdate updateTopic(String topic) {
		return new HapiTopicUpdate(topic);
	}

	public static HapiMessageSubmit submitMessageTo(String topic) {
		return new HapiMessageSubmit(topic);
	}

	public static HapiMessageSubmit submitMessageTo(Function<HapiApiSpec, TopicID> topicFn) {
		return new HapiMessageSubmit(topicFn);
	}

	/* FILE */
	public static HapiFileCreate fileCreate(String fileName) {
		return new HapiFileCreate(fileName);
	}

	public static HapiFileAppend fileAppend(String fileName) {
		return new HapiFileAppend(fileName);
	}

	public static HapiFileUpdate fileUpdate(String fileName) {
		return new HapiFileUpdate(fileName);
	}

	public static HapiFileDelete fileDelete(String fileName) {
		return new HapiFileDelete(fileName);
	}

	public static HapiFileDelete fileDelete(Supplier<String> fileNameSupplier) {
		return new HapiFileDelete(fileNameSupplier);
	}

	/* TOKEN */
	public static HapiTokenDissociate tokenDissociate(String account, String... tokens) {
		return new HapiTokenDissociate(account, tokens);
	}

	public static HapiTokenAssociate tokenAssociate(String account, String... tokens) {
		return new HapiTokenAssociate(account, tokens);
	}

	public static HapiTokenAssociate tokenAssociate(String account, List<String> tokens) {
		return new HapiTokenAssociate(account, tokens);
	}

	public static HapiTokenCreate tokenCreate(String token) {
		return new HapiTokenCreate(token).name(token);
	}

	public static HapiTokenUpdate tokenUpdate(String token) {
		return new HapiTokenUpdate(token);
	}

	public static HapiTokenFeeScheduleUpdate tokenFeeScheduleUpdate(String token) {
		return new HapiTokenFeeScheduleUpdate(token);
	}

	public static HapiTokenPause tokenPause(String token) {
		return new HapiTokenPause(token);
	}

	public static HapiTokenUnpause tokenUnpause(String token) {
		return new HapiTokenUnpause(token);
	}

	public static HapiTokenDelete tokenDelete(String token) {
		return new HapiTokenDelete(token);
	}

	public static HapiTokenFreeze tokenFreeze(String token, String account) {
		return new HapiTokenFreeze(token, account);
	}

	public static HapiTokenUnfreeze tokenUnfreeze(String token, String account) {
		return new HapiTokenUnfreeze(token, account);
	}

	public static HapiTokenKycGrant grantTokenKyc(String token, String account) {
		return new HapiTokenKycGrant(token, account);
	}

	public static HapiTokenKycRevoke revokeTokenKyc(String token, String account) {
		return new HapiTokenKycRevoke(token, account);
	}

	public static HapiTokenWipe wipeTokenAccount(String token, String account, long amount) {
		return new HapiTokenWipe(token, account, amount);
	}

	public static HapiTokenWipe wipeTokenAccount(String token, String account, List<Long> serialNumbers) {
		return new HapiTokenWipe(token, account, serialNumbers);
	}

	public static HapiTokenMint mintToken(String token, long amount) {
		return new HapiTokenMint(token, amount);
	}

	public static HapiTokenMint mintToken(String token, List<ByteString> meta, String txName) {
		return new HapiTokenMint(token, meta, txName);
	}

	public static HapiTokenMint mintToken(String token, List<ByteString> metadata) {
		return new HapiTokenMint(token, metadata);
	}

	public static HapiTokenMint invalidMintToken(String token, List<ByteString> metadata, long amount) {
		return new HapiTokenMint(token, metadata, amount);
	}

	public static HapiTokenBurn burnToken(String token, long amount) {
		return new HapiTokenBurn(token, amount);
	}

	public static HapiTokenBurn burnToken(String token, List<Long> serialNumbers) {
		return new HapiTokenBurn(token, serialNumbers);
	}

	public static HapiTokenBurn invalidBurnToken(String token, List<Long> serialNumbers, long amount) {
		return new HapiTokenBurn(token, serialNumbers, amount);
	}

	/* SCHEDULE */
	public static <T extends HapiTxnOp<T>> HapiScheduleCreate<T> scheduleCreate(String scheduled, HapiTxnOp<T> txn) {
		return new HapiScheduleCreate<>(scheduled, txn);
	}

	public static HapiScheduleSign scheduleSign(String schedule) {
		return new HapiScheduleSign(schedule);
	}

	public static HapiScheduleCreate<HapiCryptoCreate> scheduleCreateFunctionless(String scheduled) {
		return new HapiScheduleCreate<>(scheduled, cryptoCreate("doomed")).functionless();
	}

	public static HapiScheduleDelete scheduleDelete(String schedule) {
		return new HapiScheduleDelete(schedule);
	}

	/* SYSTEM */
	public static HapiSysDelete systemFileDelete(String target) {
		return new HapiSysDelete().file(target);
	}

	public static HapiSysUndelete systemFileUndelete(String target) {
		return new HapiSysUndelete().file(target);
	}


	public static HapiSysDelete systemContractDelete(String target) {
		return new HapiSysDelete().contract(target);
	}

	public static HapiSysUndelete systemContractUndelete(String target) {
		return new HapiSysUndelete().contract(target);
	}

	/* NETWORK */
	public static <T extends HapiTxnOp<T>> HapiUncheckedSubmit<T> uncheckedSubmit(HapiTxnOp<T> subOp) {
		return new HapiUncheckedSubmit<>(subOp);
	}

	/* SMART CONTRACT */
	public static HapiContractCall contractCallFrom(String details) {
		return HapiContractCall.fromDetails(details);
	}

	public static HapiContractCall contractCall(String contract) {
		return new HapiContractCall(contract);
	}

	/**
	 * This method allows the developer to invoke a contract function by the name of the called contract and the name
	 * of the desired function
	 *
	 * @param contract
	 * 		the name of the contract
	 * @param functionName
	 * 		the name of the function
	 * @param params
	 * 		the arguments (if any) passed to the contract's function
	 */
	public static HapiContractCall contractCall(String contract, String functionName, Object... params) {
		final var abi = getABIFor(FUNCTION, functionName, contract);
		return new HapiContractCall(abi, contract, params);
	}

	/**
	 * This method allows the developer to invoke a contract function by the name of the called contract and the name
	 * of the desired function and make an ethereum call
	 *
	 * @param contract
	 * 		the name of the contract
	 * @param functionName
	 * 		the name of the function
	 * @param params
	 * 		the arguments (if any) passed to the contract's function
	 */
	public static HapiEthereumCall ethereumCall(String contract, String functionName, Object... params) {
		final var abi = getABIFor(FUNCTION, functionName, contract);
		return new HapiEthereumCall(abi, contract, params);
	}

	public static HapiEthereumCall ethereumCallWithFunctionAbi(boolean isTokenFlow, String contract, String abi, Object... params) {
		return new HapiEthereumCall(isTokenFlow, abi, contract, params);
	}

	/**
	 * This method allows the developer to transfer hbars to an <b>account that's not a smart contract</b> through an
	 * Ethereum transaction.
	 *
	 * @param account
	 * 		the name of the account in the registry
	 * @param amount
	 * 		the amount of tinybars to be transferred from the Ethereum transaction sender to the specified account
	 */
	public static HapiEthereumCall ethereumCryptoTransfer(String account, long amount) {
		return new HapiEthereumCall(account, amount);
	}

	/**
	 * This method provides for the proper execution of specs, which execute contract calls with a function ABI instead
	 * of
	 * function name
	 *
	 * @param contract
	 * 		the name of the contract
	 * @param abi
	 * 		the contract's function ABI
	 * @param params
	 * 		the arguments (if any) passed to the contract's function
	 */
	public static HapiContractCall contractCallWithFunctionAbi(String contract, String abi, Object... params) {
		return new HapiContractCall(abi, contract, params);
	}

	public static HapiContractCall contractCall(String contract, String abi, Function<HapiApiSpec, Object[]> fn) {
		return new HapiContractCall(abi, contract, fn);
	}

	public static HapiContractCall explicitContractCall(String contract, String abi, Object... params) {
		return new HapiContractCall(abi, contract, params);
	}

	public static HapiEthereumContractCreate ethereumContractCreate(final String contractName) {
		return new HapiEthereumContractCreate(contractName).bytecode(contractName);
	}

	public static HapiContractCreate contractCreate(final String contractName, final Object... constructorParams) {
		if (constructorParams.length > 0) {
			final var constructorABI = getABIFor(CONSTRUCTOR, EMPTY, contractName);
			return new HapiContractCreate(contractName, constructorABI, constructorParams).bytecode(contractName);
		} else {
			return new HapiContractCreate(contractName).bytecode(contractName);
		}
	}

	public static HapiContractCreate createDefaultContract(final String name) {
		return new HapiContractCreate(name);
	}

	/**
	 * Previously - when creating contracts we were passing a name, chosen by the developer, which can differentiate
	 * from the name of the contract.
	 * The new implementation of the contract creation depends on the exact name of the contract, which means, that we
	 * can not create multiple instances of the contract with the same name.
	 * Therefore - in order to provide each contract with a unique name - the developer must attach a suffix to happily
	 * create multiple instances of the same contract, but with different names.
	 * <p><b>Example</b>:</p>
	 * <p>final var contract = "TransferringContract";</p>
	 * <p>contractCreate(contract).balance(10_000L).payingWith(ACCOUNT),</p>
	 * <p>contractCustomCreate(contract, to).balance(10_000L).payingWith(ACCOUNT)</p>
	 *
	 * @param contractName
	 * 		the name of the contract
	 * @param suffix
	 * 		an additional String literal, which provides the instance of the contract with a unique identifier
	 * @param constructorParams
	 * 		the arguments (if any) passed to the contract's constructor
	 */
	public static HapiContractCreate contractCustomCreate(final String contractName, final String suffix,
			final Object... constructorParams) {
		if (constructorParams.length > 0) {
			final var constructorABI = getABIFor(CONSTRUCTOR, EMPTY, contractName);
			return new HapiContractCreate(contractName + suffix, constructorABI, constructorParams).bytecode(
					contractName);
		} else {
			return new HapiContractCreate(contractName + suffix).bytecode(contractName);
		}
	}

	public static HapiContractDelete contractDelete(String contract) {
		return new HapiContractDelete(contract);
	}

	public static HapiContractUpdate contractUpdate(String contract) {
		return new HapiContractUpdate(contract);
	}

	/**
	 * This method enables the developer to upload one or many contract(s) bytecode(s)
	 *
	 * @param contractsNames
	 * 		the name(s) of the contract(s), which are to be deployed
	 */
	public static HapiSpecOperation uploadInitCode(final String... contractsNames) {
		return withOpContext((spec, ctxLog) -> {
			List<HapiSpecOperation> ops = new ArrayList<>();
			for (String contractName : contractsNames) {
				final var path = getResourcePath(contractName, ".bin");
				final var file = new HapiFileCreate(contractName);
				final var updatedFile = updateLargeFile(GENESIS, contractName, extractByteCode(path));
				ops.add(file);
				ops.add(updatedFile);
			}
			allRunFor(spec, ops);
		});
	}

	public static HapiSpecOperation uploadSingleInitCode(final String contractName, final long expiry, final String payingWith, final LongConsumer exposingTo) {
		return withOpContext((spec, ctxLog) -> {
			List<HapiSpecOperation> ops = new ArrayList<>();
			final var path = getResourcePath(contractName, ".bin");
			final var file = new HapiFileCreate(contractName).payingWith(payingWith).exposingNumTo(exposingTo).expiry(expiry);
			final var updatedFile = updateLargeFile(GENESIS, contractName, extractByteCode(path));
			ops.add(file);
			ops.add(updatedFile);
			allRunFor(spec, ops);
		});
	}

	public static HapiSpecOperation uploadSingleInitCode(final String contractName, final ResponseCodeEnum... statuses) {
		return withOpContext((spec, ctxLog) -> {
			List<HapiSpecOperation> ops = new ArrayList<>();
			final var path = getResourcePath(contractName, ".bin");
			final var file = new HapiFileCreate(contractName).hasRetryPrecheckFrom(statuses);
			final var updatedFile = updateLargeFile(GENESIS, contractName, extractByteCode(path));
			ops.add(file);
			ops.add(updatedFile);
			allRunFor(spec, ops);
		});
	}

	/**
	 * This method enables uploading a contract bytecode with the constructor parameters (if present) appended at the
	 * end
	 * of the file
	 *
	 * @param contractName
	 * 		the name of the contract, which is to be deployed
	 * @param abi
	 * 		the abi of the contract
	 * @param args
	 * 		the constructor arguments
	 */
	public static HapiSpecOperation uploadInitCodeWithConstructorArguments(final String contractName, final String abi,
			final Object... args) {
		return withOpContext((spec, ctxLog) -> {
			List<HapiSpecOperation> ops = new ArrayList<>();

			final var path = getResourcePath(contractName, ".bin");
			final var file = new HapiFileCreate(contractName);
			final var fileByteCode = extractByteCode(path);
			final byte[] params = args.length == 0 ? new byte[] { } : CallTransaction.Function.fromJsonInterface(
					abi).encodeArguments(args);
			final var updatedFile = updateLargeFile(GENESIS, contractName,
					params.length == 0 ? fileByteCode : fileByteCode.concat(ByteString.copyFrom(params)));
			ops.add(file);
			ops.add(updatedFile);
			allRunFor(spec, ops);
		});
	}

	/* SYSTEM */
	public static HapiFreeze hapiFreeze(final Instant freezeStartTime) {
		return new HapiFreeze().startingAt(freezeStartTime);
	}
}
