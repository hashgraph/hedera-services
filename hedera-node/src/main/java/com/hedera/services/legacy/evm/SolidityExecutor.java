package com.hedera.services.legacy.evm;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import static com.hedera.services.utils.EntityIdUtils.accountParsedFromSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.asContract;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_BYTECODE_EMPTY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static java.math.BigInteger.ZERO;
import static java.util.Comparator.comparingLong;
import static org.apache.commons.lang3.ArrayUtils.getLength;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.ethereum.util.BIUtil.isCovers;
import static org.ethereum.util.BIUtil.toBI;
import static org.ethereum.util.BIUtil.transfer;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.ethereum.util.ByteUtil.toHexString;
import static org.ethereum.vm.VMUtils.saveProgramTraceFile;
import static org.ethereum.vm.VMUtils.zipAndEncode;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.contracts.execution.SoliditySigsVerifier;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.legacy.services.context.primitives.SequenceNumber;
import com.hedera.services.legacy.config.PropertiesLoader;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.CommonConfig;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutionSummary;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ServicesRepositoryImpl;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.util.ByteArraySet;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.NewAccountCreateAdapter;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.spongycastle.util.encoders.Hex;

public class SolidityExecutor {
	private static final Logger logger = Logger.getLogger(SolidityExecutor.class);
	private static final BlockStore NULL_BLOCK_STORE = null;

	private final long thresholdRecordDurationSecs = PropertiesLoader.getThresholdTxRecordTTL();
	private final EthereumListener listener = new EthereumListenerAdapter();

	private final long rbh;
	private final long sbh;
	private final long gasPrice;
	private final Block block;
	private final byte[] payerAddress;
	private final byte[] fundingAddress;
	private final Instant startTime;
	private final boolean localCall;
	private final Transaction solidityTxn;
	private final CommonConfig commonConfig;
	private final SequenceNumber seqNo;
	private final TransactionBody txn;
	private final SystemProperties config;
	private final BlockchainConfig blockchainConfig;
	private final SoliditySigsVerifier sigsVerifier;
	private final ProgramInvokeFactory programInvokeFactory = new ProgramInvokeFactoryImpl();
	private final ServicesRepositoryImpl repository;
	private final ServicesRepositoryImpl trackingRepository;
	private final NewAccountCreateAdapter contractCreateAdaptor = new SequenceAccountCreator();
	private final Optional<TransactionContext> txnCtx;

	private VM vm;
	private String errorMessage;
	private boolean readyToExecute = false;
	private Program program;
	private BigInteger gasLeft;
	private ProgramResult result = new ProgramResult();
	private TransactionReceipt receipt;
	private PrecompiledContracts.PrecompiledContract precompiledContract;
	private List<LogInfo> logs;
	private Optional<List<ContractID>> createdContracts = Optional.empty();
	private ByteArraySet touchedAccounts = new ByteArraySet();
	private ResponseCodeEnum errorCode;

	private class SequenceAccountCreator extends NewAccountCreateAdapter {
		@Override
		public byte[] calculateNewAddress(byte[] ownerAddress, Repository track) {
			AccountState creatorHgState = trackingRepository.getAccountState(solidityTxn.getSender());

			return asSolidityAddress(
					0,
					creatorHgState.getHGRealmId(),
					seqNo.getNextSequenceNum());
		}
	}

	public SolidityExecutor(
			Transaction solidityTxn,
			ServicesRepositoryImpl repository,
			Block block,
			String payerAddress,
			String fundingAddress,
			TransactionBody txn,
			Instant startTime,
			SequenceNumber seqNo,
			long rbh,
			long sbh,
			TransactionContext txnCtx,
			boolean localCall,
			SoliditySigsVerifier sigsVerifier
	) {
		this.txn = txn;
		this.rbh = rbh;
		this.sbh = sbh;
		this.block = block;
		this.seqNo = seqNo;
		this.txnCtx = Optional.ofNullable(txnCtx);
		this.gasLeft = toBI(solidityTxn.getGasLimit());
		this.gasPrice = ByteUtil.byteArrayToInt(solidityTxn.getGasPrice());
		this.startTime = startTime;
		this.localCall = localCall;
		this.repository = repository;
		this.solidityTxn = solidityTxn;
		this.sigsVerifier = sigsVerifier;
		this.trackingRepository = repository.startTracking();

		this.payerAddress = Optional.ofNullable(payerAddress)
				.map(ByteUtil::hexStringToBytes)
				.orElse(solidityTxn.getSender());
		this.fundingAddress = Optional.ofNullable(fundingAddress)
				.map(ByteUtil::hexStringToBytes)
				.orElse(EMPTY_BYTE_ARRAY);

		commonConfig = CommonConfig.getDefault();
		config = commonConfig.systemProperties();
		blockchainConfig = config.getBlockchainConfig().getConfigForBlock(block.getNumber());
	}

