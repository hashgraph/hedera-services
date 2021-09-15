package com.hedera.services.bdd.suites.token;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class TokenBaseOpFeesSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenBaseOpFeesSuite.class);

	public static void main(String... args) {
		new TokenBaseOpFeesSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
			List.of(new HapiApiSpec[] {
					baseTokenFreezeChargedAsExpected(),
					//baseUniqueMintOperationIsChargedExpectedFee(),
					}
			)
		);
	}


	private HapiApiSpec baseUniqueMintOperationIsChargedExpectedFee() {
		final var uniqueToken = "nftType";
		final var supplyKey = "mint!";
		final var civilianPayer = "civilian";
		final var standard100ByteMetadata = ByteString.copyFromUtf8(
				"0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789");
		final var baseTxn = "baseTxn";
		final var expectedNftMintPriceUsd = 0.001;

		return defaultHapiSpec("BaseUniqueMintOperationIsChargedExpectedFee")
				.given(
						newKeyNamed(supplyKey),
						cryptoCreate(civilianPayer).key(supplyKey),
						tokenCreate(uniqueToken)
								.initialSupply(0L)
								.expiry(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS)
								.supplyKey(supplyKey)
								.tokenType(NON_FUNGIBLE_UNIQUE)
				)
				.when(
						mintToken(uniqueToken, List.of(standard100ByteMetadata))
								.payingWith(civilianPayer)
								.signedBy(supplyKey)
								.blankMemo()
								.via(baseTxn)
				).then(
						validateChargedUsdWithin(baseTxn, expectedNftMintPriceUsd, 0.01)
				);
	}


	private HapiApiSpec baseTokenFreezeChargedAsExpected() {
		final var expectedFreezePriceUsd = 0.001;
		final var baseTxn = "baseTxn";

		return defaultHapiSpec("baseTokenFreezeChargedAsExpected")
				.given(
						newKeyNamed("multiKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						cryptoCreate("payer"),
						cryptoCreate("civilian"),
						tokenCreate("tbd")
								.adminKey("multiKey")
								.freezeKey("multiKey")
								.kycKey("multiKey")
								.wipeKey("multiKey")
								.supplyKey("multiKey")
								.freezeDefault(false)
								.treasury(TOKEN_TREASURY)
								.payingWith("payer"),
						tokenAssociate("civilian", "tbd")
				).when(
						tokenFreeze("tbd", "civilian")
								.via(baseTxn)
				).then(
						validateChargedUsdWithin(baseTxn, expectedFreezePriceUsd, 0.01)
				);
	}
	private HapiApiSpec baseTokenUnFreezeChargedAsExpected() {
		final var expectedNftMintPriceUsd = 0.001;
		final var baseTxn = "baseTxn";

		return defaultHapiSpec("DeletionWorksAsExpected")
				.given(
						newKeyNamed("multiKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						cryptoCreate("payer"),
						tokenCreate("tbd")
								.adminKey("multiKey")
								.freezeKey("multiKey")
//								.kycKey("multiKey")
//								.wipeKey("multiKey")
//								.supplyKey("multiKey")
								.freezeDefault(false)
								.treasury(TOKEN_TREASURY)
								.payingWith("payer"),
						tokenAssociate(GENESIS, "tbd")
				).when(
						tokenFreeze("tbd", GENESIS),
						tokenUnfreeze("tbd", GENESIS),
						cryptoTransfer(moving(1, "tbd")
								.between(TOKEN_TREASURY, GENESIS)),
						tokenDelete("tbd").payingWith("payer")
				).then(
						validateChargedUsdWithin(baseTxn, expectedNftMintPriceUsd, 0.01)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
