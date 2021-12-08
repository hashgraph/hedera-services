package com.hedera.services.bdd.suites.crypto;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;

public class AutoAccountCreationSuite extends HapiApiSuite {
	private static final long initialBalance = 1000L;
	private static final ByteString aliasContent = ByteString.copyFromUtf8(
			"a479462fba67674b5a41acfb16cb6828626b61d3f389fa611005a45754130e5c749073c0b1b791596430f4a54649cc8a3f6d28147dd4099070a5c3c4811d1771");
	private static final Key validEd25519Key = Key.newBuilder().setEd25519(aliasContent).build();
	private static final ByteString valid25519Alias = validEd25519Key.toByteString();

	private static final Logger log = LogManager.getLogger(AutoAccountCreationSuite.class);

	public static void main(String... args) {
		new AutoAccountCreationSuite().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						autoAccountCreationsHappyPath(),
//						autoAccountCreationBadAlias(),
//						autoAccountCreationUnsupportedAlias(),
						transferToAccountAutoCreatedUsingAlias(),
//						transferToAccountAutoCreatedUsingAccount()
				}
		);
	}

//	private HapiApiSpec transferToAccountAutoCreatedUsingAccount() {
//	}

	private HapiApiSpec transferToAccountAutoCreatedUsingAlias() {
		final var alias = AutoCreateUtils.randomValidECDSAAlias();
		return defaultHapiSpec("transferToAccountAutoCreated")
				.given(
						UtilVerbs.inParallel(
								cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
						)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("payer", alias, ONE_HUNDRED_HBARS)
						).via("transferTxn"),

						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAccountInfo("payer").has(
								accountWith().balance((initialBalance * ONE_HBAR) - ONE_HUNDRED_HBARS)),
						getAccountInfo(alias).has(
								accountWith().expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.1))
				).then(
						cryptoTransfer(
								tinyBarsFromTo("payer", alias, ONE_HUNDRED_HBARS)
						).via("transferTxn2").hasPrecheck(ResponseCodeEnum.INVALID_ACCOUNT_ID) // will be modified as below
//						cryptoTransfer(
//								tinyBarsFromTo("payer", alias, ONE_HUNDRED_HBARS)
//						).via("transferTxn2"),
//						getTxnRecord("transferTxn2").andAllChildRecords().logged(),
//						getAccountInfo("payer").has(
//								accountWith().balance((initialBalance * ONE_HBAR) - (2 * ONE_HUNDRED_HBARS))),
//						getAccountInfo(alias).has(
//								accountWith().expectedBalanceWithChargedUsd((2 * ONE_HUNDRED_HBARS), 0.05, 0.1))
				);
	}

	private HapiApiSpec autoAccountCreationUnsupportedAlias() {
		final var threshKeyAlias = Key.newBuilder()
				.setThresholdKey(ThresholdKey.newBuilder()
						.setThreshold(2)
						.setKeys(KeyList.newBuilder()
								.addKeys(Key.newBuilder()
										.setEd25519(ByteString.copyFrom("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes())))
								.addKeys(Key.newBuilder()
										.setECDSASecp256K1(
												ByteString.copyFrom("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes())))
								.addKeys(Key.newBuilder()
										.setEd25519(
												ByteString.copyFrom("cccccccccccccccccccccccccccccccc".getBytes())))))
				.build().toByteString();
		final var keyListAlias = Key.newBuilder()
				.setKeyList(KeyList.newBuilder()
						.addKeys(Key.newBuilder()
								.setEd25519(ByteString.copyFrom("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes())))
						.addKeys(Key.newBuilder()
								.setECDSASecp256K1(
										ByteString.copyFrom("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes()))))
				.build().toByteString();
		final var contractKeyAlias = Key.newBuilder().setContractID(
				ContractID.newBuilder().setContractNum(100L)).build().toByteString();
		final var delegateContractKeyAlias = Key.newBuilder().setDelegatableContractId(
				ContractID.newBuilder().setContractNum(100L)).build().toByteString();

		return defaultHapiSpec("autoAccountCreationUnsupportedAlias")
				.given(
						UtilVerbs.inParallel(
								cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
						)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("payer", threshKeyAlias, ONE_HUNDRED_HBARS)
						).hasPrecheck(ResponseCodeEnum.INVALID_ALIAS_KEY)
								.via("transferTxnThreshKey"),
						cryptoTransfer(
								tinyBarsFromTo("payer", keyListAlias, ONE_HUNDRED_HBARS)
						).hasPrecheck(ResponseCodeEnum.INVALID_ALIAS_KEY)
								.via("transferTxnKeyList"),
						cryptoTransfer(
								tinyBarsFromTo("payer", contractKeyAlias, ONE_HUNDRED_HBARS)
						).hasPrecheck(ResponseCodeEnum.INVALID_ALIAS_KEY)
								.via("transferTxnContract"),
						cryptoTransfer(
								tinyBarsFromTo("payer", delegateContractKeyAlias, ONE_HUNDRED_HBARS)
						).hasPrecheck(ResponseCodeEnum.INVALID_ALIAS_KEY)
								.via("transferTxnKeyDelegate")
				).then();
	}


	private HapiApiSpec autoAccountCreationBadAlias() {
		final var invalidAlias = valid25519Alias.substring(0, 10);

		return defaultHapiSpec("autoAccountCreationBadAlias")
				.given(
						UtilVerbs.inParallel(
								cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
						)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("payer", invalidAlias, ONE_HUNDRED_HBARS)
						).hasPrecheck(ResponseCodeEnum.INVALID_ALIAS_KEY)
								.via("transferTxnBad")
				).then();
	}

	private HapiApiSpec autoAccountCreationsHappyPath() {
		return defaultHapiSpec("autoAccountCreationsHappyPath")
				.given(
						UtilVerbs.inParallel(
								cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
						)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("payer", valid25519Alias, ONE_HUNDRED_HBARS)
						).via("transferTxn")
				).then(
						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAccountInfo("payer").has(
								accountWith().balance((initialBalance * ONE_HBAR) - ONE_HUNDRED_HBARS)),
						getAccountInfo(valid25519Alias).has(
								accountWith().expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.1))
				);
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

}