	public void init() {
		if (localCall || payerIsSolvent()) {
			readyToExecute = true;
		}
	}

	private boolean payerIsSolvent() {
		var totalCost = toBI(solidityTxn.getValue()).add(gasCost());
		var payerBalance = repository.getBalance(this.payerAddress);
		var isSolvent = isCovers(payerBalance, totalCost);

		if (!isSolvent) {
			errorCode = INSUFFICIENT_PAYER_BALANCE;
			setError(String.format("Transaction cost %s exceeds payer balance %s", totalCost, payerBalance));
		}

		return isSolvent;
	}

	public void execute() {
		if (!readyToExecute) {
			return;
		}

		if (!localCall) {
			repository.increaseNonce(solidityTxn.getSender());
			repository.addBalance(this.payerAddress, gasCost().negate());
		}

		if (solidityTxn.isContractCreation()) {
			create();
		} else {
			call();
		}
	}

	private BigInteger gasCost() {
		return toBI(solidityTxn.getGasPrice()).multiply(toBI(solidityTxn.getGasLimit()));
	}

	private void call() {
		if (!readyToExecute) {
			return;
		}

		byte[] targetAddress = solidityTxn.getReceiveAddress();
		precompiledContract = PrecompiledContracts.getContractForAddress(new DataWord(targetAddress), blockchainConfig);

		if (precompiledContract != null) {
			var gasRequired = BigInteger.valueOf(precompiledContract.getGasForData(solidityTxn.getData()));
			if (!localCall && gasLeft.compareTo(gasRequired) < 0) {
				var gasLimit = BigInteger.valueOf(PropertiesLoader.getMaxGasLimit());
				var gasSupplied = ByteUtil.bytesToBigInteger(solidityTxn.getGasLimit());
				errorCode = (gasSupplied.compareTo(gasLimit) < 0) ? INSUFFICIENT_GAS : MAX_GAS_LIMIT_EXCEEDED;
				setError(String.format(
								"OOG calling precompiled contract 0x%s (%s required, %s remaining)",
								Hex.toHexString(targetAddress), gasRequired, gasLeft));
				gasLeft = ZERO;
				return;
			} else {
				gasLeft = gasLeft.subtract(gasRequired);
				Pair<Boolean, byte[]> out = precompiledContract.execute(solidityTxn.getData());
				if (!out.getLeft()) {
					errorCode = CONTRACT_EXECUTION_EXCEPTION;
					setError(String.format("Error executing precompiled contract 0x%s", Hex.toHexString(targetAddress)));
					gasLeft = ZERO;
					return;
				}
			}
		} else {
			byte[] code = repository.getCode(targetAddress);
			if (isEmpty(code)) {
				errorCode = CONTRACT_BYTECODE_EMPTY;
				setError(String.format("Error: Bytecode is empty for contract 0x%s", Hex.toHexString(targetAddress)));
			} else {
				var programInvoke = programInvokeFactory.createProgramInvoke(
						solidityTxn, block, trackingRepository, NULL_BLOCK_STORE, localCall);
				this.vm = new VM(config);
				this.program = new Program(
						repository.getCodeHash(targetAddress),
						code,
						programInvoke,
						solidityTxn,
						config,
						contractCreateAdaptor,
						fundingAddress,
						rbh,
						sbh,
						PropertiesLoader.getDefaultContractDurationInSec(),
						thresholdRecordDurationSecs).withCommonConfig(commonConfig);
			}
		}
		if (!localCall) {
			transfer(trackingRepository, solidityTxn.getSender(), targetAddress, toBI(solidityTxn.getValue()));
			touchedAccounts.add(targetAddress);
		}
	}

