package com.hedera.services.yahcli.suites;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.HapiApiSpec.CostSnapshotMode.TAKE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_CHILD_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.GET_CHILD_RESULT_ABI;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getTransactionFee;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

public class CostOfEveryThingSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CostOfEveryThingSuite.class);

	enum service {
		CRYPTO, CONSENSUS, TOKEN, FILE, CONTRACT, SCHEDULED, INVALID
	}

	private final Map<String, service> SERVICES_TO_ENUM = Map.ofEntries(
			Map.entry("crypto", service.CRYPTO),
			Map.entry("consensus", service.CONSENSUS),
			Map.entry("token", service.TOKEN),
			Map.entry("file", service.FILE),
			Map.entry("contract", service.CONTRACT),
			Map.entry("scheduled", service.SCHEDULED));
	private final Set<service> VALID_SERVICES = new HashSet<>(SERVICES_TO_ENUM.values());

	HapiApiSpec.CostSnapshotMode costSnapshotMode = TAKE;
	private final Map<String, String> specConfig;
	private final EnumSet<service> services;
	private StringBuilder feeTableBuilder;
	private String serviceBorder;

	public CostOfEveryThingSuite(final Map<String, String> specConfig,
			final StringBuilder feeTableBuilder, final String serviceBorder, final String[] services) {
		this.specConfig = specConfig;
		this.feeTableBuilder = feeTableBuilder;
		this.serviceBorder = serviceBorder;
		this.services = rationalized(services);
	}

	private EnumSet<service> rationalized(final String[] services) {
		if(Arrays.asList(services).contains("all")) {
			return (EnumSet<service>) VALID_SERVICES;
		}
		return Arrays.stream(services)
				.map(s -> SERVICES_TO_ENUM.getOrDefault(s, service.INVALID))
				.peek(s -> {
					if (!VALID_SERVICES.contains(s)) {
						throw new IllegalArgumentException("Invalid service provided!");
					}
				}).collect(Collectors.toCollection(() -> EnumSet.noneOf(service.class)));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return Stream.of(
				ofNullable(services.contains(service.CRYPTO) ? canonicalCryptoOps() : null),
				ofNullable(services.contains(service.CONSENSUS) ? canonicalTopicOps() : null),
				ofNullable(services.contains(service.TOKEN) ? canonicalTokenOps() : null),
				ofNullable(services.contains(service.FILE) ? canonicalFileOps() : null),
				ofNullable(services.contains(service.CONTRACT) ? canonicalContractOps() : null),
				ofNullable(services.contains(service.SCHEDULED) ? canonicalScheduleOps() : null)
		).flatMap(Optional::stream).collect(toList());
	}

	HapiApiSpec canonicalContractOps() {
//		appendServiceName("Smart Contract Service");
		return HapiApiSpec.customHapiSpec(String.format("canonicalContractOps"))
				.withProperties(
						specConfig,
						Map.of("cost.snapshot.mode", costSnapshotMode.toString())
				)
				.given(
						newKeyNamed("key").shape(SIMPLE),
						cryptoCreate("payer")
								.key("key")
								.balance(10_000_000_000L),
						fileCreate("contractFile")
								.payingWith("payer")
								.path("resources/CreateTrivial.bin")
				)
				.when(
						contractCreate("testContract")
								.blankMemo()
								.entityMemo("")
								.bytecode("contractFile")
								.adminKey("key")
								.autoRenewSecs(THREE_MONTHS_IN_SECONDS-1)
								.gas(30000)
								.payingWith("payer")
								.hasKnownStatus(SUCCESS)
								.via("canonicalContractCreate"),
						contractUpdate("testContract")
								.newMemo("")
								.blankMemo()
								.payingWith("payer")
								.newKey("key")
								.newExpirySecs(THREE_MONTHS_IN_SECONDS)
								.via("canonicalContractUpdate"),
						contractCall("testContract", CREATE_CHILD_ABI)
								.blankMemo()
								.payingWith("payer")
								.gas(100000)
								.via("canonicalContractCall"),
						getContractInfo("testContract")
								.payingWith("payer")
								.via("canonicalGetContractInfo"),
						contractCallLocal("testContract", GET_CHILD_RESULT_ABI)
								.payingWith("payer")
								.nodePayment(100_000_000)
								.gas(50000)
								.via("canonicalContractCallLocal"),
						getContractBytecode("testContract")
								.payingWith("payer")
								.via("canonicalGetContractByteCode"),
						getContractRecords("testContract")
								.logged()
								.via("canonicalGetContractRecords"),
						contractDelete("testContract")
								.blankMemo()
								.payingWith("payer")
								.via("canonicalContractDelete")
				)
				.then(
						withOpContext((spec, log) -> appendServiceName("Smart Contract Service")),
						getTransactionFee("canonicalContractCreate", feeTableBuilder, "contractCreate"),
						getTransactionFee("canonicalContractUpdate", feeTableBuilder, "contractUpdate"),
						getTransactionFee("canonicalContractCall", feeTableBuilder, "contractCall"),
						getTransactionFee("canonicalGetContractInfo", feeTableBuilder, "getContractInfo"),
						getTransactionFee("canonicalContractCallLocal", feeTableBuilder, "contractCallLocal"),
						getTransactionFee("canonicalGetContractByteCode", feeTableBuilder, "getContractByteCode"),
						getTransactionFee("canonicalGetContractRecords", feeTableBuilder, "getContractRecords"),
						getTransactionFee("canonicalContractDelete", feeTableBuilder, "contractDelete")
				);
	}

	HapiApiSpec canonicalFileOps() {
//		appendServiceName("File Service");
		int fileSize = 1000;
		final byte[] first = randomUtf8Bytes(fileSize);
		final byte[] next = randomUtf8Bytes(fileSize);

		return HapiApiSpec.customHapiSpec(String.format("canonicalFileOps"))
				.withProperties(
						specConfig,
						Map.of("cost.snapshot.mode", costSnapshotMode.toString())
				)
				.given(
						newKeyNamed("key").shape(SIMPLE),
						cryptoCreate("payer")
								.key("key")
								.balance(1_000_000_000L),
						newKeyListNamed("WACL", List.of("payer")),
						fileCreate("memorable")
								.blankMemo()
								.entityMemo("")
								.payingWith("payer")
								.key("WACL")
								.expiry(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS-1)
								.contents(first)
								.via("canonicalFileCreate")
				)
				.when(
						fileAppend("memorable")
								.blankMemo()
								.payingWith("payer")
								.content(next)
								.via("canonicalFileAppend"),
						fileUpdate("memorable")
								.blankMemo()
								.entityMemo("")
								.payingWith("payer")
								.contents(next)
								.extendingExpiryBy(THREE_MONTHS_IN_SECONDS)
								.via("canonicalFileUpdate"),
						getFileContents("memorable")
								.via("canonicalGetFileContents"),
						getFileInfo("memorable")
								.via("canonicalGetFileInfo"),
						fileDelete("memorable")
								.blankMemo()
								.payingWith("payer")
								.via("canonicalFileDelete")
				)
				.then(
						withOpContext((spec, log) -> appendServiceName("File Service")),
						getTransactionFee("canonicalFileCreate", feeTableBuilder, "fileCreate"),
						getTransactionFee("canonicalFileAppend", feeTableBuilder, "fileAppend"),
						getTransactionFee("canonicalFileUpdate", feeTableBuilder, "fileUpdate"),
						getTransactionFee("canonicalGetFileContents", feeTableBuilder, "getFileContents"),
						getTransactionFee("canonicalGetFileInfo", feeTableBuilder, "getFileInfo"),
						getTransactionFee("canonicalFileDelete", feeTableBuilder, "fileDelete")

				);
	}

	HapiApiSpec canonicalTopicOps() {
//		appendServiceName("Consensus Service");
		return HapiApiSpec.customHapiSpec(String.format("canonicalTopicOps"))
				.withProperties(
						specConfig,
						Map.of("cost.snapshot.mode", costSnapshotMode.toString())
				)
				.given(
						newKeyNamed("key").shape(SIMPLE),
						cryptoCreate("payer")
								.key("key")
								.balance(100_000_000L)
				)
				.when(
						createTopic("testTopic")
								.blankMemo()
								.topicMemo("")
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS-1)
								.adminKeyName("payer")
								.payingWith("payer")
								.via("canonicalTopicCreate"),
						updateTopic("testTopic")
								.blankMemo()
								.topicMemo("")
								.payingWith("payer")
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
								.via("canonicalTopicUpdate"),
						submitMessageTo("testTopic")
								.message("testMessage")
								.payingWith("payer")
								.hasKnownStatus(SUCCESS)
								.via("canonicalSubmitMessage"),
						getTopicInfo("testTopic")
								.payingWith("payer")
								.via("canonicalGetTopicInfo"),
						deleteTopic("testTopic")
								.payingWith("payer")
								.via("canonicalTopicDelete")
				)
				.then(
						withOpContext((spec, log) -> appendServiceName("Consensus Service")),
						getTransactionFee("canonicalTopicCreate", feeTableBuilder, "consensusCreateTopic"),
						getTransactionFee("canonicalTopicUpdate", feeTableBuilder, "consensusUpdateTopic"),
						getTransactionFee("canonicalSubmitMessage", feeTableBuilder, "consensusSubmitMessage"),
						getTransactionFee("canonicalGetTopicInfo", feeTableBuilder, "consensusGetInfo"),
						getTransactionFee("canonicalTopicDelete", feeTableBuilder, "consensusDeleteTopic")
				);
	}

	HapiApiSpec canonicalTokenOps() {
//		appendServiceName("Token Service");
		return HapiApiSpec.customHapiSpec(String.format("canonicalTokenOps"))
				.withProperties(
						specConfig,
						Map.of("cost.snapshot.mode", costSnapshotMode.toString())
				)
				.given(
						newKeyNamed("adminKey").shape(SIMPLE),
						newKeyNamed("freezeKey"),
						newKeyNamed("kycKey"),
						newKeyNamed("supplyKey"),
						newKeyNamed("wipeKey"),
						cryptoCreate(TOKEN_TREASURY)
								.key("adminKey")
								.balance(1_000 * ONE_HBAR),
						cryptoCreate("autoRenewAccount")
								.key("adminKey")
								.balance(0L),
						cryptoCreate("testAccountA")
								.key("adminKey")
				)
				.when(
						tokenCreate("primary")
								.entityMemo("")
								.blankMemo()
								.name("aStrOfSize12")
								.symbol("smbl")
								.treasury(TOKEN_TREASURY)
								.autoRenewAccount("autoRenewAccount")
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS-1)
								.adminKey("adminKey")
								.payingWith(TOKEN_TREASURY)
								.via("canonicalTokenCreate"),
						tokenUpdate("primary")
								.entityMemo("")
								.blankMemo()
								.payingWith(TOKEN_TREASURY)
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
								.via("canonicalTokenUpdate"),
						tokenCreate("testToken")
								.entityMemo("")
								.name("testCoin")
								.treasury(TOKEN_TREASURY)
								.autoRenewAccount("autoRenewAccount")
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
								.initialSupply(500)
								.decimals(1)
								.adminKey("adminKey")
								.freezeKey("freezeKey")
								.kycKey("kycKey")
								.supplyKey("supplyKey")
								.wipeKey("wipeKey"),
						mintToken("testToken", 1)
								.blankMemo()
								.payingWith(TOKEN_TREASURY)
								.via("cannonicalMintToken"),
						burnToken("testToken", 1)
								.blankMemo()
								.payingWith(TOKEN_TREASURY)
								.via("canonicalBurnToken"),
						tokenAssociate("testAccountA", "testToken")
								.blankMemo()
								.payingWith(TOKEN_TREASURY)
								.via("canonicalTokenAssociation"),
						revokeTokenKyc("testToken", "testAccountA")
								.blankMemo()
								.payingWith(TOKEN_TREASURY)
								.via("canonicalTokenRevokeKyc"),
						grantTokenKyc("testToken", "testAccountA")
								.blankMemo()
								.payingWith(TOKEN_TREASURY)
								.via("canonicalTokenGrantKyc"),
						tokenFreeze("testToken", "testAccountA")
								.blankMemo()
								.payingWith(TOKEN_TREASURY)
								.via("canonicalTokenFreeze"),
						tokenUnfreeze("testToken", "testAccountA")
								.blankMemo()
								.payingWith(TOKEN_TREASURY)
								.via("canonicalTokenUnFreeze"),
						cryptoTransfer(moving(1, "testToken")
								.between(TOKEN_TREASURY, "testAccountA"))
								.blankMemo()
								.payingWith(TOKEN_TREASURY)
								.via("canonicalTokenTransfer"),
						wipeTokenAccount("testToken", "testAccountA", 1)
								.payingWith(TOKEN_TREASURY)
								.blankMemo()
								.via("canonicalTokenWipe"),
						tokenDissociate("testAccountA", "testToken")
								.blankMemo()
								.payingWith(TOKEN_TREASURY)
								.via("canonicalTokenDissociation"),
						getTokenInfo("testToken")
								.payingWith(TOKEN_TREASURY)
								.via("canonicalTokenGetInfo"),
						tokenDelete("testToken")
								.blankMemo()
								.payingWith(TOKEN_TREASURY)
								.via("canonicalTokenDelete")
				)
				.then(
						withOpContext((spec, log) -> appendServiceName("Token Service")),
						getTransactionFee("canonicalTokenCreate", feeTableBuilder, "tokenCreate"),
						getTransactionFee("canonicalTokenUpdate", feeTableBuilder, "tokenUpdate"),
						getTransactionFee("cannonicalMintToken", feeTableBuilder, "tokenMintSingle"),
						getTransactionFee("canonicalBurnToken", feeTableBuilder, "tokenBurnSingle"),
						getTransactionFee("canonicalTokenAssociation", feeTableBuilder, "tokenAssociate"),
						getTransactionFee("canonicalTokenGrantKyc", feeTableBuilder, "tokenGrantKyc"),
						getTransactionFee("canonicalTokenRevokeKyc", feeTableBuilder, "tokenRevokeKyc"),
						getTransactionFee("canonicalTokenFreeze", feeTableBuilder, "tokenFreeze"),
						getTransactionFee("canonicalTokenUnFreeze", feeTableBuilder, "tokenUnFreeze"),
						getTransactionFee("canonicalTokenTransfer", feeTableBuilder, "tokenTransfer"),
						getTransactionFee("canonicalTokenWipe", feeTableBuilder, "tokenWipe"),
						getTransactionFee("canonicalTokenDissociation", feeTableBuilder, "tokenDissociate"),
						getTransactionFee("canonicalTokenGetInfo", feeTableBuilder, "getTokenInfo"),
						getTransactionFee("canonicalTokenDelete", feeTableBuilder, "tokenDelete")
				);
	}

	HapiApiSpec canonicalCryptoOps() {
//		appendServiceName("Cryptocurrency Service");
		KeyShape shape = SIMPLE;
		KeyShape smallKey = threshOf(1, 3);
		KeyShape midsizeKey = listOf(SIMPLE, listOf(2), threshOf(1, 2));
		KeyShape hugeKey = threshOf(4, SIMPLE, SIMPLE, listOf(4), listOf(3), listOf(2));

		return HapiApiSpec.customHapiSpec(String.format("canonicalCryptoOps"))
				.withProperties(
						specConfig,
						Map.of("cost.snapshot.mode", costSnapshotMode.toString())
				)
				.given(
						newKeyNamed("key").shape(shape),
						newKeyNamed("smallKey").shape(smallKey),
						newKeyNamed("midsizeKey").shape(midsizeKey),
						newKeyNamed("hugeKey").shape(hugeKey),
						cryptoCreate("small").key("smallKey"),
						cryptoCreate("midsize").key("midsizeKey"),
						cryptoCreate("huge").key("hugeKey")
				)
				.when(
						cryptoCreate("canonicalAccount")
								.key("key")
								.blankMemo()
								.balance(10_000_000L)
								.entityMemo("")
								.autoRenewSecs(THREE_MONTHS_IN_SECONDS)
								.via("canonicalCryptoCreation"),
						cryptoUpdate("canonicalAccount")
								.payingWith("canonicalAccount")
								.blankMemo()
								.expiring(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS)
								.entityMemo("")
								.via("canonicalCryptoUpdate"),
						cryptoCreate("testAccount")
								.key("key"),
						cryptoTransfer(tinyBarsFromTo("canonicalAccount", "testAccount", 1L))
								.payingWith("canonicalAccount")
								.blankMemo()
								.via("canonicalCryptoTransfer"),
						getAccountRecords("canonicalAccount")
								.via("canonicalGetRecords"),
						getAccountInfo("canonicalAccount")
								.via("canonicalGetAccountInfo"),
						cryptoCreate("canonicalAccountTBD")
								.key("key"),
						cryptoDelete("canonicalAccountTBD")
								.blankMemo()
								.via("canonicalCryptoDeletion")
				)
				.then(
						withOpContext((spec, log) -> appendServiceName("Cryptocurrency Service")),
						getTransactionFee("canonicalCryptoCreation", feeTableBuilder, "cryptoCreate"),
						getTransactionFee("canonicalCryptoUpdate", feeTableBuilder, "cryptoUpdate"),
						getTransactionFee("canonicalCryptoTransfer", feeTableBuilder, "cryptoTransfer"),
						getTransactionFee("canonicalGetRecords", feeTableBuilder, "cryptoGetAccountRecords"),
						getTransactionFee("canonicalGetAccountInfo", feeTableBuilder, "cryptoGetAccountInfo"),
						getTransactionFee("canonicalCryptoDeletion", feeTableBuilder, "cryptoDelete")
				);
	}

	HapiApiSpec canonicalScheduleOps() {
//		appendServiceName("Schedule Transaction Service");
		return HapiApiSpec.customHapiSpec(String.format("canonicalScheduleOps"))
				.withProperties(
						specConfig,
						Map.of("cost.snapshot.mode", costSnapshotMode.toString())
				)
				.given(
						cryptoCreate("payingSender")
								.balance(A_HUNDRED_HBARS),
						cryptoCreate("receiver")
								.balance(0L)
								.receiverSigRequired(true)
				)
				.when(
						scheduleCreate("canonicalSchedule",
								cryptoTransfer(tinyBarsFromTo("payingSender", "receiver", 1L))
										.memo("")
										.fee(ONE_HBAR)
										.signedBy("payingSender")
						)
								.via("canonicalScheduleCreation")
								.payingWith("payingSender")
								.adminKey("payingSender")
								.inheritingScheduledSigs(),
						getScheduleInfo("canonicalSchedule")
								.payingWith("payingSender"),
						scheduleSign("canonicalSchedule")
								.via("canonicalScheduleSigning")
								.payingWith("payingSender")
								.withSignatories("receiver"),
						scheduleCreate("tbd",
								cryptoTransfer(tinyBarsFromTo("payingSender", "receiver", 1L))
										.memo("")
										.fee(ONE_HBAR)
										.signedBy("payingSender")
						)
								.payingWith("payingSender")
								.adminKey("payingSender")
								.inheritingScheduledSigs(),
						scheduleDelete("tbd")
								.via("canonicalScheduleDeletion")
								.payingWith("payingSender")
				)
				.then(
						withOpContext((spec, log) -> appendServiceName("Schedule Transaction Service")),
						getTransactionFee("canonicalScheduleCreation", feeTableBuilder, "scheduleCreate"),
						getTransactionFee("canonicalScheduleSigning", feeTableBuilder, "scheduleSign"),
						getTransactionFee("canonicalScheduleDeletion", feeTableBuilder, "scheduleDelete")
				);
	}

	private void appendServiceName(final String serviceName) {
		feeTableBuilder.append(serviceBorder);
		feeTableBuilder.append(String.format("%30s | Fees \t\t |\n", serviceName));
		feeTableBuilder.append(serviceBorder);
	}
}
