package com.hedera.services.bdd.suites.autorenew;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SEND_TO_TWO_ABI;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.disablingAutoRenewWithDefaults;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.leavingAutoRenewDisabledWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;

public class NoGprIfNoAutoRenewSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(NoGprIfNoAutoRenewSuite.class);

	public static void main(String... args) {
		new NoGprIfNoAutoRenewSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						noGracePeriodRestrictionsIfNoAutoRenewSuiteSetup(),

						payerRestrictionsNotEnforced(),
//						cryptoTransferRestrictionsEnforced(),
//						tokenMgmtRestrictionsEnforced(),
//						cryptoDeleteRestrictionsEnforced(),
//						treasuryOpsRestrictionEnforced(),
//						tokenAutoRenewOpsEnforced(),
//						topicAutoRenewOpsEnforced(),
//						cryptoUpdateRestrictionsEnforced(),
//						contractCallRestrictionsEnforced(),

//						noGracePeriodRestrictionsIfNoAutoRenewSuiteCleanup(),
				}
		);
	}

	private HapiApiSpec contractCallRestrictionsEnforced() {
		final var civilian = "misc";
		final var detachedAccount = "gone";
		final var bytecode = "bytecode";
		final var contract = "doubleSend";
		final AtomicInteger detachedNum = new AtomicInteger();
		final AtomicInteger civilianNum = new AtomicInteger();

		return defaultHapiSpec("ContractCallRestrictionsEnforced")
				.given(
						fileCreate(bytecode).path(ContractResources.DOUBLE_SEND_BYTECODE_PATH),
						contractCreate(contract)
								.balance(ONE_HBAR)
								.bytecode(bytecode),
						cryptoCreate(civilian)
								.balance(0L),
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(1)
				).when(
						sleepFor(1_500L),
						cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
						withOpContext((spec, opLog) -> {
							detachedNum.set((int) spec.registry().getAccountID(detachedAccount).getAccountNum());
							civilianNum.set((int) spec.registry().getAccountID(civilian).getAccountNum());
						}),
						sourcing(() -> contractCall(contract, SEND_TO_TWO_ABI, new Object[] {
								civilianNum.get(), detachedNum.get()
						})
								.hasKnownStatus(INVALID_SOLIDITY_ADDRESS)),
						getAccountBalance(civilian).hasTinyBars(0L),
						getAccountBalance(detachedAccount).hasTinyBars(0L)
				).then(
						cryptoUpdate(detachedAccount)
								.expiring(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS),
						sourcing(() -> contractCall(contract, SEND_TO_TWO_ABI, new Object[] {
								civilianNum.get(), detachedNum.get()
						})),
						getAccountBalance(civilian).hasTinyBars(1L),
						getAccountBalance(detachedAccount).hasTinyBars(1L)
				);
	}

	private HapiApiSpec cryptoUpdateRestrictionsEnforced() {
		final var detachedAccount = "gone";
		final long certainlyPast = Instant.now().getEpochSecond() - THREE_MONTHS_IN_SECONDS;
		final long certainlyDistant = Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS;

		return defaultHapiSpec("CryptoUpdateRestrictionsEnforced")
				.given(
						newKeyNamed("ntb"),
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(1),
						sleepFor(1_500L),
						cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L))
				).when(
						cryptoUpdate(detachedAccount)
								.memo("Can't update receiverSigRequired")
								.receiverSigRequired(true)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						cryptoUpdate(detachedAccount)
								.memo("Can't update key")
								.key("ntb")
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						cryptoUpdate(detachedAccount)
								.memo("Can't update auto-renew period")
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						cryptoUpdate(detachedAccount)
								.memo("Can't update memo")
								.entityMemo("NOPE")
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						cryptoUpdate(detachedAccount)
								.memo("Can't pass precheck with past expiry")
								.expiring(certainlyPast)
								.hasPrecheck(INVALID_EXPIRATION_TIME),
						cryptoUpdate(detachedAccount)
								.memo("CAN extend expiry")
								.expiring(certainlyDistant)
				).then(
						cryptoUpdate(detachedAccount)
								.memo("Should work now!")
								.receiverSigRequired(true),
						cryptoUpdate(detachedAccount)
								.key("ntb"),
						cryptoUpdate(detachedAccount)
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS),
						cryptoUpdate(detachedAccount)
								.entityMemo("NOPE"),
						cryptoUpdate(detachedAccount)
								.expiring(certainlyDistant - 1_234L)
								.hasKnownStatus(EXPIRATION_REDUCTION_NOT_ALLOWED)
				);
	}

	private HapiApiSpec payerRestrictionsNotEnforced() {
		final var notDetachedAccount = "gone";

		return defaultHapiSpec("PayerRestrictionsEnforced")
				.given(
						cryptoCreate(notDetachedAccount)
								.balance(0L)
								.autoRenewSecs(1)
				).when(
						sleepFor(1_500L),
						cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L))
				).then(
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
								.payingWith(notDetachedAccount)
								.hasPrecheck(INSUFFICIENT_PAYER_BALANCE),
						getAccountInfo("0.0.2")
								.payingWith(notDetachedAccount)
								.hasCostAnswerPrecheck(INSUFFICIENT_PAYER_BALANCE),
						getAccountInfo("0.0.2")
								.payingWith(notDetachedAccount)
								.nodePayment(666L)
								.hasAnswerOnlyPrecheck(INSUFFICIENT_PAYER_BALANCE),
						scheduleCreate("notToBe",
								cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1))
						)
								.designatingPayer(notDetachedAccount)
								.hasKnownStatus(INSUFFICIENT_PAYER_BALANCE)

				);
	}

	private HapiApiSpec topicAutoRenewOpsEnforced() {
		final var topicWithDetachedAsAutoRenew = "c";
		final var topicSansDetachedAsAutoRenew = "d";
		final var detachedAccount = "gone";
		final var adminKey = "tak";
		final var civilian = "misc";
		final var notToBe = "nope";

		return defaultHapiSpec("TopicAutoRenewOpsEnforced")
				.given(
						newKeyNamed(adminKey),
						cryptoCreate(civilian)
				).when(
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(2),
						createTopic(topicWithDetachedAsAutoRenew)
								.adminKeyName(adminKey)
								.autoRenewAccountId(detachedAccount),
						createTopic(topicSansDetachedAsAutoRenew)
								.adminKeyName(adminKey)
								.autoRenewAccountId(civilian),
						sleepFor(1_500L)
				).then(
						createTopic(notToBe)
								.adminKeyName(adminKey)
								.autoRenewAccountId(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						updateTopic(topicWithDetachedAsAutoRenew)
								.autoRenewAccountId(civilian)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						updateTopic(topicSansDetachedAsAutoRenew)
								.autoRenewAccountId(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						getTopicInfo(topicSansDetachedAsAutoRenew)
								.hasAutoRenewAccount(civilian),
						getTopicInfo(topicWithDetachedAsAutoRenew)
								.hasAutoRenewAccount(detachedAccount)
				);
	}

	private HapiApiSpec tokenAutoRenewOpsEnforced() {
		final var tokenWithDetachedAsAutoRenew = "c";
		final var tokenSansDetachedAsAutoRenew = "d";
		final var detachedAccount = "gone";
		final var adminKey = "tak";
		final var civilian = "misc";
		final var notToBe = "nope";

		return defaultHapiSpec("TokenAutoRenewOpsEnforced")
				.given(
						newKeyNamed(adminKey),
						cryptoCreate(civilian)
				).when(
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(2),
						tokenCreate(tokenWithDetachedAsAutoRenew)
								.adminKey(adminKey)
								.autoRenewAccount(detachedAccount),
						tokenCreate(tokenSansDetachedAsAutoRenew)
								.autoRenewAccount(civilian)
								.adminKey(adminKey),
						sleepFor(1_500L)
				).then(
						tokenCreate(notToBe)
								.autoRenewAccount(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						tokenUpdate(tokenWithDetachedAsAutoRenew)
								.autoRenewAccount(civilian)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						tokenUpdate(tokenSansDetachedAsAutoRenew)
								.autoRenewAccount(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						getTokenInfo(tokenSansDetachedAsAutoRenew)
								.hasAutoRenewAccount(civilian),
						getTokenInfo(tokenWithDetachedAsAutoRenew)
								.hasAutoRenewAccount(detachedAccount)
				);
	}

	private HapiApiSpec treasuryOpsRestrictionEnforced() {
		final var aToken = "c";
		final var detachedAccount = "gone";
		final var tokenMultiKey = "tak";
		final var civilian = "misc";
		final long expectedSupply = 1_234L;

		return defaultHapiSpec("MustReattachTreasuryBeforeUpdating")
				.given(
						newKeyNamed(tokenMultiKey),
						cryptoCreate(civilian)
				).when(
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(2),
						tokenCreate(aToken)
								.adminKey(tokenMultiKey)
								.supplyKey(tokenMultiKey)
								.initialSupply(expectedSupply)
								.treasury(detachedAccount),
						tokenAssociate(civilian, aToken),
						sleepFor(1_500L)
				).then(
						tokenUpdate(aToken)
								.treasury(civilian)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						mintToken(aToken, 1L)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						burnToken(aToken, 1L)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						getTokenInfo(aToken)
								.hasTreasury(detachedAccount),
						getAccountBalance(detachedAccount)
								.hasTokenBalance(aToken, expectedSupply)
				);
	}

	private HapiApiSpec tokenMgmtRestrictionsEnforced() {
		final var notToBe = "a";
		final var tokenNotYetAssociated = "b";
		final var tokenAlreadyAssociated = "c";
		final var detachedAccount = "gone";
		final var tokenMultiKey = "tak";
		final var civilian = "misc";

		return defaultHapiSpec("TokenMgmtRestrictionsEnforced")
				.given(
						newKeyNamed(tokenMultiKey),
						cryptoCreate(civilian),
						tokenCreate(tokenNotYetAssociated)
								.adminKey(tokenMultiKey),
						tokenCreate(tokenAlreadyAssociated)
								.freezeKey(tokenMultiKey)
								.kycKey(tokenMultiKey)
				).when(
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(2),
						tokenAssociate(detachedAccount, tokenAlreadyAssociated),
						sleepFor(1_500L)
				).then(
						tokenCreate(notToBe)
								.treasury(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						tokenUnfreeze(tokenAlreadyAssociated, detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						tokenFreeze(tokenAlreadyAssociated, detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						grantTokenKyc(tokenAlreadyAssociated, detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						revokeTokenKyc(tokenAlreadyAssociated, detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						tokenAssociate(detachedAccount, tokenNotYetAssociated)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						tokenUpdate(tokenNotYetAssociated)
								.treasury(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						tokenDissociate(detachedAccount, tokenAlreadyAssociated)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL)
				);
	}

	private HapiApiSpec cryptoDeleteRestrictionsEnforced() {
		final var detachedAccount = "gone";
		final var civilian = "misc";

		return defaultHapiSpec("CryptoDeleteRestrictionsEnforced")
				.given(
						cryptoCreate(civilian),
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(2)
				).when(
						sleepFor(1_500L)
				).then(
						cryptoDelete(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						cryptoDelete(civilian)
								.transfer(detachedAccount)
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL)
				);
	}

	private HapiApiSpec cryptoTransferRestrictionsEnforced() {
		final var aToken = "c";
		final var detachedAccount = "gone";
		final var civilian = "misc";

		return defaultHapiSpec("CryptoTransferRestrictionsEnforced")
				.given(
						cryptoCreate(civilian),
						cryptoCreate(detachedAccount)
								.balance(0L)
								.autoRenewSecs(2),
						tokenCreate(aToken)
								.treasury(detachedAccount),
						tokenAssociate(civilian, aToken)
				).when(
						sleepFor(1_500L)
				).then(
						cryptoTransfer(tinyBarsFromTo(GENESIS, detachedAccount, ONE_MILLION_HBARS))
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
						cryptoTransfer(moving(1, aToken).between(detachedAccount, civilian))
								.hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL)
				);
	}

	private HapiApiSpec noGracePeriodRestrictionsIfNoAutoRenewSuiteSetup() {
		return defaultHapiSpec("NoGracePeriodRestrictionsIfNoAutoRenewSuiteSetup")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(leavingAutoRenewDisabledWith(1))
				);
	}

	private HapiApiSpec noGracePeriodRestrictionsIfNoAutoRenewSuiteCleanup() {
		return defaultHapiSpec("NoGracePeriodRestrictionsIfNoAutoRenewSuiteCleanup")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(disablingAutoRenewWithDefaults())
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