	private void create() {
		var sponsor = trackingRepository.getAccountState(solidityTxn.getSender());
		long newSequence = seqNo.getNextSequenceNum();
		byte[] newContractAddress = asSolidityAddress(0, sponsor.getHGRealmId(), newSequence);

		solidityTxn.setContractAddress(newContractAddress);

		var alreadyExtant = trackingRepository.getAccountState(newContractAddress);
		if (alreadyExtant != null && alreadyExtant.isContractExist(blockchainConfig)) {
			errorCode = CONTRACT_EXECUTION_EXCEPTION;
			setError(String.format("Cannot overwrite extant contract @ 0x%s!", Hex.toHexString(newContractAddress)));
			gasLeft = ZERO;
			return;
		}

		BigInteger oldBalance = repository.getBalance(newContractAddress);
		trackingRepository.createAccount(newContractAddress);
		trackingRepository.addBalance(newContractAddress, oldBalance);
		trackingRepository.setSmartContract(newContractAddress, true);
		trackingRepository.setHGRealmId(newContractAddress, sponsor.getHGRealmId());
		trackingRepository.setHGShardId(newContractAddress, sponsor.getHGShardId());
		trackingRepository.setHGAccountId(newContractAddress, newSequence);

		long createTimeMs = startTime.getEpochSecond();
		if (txn != null && txn.hasContractCreateInstance()) {
			var op = txn.getContractCreateInstance();
			var autoRenewPeriod = op.getAutoRenewPeriod();
			trackingRepository.setAutoRenewPeriod(newContractAddress, autoRenewPeriod.getSeconds());
			var expiry = RequestBuilder.getExpirationTime(this.startTime, autoRenewPeriod);
			trackingRepository.setExpirationTime(newContractAddress, expiry.getSeconds());
			trackingRepository.setCreateTimeMs(newContractAddress, createTimeMs);
		}

		if (blockchainConfig.eip161()) {
			trackingRepository.increaseNonce(newContractAddress);
		}

		if (!isEmpty(solidityTxn.getData())) {
			ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(
					solidityTxn, block, trackingRepository, NULL_BLOCK_STORE, localCall);
			this.vm = new VM(config);
			this.program = new Program(
					null,
					solidityTxn.getData(),
					programInvoke,
					solidityTxn,
					config,
					contractCreateAdaptor,
					fundingAddress,
					rbh,
					sbh,
					PropertiesLoader.getDefaultContractDurationInSec(),
					thresholdRecordDurationSecs).withCommonConfig(commonConfig);
		}
		if (!localCall) {
			transfer(trackingRepository, solidityTxn.getSender(), newContractAddress, toBI(solidityTxn.getValue()));
			touchedAccounts.add(newContractAddress);
		}
	}

