package com.hedera.services.bdd.suites.utils.validation;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.KeyShape;

import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.includingDeduction;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;

import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;

import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.keypairs.SpecUtils;
import com.hedera.services.bdd.suites.utils.validation.domain.ConsensusScenario;
import com.hedera.services.bdd.suites.utils.validation.domain.ContractScenario;
import com.hedera.services.bdd.suites.utils.validation.domain.CryptoScenario;

import static com.hedera.services.bdd.suites.utils.validation.ValidationScenarios.Scenario.SYSTEM_KEYS;
import static com.hedera.services.bdd.suites.utils.validation.domain.ConsensusScenario.NOVEL_TOPIC_NAME;
import static com.hedera.services.bdd.suites.utils.validation.domain.ConsensusScenario.PERSISTENT_TOPIC_NAME;
import static com.hedera.services.bdd.suites.utils.validation.domain.ContractScenario.DEFAULT_BYTECODE_RESOURCE;
import static com.hedera.services.bdd.suites.utils.validation.domain.ContractScenario.DEFAULT_CONTRACT_RESOURCE;
import static com.hedera.services.bdd.suites.utils.validation.domain.ContractScenario.DEFAULT_LUCKY_NUMBER;
import static com.hedera.services.bdd.suites.utils.validation.domain.ContractScenario.NOVEL_CONTRACT_NAME;
import static com.hedera.services.bdd.suites.utils.validation.domain.ContractScenario.PERSISTENT_CONTRACT_NAME;
import static com.hedera.services.bdd.suites.utils.validation.domain.CryptoScenario.*;
import static com.hedera.services.bdd.suites.utils.validation.domain.FileScenario.*;

import com.hedera.services.bdd.suites.utils.validation.domain.FileScenario;
import com.hedera.services.bdd.suites.utils.validation.domain.Network;
import com.hedera.services.bdd.suites.utils.validation.domain.Node;
import com.hedera.services.bdd.suites.utils.validation.domain.PersistentContract;
import com.hedera.services.bdd.suites.utils.validation.domain.PersistentFile;
import com.hedera.services.bdd.suites.utils.validation.domain.Scenarios;
import com.hedera.services.bdd.suites.utils.validation.domain.ValidationConfig;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.TopicID;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome.SUITE_PASSED;
import static com.hedera.services.bdd.suites.utils.validation.ValidationScenarios.Scenario.CONSENSUS;
import static com.hedera.services.bdd.suites.utils.validation.ValidationScenarios.Scenario.CONTRACT;
import static com.hedera.services.bdd.suites.utils.validation.ValidationScenarios.Scenario.CRYPTO;
import static com.hedera.services.bdd.suites.utils.validation.ValidationScenarios.Scenario.FILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;