	public void go() {
		if (!readyToExecute) {
			return;
		}

		try {
			if (vm != null) {
				if (config.playVM()) {
					vm.play(program);
				}

				result = program.getResult();
				gasLeft = toBI(solidityTxn.getGasLimit()).subtract(toBI(program.getResult().getGasUsed()));
				if (solidityTxn.isContractCreation() && !result.isRevert()) {
					long durationInSeconds = program.getOwnerRemainingDuration();
					int codeLengthBytes = getLength(program.getResult().getHReturn());
					long returnDataGasValue = Program.calculateStorageGasNeeded(
							codeLengthBytes, durationInSeconds, this.sbh, this.gasPrice);
					if (gasLeft.compareTo(BigInteger.valueOf(returnDataGasValue)) < 0) {
						if (!blockchainConfig.getConstants().createEmptyContractOnOOG()) {
							program.setRuntimeFailure(Program.Exception.notEnoughSpendingGas(
									"No gas to return just created contract", returnDataGasValue, program));
							result = program.getResult();
						}
						result.setHReturn(EMPTY_BYTE_ARRAY);
					} else if (getLength(result.getHReturn()) > blockchainConfig.getConstants().getMAX_CONTRACT_SZIE()) {
						program.setRuntimeFailure(Program.Exception.notEnoughSpendingGas(
								"Contract size too large: " + getLength(result.getHReturn()), returnDataGasValue,
								program));
						result = program.getResult();
						result.setHReturn(EMPTY_BYTE_ARRAY);
					} else {
						gasLeft = gasLeft.subtract(BigInteger.valueOf(returnDataGasValue));
						trackingRepository.saveCode(solidityTxn.getContractAddress(), result.getHReturn());
					}
				}

				String validationError = config.getBlockchainConfig().getConfigForBlock(block.getNumber())
						.validateTransactionChanges(NULL_BLOCK_STORE, block, solidityTxn, null);
				if (validationError != null) {
					program.setRuntimeFailure(new RuntimeException(validationError));
				}

				Set<AccountID> receivers = StreamSupport.stream(program.getEndowments().spliterator(), false)
						.map(EntityIdUtils::accountParsedFromSolidityAddress)
						.collect(Collectors.toSet());
				boolean hasValidSigs = sigsVerifier.allRequiredKeysAreActive(receivers);
				if (result.getException() != null || result.isRevert() || !hasValidSigs) {
					result.getDeleteAccounts().clear();
					result.getLogInfoList().clear();
					result.resetFutureRefund();
					rollback();

					if (result.getException() != null) {
						throw result.getException();
					} else if (!hasValidSigs) {
						errorCode = INVALID_SIGNATURE;
						setError("Non-contract receiver signatures required, but not present");
					} else {
						errorCode = CONTRACT_REVERT_EXECUTED;
						setError("Execution was reverted");
					}
				} else {
					touchedAccounts.addAll(result.getTouchedAccounts());
					trackingRepository.commit();
				}
			} else {
				trackingRepository.commit();
			}
		} catch (Throwable e) {
			e.printStackTrace();
			if (e instanceof Program.OutOfGasException) {
				BigInteger gasUpperBound = BigInteger.valueOf(PropertiesLoader.getMaxGasLimit());
				BigInteger gasProvided = ByteUtil.bytesToBigInteger(solidityTxn.getGasLimit());
				if (gasUpperBound.compareTo(gasProvided) > 0) {
					errorCode = INSUFFICIENT_GAS;
				} else {
					errorCode = MAX_GAS_LIMIT_EXCEEDED;
				}
			} else if (e instanceof Program.InvalidAccountAddressException) {
				errorCode = ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
			} else if (e instanceof Program.SameOwnerObtainerOnSelfSestructException) {
				errorCode = ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
			} else if (e instanceof Program.StaticCallModificationException) {
				errorCode = ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION;
			} else if (e instanceof Program.EmptyByteCodeException) {
				errorCode = CONTRACT_BYTECODE_EMPTY;
			} else {
				errorCode = CONTRACT_EXECUTION_EXCEPTION;
			}
			// https://github.com/ethereum/cpp-ethereum/blob/develop/libethereum/Executive.cpp#L241
			rollback();
			gasLeft = ZERO;
			setError(e.getMessage());
		}
	}

	public ResponseCodeEnum getErrorCode() {
		return errorCode;
	}

	private void rollback() {
		trackingRepository.rollback();
		touchedAccounts.remove(
				solidityTxn.isContractCreation() ? solidityTxn.getContractAddress() : solidityTxn.getReceiveAddress());
	}

	public TransactionExecutionSummary finalizeExecution() {
		if (!readyToExecute) {
			return null;
		}

		TransactionExecutionSummary.Builder summaryBuilder = TransactionExecutionSummary.builderFor(solidityTxn)
				.gasLeftover(gasLeft)
				.logs(result.getLogInfoList())
				.result(result.getHReturn());

		if (result != null) {
			finalizeAnyCreatedContracts();
			if (result.isRevert() || !StringUtils.isEmpty(errorMessage)) {
				long storageGasRefund = result.getStorageGasUsed();
				if (storageGasRefund > 0) {
					gasLeft = gasLeft.add(BigInteger.valueOf(storageGasRefund));
				}

			}

			result.addFutureRefund(result.getDeleteAccounts().size() * config.getBlockchainConfig()
					.getConfigForBlock(block.getNumber()).getGasCost().getSUICIDE_REFUND());
			long gasRefund = Math.min(result.getFutureRefund(), getGasUsed() / 2);
			gasLeft = gasLeft.add(BigInteger.valueOf(gasRefund));

			summaryBuilder.gasUsed(toBI(result.getGasUsed())).gasRefund(toBI(gasRefund))
					.deletedAccounts(result.getDeleteAccounts())
					.internalTransactions(result.getInternalTransactions());
			if (result.getException() != null) {
				summaryBuilder.markAsFailed();
			}
		}

		TransactionExecutionSummary summary = summaryBuilder.build();
		if (!localCall) {
			repository.addBalance(this.payerAddress, summary.getLeftover().add(summary.getRefund()));
			touchedAccounts.add(this.payerAddress);

			var fee = summary.getFee();
			repository.addBalance(this.fundingAddress, fee);
			touchedAccounts.add(this.fundingAddress);
			txnCtx.ifPresent(ctx -> ctx.addNonThresholdFeeChargedToPayer(fee.longValue()));
		}

		if (result != null) {
			logs = result.getLogInfoList();
			for (DataWord address : result.getDeleteAccounts()) {
				repository.setDeleted(address.getLast20Bytes(), true);
			}
		}

		if (blockchainConfig.eip161()) {
			touchedAccounts.forEach(address ->
					Optional.ofNullable(repository.getAccountState(address))
							.ifPresent(account -> {
								if (account.isEmpty()) {
									repository.delete(address);
								}
							}));
		}

		listener.onTransactionExecuted(summary);
		if (config.vmTrace() && program != null && result != null) {
			var trace = program.getTrace().result(result.getHReturn()).error(result.getException()).toString();
			if (config.vmTraceCompressed()) {
				trace = zipAndEncode(trace);
			}
			var hash = toHexString(solidityTxn.getHash());
			saveProgramTraceFile(config, hash, trace);
			listener.onVMTraceCreated(hash, trace);
		}

		return summary;
	}

	public TransactionReceipt getReceipt() {
		if (receipt == null) {
			receipt = new TransactionReceipt();
			receipt.setCumulativeGas(getGasUsed());
			receipt.setTransaction(solidityTxn);
			receipt.setLogInfoList(getVMLogs());
			receipt.setGasUsed(getGasUsed());
			receipt.setExecutionResult(getResult().getHReturn());
			receipt.setError(errorMessage);
		}
		return receipt;
	}

	public List<LogInfo> getVMLogs() {
		return logs;
	}

	public Optional<List<ContractID>> getCreatedContracts() {
		return createdContracts;
	}

	public ProgramResult getResult() {
		return result;
	}

	private long getGasUsed() {
		return toBI(solidityTxn.getGasLimit()).subtract(gasLeft).longValue();
	}

	private void finalizeAnyCreatedContracts() {
		createdContracts = Optional.ofNullable(contractCreateAdaptor.getCreatedContracts()).map(creations ->
			creations.values()
					.stream()
					.flatMap(List::stream)
					.peek(this::initNewContract)
					.map(address -> asContract(accountParsedFromSolidityAddress(address)))
					.sorted(comparingLong(ContractID::getShardNum)
									.thenComparingLong(ContractID::getRealmNum)
									.thenComparingLong(ContractID::getContractNum))
					.collect(Collectors.toList()));
	}

	private void initNewContract(byte[] address) {
		var id = accountParsedFromSolidityAddress(address);
		var sponsor = repository.getHGCAccount(solidityTxn.getSender());

		repository.setSmartContract(address, true);
		repository.setHGRealmId(address, sponsor.getHGRealmId());
		repository.setHGShardId(address, sponsor.getHGShardId());
		repository.setHGAccountId(address, id.getAccountNum());

		repository.setCreateTimeMs(address, startTime.getEpochSecond());
		var expiry = RequestBuilder.getExpirationTime(
				startTime,
				Duration.newBuilder().setSeconds(PropertiesLoader.getDefaultContractDurationInSec()).build());
		repository.setExpirationTime(address, expiry.getSeconds());
	}

	private void setError(String message) {
		logger.debug(message);
		errorMessage = message;
	}
}