public class ValidationScenarios extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ValidationScenarios.class);

	private static String LUCKY_NO_LOOKUP_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"pick\"," +
			"\"outputs\":[{\"internalType\":\"uint32\",\"name\":\"\",\"type\":\"uint32\"}],\"payable\":false," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";
	private static String BELIEVE_IN_ABI = "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint32\"," +
			"\"name\":\"no\",\"type\":\"uint32\"}],\"na    me\":\"believeIn\",\"outputs\":[],\"payable\":false," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static String CONSPICUOUS_DONATION_ABI = "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint32\"," +
			"\"name\":\"toNum\",\"type\":\"uint32\"},{\"internalType\":\"string\",\"name\":\"saying\"," +
			"\"type\":\"string\"}],\"name\":\"donate\",\"outputs\":[],\"payable\":true," +
			"\"stateMutability\":\"payable\",\"type\":\"function\"}";

	private static Scenarios scenarios;
	private static ValidationConfig validationConfig;
	private static ScenarioParams params = new ScenarioParams();
	private static List<String> nodeAccounts = new ArrayList<>();
	private static int nextAccount = 0;
	private static AtomicLong startingBalance = new AtomicLong(-1L);
	private static AtomicLong endingBalance = new AtomicLong(-1L);
	private static AtomicBoolean errorsOccurred = new AtomicBoolean(false);
	private static AtomicReference<String> novelAccountUsed = new AtomicReference<>(null);
	private static AtomicReference<String> novelFileUsed = new AtomicReference<>(null);
	private static AtomicReference<String> novelContractUsed = new AtomicReference<>(null);
	private static AtomicReference<String> novelTopicUsed = new AtomicReference<>(null);

	public static void main(String... args) {
		readConfig();
		parse(args);

		assertValidParams();
		log.info("Using nodes " + nodes());
		FinalOutcome outcome = new ValidationScenarios().runSuiteSync();

		printNovelUsage();
		printBalanceChange();
		persistUpdatedConfig();

		System.exit((outcome == SUITE_PASSED && !errorsOccurred.get()) ? 0 : 1);
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return Stream.of(
					Optional.of(recordPayerBalance(startingBalance::set)),
					Optional.ofNullable(params.getScenarios().contains(CRYPTO) ? cryptoScenario() : null),
					Optional.ofNullable(params.getScenarios().contains(FILE) ? fileScenario() : null),
					Optional.ofNullable(params.getScenarios().contains(CONTRACT) ? contractScenario() : null),
					Optional.ofNullable(params.getScenarios().contains(CONSENSUS) ? consensusScenario() : null),
					Optional.ofNullable(params.getScenarios().contains(SYSTEM_KEYS) ? getSystemKeys() : null),
					Optional.ofNullable(params.getScenarios().isEmpty() ? null : recordPayerBalance(endingBalance::set)))
				.flatMap(Optional::stream)
				.collect(Collectors.toList());
	}

	private static HapiApiSpec getSystemKeys() {
		final long[] accounts = { 2, 50, 55, 56, 57, 58 };
		final long[] files = { 101, 102, 111, 112, 121, 122 };
		try {
			return customHapiSpec("GetSystemKeys")
					.withProperties(Map.of(
							"nodes", nodes(),
							"default.payer", primaryPayer(),
							"startupAccounts.literal", payerKeystoreLiteral()
					)).given( ).when( ).then(flattened(
							Arrays.stream(accounts)
									.mapToObj(num -> getAccountInfo(String.format("0.0.%d", num))
											.setNodeFrom(ValidationScenarios::nextNode)
											.logged())
									.toArray(n -> new HapiSpecOperation[n]),
							Arrays.stream(files)
									.mapToObj(num -> getFileInfo(String.format("0.0.%d", num))
											.setNodeFrom(ValidationScenarios::nextNode)
											.logged())
									.toArray(n -> new HapiSpecOperation[n])
					));
		} catch (Exception e) {
			log.warn("Unable to record inital payer balance, skipping it!", e);
			errorsOccurred.set(true);
			return null;
		}
	}

	private static HapiApiSpec recordPayerBalance(LongConsumer learner) {
		try {
			return customHapiSpec("RecordPayerBalance")
					.withProperties(Map.of(
							"nodes", nodes(),
							"default.payer", primaryPayer(),
							"startupAccounts.literal", payerKeystoreLiteral()
					)).given( ).when( ).then(
							withOpContext((spec, opLog) -> {
									var lookup = getAccountBalance(() -> idLiteral(targetNetwork().getBootstrap()));
									allRunFor(spec, lookup);
									learner.accept(lookup.getResponse().getCryptogetAccountBalance().getBalance());
							})
					);
		} catch (Exception e) {
			log.warn("Unable to record inital payer balance, skipping it!", e);
			errorsOccurred.set(true);
			return null;
		}
	}

	private static HapiApiSpec cryptoScenario() {
		try {
			ensureScenarios();
			if (scenarios.getCrypto() == null) {
				scenarios.setCrypto(new CryptoScenario());
			}
			var crypto = scenarios.getCrypto();
			var transferFee = new AtomicLong(0);

			long expectedDelta = params.isNovelContent() ? 2L : 1L;
			return customHapiSpec("CryptoScenario")
					.withProperties(Map.of(
							"nodes", nodes(),
							"default.payer", primaryPayer(),
							"startupAccounts.literal", payerKeystoreLiteral()
					)).given(
							ensureValidatedAccountExistence(
									SENDER_NAME,
									2L,
									pemForAccount(senderOrNegativeOne(crypto).getAsLong()),
									senderOrNegativeOne(crypto),
									crypto::setSender),
							ensureValidatedAccountExistence(
									RECEIVER_NAME,
									0L,
									pemForAccount(receiverOrNegativeOne(crypto).getAsLong()),
									receiverOrNegativeOne(crypto),
									crypto::setReceiver),
							balanceSnapshot("receiverBefore", RECEIVER_NAME)
					).when(flattened(
							cryptoTransfer(tinyBarsFromTo(SENDER_NAME, RECEIVER_NAME, 1L))
									.setNodeFrom(ValidationScenarios::nextNode)
									.via("transferTxn"),
							withOpContext((spec, opLog) -> {
								var lookup = getTxnRecord("transferTxn")
										.setNodeFrom(ValidationScenarios::nextNode)
										.logged();
								allRunFor(spec, lookup);
								var record = lookup.getResponseRecord();
								transferFee.set(record.getTransactionFee());
							}),
							novelAccountIfDesired(transferFee)
					)).then(
							getAccountBalance(RECEIVER_NAME)
									.setNodeFrom(ValidationScenarios::nextNode)
									.hasTinyBars(changeFromSnapshot("receiverBefore", expectedDelta))
					);
		} catch (Exception e) {
			log.warn("Unable to initialize crypto scenario, skipping it!", e);
			errorsOccurred.set(true);
			return null;
		}
	}

	private static HapiSpecOperation[] novelAccountIfDesired(AtomicLong transferFee) {
		if (!params.isNovelContent()) {
			return new HapiSpecOperation[0];
		}

		KeyShape complex = KeyShape.threshOf(1, KeyShape.listOf(2), KeyShape.threshOf(1, 3));
		return new HapiSpecOperation[] {
				newKeyNamed("novelAccountFirstKey").shape(complex),
				newKeyNamed("novelAccountSecondKey"),
				cryptoCreate(NOVEL_ACCOUNT_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.balance(ignore -> 10 * transferFee.get())
						.key("novelAccountFirstKey"),
				cryptoUpdate(NOVEL_ACCOUNT_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.key("novelAccountSecondKey"),
				cryptoTransfer(tinyBarsFromTo(SENDER_NAME, RECEIVER_NAME, 1L))
						.setNodeFrom(ValidationScenarios::nextNode)
						.payingWith(NOVEL_ACCOUNT_NAME),
				cryptoDelete(NOVEL_ACCOUNT_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.transfer(GENESIS),
				withOpContext((spec, opLog) ->
						novelAccountUsed.set(HapiPropertySource.asAccountString(
								spec.registry().getAccountID(NOVEL_ACCOUNT_NAME))))
		};
	}

	private static LongSupplier senderOrNegativeOne(CryptoScenario crypto) {
		return () -> Optional.ofNullable(crypto.getSender()).orElse(-1L);
	}

	private static LongSupplier receiverOrNegativeOne(CryptoScenario crypto) {
		return () -> Optional.ofNullable(crypto.getReceiver()).orElse(-1L);
	}

	private static HapiSpecOperation ensureValidatedAccountExistence(
			String name,
			long minBalance,
			String pemLoc,
			LongSupplier num,
			LongConsumer update
	) {
		return UtilVerbs.withOpContext((spec, opLog) -> {
			long accountNo = num.getAsLong();
			if (accountNo > 0) {
				var check = getAccountInfo(idLiteral(num.getAsLong()))
						.setNodeFrom(ValidationScenarios::nextNode);
				allRunFor(spec, check);

				var info = check.getResponse().getCryptoGetInfo().getAccountInfo();
				var ocKeystore = SpecUtils.asOcKeystore(new File(pemLoc), KeyFactory.PEM_PASSPHRASE);
				var expectedKey = Key.newBuilder()
						.setEd25519(ByteString.copyFrom(Hex.decodeHex(ocKeystore.getPublicKeyAbyteStr())))
						.build();
				Assert.assertEquals(
						String.format("Account 0.0.%d had a different key than expected!", accountNo),
						expectedKey,
						info.getKey());
				spec.registry().saveKey(name, expectedKey);
				spec.registry().saveAccountId(name, accountId(accountNo));
				spec.keys().incorporate(name, ocKeystore);

				if (info.getBalance() < minBalance) {
					var transfer = cryptoTransfer(tinyBarsFromTo(GENESIS, name, minBalance))
							.setNodeFrom(ValidationScenarios::nextNode);
					allRunFor(spec, transfer);
				}
			} else {
				var create = TxnVerbs.cryptoCreate(name)
						.setNodeFrom(ValidationScenarios::nextNode)
						.balance(minBalance);
				allRunFor(spec, create);
				var createdNo = create.numOfCreatedAccount();
				var newLoc = pemLoc.replace("account-1", String.format("account%d", createdNo));
				spec.keys().exportSimpleKey(newLoc, name);
				update.accept(createdNo);
			}
		});
	}

	private static HapiApiSpec fileScenario() {
		try {
			ensureScenarios();
			if (scenarios.getFile() == null) {
				var fs = new FileScenario();
				fs.setPersistent(new PersistentFile());
				scenarios.setFile(fs);
			}
			var file = scenarios.getFile();

			return customHapiSpec("FileScenario")
					.withProperties(Map.of(
							"nodes", nodes(),
							"default.payer", primaryPayer(),
							"startupAccounts.literal", payerKeystoreLiteral()
					)).given(
							ensureValidatedFileExistence(
									PERSISTENT_FILE_NAME,
									file.getPersistent().getContents(),
									pemForFile(persistentOrNegativeOne(file).getAsLong()),
									persistentOrNegativeOne(file),
									num -> file.getPersistent().setNum(num),
									loc -> file.getPersistent().setContents(loc))
					).when( ).then(
							novelFileIfDesired()
					);
		} catch (Exception e) {
			log.warn("Unable to initialize file scenario, skipping it!", e);
			errorsOccurred.set(true);
			return null;
		}
	}

	private static HapiSpecOperation[] novelFileIfDesired() {
		if (!params.isNovelContent()) {
			return new HapiSpecOperation[0];
		}

		KeyShape firstComplex = KeyShape.listOf(KeyShape.threshOf(2, 3), KeyShape.threshOf(1, 3));
		KeyShape secondComplex = KeyShape.listOf(3);
		SigControl normalDelete = secondComplex.signedWith(KeyShape.sigs(ON, ON, ON));
		SigControl revocation = secondComplex.signedWith(KeyShape.sigs(ON, OFF, OFF));
		return new HapiSpecOperation[] {
				newKeyNamed("novelFileFirstKey").shape(firstComplex),
				newKeyNamed("novelFileSecondKey").shape(secondComplex),
				fileCreate(NOVEL_FILE_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.key("novelFileFirstKey")
						.contents("abcdefghijklm"),
				fileAppend(NOVEL_FILE_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.content("nopqrstuvwxyz"),
				getFileContents(NOVEL_FILE_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.hasContents(ignore -> "abcdefghijklmnopqrstuvwxyz".getBytes()),
				fileUpdate(NOVEL_FILE_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.wacl("novelFileSecondKey"),
				fileDelete(NOVEL_FILE_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.sigControl(ControlForKey.forKey(
						NOVEL_FILE_NAME,
						params.isRevocationService() ? revocation : normalDelete)),
				withOpContext((spec, opLog) ->
						novelFileUsed.set(HapiPropertySource.asFileString(
								spec.registry().getFileId(NOVEL_FILE_NAME))))
		};
	}

	private static LongSupplier persistentOrNegativeOne(FileScenario file) {
		return () -> Optional.ofNullable(file.getPersistent())
				.flatMap(s -> Optional.ofNullable(s.getNum()))
				.orElse(-1L);
	}

	private static HapiSpecOperation ensureValidatedFileExistence(
			String name,
			String contentsLoc,
			String pemLoc,
			LongSupplier num,
			LongConsumer numUpdate,
			Consumer<String> contentsUpdate
	) {
		return UtilVerbs.withOpContext((spec, opLog) -> {
			long fileNo = num.getAsLong();
			if (fileNo > 0) {
				var expectedContents = Files.readAllBytes(Paths.get(pathTo(contentsLoc)));
				var literal = idLiteral(num.getAsLong());
				var infoCheck = getFileInfo(literal)
						.setNodeFrom(ValidationScenarios::nextNode);
				allRunFor(spec, infoCheck);

				var info = infoCheck.getResponse().getFileGetInfo().getFileInfo();
				var ocKeystore = SpecUtils.asOcKeystore(new File(pemLoc), KeyFactory.PEM_PASSPHRASE);
				var expectedKey = Key.newBuilder()
						.setKeyList(KeyList.newBuilder()
								.addKeys(Key.newBuilder()
										.setEd25519(
												ByteString.copyFrom(Hex.decodeHex(ocKeystore.getPublicKeyAbyteStr())))))
						.build();
				Assert.assertEquals(
						String.format("File 0.0.%d had a different key than expected!", fileNo),
						expectedKey.getKeyList(),
						info.getKeys());

				var contentsCheck = getFileContents(literal)
						.setNodeFrom(ValidationScenarios::nextNode)
						.hasContents(ignore -> expectedContents);
				allRunFor(spec, contentsCheck);

				spec.registry().saveKey(name, expectedKey);
				spec.registry().saveFileId(name, fileId(fileNo));
				spec.keys().incorporateSimpleWacl(name, ocKeystore);
			} else {
				var contents = (contentsLoc != null)
						? Files.readAllBytes(Paths.get(pathTo(contentsLoc)))
						: ValidationScenarios.class.getClassLoader().getResourceAsStream(DEFAULT_CONTENTS_RESOURCE).readAllBytes();
				var filesDir = new File("files/");
				if (!filesDir.exists()) {
					filesDir.mkdir();
				}

				var fileName = DEFAULT_CONTENTS_RESOURCE.substring(DEFAULT_CONTENTS_RESOURCE.lastIndexOf("/") + 1);
				var fout = Files.newBufferedWriter(Paths.get(String.format("files/%s", fileName)));
				fout.write(new String(contents));
				fout.close();
				contentsUpdate.accept(fileName);

				var create = fileCreate(name)
						.setNodeFrom(ValidationScenarios::nextNode)
						.waclShape(KeyShape.listOf(1))
						.contents(contents);
				allRunFor(spec, create);
				var createdNo = create.numOfCreatedFile();
				var newLoc = pemLoc.replace("file-1", String.format("file%d", createdNo));
				spec.keys().exportSimpleWacl(newLoc, name);
				numUpdate.accept(createdNo);
			}
		});
	}

	private HapiApiSpec contractScenario() {
		try {
			ensureScenarios();
			if (scenarios.getContract() == null) {
				var cs = new ContractScenario();
				cs.setPersistent(new PersistentContract());
				scenarios.setContract(cs);
			}
			var contract = scenarios.getContract();

			Object[] donationArgs = new Object[] { Integer.valueOf((int)targetNetwork().getBootstrap()), "Hey, Ma!" };

			return customHapiSpec("ContractScenario")
					.withProperties(Map.of(
							"nodes", nodes(),
							"default.payer", primaryPayer(),
							"startupAccounts.literal", payerKeystoreLiteral()
					)).given(
							ensureValidatedContractExistence(
									PERSISTENT_CONTRACT_NAME,
									contract.getPersistent().getLuckyNo(),
									contract.getPersistent().getSource(),
									pemForContract(persistentContractOrNegativeOne(contract).getAsLong()),
									persistentContractOrNegativeOne(contract),
									num -> contract.getPersistent().setNum(num),
									bytecodeNum -> contract.getPersistent().setBytecode(bytecodeNum),
									luckyNo -> contract.getPersistent().setLuckyNo(luckyNo),
									loc -> contract.getPersistent().setSource(loc))
					).when(flattened(
							contractCall(PERSISTENT_CONTRACT_NAME)
									.setNodeFrom(ValidationScenarios::nextNode)
									.sending(1L),
							contractCall(PERSISTENT_CONTRACT_NAME, CONSPICUOUS_DONATION_ABI, donationArgs)
									.setNodeFrom(ValidationScenarios::nextNode)
									.via("donation"),
							getTxnRecord("donation")
									.setNodeFrom(ValidationScenarios::nextNode)
									.logged()
									.has(recordWith().transfers(
											includingDeduction(contract.getPersistent()::getNum, 1))),
							novelContractIfDesired(contract)
					)).then();
		} catch (Exception e) {
			log.warn("Unable to initialize contract scenario, skipping it!", e);
			errorsOccurred.set(true);
			return null;
		}
	}

	private static HapiSpecOperation[] novelContractIfDesired(ContractScenario contract) {
		if (!params.isNovelContent()) {
			return new HapiSpecOperation[0];
		}

		KeyShape complex = KeyShape.listOf(KeyShape.threshOf(2, 3), KeyShape.threshOf(1, 3));
		return new HapiSpecOperation[] {
				newKeyNamed("firstNovelKey").shape(complex),
				newKeyNamed("secondNovelKey"),
				contractCreate(NOVEL_CONTRACT_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.adminKey("firstNovelKey")
						.balance(1)
						.bytecode(() -> idLiteral(contract.getPersistent().getBytecode())),
				contractUpdate(NOVEL_CONTRACT_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.newKey("secondNovelKey"),
				contractDelete(NOVEL_CONTRACT_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.transferAccount(PERSISTENT_CONTRACT_NAME),
				withOpContext((spec, opLog) ->
						novelContractUsed.set(HapiPropertySource.asAccountString(
								spec.registry().getAccountID(NOVEL_CONTRACT_NAME))))

		};
	}

	private static HapiSpecOperation ensureValidatedContractExistence(
			String name,
			Integer luckyNo,
			String bytecodeLoc,
			String pemLoc,
			LongSupplier num,
			LongConsumer numUpdate,
			LongConsumer bytecodeNumUpdate,
			IntConsumer luckyNoUpdate,
			Consumer<String> sourceUpdate
	) {
		return UtilVerbs.withOpContext((spec, opLog) -> {
			long contractNo = num.getAsLong();
			if (contractNo > 0) {
				var literal = idLiteral(num.getAsLong());
				var infoCheck = getContractInfo(literal)
						.setNodeFrom(ValidationScenarios::nextNode);
				allRunFor(spec, infoCheck);

				var info = infoCheck.getResponse().getContractGetInfo().getContractInfo();
				var ocKeystore = SpecUtils.asOcKeystore(new File(pemLoc), KeyFactory.PEM_PASSPHRASE);
				var expectedKey = Key.newBuilder()
						.setEd25519(ByteString.copyFrom(Hex.decodeHex(ocKeystore.getPublicKeyAbyteStr())))
						.build();
				Assert.assertEquals(
						String.format("Contract 0.0.%d had a different key than expected!", contractNo),
						expectedKey,
						info.getAdminKey());

				var bytecodeCheck = getContractBytecode(literal)
						.setNodeFrom(ValidationScenarios::nextNode)
						.isNonEmpty();
				allRunFor(spec, bytecodeCheck);

				Object[] expected = new Object[] { BigInteger.valueOf(luckyNo) };
				var luckyNoCheck = contractCallLocal(literal, LUCKY_NO_LOOKUP_ABI)
						.setNodeFrom(ValidationScenarios::nextNode)
						.has(resultWith()
								.resultThruAbi(
										LUCKY_NO_LOOKUP_ABI,
										isLiteralResult(expected)));
				allRunFor(spec, luckyNoCheck);

				spec.registry().saveKey(name, expectedKey);
				spec.registry().saveContractId(name, contractId(contractNo));
				spec.registry().saveAccountId(name, accountId(contractNo));
				spec.keys().incorporate(name, ocKeystore);
			} else {
				var baseName = (bytecodeLoc != null) ? bytecodeLoc : DEFAULT_BYTECODE_RESOURCE;
				var bytecode = (bytecodeLoc != null)
						? Files.readAllBytes(Paths.get(pathToContract(bytecodeLoc)))
						: ValidationScenarios.class.getClassLoader().getResourceAsStream(DEFAULT_BYTECODE_RESOURCE).readAllBytes();
				var contractsDir = new File("contracts/");
				if (!contractsDir.exists()) {
					contractsDir.mkdir();
				}

				var fileName = baseName.substring(baseName.lastIndexOf("/") + 1);
				var fout = Files.newOutputStream(Paths.get(pathToContract(fileName)));
				fout.write(bytecode);
				fout.close();
				fileName = fileName.replace(".bin", ".sol");
				var sol = new String(ValidationScenarios.class.getClassLoader()
						.getResourceAsStream(DEFAULT_CONTRACT_RESOURCE).readAllBytes());
				var sourceOut = Files.newBufferedWriter(Paths.get(pathToContract(fileName)));
				sourceOut.write(sol);
				sourceOut.close();
				sourceUpdate.accept(fileName);

				var bytecodeName = name + "Bytecode";
				var bytecodeCreate = fileCreate(bytecodeName)
						.key(GENESIS)
						.setNodeFrom(ValidationScenarios::nextNode)
						.contents(bytecode);
				allRunFor(spec, bytecodeCreate);
				bytecodeNumUpdate.accept(bytecodeCreate.numOfCreatedFile());

				var create = contractCreate(PERSISTENT_CONTRACT_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.bytecode(bytecodeName);
				allRunFor(spec, create);

				Integer numberToUse = (luckyNo == null) ? DEFAULT_LUCKY_NUMBER : luckyNo;
				Object[] args = new Object[] { Integer.valueOf(numberToUse) };
				var setLucky = contractCall(PERSISTENT_CONTRACT_NAME, BELIEVE_IN_ABI, args);
				allRunFor(spec, setLucky);

				var createdNo = create.numOfCreatedContract();
				var newLoc = pemLoc.replace("contract-1", String.format("contract%d", createdNo));
				spec.keys().exportSimpleKey(newLoc, name);
				numUpdate.accept(createdNo);

				if (luckyNo == null) {
					luckyNoUpdate.accept(DEFAULT_LUCKY_NUMBER);
				}
			}
		});
	}

	private static LongSupplier persistentContractOrNegativeOne(ContractScenario contract) {
		return () -> Optional.ofNullable(contract.getPersistent())
				.flatMap(s -> Optional.ofNullable(s.getNum()))
				.orElse(-1L);
	}

	private HapiApiSpec consensusScenario() {
		try {
			ensureScenarios();
			if (scenarios.getConsensus() == null) {
				scenarios.setConsensus(new ConsensusScenario());
			}
			var consensus = scenarios.getConsensus();
			var expectedSeqNo = new AtomicLong(0);
			KeyShape complex = KeyShape.threshOf(1, KeyShape.listOf(2), KeyShape.threshOf(1, 3));

			return customHapiSpec("ConsensusScenario")
					.withProperties(Map.of(
							"nodes", nodes(),
							"default.payer", primaryPayer(),
							"startupAccounts.literal", payerKeystoreLiteral()
					)).given(
							ensureValidatedTopicExistence(
									PERSISTENT_TOPIC_NAME,
									pemForTopic(persistentTopicOrNegativeOne(consensus).getAsLong()),
									persistentTopicOrNegativeOne(consensus),
									consensus::setPersistent,
									expectedSeqNo)
					).when(flattened(
							submitMessageTo(PERSISTENT_TOPIC_NAME)
									.setNodeFrom(ValidationScenarios::nextNode)
									.message("The particular is pounded till it is man."),
							novelTopicIfDesired()
					)).then(
							getTopicInfo(PERSISTENT_TOPIC_NAME)
									.setNodeFrom(ValidationScenarios::nextNode)
									.hasSeqNo(expectedSeqNo::get)
									.logged()
					);
		} catch (Exception e) {
			log.warn("Unable to initialize consensus scenario, skipping it!", e);
			errorsOccurred.set(true);
			return null;
		}
	}

	private static HapiSpecOperation[] novelTopicIfDesired() {
		if (!params.isNovelContent()) {
			return new HapiSpecOperation[0];
		}

		KeyShape complex = KeyShape.threshOf(1, KeyShape.listOf(2), KeyShape.threshOf(1, 3));
		return new HapiSpecOperation[] {
				newKeyNamed("novelTopicAdmin").shape(complex),
				createTopic(NOVEL_TOPIC_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.adminKeyName("novelTopicAdmin")
						.submitKeyShape(KeyShape.SIMPLE),
				submitMessageTo(NOVEL_TOPIC_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.signedBy(GENESIS)
						.hasKnownStatus(INVALID_SIGNATURE),
				updateTopic(NOVEL_TOPIC_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.signedBy(GENESIS, "novelTopicAdmin")
						.submitKey(EMPTY_KEY),
				submitMessageTo(NOVEL_TOPIC_NAME)
						.setNodeFrom(ValidationScenarios::nextNode)
						.signedBy(GENESIS),
				deleteTopic(NOVEL_TOPIC_NAME)
						.setNodeFrom(ValidationScenarios::nextNode),
				withOpContext((spec, opLog) ->
						novelTopicUsed.set(HapiPropertySource.asTopicString(
								spec.registry().getTopicID(NOVEL_TOPIC_NAME))))
		};
	}

	private static HapiSpecOperation ensureValidatedTopicExistence(
			String name,
			String pemLoc,
			LongSupplier num,
			LongConsumer update,
			AtomicLong expectedSeqNo
	) {
		return UtilVerbs.withOpContext((spec, opLog) -> {
			long topicNo = num.getAsLong();
			if (topicNo > 0) {
				var check = getTopicInfo(idLiteral(num.getAsLong()))
						.setNodeFrom(ValidationScenarios::nextNode);
				allRunFor(spec, check);

				var info = check.getResponse().getConsensusGetTopicInfo().getTopicInfo();
				var ocKeystore = SpecUtils.asOcKeystore(new File(pemLoc), KeyFactory.PEM_PASSPHRASE);
				var expectedKey = Key.newBuilder()
						.setEd25519(ByteString.copyFrom(Hex.decodeHex(ocKeystore.getPublicKeyAbyteStr())))
						.build();
				Assert.assertEquals(
						String.format("Topic 0.0.%d had a different key than expected!", topicNo),
						expectedKey,
						info.getAdminKey());
				expectedSeqNo.set(info.getSequenceNumber() + 1);
				spec.registry().saveKey(name, expectedKey);
				spec.registry().saveTopicId(name, topicId(topicNo));
				spec.keys().incorporate(name, ocKeystore);
			} else {
				var create = createTopic(name)
						.setNodeFrom(ValidationScenarios::nextNode)
						.adminKeyShape(KeyShape.SIMPLE);
				allRunFor(spec, create);
				var createdNo = create.numOfCreatedTopic();
				var newLoc = pemLoc.replace("topic-1", String.format("topic%d", createdNo));
				spec.keys().exportSimpleKey(newLoc, name);
				update.accept(createdNo);
				expectedSeqNo.set(1);
			}
		});
	}

	private static LongSupplier persistentTopicOrNegativeOne(ConsensusScenario consensus) {
		return () -> Optional.ofNullable(consensus.getPersistent()).orElse(-1L);
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	private static void parse(String[] args) {
		var KEY_VALUE_PATTERN = Pattern.compile("([\\w\\d]+)=([\\w\\d,]+)");

		for (String arg : args)	{
			var matcher = KEY_VALUE_PATTERN.matcher(arg);
			if (!matcher.matches()) {
				log.warn(String.format("Ignoring command-line argument '%s'", arg));
			} else {
				if ("target".equals(keyOf(matcher))) {
					params.setTargetNetwork(valueOf(matcher));
				} else if ("defaultNodePayment".equals(keyOf(matcher))) {
					try {
						params.setDefaultNodePayment(Long.parseLong(valueOf(matcher)));
					} catch (NumberFormatException ignore) {}
				} else if ("novel".equals(keyOf(matcher))) {
					params.setNovelContent(valueOf(matcher).toLowerCase().equals("true"));
				} else if ("revocation".equals(keyOf(matcher))) {
					params.setRevocationService(valueOf(matcher).toLowerCase().equals("true"));
				} else if ("scenarios".equals(keyOf(matcher))) {
					Set<String> legal = Arrays.stream(Scenario.class.getEnumConstants())
							.map(Object::toString)
							.collect(Collectors.toSet());
					List<String> listed = Arrays.stream(valueOf(matcher).split(","))
							.map(name -> name.equals("syskeys") ? "SYSTEM_KEYS" : name)
							.filter(v -> legal.contains(v.toUpperCase()))
							.collect(Collectors.toList());
					if (listed.size() == 1) {
						params.setScenarios(EnumSet.of(Scenario.valueOf(listed.get(0).toUpperCase())));
					} else if (listed.size() > 1) {
						params.setScenarios(EnumSet.of(
								Scenario.valueOf(listed.get(0).toUpperCase()),
								listed.subList(1, listed.size())
										.stream()
										.map(String::toUpperCase)
										.map(Scenario::valueOf)
										.toArray(n -> new Scenario[n])));
					}
				} else {
					log.warn(String.format("Ignoring unknown parameter key '%s'", keyOf(matcher)));
				}
			}
		}
	}

	private static String keyOf(Matcher m) {
		return m.group(1);
	}

	private static String valueOf(Matcher m) {
		return m.group(2);
	}

	enum Scenario { CRYPTO, FILE, CONTRACT, CONSENSUS, SYSTEM_KEYS }

	private static class ScenarioParams {
		static long DEFAULT_NODE_PAYMENT_TINYBARS = 25;
		final static String PASSPHRASE_ENV_VAR = "BOOTSTRAP_PASSPHRASE";
		final static String DEFAULT_PASSPHRASE = "swirlds";

		private long defaultNodePayment = DEFAULT_NODE_PAYMENT_TINYBARS;
		private String targetNetwork;
		private boolean revocationService = false;
		private boolean novelContent = true;
		private EnumSet<Scenario> scenarios = EnumSet.noneOf(Scenario.class);

		boolean isRevocationService() {
			return revocationService;
		}

		String getRawPassphrase() {
			return Optional.ofNullable(System.getenv(PASSPHRASE_ENV_VAR)).orElse(DEFAULT_PASSPHRASE);
		}

		public String getPrintablePassphrase() {
			if (System.getenv(PASSPHRASE_ENV_VAR) != null) {
				return String.format("******* [from $%s]", PASSPHRASE_ENV_VAR);
			} else {
				return DEFAULT_PASSPHRASE;
			}
		}

		public long getDefaultNodePayment() {
			return defaultNodePayment;
		}

		public void setDefaultNodePayment(long defaultNodePayment) {
			this.defaultNodePayment = defaultNodePayment;
		}

		public void setRevocationService(boolean revocationService) {
			this.revocationService = revocationService;
		}

		public EnumSet<Scenario> getScenarios() {
			return scenarios;
		}

		public void setScenarios(EnumSet<Scenario> scenarios) {
			this.scenarios = scenarios;
		}

		public String getTargetNetwork() {
			return targetNetwork;
		}

		public void setTargetNetwork(String targetNetwork) {
			this.targetNetwork = targetNetwork;
		}

		public boolean isNovelContent() {
			return novelContent;
		}

		public void setNovelContent(boolean novelContent) {
			this.novelContent = novelContent;
		}
	}

	private static void assertValidParams() {
		boolean exit = false;
		if (!validationConfig.getNetworks().containsKey(params.getTargetNetwork())) {
			log.error(String.format("No config present for network '%s', exiting.", params.getTargetNetwork()));
			exit = true;
		}
		for (Node node : targetNetwork().getNodes()) {
			nodeAccounts.add(String.format("0.0.%d", node.getAccount()));
		}
		if (exit) {
			System.exit(1);
		}
	}

	private static void readConfig() {
		var yamlIn = new Yaml(new Constructor(ValidationConfig.class));
		try {
			validationConfig = yamlIn.load(Files.newInputStream(Paths.get("config.yml")));
		} catch (IOException e) {
			log.error("Could not find valid 'config.yml' in working directory, exiting.", e);
			System.exit(1);
		}
	}

	private static String nextNode() {
		var account = nodeAccounts.get(nextAccount++);
		nextAccount %= nodeAccounts.size();
		return account;
	}

	private static String nodes() {
		return targetNetwork().getNodes()
				.stream()
				.map(node -> String.format("%s:0.0.%d", node.getIpv4Addr(), node.getAccount()))
				.collect(Collectors.joining(","));
	}

	private static String primaryPayer() {
		return String.format("0.0.%d", targetNetwork().getBootstrap());
	}

	private static Network targetNetwork() {
		return validationConfig.getNetworks().get(params.getTargetNetwork());
	}

	private static String payerKeystoreLiteral() throws IOException, KeyStoreException {
		String loc = pemForAccount(targetNetwork().getBootstrap());
		var f = new File(loc);
		if (!f.exists()) {
			log.error(String.format("Missing bootstrap PEM @ '%s', exiting.", loc));
		}

		return SpecUtils.asSerializedOcKeystore(f, params.getRawPassphrase(), accountId(targetNetwork().getBootstrap()));
	}

	private static AccountID accountId(long num) {
		return HapiPropertySource.asAccount(String.format("0.0.%d", num));
	}

	private static TopicID topicId(long num) {
		return HapiPropertySource.asTopic(String.format("0.0.%d", num));
	}

	private static FileID fileId(long num) {
		return HapiPropertySource.asFile(String.format("0.0.%d", num));
	}

	private static ContractID contractId(long num) {
		return HapiPropertySource.asContract(String.format("0.0.%d", num));
	}

	private static String idLiteral(long num) {
		return String.format("0.0.%d", num);
	}

	private static String pemForAccount(long num) {
		return String.format("keys/%s-account%d.pem", params.getTargetNetwork(), num);
	}

	private static String pemForTopic(long num) {
		return String.format("keys/%s-topic%d.pem", params.getTargetNetwork(), num);
	}

	private static String pemForFile(long num) {
		return String.format("keys/%s-file%d.pem", params.getTargetNetwork(), num);
	}

	private static String pemForContract(long num) {
		return String.format("keys/%s-contract%d.pem", params.getTargetNetwork(), num);
	}

	private static String pathTo(String contents) {
		return String.format("files/%s", contents);
	}

	private static String pathToContract(String contents) {
		return String.format("contracts/%s", contents);
	}

	private static void persistUpdatedConfig() {
		var yamlOut = new Yaml(new SkipNullRepresenter());
		var doc = yamlOut.dumpAs(validationConfig, Tag.MAP, null);
		try {
			var writer = Files.newBufferedWriter(Paths.get("config.yml"));
			writer.write(doc);
			writer.close();
		} catch (IOException e) {
			log.warn("Could not update config.yml with scenario results, skipping!", e);
		}
	}

	private static class SkipNullRepresenter extends Representer {
		@Override
		protected NodeTuple representJavaBeanProperty(
				Object javaBean,
				Property property,
				Object propertyValue,
				Tag customTag
		) {
			if (propertyValue == null) {
				return null;
			} else {
				return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
			}
		}
	}

	private static void ensureScenarios() {
		if (targetNetwork().getScenarios() == null) {
			scenarios = new Scenarios();
			targetNetwork().setScenarios(scenarios);
		} else {
			scenarios = targetNetwork().getScenarios();
		}
	}

	private static void printBalanceChange() {
		if (startingBalance.get() >= 0 && endingBalance.get() >= 0) {
			long payerChange = endingBalance.get() - startingBalance.get();
			log.info(String.format(
					"0.0.%d balance change was %d tinyBars (%.2f \u0127)",
					targetNetwork().getBootstrap(),
					payerChange,
					(double)payerChange / 100_000_000));
		} else if (startingBalance.get() >= 0) {
			log.info(String.format(
					"0.0.%d balance is now %d tinyBars (%.2f \u0127)",
					targetNetwork().getBootstrap(),
					startingBalance.get(),
					(double)startingBalance.get() / 100_000_000));
		}
	}

	private static void printNovelUsage() {
		log.info("------------------------------------------------------------------");
		Optional.ofNullable(novelAccountUsed.get()).ifPresent(s ->
				log.info("Novel account used (should now be deleted) was " + s));
		Optional.ofNullable(novelFileUsed.get()).ifPresent(s ->
				log.info("Novel file used (should now be deleted) was " + s));
		Optional.ofNullable(novelContractUsed.get()).ifPresent(s ->
				log.info("Novel contract used (should now be deleted) was " + s));
		Optional.ofNullable(novelTopicUsed.get()).ifPresent(s ->
				log.info("Novel topic used (should now be deleted) was " + s));
	}
}

