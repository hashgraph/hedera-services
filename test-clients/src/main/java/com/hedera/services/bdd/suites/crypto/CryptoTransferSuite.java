package com.hedera.services.bdd.suites.crypto;

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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts;
import com.hedera.services.bdd.spec.assertions.BaseErroringAssertsProvider;
import com.hedera.services.bdd.spec.assertions.ErroringAsserts;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.fee.FeeObject;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.accountIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asAccountString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asTopicString;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.AutoAssocAsserts.accountTokenPairsInAnyOrder;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingFungibleMovement;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingNonfungibleMovement;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.including;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenPause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnpause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uncheckedSubmit;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.allowanceTinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeNoFallback;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbarWithAllowance;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUniqueWithAllowance;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithAllowance;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithDecimals;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.aaWith;
import static com.hedera.services.bdd.suites.contract.Utils.accountId;
import static com.hedera.services.bdd.suites.contract.Utils.ocWith;
import static com.hedera.services.bdd.suites.contract.precompile.DynamicGasCostSuite.captureOneChildCreate2MetaFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CryptoTransferSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoTransferSuite.class);
	private final String owner = "owner";
	private final String otherOwner = "otherOwner";
	private final String spender = "spender";
	private final String receiver = "receiver";
	private final String otherReceiver = "otherReceiver";
	private final String anotherReceiver = "anotherReceiver";
	private final String fungibleToken = "fungible";
	private final String nonFungibleToken = "nonFungible";
	private final String tokenWithCustomFee = "tokenWithCustomFee";
	private final String adminKey = "adminKey";
	private final String kycKey = "kycKey";
	private final String freezeKey = "freezeKey";
	private final String supplyKey = "supplyKey";
	private final String wipeKey = "wipeKey";
	private final String pauseKey = "pauseKey";

	public static void main(String... args) {
		new CryptoTransferSuite().runSuiteAsync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						transferWithMissingAccountGetsInvalidAccountId(),
//						vanillaTransferSucceeds(),
//						complexKeyAcctPaysForOwnTransfer(),
//						twoComplexKeysRequired(),
//						specialAccountsBalanceCheck(),
//						tokenTransferFeesScaleAsExpected(),
//						okToSetInvalidPaymentHeaderForCostAnswer(),
//						baseCryptoTransferFeeChargedAsExpected(),
//						autoAssociationRequiresOpenSlots(),
//						royaltyCollectorsCanUseAutoAssociation(),
//						royaltyCollectorsCannotUseAutoAssociationWithoutOpenSlots(),
//						dissociatedRoyaltyCollectorsCanUseAutoAssociation(),
//						hbarAndFungibleSelfTransfersRejectedBothInPrecheckAndHandle(),
//						transferToNonAccountEntitiesReturnsInvalidAccountId(),
//						nftSelfTransfersRejectedBothInPrecheckAndHandle(),
//						checksExpectedDecimalsForFungibleTokenTransferList(),
//						allowanceTransfersWorkAsExpected(),
//						allowanceTransfersWithComplexTransfersWork(),
//						canUseMirrorAliasesForNonContractXfers(),
//						canUseEip1014AliasesForXfers(),
//						cannotTransferFromImmutableAccounts(),
//						nftTransfersHaveTransitiveClosure(),
				}
		);
	}

	@Override
	public boolean canRunConcurrent() {
		return true;
	}

	// https://github.com/hashgraph/hedera-services/issues/2875
	private HapiApiSpec canUseMirrorAliasesForNonContractXfers() {
		final var party = "party";
		final var counterparty = "counterparty";
		final var fungibleToken = "fungibleToken";
		final var nonFungibleToken = "nonFungibleToken";
		final var supplyKey = "multi";
		final AtomicReference<TokenID> ftId = new AtomicReference<>();
		final AtomicReference<TokenID> nftId = new AtomicReference<>();
		final AtomicReference<AccountID> partyId = new AtomicReference<>();
		final AtomicReference<AccountID> counterId = new AtomicReference<>();
		final AtomicReference<ByteString> partyAlias = new AtomicReference<>();
		final AtomicReference<ByteString> counterAlias = new AtomicReference<>();
		final var hbarXfer = "hbarXfer";
		final var nftXfer = "nftXfer";
		final var ftXfer = "ftXfer";

		return defaultHapiSpec("CanUseMirrorAliasesForNonContractXfers")
				.given(
						newKeyNamed(supplyKey),
						cryptoCreate(party).maxAutomaticTokenAssociations(2),
						cryptoCreate(counterparty).maxAutomaticTokenAssociations(2),
						tokenCreate(fungibleToken)
								.treasury(party)
								.initialSupply(1_000_000),
						tokenCreate(nonFungibleToken)
								.initialSupply(0)
								.treasury(party)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey(supplyKey),
						mintToken(nonFungibleToken, List.of(
								copyFromUtf8("Please mind the vase.")
						)),
						withOpContext((spec, opLog) -> {
							final var registry = spec.registry();
							ftId.set(registry.getTokenID(fungibleToken));
							nftId.set(registry.getTokenID(nonFungibleToken));
							partyId.set(registry.getAccountID(party));
							counterId.set(registry.getAccountID(counterparty));
							partyAlias.set(ByteString.copyFrom(asSolidityAddress(partyId.get())));
							counterAlias.set(ByteString.copyFrom(asSolidityAddress(counterId.get())));
						})
				).when(
						cryptoTransfer((spec, b) -> b.setTransfers(TransferList.newBuilder()
								.addAccountAmounts(aaWith(partyAlias.get(), -1))
								.addAccountAmounts(aaWith(partyId.get(), -1))
								.addAccountAmounts(aaWith(counterId.get(), +2)))
						)
								.signedBy(DEFAULT_PAYER, party)
								.hasKnownStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
						// Check signing requirements aren't distorted by aliases
						cryptoTransfer((spec, b) -> {
							b.setTransfers(TransferList.newBuilder()
									.addAccountAmounts(aaWith(partyAlias.get(), -2))
									.addAccountAmounts(aaWith(counterId.get(), +2)));
						}).signedBy(DEFAULT_PAYER).hasKnownStatus(INVALID_SIGNATURE),
						cryptoTransfer((spec, b) ->
								b.addTokenTransfers(TokenTransferList.newBuilder()
										.setToken(nftId.get())
										.addNftTransfers(ocWith(accountId(partyAlias.get()), counterId.get(), 1L)))
						).signedBy(DEFAULT_PAYER).hasKnownStatus(INVALID_SIGNATURE),
						cryptoTransfer((spec, b) ->
								b.addTokenTransfers(TokenTransferList.newBuilder()
										.setToken(ftId.get())
										.addTransfers(aaWith(partyAlias.get(), -500))
										.addTransfers(aaWith(counterAlias.get(), +500)))
						).signedBy(DEFAULT_PAYER).hasKnownStatus(INVALID_SIGNATURE),
						// Now do the actual transfers
						cryptoTransfer((spec, b) ->
								b.setTransfers(TransferList.newBuilder()
										.addAccountAmounts(aaWith(partyAlias.get(), -2))
										.addAccountAmounts(aaWith(counterAlias.get(), +2)))
						).signedBy(DEFAULT_PAYER, party).via(hbarXfer),
						cryptoTransfer((spec, b) ->
								b.addTokenTransfers(TokenTransferList.newBuilder()
										.setToken(nftId.get())
										.addNftTransfers(ocWith(
												accountId(partyAlias.get()),
												accountId(counterAlias.get()),
												1L)))
						).signedBy(DEFAULT_PAYER, party).via(nftXfer),
						cryptoTransfer((spec, b) ->
								b.addTokenTransfers(TokenTransferList.newBuilder()
										.setToken(ftId.get())
										.addTransfers(aaWith(partyAlias.get(), -500))
										.addTransfers(aaWith(counterAlias.get(), +500)))
						).signedBy(DEFAULT_PAYER, party).via(ftXfer)
				).then(
						getTxnRecord(hbarXfer).logged(),
						getTxnRecord(nftXfer).logged(),
						getTxnRecord(ftXfer).logged()
				);
	}

	private HapiApiSpec canUseEip1014AliasesForXfers() {
		final var party = "party";
		final var counterparty = "counterparty";
		final var partyCreation2 = "partyCreation2";
		final var counterCreation2 = "counterCreation2";
		final var multiKey = "multi";
		final var contract = "CreateDonor";

		final AtomicReference<String> partyAliasAddr = new AtomicReference<>();
		final AtomicReference<String> partyMirrorAddr = new AtomicReference<>();
		final AtomicReference<String> counterAliasAddr = new AtomicReference<>();
		final AtomicReference<String> counterMirrorAddr = new AtomicReference<>();
		final AtomicReference<TokenID> ftId = new AtomicReference<>();
		final AtomicReference<TokenID> nftId = new AtomicReference<>();
		final AtomicReference<String> partyLiteral = new AtomicReference<>();
		final AtomicReference<AccountID> partyId = new AtomicReference<>();
		final AtomicReference<String> counterLiteral = new AtomicReference<>();
		final AtomicReference<AccountID> counterId = new AtomicReference<>();
		final var hbarXfer = "hbarXfer";
		final var nftXfer = "nftXfer";
		final var ftXfer = "ftXfer";

		final byte[] salt = unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");
		final byte[] otherSalt = unhex("aabbccddee880011aabbccddee880011aabbccddee880011aabbccddee880011");

		return defaultHapiSpec("CanUseEip1014AliasesForXfers")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(TOKEN_TREASURY),
						uploadInitCode(contract),
						contractCreate(contract)
								.adminKey(multiKey)
								.payingWith(GENESIS),
						contractCall(contract, "buildDonor", salt)
								.sending(1000)
								.payingWith(GENESIS)
								.gas(2_000_000L)
								.via(partyCreation2),
						captureOneChildCreate2MetaFor(
								party, partyCreation2, partyMirrorAddr, partyAliasAddr),
						contractCall(contract, "buildDonor", otherSalt)
								.sending(1000)
								.payingWith(GENESIS)
								.gas(2_000_000L)
								.via(counterCreation2),
						captureOneChildCreate2MetaFor(
								counterparty, counterCreation2, counterMirrorAddr, counterAliasAddr),
						tokenCreate(fungibleToken)
								.treasury(TOKEN_TREASURY)
								.initialSupply(1_000_000),
						tokenCreate(nonFungibleToken)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey(multiKey),
						mintToken(nonFungibleToken, List.of(
								copyFromUtf8("Please mind the vase.")
						)),
						withOpContext((spec, opLog) -> {
							final var registry = spec.registry();
							ftId.set(registry.getTokenID(fungibleToken));
							nftId.set(registry.getTokenID(nonFungibleToken));
							partyId.set(accountIdFromHexedMirrorAddress(partyMirrorAddr.get()));
							partyLiteral.set(asAccountString(partyId.get()));
							counterId.set(accountIdFromHexedMirrorAddress(counterMirrorAddr.get()));
							counterLiteral.set(asAccountString(counterId.get()));
						})
				).when(
						sourcing(() -> tokenAssociate(partyLiteral.get(),
								List.of(fungibleToken, nonFungibleToken))
								.signedBy(DEFAULT_PAYER, multiKey)),
						sourcing(() -> tokenAssociate(counterLiteral.get(),
								List.of(fungibleToken, nonFungibleToken))
								.signedBy(DEFAULT_PAYER, multiKey)),
						sourcing(() -> getContractInfo(partyLiteral.get()).logged()),
						sourcing(() -> cryptoTransfer(
								moving(500_000, fungibleToken)
										.between(TOKEN_TREASURY, partyLiteral.get()),
								movingUnique(nonFungibleToken, 1L)
										.between(TOKEN_TREASURY, partyLiteral.get())
						).signedBy(DEFAULT_PAYER, TOKEN_TREASURY)),
						cryptoTransfer((spec, b) -> b.setTransfers(TransferList.newBuilder()
								.addAccountAmounts(aaWith(partyAliasAddr.get(), -1))
								.addAccountAmounts(aaWith(partyId.get(), -1))
								.addAccountAmounts(aaWith(counterId.get(), +2)))
						)
								.signedBy(DEFAULT_PAYER, multiKey)
								.hasKnownStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
						// Check signing requirements aren't distorted by aliases
						cryptoTransfer((spec, b) -> {
							b.setTransfers(TransferList.newBuilder()
									.addAccountAmounts(aaWith(partyAliasAddr.get(), -2))
									.addAccountAmounts(aaWith(counterAliasAddr.get(), +2)));
						}).signedBy(DEFAULT_PAYER).hasKnownStatus(INVALID_SIGNATURE),
						cryptoTransfer((spec, b) ->
								b.addTokenTransfers(TokenTransferList.newBuilder()
										.setToken(nftId.get())
										.addNftTransfers(ocWith(accountId(partyAliasAddr.get()), counterId.get(), 1L)))
						).signedBy(DEFAULT_PAYER).hasKnownStatus(INVALID_SIGNATURE),
						cryptoTransfer((spec, b) ->
								b.addTokenTransfers(TokenTransferList.newBuilder()
										.setToken(ftId.get())
										.addTransfers(aaWith(partyAliasAddr.get(), -500))
										.addTransfers(aaWith(counterAliasAddr.get(), +500)))
						).signedBy(DEFAULT_PAYER).hasKnownStatus(INVALID_SIGNATURE),
						// Now do the actual transfers
						cryptoTransfer((spec, b) ->
								b.setTransfers(TransferList.newBuilder()
										.addAccountAmounts(aaWith(partyAliasAddr.get(), -2))
										.addAccountAmounts(aaWith(counterAliasAddr.get(), +2)))
						).signedBy(DEFAULT_PAYER, multiKey).via(hbarXfer),
						cryptoTransfer((spec, b) ->
								b.addTokenTransfers(TokenTransferList.newBuilder()
										.setToken(nftId.get())
										.addNftTransfers(ocWith(
												accountId(partyAliasAddr.get()),
												accountId(counterAliasAddr.get()),
												1L)))
						).signedBy(DEFAULT_PAYER, multiKey).via(nftXfer),
						cryptoTransfer((spec, b) ->
								b.addTokenTransfers(TokenTransferList.newBuilder()
										.setToken(ftId.get())
										.addTransfers(aaWith(partyAliasAddr.get(), -500))
										.addTransfers(aaWith(counterAliasAddr.get(), +500)))
						).signedBy(DEFAULT_PAYER, multiKey).via(ftXfer)
				).then(
						sourcing(() -> getTxnRecord(hbarXfer).hasPriority(
								recordWith().transfers(
										including(tinyBarsFromTo(
												partyLiteral.get(), counterLiteral.get(), 2))))),
						sourcing(() -> getTxnRecord(nftXfer).hasPriority(
								recordWith().tokenTransfers(
										includingNonfungibleMovement(movingUnique(nonFungibleToken, 1L)
												.between(partyLiteral.get(), counterLiteral.get()))))),
						sourcing(() -> getTxnRecord(ftXfer).hasPriority(
								recordWith().tokenTransfers(
										includingFungibleMovement(moving(500, fungibleToken)
												.between(partyLiteral.get(), counterLiteral.get())))))
				);
	}

	private HapiApiSpec cannotTransferFromImmutableAccounts() {
		final var contract = "PayableConstructor";
		final var firstStakingFund = "0.0.800";
		final var secondStakingFund = "0.0.801";
		final var snapshot800 = "800startBalance";
		final var snapshot801 = "801startBalance";
		final var multiKey = "swiss";
		final var mutableToken = "token";
		final AtomicReference<FeeObject> feeObs = new AtomicReference<>();

		return defaultHapiSpec("CannotTransferFromImmutableAccounts")
				.given(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"staking.fees.stakingRewardPercentage", "10",
										"staking.fees.nodeRewardPercentage", "10"
								)),
						newKeyNamed(multiKey),
						uploadInitCode(contract),
						contractCreate(contract)
								.balance(ONE_HBAR)
								.immutable()
								.payingWith(GENESIS)
				).when(
						balanceSnapshot(snapshot800, firstStakingFund),
						cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, firstStakingFund, ONE_HBAR))
								.signedBy(DEFAULT_PAYER)
								.exposingFeesTo(feeObs)
								.logged(),
						sourcing(() ->
								getAccountBalance(firstStakingFund)
										.hasTinyBars(
												changeFromSnapshot(snapshot800,
														(long) (ONE_HBAR + ((feeObs.get().getNetworkFee() + feeObs.get().getServiceFee()) * 0.1))))),

						balanceSnapshot(snapshot801, secondStakingFund),
						cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, secondStakingFund, ONE_HBAR))
								.signedBy(DEFAULT_PAYER)
								.logged(),
						sourcing(() ->
								getAccountBalance(secondStakingFund)
										.hasTinyBars(
												changeFromSnapshot(snapshot801,
														(long) (ONE_HBAR + ((feeObs.get().getNetworkFee() + feeObs.get().getServiceFee()) * 0.1)))))
				).then(
						// Even the treasury cannot withdraw from an immutable contract
						cryptoTransfer(tinyBarsFromTo(contract, FUNDING, ONE_HBAR))
								.payingWith(GENESIS).signedBy(GENESIS).fee(ONE_HBAR)
								.hasKnownStatus(INVALID_SIGNATURE),
						// Even the treasury cannot withdraw staking funds
						cryptoTransfer(tinyBarsFromTo(firstStakingFund, FUNDING, ONE_HBAR))
								.payingWith(GENESIS).signedBy(GENESIS).fee(ONE_HBAR)
								.hasKnownStatus(INVALID_ACCOUNT_ID),
						cryptoTransfer(tinyBarsFromTo(secondStakingFund, FUNDING, ONE_HBAR))
								.payingWith(GENESIS).signedBy(GENESIS).fee(ONE_HBAR)
								.hasKnownStatus(INVALID_ACCOUNT_ID),
						// Immutable accounts cannot be updated or deleted
						cryptoUpdate(firstStakingFund)
								.payingWith(GENESIS).signedBy(GENESIS).fee(ONE_HBAR)
								.hasKnownStatus(INVALID_ACCOUNT_ID),
						cryptoDelete(firstStakingFund)
								.payingWith(GENESIS).signedBy(GENESIS).fee(ONE_HBAR).hasKnownStatus(INVALID_ACCOUNT_ID),
						// Immutable accounts cannot serve any role for tokens
						tokenCreate(mutableToken).adminKey(multiKey),
						tokenAssociate(secondStakingFund, mutableToken)
								.payingWith(GENESIS).signedBy(GENESIS).fee(ONE_HBAR)
								.hasKnownStatus(INVALID_ACCOUNT_ID),
						tokenUpdate(mutableToken)
								.payingWith(GENESIS).signedBy(GENESIS, multiKey).fee(ONE_HBAR)
								.treasury(firstStakingFund)
								.hasKnownStatus(INVALID_ACCOUNT_ID),
						tokenCreate("notToBe")
								.treasury(firstStakingFund)
								.payingWith(GENESIS).signedBy(GENESIS).fee(ONE_HBAR)
								.hasKnownStatus(INVALID_ACCOUNT_ID),
						tokenCreate("notToBe")
								.autoRenewAccount(secondStakingFund)
								.payingWith(GENESIS).signedBy(GENESIS).fee(ONE_HBAR)
								.hasKnownStatus(INVALID_AUTORENEW_ACCOUNT),
						tokenCreate("notToBe")
								.payingWith(GENESIS).signedBy(GENESIS).fee(ONE_HBAR)
								.withCustom(fixedHbarFee(5 * ONE_HBAR, firstStakingFund))
								.hasKnownStatus(INVALID_CUSTOM_FEE_COLLECTOR),
						// Immutable accounts cannot be topic auto-renew accounts
						createTopic("notToBe")
								.autoRenewAccountId(secondStakingFund)
								.payingWith(GENESIS).signedBy(GENESIS).fee(ONE_HBAR)
								.hasKnownStatus(INVALID_AUTORENEW_ACCOUNT),
						// Immutable accounts cannot be schedule transaction payers
						scheduleCreate("notToBe",
								cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1))
						)
								.payingWith(GENESIS).signedBy(GENESIS).fee(ONE_HBAR)
								.designatingPayer(firstStakingFund)
								.fee(ONE_HUNDRED_HBARS)
								.hasKnownStatus(INVALID_ACCOUNT_ID),
						// Immutable accounts cannot approve or adjust allowances
						cryptoApproveAllowance()
								.payingWith(GENESIS).signedBy(GENESIS).fee(ONE_HBAR)
								.addCryptoAllowance(secondStakingFund, FUNDING, 100L)
								.hasKnownStatus(INVALID_ALLOWANCE_OWNER_ID)
				);
	}

	private HapiApiSpec allowanceTransfersWithComplexTransfersWork() {
		return defaultHapiSpec("AllowanceTransfersWithComplexTransfersWork")
				.given(
						newKeyNamed(adminKey),
						newKeyNamed(freezeKey),
						newKeyNamed(kycKey),
						newKeyNamed(supplyKey),
						cryptoCreate(owner).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(otherOwner).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(receiver).balance(0L),
						cryptoCreate(otherReceiver).balance(ONE_HBAR),
						cryptoCreate(anotherReceiver).balance(0L),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.maxSupply(10000)
								.initialSupply(5000)
								.adminKey(adminKey)
								.kycKey(kycKey),
						tokenCreate(nonFungibleToken)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.maxSupply(12L)
								.supplyKey(supplyKey)
								.adminKey(adminKey)
								.kycKey(kycKey)
								.initialSupply(0L),
						mintToken(nonFungibleToken, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c"),
								ByteString.copyFromUtf8("d"),
								ByteString.copyFromUtf8("e")))
				)
				.when(
						tokenAssociate(owner, fungibleToken, nonFungibleToken),
						tokenAssociate(otherOwner, fungibleToken, nonFungibleToken),
						tokenAssociate(receiver, fungibleToken, nonFungibleToken),
						tokenAssociate(spender, fungibleToken),
						tokenAssociate(anotherReceiver, fungibleToken),
						grantTokenKyc(fungibleToken, owner),
						grantTokenKyc(fungibleToken, otherOwner),
						grantTokenKyc(fungibleToken, receiver),
						grantTokenKyc(fungibleToken, anotherReceiver),
						grantTokenKyc(fungibleToken, spender),
						grantTokenKyc(nonFungibleToken, owner),
						grantTokenKyc(nonFungibleToken, otherOwner),
						grantTokenKyc(nonFungibleToken, receiver),
						cryptoTransfer(
								moving(100, fungibleToken).between(TOKEN_TREASURY, spender),
								moving(1000, fungibleToken).between(TOKEN_TREASURY, owner),
								movingUnique(nonFungibleToken, 1, 2).between(TOKEN_TREASURY, owner),
								moving(1000, fungibleToken).between(TOKEN_TREASURY, otherOwner),
								movingUnique(nonFungibleToken, 3, 4).between(TOKEN_TREASURY, otherOwner)),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender, 10 * ONE_HBAR)
								.addTokenAllowance(owner, fungibleToken, spender, 500)
								.addNftAllowance(owner, nonFungibleToken, spender, false, List.of(1L, 2L))
								.fee(ONE_HUNDRED_HBARS),
						cryptoApproveAllowance()
								.payingWith(otherOwner)
								.addCryptoAllowance(otherOwner, spender, 5 * ONE_HBAR)
								.addTokenAllowance(otherOwner, fungibleToken, spender, 100)
								.addNftAllowance(otherOwner, nonFungibleToken, spender, true, List.of(3L))
								.fee(ONE_HUNDRED_HBARS)
				)
				.then(
						cryptoTransfer(
								movingHbar(ONE_HBAR).between(spender, receiver),
								movingHbar(ONE_HBAR).between(otherReceiver, anotherReceiver),
								movingHbar(ONE_HBAR).between(owner, receiver),
								movingHbar(ONE_HBAR).between(otherOwner, receiver),
								movingHbarWithAllowance(ONE_HBAR).between(owner, receiver),
								movingHbarWithAllowance(ONE_HBAR).between(otherOwner, receiver),
								moving(50, fungibleToken).between(receiver, anotherReceiver),
								moving(50, fungibleToken).between(spender, receiver),
								moving(50, fungibleToken).between(owner, receiver),
								moving(15, fungibleToken).between(otherOwner, receiver),
								movingWithAllowance(30, fungibleToken).between(owner, receiver),
								movingWithAllowance(10, fungibleToken).between(otherOwner, receiver),
								movingWithAllowance(5, fungibleToken).between(otherOwner, owner),
								movingUnique(nonFungibleToken, 1L).between(owner, receiver),
								movingUniqueWithAllowance(nonFungibleToken, 2L).between(owner, receiver),
								movingUniqueWithAllowance(nonFungibleToken, 4L).between(otherOwner, receiver),
								movingUniqueWithAllowance(nonFungibleToken, 3L).between(otherOwner, receiver)
						)
								.payingWith(spender)
								.signedBy(spender, owner, otherReceiver, otherOwner)
								.via("complexAllowanceTransfer"),
						getTxnRecord("complexAllowanceTransfer").logged(),
						getAccountDetails(owner)
								.payingWith(GENESIS)
								.hasToken(relationshipWith(fungibleToken).balance(925))
								.hasToken(relationshipWith(nonFungibleToken).balance(0))
								.has(AccountDetailsAsserts.accountWith()
										.balanceLessThan(98 * ONE_HBAR)
										.cryptoAllowancesContaining(spender, 9 * ONE_HBAR)
										.tokenAllowancesContaining(fungibleToken, spender, 475)),
						getAccountDetails(otherOwner)
								.payingWith(GENESIS)
								.hasToken(relationshipWith(fungibleToken).balance(970))
								.hasToken(relationshipWith(nonFungibleToken).balance(0))
								.has(AccountDetailsAsserts.accountWith()
										.balanceLessThan(98 * ONE_HBAR)
										.cryptoAllowancesContaining(spender, 4 * ONE_HBAR)
										.tokenAllowancesContaining(fungibleToken, spender, 85)
										.nftApprovedAllowancesContaining(nonFungibleToken, spender)),
						getAccountInfo(receiver)
								.hasToken(relationshipWith(fungibleToken).balance(105))
								.hasToken(relationshipWith(nonFungibleToken).balance(4))
								.has(accountWith().balance(5 * ONE_HBAR)),
						getAccountInfo(anotherReceiver)
								.hasToken(relationshipWith(fungibleToken).balance(50))
								.has(accountWith().balance(ONE_HBAR))
				);
	}

	private HapiApiSpec allowanceTransfersWorkAsExpected() {
		return defaultHapiSpec("AllowanceTransfersWorkAsExpected")
				.given(
						fileUpdate(APP_PROPERTIES)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.overridingProps(Map.of(
										"hedera.allowances.maxTransactionLimit", "20",
										"hedera.allowances.maxAccountLimit", "100")
								),
						newKeyNamed(adminKey),
						newKeyNamed(freezeKey),
						newKeyNamed(kycKey),
						newKeyNamed(pauseKey),
						newKeyNamed(supplyKey),
						newKeyNamed(wipeKey),
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(owner).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(receiver),
						cryptoCreate(otherReceiver)
								.balance(ONE_HBAR)
								.maxAutomaticTokenAssociations(1),
						tokenCreate(fungibleToken)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.maxSupply(10000)
								.initialSupply(5000)
								.adminKey(adminKey)
								.pauseKey(pauseKey)
								.kycKey(kycKey)
								.freezeKey(freezeKey),
						tokenCreate(nonFungibleToken)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.maxSupply(12L)
								.supplyKey(supplyKey)
								.adminKey(adminKey)
								.freezeKey(freezeKey)
								.wipeKey(wipeKey)
								.pauseKey(pauseKey)
								.initialSupply(0L),
						tokenCreate(tokenWithCustomFee)
								.treasury(TOKEN_TREASURY)
								.supplyType(TokenSupplyType.FINITE)
								.initialSupply(1000)
								.maxSupply(5000)
								.adminKey(adminKey)
								.withCustom(fixedHtsFee(10, "0.0.0", TOKEN_TREASURY)),
						mintToken(nonFungibleToken, List.of(ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c"),
								ByteString.copyFromUtf8("d"),
								ByteString.copyFromUtf8("e"),
								ByteString.copyFromUtf8("f")))
				)
				.when(
						tokenAssociate(owner, fungibleToken, nonFungibleToken, tokenWithCustomFee),
						tokenAssociate(receiver, fungibleToken, nonFungibleToken, tokenWithCustomFee),
						grantTokenKyc(fungibleToken, owner),
						grantTokenKyc(fungibleToken, receiver),
						cryptoTransfer(
								moving(1000, fungibleToken).between(TOKEN_TREASURY, owner),
								moving(15, tokenWithCustomFee).between(TOKEN_TREASURY, owner),
								movingUnique(nonFungibleToken, 1L, 2L, 3L, 4L, 5L, 6L).between(TOKEN_TREASURY, owner)),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addCryptoAllowance(owner, spender, 10 * ONE_HBAR)
								.addTokenAllowance(owner, fungibleToken, spender, 1500)
								.addTokenAllowance(owner, tokenWithCustomFee, spender, 100)
								.addNftAllowance(owner, nonFungibleToken, spender, false, List.of(1L, 2L, 3L, 4L, 6L))
								.fee(ONE_HUNDRED_HBARS)
				)
				.then(
						cryptoTransfer(movingWithAllowance(10, tokenWithCustomFee).between(owner, receiver))
								.payingWith(spender)
								.signedBy(spender)
								.fee(ONE_HBAR)
								.hasKnownStatus(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE),
						cryptoTransfer(movingWithAllowance(100, fungibleToken).between(owner, owner))
								.payingWith(spender)
								.signedBy(spender)
								.dontFullyAggregateTokenTransfers()
								.hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
						cryptoTransfer(movingUniqueWithAllowance(nonFungibleToken, 3).between(owner, otherReceiver))
								.payingWith(spender)
								.signedBy(spender),
						cryptoTransfer(movingWithAllowance(100, fungibleToken).between(owner, otherReceiver))
								.payingWith(spender)
								.signedBy(spender)
								.hasKnownStatus(NO_REMAINING_AUTOMATIC_ASSOCIATIONS),
						cryptoUpdate(otherReceiver)
								.receiverSigRequired(true)
								.maxAutomaticAssociations(2),
						cryptoTransfer(movingUniqueWithAllowance(nonFungibleToken, 4).between(owner, otherReceiver))
								.payingWith(spender)
								.signedBy(spender)
								.hasKnownStatus(INVALID_SIGNATURE),
						cryptoTransfer(movingUniqueWithAllowance(nonFungibleToken, 4).between(owner, otherReceiver))
								.payingWith(spender)
								.signedBy(spender, otherReceiver),
						cryptoTransfer(movingUnique(nonFungibleToken, 6).between(owner, receiver)),
						cryptoTransfer(movingUniqueWithAllowance(nonFungibleToken, 6).between(owner, receiver))
								.payingWith(spender)
								.signedBy(spender)
								.hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
						cryptoTransfer(movingUniqueWithAllowance(nonFungibleToken, 6).between(receiver, owner))
								.payingWith(spender)
								.signedBy(spender)
								.hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
						cryptoTransfer(movingUnique(nonFungibleToken, 6).between(receiver, owner)),
						cryptoTransfer(movingUniqueWithAllowance(nonFungibleToken, 6).between(owner, receiver))
								.payingWith(spender)
								.signedBy(spender)
								.hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
						cryptoTransfer(movingUnique(nonFungibleToken, 6).between(owner, receiver)),
						tokenAssociate(otherReceiver, fungibleToken),
						grantTokenKyc(fungibleToken, otherReceiver),
						cryptoTransfer(movingWithAllowance(1100, fungibleToken).between(owner, otherReceiver))
								.payingWith(spender)
								.signedBy(spender, otherReceiver)
								.hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE),
						cryptoTransfer(allowanceTinyBarsFromTo(owner, receiver, 5 * ONE_HBAR))
								.payingWith(DEFAULT_PAYER)
								.signedBy(DEFAULT_PAYER)
								.hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
						tokenPause(fungibleToken),
						cryptoTransfer(
								movingWithAllowance(50, fungibleToken).between(owner, receiver),
								movingUniqueWithAllowance(nonFungibleToken, 1).between(owner, receiver))
								.payingWith(spender)
								.signedBy(spender)
								.hasKnownStatus(TOKEN_IS_PAUSED),
						tokenUnpause(fungibleToken),
						tokenFreeze(fungibleToken, owner),
						cryptoTransfer(
								movingWithAllowance(50, fungibleToken).between(owner, receiver),
								movingUniqueWithAllowance(nonFungibleToken, 1).between(owner, receiver))
								.payingWith(spender)
								.signedBy(spender)
								.hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
						tokenUnfreeze(fungibleToken, owner),
						revokeTokenKyc(fungibleToken, receiver),
						cryptoTransfer(
								movingWithAllowance(50, fungibleToken).between(owner, receiver),
								movingUniqueWithAllowance(nonFungibleToken, 1).between(owner, receiver))
								.payingWith(spender)
								.signedBy(spender)
								.hasKnownStatus(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN),
						grantTokenKyc(fungibleToken, receiver),
						cryptoTransfer(allowanceTinyBarsFromTo(owner, receiver, 5 * ONE_HBAR),
								tinyBarsFromTo(spender, receiver, ONE_HBAR))
								.payingWith(spender)
								.signedBy(spender),
						cryptoTransfer(
								movingWithAllowance(50, fungibleToken).between(owner, receiver),
								movingUniqueWithAllowance(nonFungibleToken, 1).between(owner, receiver))
								.payingWith(spender)
								.signedBy(spender),
						cryptoTransfer(allowanceTinyBarsFromTo(owner, receiver, 5 * ONE_HBAR + 1))
								.payingWith(spender)
								.signedBy(spender)
								.hasKnownStatus(AMOUNT_EXCEEDS_ALLOWANCE),
						cryptoTransfer(
								movingWithAllowance(50, fungibleToken).between(owner, receiver),
								movingUniqueWithAllowance(nonFungibleToken, 5).between(owner, receiver))
								.payingWith(spender)
								.signedBy(spender)
								.hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
						getAccountDetails(owner)
								.payingWith(GENESIS)
								.has(AccountDetailsAsserts.accountWith().tokenAllowancesContaining(fungibleToken,
										spender, 1450))
								.hasToken(relationshipWith(fungibleToken).balance(950L)),
						cryptoTransfer(moving(1000, fungibleToken).between(TOKEN_TREASURY, owner)),
						cryptoTransfer(
								movingUniqueWithAllowance(nonFungibleToken, 2L).between(owner, receiver),
								movingWithAllowance(1451, fungibleToken).between(owner, receiver))
								.payingWith(spender)
								.signedBy(spender)
								.hasKnownStatus(AMOUNT_EXCEEDS_ALLOWANCE),
						getAccountInfo(owner)
								.hasToken(relationshipWith(nonFungibleToken).balance(2)),
						cryptoTransfer(allowanceTinyBarsFromTo(owner, receiver, 5 * ONE_HBAR))
								.payingWith(spender)
								.signedBy(spender),
						cryptoTransfer(
								movingWithAllowance(50, fungibleToken).between(owner, receiver),
								movingUniqueWithAllowance(nonFungibleToken, 2L).between(owner, receiver))
								.payingWith(spender)
								.signedBy(spender),
						cryptoTransfer(allowanceTinyBarsFromTo(owner, receiver, 5 * ONE_HBAR))
								.payingWith(spender)
								.signedBy(spender)
								.hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
						cryptoTransfer(movingUnique(nonFungibleToken, 2L).between(receiver, owner)),
						cryptoTransfer(movingUniqueWithAllowance(nonFungibleToken, 2L).between(owner, receiver))
								.payingWith(spender)
								.signedBy(spender)
								.hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
						cryptoApproveAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nonFungibleToken, spender, true, List.of())
								.fee(ONE_HUNDRED_HBARS),
						cryptoTransfer(movingUniqueWithAllowance(nonFungibleToken, 2L).between(owner, receiver))
								.payingWith(spender)
								.signedBy(spender),
						cryptoTransfer(movingUnique(nonFungibleToken, 2L).between(receiver, owner)),
						cryptoTransfer(movingUniqueWithAllowance(nonFungibleToken, 2L).between(owner, receiver))
								.payingWith(spender)
								.signedBy(spender),
						getAccountDetails(owner)
								.payingWith(GENESIS)
								.has(AccountDetailsAsserts.accountWith()
										.cryptoAllowancesCount(0)
										.tokenAllowancesContaining(fungibleToken, spender, 1400)
										.nftApprovedAllowancesContaining(nonFungibleToken, spender))
				);
	}

	private HapiApiSpec checksExpectedDecimalsForFungibleTokenTransferList() {
		final var party = "owningParty";
		final var multipurpose = "multi";
		final var fungibleType = "fungible";

		return defaultHapiSpec("checksExpectedDecimalsForFungibleTokenTransferList")
				.given(
						newKeyNamed(multipurpose),
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(party).maxAutomaticTokenAssociations(123),
						tokenCreate(fungibleType)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.decimals(2)
								.initialSupply(1234)
								.via("tokenCreate"),
						getTxnRecord("tokenCreate")
								.hasNewTokenAssociation(fungibleType, TOKEN_TREASURY),

						cryptoTransfer(moving(100, fungibleType).between(TOKEN_TREASURY, party)).via("initialXfer"),
						getTxnRecord("initialXfer")
								.hasNewTokenAssociation(fungibleType, party)
				).when(
						getAccountInfo(party).savingSnapshot(party),
						cryptoTransfer(movingWithDecimals(10, fungibleType, 4)
								.betweenWithDecimals(TOKEN_TREASURY, party))
								.signedBy(DEFAULT_PAYER, party, TOKEN_TREASURY)
								.hasKnownStatus(UNEXPECTED_TOKEN_DECIMALS)
								.via("failedTxn"),
						cryptoTransfer(movingWithDecimals(20, fungibleType, 2)
								.betweenWithDecimals(TOKEN_TREASURY, party))
								.signedBy(DEFAULT_PAYER, party, TOKEN_TREASURY)
								.hasKnownStatus(SUCCESS)
								.via("validTxn"),
						usableTxnIdNamed("uncheckedTxn").payerId(DEFAULT_PAYER),
						uncheckedSubmit(
								cryptoTransfer(movingWithDecimals(10, fungibleType, 4)
										.betweenWithDecimals(TOKEN_TREASURY, party))
										.signedBy(DEFAULT_PAYER, party, TOKEN_TREASURY)
										.txnId("uncheckedTxn"))
								.payingWith(GENESIS)
				).then(
						sleepFor(5_000),
						getReceipt("uncheckedTxn").hasPriorityStatus(UNEXPECTED_TOKEN_DECIMALS).logged(),
						getReceipt("validTxn").hasPriorityStatus(SUCCESS),
						getTxnRecord("validTxn").logged(),
						getAccountInfo(party)
								.hasAlreadyUsedAutomaticAssociations(1)
								.hasToken(relationshipWith(fungibleType).balance(120))
								.logged()
				);
	}

	private HapiApiSpec nftTransfersHaveTransitiveClosure() {
		final var aParty = "aParty";
		final var bParty = "bParty";
		final var cParty = "cParty";
		final var dParty = "dParty";
		final var multipurpose = "multi";
		final var nftType = "nftType";
		final var hotTxn = "hotTxn";
		final var mintTxn = "mintTxn";

		return defaultHapiSpec("NftTransfersHaveTransitiveClosure")
				.given(
						newKeyNamed(multipurpose),
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(aParty).maxAutomaticTokenAssociations(1),
						cryptoCreate(bParty).maxAutomaticTokenAssociations(1),
						cryptoCreate(cParty).maxAutomaticTokenAssociations(1),
						cryptoCreate(dParty).maxAutomaticTokenAssociations(1),
						tokenCreate(nftType)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.supplyKey(multipurpose)
								.initialSupply(0),
						mintToken(nftType, List.of(copyFromUtf8("Hot potato!"))).via(mintTxn),
						getTxnRecord(mintTxn).logged(),
						cryptoTransfer(movingUnique(nftType, 1L)
								.between(TOKEN_TREASURY, aParty))
				).when(
						cryptoTransfer((spec, b) -> {
									final var registry = spec.registry();
									final var aId = registry.getAccountID(aParty);
									final var bId = registry.getAccountID(bParty);
									final var cId = registry.getAccountID(cParty);
									final var dId = registry.getAccountID(dParty);
									b.addTokenTransfers(TokenTransferList.newBuilder()
											.setToken(registry.getTokenID(nftType))
											.addNftTransfers(ocWith(aId, bId, 1))
											.addNftTransfers(ocWith(bId, cId, 1))
											.addNftTransfers(ocWith(cId, dId, 1)));
								}
						)
								.via(hotTxn)
								.signedBy(DEFAULT_PAYER, aParty, bParty, cParty)
				).then(
						getTxnRecord(hotTxn)
								.hasPriority(recordWith()
										.tokenTransfers(new BaseErroringAssertsProvider<>() {
											@Override
											public ErroringAsserts<List<TokenTransferList>> assertsFor(
													final HapiApiSpec spec
											) {
												return tokenTransfers -> {
													try {
														assertEquals(1, tokenTransfers.size(),
																"No transfers appeared");
														final var changes = tokenTransfers.get(0);
//														assertEquals(1, changes.getNftTransfersCount(),
//																"Transitive closure didn't happen");
													} catch (Throwable failure) {
														return List.of(failure);
													}
													return Collections.emptyList();
												};
											}
										})).logged()
				);
	}

	private HapiApiSpec nftSelfTransfersRejectedBothInPrecheckAndHandle() {
		final var owningParty = "owningParty";
		final var multipurpose = "multi";
		final var nftType = "nftType";
		final var uncheckedTxn = "uncheckedTxn";

		return defaultHapiSpec("NftSelfTransfersRejectedBothInPrecheckAndHandle")
				.given(
						newKeyNamed(multipurpose),
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(owningParty).maxAutomaticTokenAssociations(123),
						tokenCreate(nftType)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.supplyKey(multipurpose)
								.initialSupply(0),
						mintToken(nftType, List.of(
								copyFromUtf8("We"),
								copyFromUtf8("are"),
								copyFromUtf8("the")
						)),
						cryptoTransfer(movingUnique(nftType, 1L, 2L)
								.between(TOKEN_TREASURY, owningParty))
				).when(
						getAccountInfo(owningParty)
								.savingSnapshot(owningParty),
						cryptoTransfer(movingUnique(nftType, 1L)
								.between(owningParty, owningParty)
						)
								.signedBy(DEFAULT_PAYER, owningParty)
								.hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
						usableTxnIdNamed(uncheckedTxn).payerId(DEFAULT_PAYER),
						uncheckedSubmit(
								cryptoTransfer(movingUnique(nftType, 1L)
										.between(owningParty, owningParty)
								)
										.signedBy(DEFAULT_PAYER, owningParty)
										.txnId(uncheckedTxn)
						).payingWith(GENESIS)
				).then(
						sleepFor(2_000),
						getReceipt(uncheckedTxn).hasPriorityStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
						getAccountInfo(owningParty)
								.has(accountWith().noChangesFromSnapshot(owningParty))
				);
	}

	private HapiApiSpec hbarAndFungibleSelfTransfersRejectedBothInPrecheckAndHandle() {
		final var owningParty = "owningParty";
		final var multipurpose = "multi";
		final var fungibleType = "fungible";
		final var uncheckedHbarTxn = "uncheckedHbarTxn";
		final var uncheckedFtTxn = "uncheckedFtTxn";

		return defaultHapiSpec("HbarAndFungibleSelfTransfersRejectedBothInPrecheckAndHandle")
				.given(
						newKeyNamed(multipurpose),
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(owningParty).maxAutomaticTokenAssociations(123),
						tokenCreate(fungibleType)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(1234),
						cryptoTransfer(moving(100, fungibleType)
								.between(TOKEN_TREASURY, owningParty))
				).when(
						getAccountInfo(owningParty)
								.savingSnapshot(owningParty),
						cryptoTransfer(tinyBarsFromTo(owningParty, owningParty, 1))
								.signedBy(DEFAULT_PAYER, owningParty)
								.hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
						cryptoTransfer(moving(1, fungibleType).between(owningParty, owningParty))
								.signedBy(DEFAULT_PAYER, owningParty)
								.dontFullyAggregateTokenTransfers()
								.hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
						/* And bypassing precheck */
						usableTxnIdNamed(uncheckedHbarTxn).payerId(DEFAULT_PAYER),
						usableTxnIdNamed(uncheckedFtTxn).payerId(DEFAULT_PAYER),
						uncheckedSubmit(
								cryptoTransfer(tinyBarsFromTo(owningParty, owningParty, 1))
										.signedBy(DEFAULT_PAYER, owningParty)
										.txnId(uncheckedHbarTxn)
						).payingWith(GENESIS),
						uncheckedSubmit(
								cryptoTransfer(moving(1, fungibleType).between(owningParty, owningParty))
										.signedBy(DEFAULT_PAYER, owningParty)
										.dontFullyAggregateTokenTransfers()
										.txnId(uncheckedFtTxn)
						).payingWith(GENESIS)
				).then(
						sleepFor(5_000),
						getReceipt(uncheckedHbarTxn).hasPriorityStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
						getReceipt(uncheckedFtTxn).hasPriorityStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
						getAccountInfo(owningParty)
								.has(accountWith().noChangesFromSnapshot(owningParty))
				);
	}

	private HapiApiSpec dissociatedRoyaltyCollectorsCanUseAutoAssociation() {
		final var commonWithCustomFees = "commonWithCustomFees";
		final var fractionalCollector = "fractionalCollector";
		final var selfDenominatedCollector = "selfDenominatedCollector";
		final var party = "party";
		final var counterparty = "counterparty";
		final var multipurpose = "multi";
		final var miscXfer = "hodlXfer";
		final var plentyOfSlots = 10;

		return defaultHapiSpec("RoyaltyCollectorsCanUseAutoAssociation")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(fractionalCollector).maxAutomaticTokenAssociations(plentyOfSlots),
						cryptoCreate(selfDenominatedCollector).maxAutomaticTokenAssociations(plentyOfSlots),
						cryptoCreate(party).maxAutomaticTokenAssociations(plentyOfSlots),
						cryptoCreate(counterparty).maxAutomaticTokenAssociations(plentyOfSlots),
						newKeyNamed(multipurpose),
						getAccountInfo(party).savingSnapshot(party),
						getAccountInfo(counterparty).savingSnapshot(counterparty),
						getAccountInfo(fractionalCollector).savingSnapshot(fractionalCollector),
						getAccountInfo(selfDenominatedCollector).savingSnapshot(selfDenominatedCollector)
				).when(
						tokenCreate(commonWithCustomFees)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.withCustom(fractionalFee(
										1, 10, 0, OptionalLong.empty(),
										fractionalCollector))
								.withCustom(fixedHtsFee(
										5, "0.0.0",
										selfDenominatedCollector))
								.initialSupply(Long.MAX_VALUE)
								.signedBy(DEFAULT_PAYER, TOKEN_TREASURY, fractionalCollector, selfDenominatedCollector),
						cryptoTransfer(
								moving(1_000_000, commonWithCustomFees).between(TOKEN_TREASURY, party)
						),
						tokenDissociate(fractionalCollector, commonWithCustomFees),
						tokenDissociate(selfDenominatedCollector, commonWithCustomFees)
				).then(
						cryptoTransfer(
								moving(1000, commonWithCustomFees).between(party, counterparty)
						).fee(ONE_HBAR).via(miscXfer),
						getTxnRecord(miscXfer)
								.hasPriority(recordWith()
										.autoAssociated(
												accountTokenPairsInAnyOrder(List.of(
														/* The counterparty auto-associates to the fungible type */
														Pair.of(counterparty, commonWithCustomFees),
														/* Both royalty collectors re-auto-associate */
														Pair.of(fractionalCollector, commonWithCustomFees),
														Pair.of(selfDenominatedCollector, commonWithCustomFees)
												))
										)),
						getAccountInfo(fractionalCollector).has(accountWith().newAssociationsFromSnapshot(
								party, List.of(
										relationshipWith(commonWithCustomFees).balance(100)
								)
						)),
						getAccountInfo(selfDenominatedCollector).has(accountWith().newAssociationsFromSnapshot(
								party, List.of(
										relationshipWith(commonWithCustomFees).balance(5)
								)
						))
				);
	}

	private HapiApiSpec royaltyCollectorsCanUseAutoAssociation() {
		final var uniqueWithRoyalty = "uniqueWithRoyalty";
		final var firstFungible = "firstFungible";
		final var secondFungible = "secondFungible";
		final var firstRoyaltyCollector = "firstRoyaltyCollector";
		final var secondRoyaltyCollector = "secondRoyaltyCollector";
		final var party = "party";
		final var counterparty = "counterparty";
		final var multipurpose = "multi";
		final var hodlXfer = "hodlXfer";
		final var plentyOfSlots = 10;
		final var exchangeAmount = 12 * 15;
		final var firstRoyaltyAmount = exchangeAmount / 12;
		final var secondRoyaltyAmount = exchangeAmount / 15;
		final var netExchangeAmount = exchangeAmount - firstRoyaltyAmount - secondRoyaltyAmount;

		return defaultHapiSpec("RoyaltyCollectorsCanUseAutoAssociation")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(firstRoyaltyCollector).maxAutomaticTokenAssociations(plentyOfSlots),
						cryptoCreate(secondRoyaltyCollector).maxAutomaticTokenAssociations(plentyOfSlots),
						cryptoCreate(party).maxAutomaticTokenAssociations(plentyOfSlots),
						cryptoCreate(counterparty).maxAutomaticTokenAssociations(plentyOfSlots),
						newKeyNamed(multipurpose),
						getAccountInfo(party).savingSnapshot(party),
						getAccountInfo(counterparty).savingSnapshot(counterparty),
						getAccountInfo(firstRoyaltyCollector).savingSnapshot(firstRoyaltyCollector),
						getAccountInfo(secondRoyaltyCollector).savingSnapshot(secondRoyaltyCollector)
				).when(
						tokenCreate(firstFungible)
								.treasury(TOKEN_TREASURY)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(123456789),
						tokenCreate(secondFungible)
								.treasury(TOKEN_TREASURY)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(123456789),
						cryptoTransfer(
								moving(1000, firstFungible).between(TOKEN_TREASURY, counterparty),
								moving(1000, secondFungible).between(TOKEN_TREASURY, counterparty)
						),
						tokenCreate(uniqueWithRoyalty)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.supplyKey(multipurpose)
								.withCustom(royaltyFeeNoFallback(1, 12, firstRoyaltyCollector))
								.withCustom(royaltyFeeNoFallback(1, 15, secondRoyaltyCollector))
								.initialSupply(0L),
						mintToken(uniqueWithRoyalty, List.of(copyFromUtf8("HODL"))),
						cryptoTransfer(
								movingUnique(uniqueWithRoyalty, 1L).between(TOKEN_TREASURY, party)
						)
				).then(
						cryptoTransfer(
								movingUnique(uniqueWithRoyalty, 1L).between(party, counterparty),
								moving(12 * 15, firstFungible).between(counterparty, party),
								moving(12 * 15, secondFungible).between(counterparty, party)
						).fee(ONE_HBAR).via(hodlXfer),
						getTxnRecord(hodlXfer)
								.hasPriority(recordWith()
										.autoAssociated(
												accountTokenPairsInAnyOrder(List.of(
														/* The counterparty auto-associates to the non-fungible type */
														Pair.of(counterparty, uniqueWithRoyalty),
														/* The sending party auto-associates to both fungibles */
														Pair.of(party, firstFungible),
														Pair.of(party, secondFungible),
														/* Both royalty collectors auto-associate to both fungibles */
														Pair.of(firstRoyaltyCollector, firstFungible),
														Pair.of(secondRoyaltyCollector, firstFungible),
														Pair.of(firstRoyaltyCollector, secondFungible),
														Pair.of(secondRoyaltyCollector, secondFungible)
												))
										)),
						getAccountInfo(party).has(accountWith().newAssociationsFromSnapshot(
								party, List.of(
										relationshipWith(uniqueWithRoyalty).balance(0),
										relationshipWith(firstFungible).balance(netExchangeAmount),
										relationshipWith(secondFungible).balance(netExchangeAmount)
								)
						)),
						getAccountInfo(counterparty).has(accountWith().newAssociationsFromSnapshot(
								party, List.of(
										relationshipWith(uniqueWithRoyalty).balance(1),
										relationshipWith(firstFungible).balance(1000 - exchangeAmount),
										relationshipWith(secondFungible).balance(1000 - exchangeAmount)
								)
						)),
						getAccountInfo(firstRoyaltyCollector).has(accountWith().newAssociationsFromSnapshot(
								party, List.of(
										relationshipWith(firstFungible).balance(exchangeAmount / 12),
										relationshipWith(secondFungible).balance(exchangeAmount / 12)
								)
						)),
						getAccountInfo(secondRoyaltyCollector).has(accountWith().newAssociationsFromSnapshot(
								party, List.of(
										relationshipWith(firstFungible).balance(exchangeAmount / 15),
										relationshipWith(secondFungible).balance(exchangeAmount / 15)
								)
						))
				);
	}

	private HapiApiSpec royaltyCollectorsCannotUseAutoAssociationWithoutOpenSlots() {
		final var uniqueWithRoyalty = "uniqueWithRoyalty";
		final var someFungible = "firstFungible";
		final var royaltyCollectorNoSlots = "royaltyCollectorNoSlots";
		final var party = "party";
		final var counterparty = "counterparty";
		final var multipurpose = "multi";
		final var hodlXfer = "hodlXfer";

		return defaultHapiSpec("RoyaltyCollectorsCanUseAutoAssociationWithoutOpenSlots")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(royaltyCollectorNoSlots),
						cryptoCreate(party).maxAutomaticTokenAssociations(123),
						cryptoCreate(counterparty).maxAutomaticTokenAssociations(123),
						newKeyNamed(multipurpose),
						getAccountInfo(party).savingSnapshot(party),
						getAccountInfo(counterparty).savingSnapshot(counterparty),
						getAccountInfo(royaltyCollectorNoSlots).savingSnapshot(royaltyCollectorNoSlots)
				).when(
						tokenCreate(someFungible)
								.treasury(TOKEN_TREASURY)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(123456789),
						cryptoTransfer(
								moving(1000, someFungible).between(TOKEN_TREASURY, counterparty)
						),
						tokenCreate(uniqueWithRoyalty)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.supplyKey(multipurpose)
								.withCustom(royaltyFeeNoFallback(1, 12, royaltyCollectorNoSlots))
								.initialSupply(0L),
						mintToken(uniqueWithRoyalty, List.of(copyFromUtf8("HODL"))),
						cryptoTransfer(
								movingUnique(uniqueWithRoyalty, 1L).between(TOKEN_TREASURY, party)
						)
				).then(
						cryptoTransfer(
								movingUnique(uniqueWithRoyalty, 1L).between(party, counterparty),
								moving(123, someFungible).between(counterparty, party)
						)
								.fee(ONE_HBAR)
								.via(hodlXfer)
								.hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
						getTxnRecord(hodlXfer)
								.hasPriority(recordWith().autoAssociated(accountTokenPairsInAnyOrder(List.of()))),
						getAccountInfo(party).has(accountWith().newAssociationsFromSnapshot(
								party, List.of(
										relationshipWith(uniqueWithRoyalty).balance(1)
								)
						))
				);
	}

	private HapiApiSpec autoAssociationRequiresOpenSlots() {
		final String tokenA = "tokenA";
		final String tokenB = "tokenB";
		final String firstUser = "firstUser";
		final String secondUser = "secondUser";
		final String treasury = "treasury";
		final String tokenAcreateTxn = "tokenACreate";
		final String tokenBcreateTxn = "tokenBCreate";
		final String transferToFU = "transferToFU";
		final String transferToSU = "transferToSU";

		return defaultHapiSpec("AutoAssociationRequiresOpenSlots")
				.given(
						cryptoCreate(treasury)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(firstUser)
								.balance(ONE_HBAR)
								.maxAutomaticTokenAssociations(1),
						cryptoCreate(secondUser)
								.balance(ONE_HBAR)
								.maxAutomaticTokenAssociations(2)
				).when(
						tokenCreate(tokenA)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasury)
								.via(tokenAcreateTxn),
						getTxnRecord(tokenAcreateTxn)
								.hasNewTokenAssociation(tokenA, treasury)
								.logged(),
						tokenCreate(tokenB)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasury)
								.via(tokenBcreateTxn),
						getTxnRecord(tokenBcreateTxn)
								.hasNewTokenAssociation(tokenB, treasury)
								.logged(),
						cryptoTransfer(moving(1, tokenA).between(treasury, firstUser))
								.via(transferToFU),
						getTxnRecord(transferToFU)
								.hasNewTokenAssociation(tokenA, firstUser)
								.logged(),
						cryptoTransfer(moving(1, tokenB).between(treasury, secondUser))
								.via(transferToSU),
						getTxnRecord(transferToSU)
								.hasNewTokenAssociation(tokenB, secondUser)
								.logged()
				).then(
						cryptoTransfer(moving(1, tokenB).between(treasury, firstUser))
								.hasKnownStatus(NO_REMAINING_AUTOMATIC_ASSOCIATIONS)
								.via("failedTransfer"),
						getAccountInfo(firstUser)
								.hasAlreadyUsedAutomaticAssociations(1)
								.hasMaxAutomaticAssociations(1)
								.logged(),
						getAccountInfo(secondUser)
								.hasAlreadyUsedAutomaticAssociations(1)
								.hasMaxAutomaticAssociations(2)
								.logged(),
						cryptoTransfer(moving(1, tokenA).between(treasury, secondUser)),
						getAccountInfo(secondUser)
								.hasAlreadyUsedAutomaticAssociations(2)
								.hasMaxAutomaticAssociations(2)
								.logged(),
						cryptoTransfer(moving(1, tokenA).between(firstUser, treasury)),
						tokenDissociate(firstUser, tokenA),
						cryptoTransfer(moving(1, tokenB).between(treasury, firstUser))
				);
	}

	private HapiApiSpec baseCryptoTransferFeeChargedAsExpected() {
		final var expectedHbarXferPriceUsd = 0.0001;
		final var expectedHtsXferPriceUsd = 0.001;
		final var expectedNftXferPriceUsd = 0.001;
		final var expectedHtsXferWithCustomFeePriceUsd = 0.002;
		final var expectedNftXferWithCustomFeePriceUsd = 0.002;
		final var transferAmount = 1L;
		final var customFeeCollector = "customFeeCollector";
		final var sender = "sender";
		final var nonTreasurySender = "nonTreasurySender";
		final var receiver = "receiver";
		final var hbarXferTxn = "hbarXferTxn";
		final var fungibleToken = "fungibleToken";
		final var fungibleTokenWithCustomFee = "fungibleTokenWithCustomFee";
		final var htsXferTxn = "htsXferTxn";
		final var htsXferTxnWithCustomFee = "htsXferTxnWithCustomFee";
		final var nonFungibleToken = "nonFungibleToken";
		final var nonFungibleTokenWithCustomFee = "nonFungibleTokenWithCustomFee";
		final var nftXferTxn = "nftXferTxn";
		final var nftXferTxnWithCustomFee = "nftXferTxnWithCustomFee";
		final var supplyKey = "supplyKey";

		return defaultHapiSpec("BaseCryptoTransferIsChargedAsExpected")
				.given(
						cryptoCreate(nonTreasurySender).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(sender).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(receiver),
						cryptoCreate(customFeeCollector),
						tokenCreate(fungibleToken)
								.treasury(sender)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(100L),
						tokenCreate(fungibleTokenWithCustomFee)
								.treasury(sender)
								.tokenType(FUNGIBLE_COMMON)
								.withCustom(fixedHbarFee(transferAmount, customFeeCollector))
								.initialSupply(100L),
						tokenAssociate(receiver, fungibleToken, fungibleTokenWithCustomFee),
						newKeyNamed(supplyKey),
						tokenCreate(nonFungibleToken)
								.initialSupply(0)
								.supplyKey(supplyKey)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(sender),
						tokenCreate(nonFungibleTokenWithCustomFee)
								.initialSupply(0)
								.supplyKey(supplyKey)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.withCustom(fixedHbarFee(transferAmount, customFeeCollector))
								.treasury(sender),
						tokenAssociate(nonTreasurySender,
								List.of(fungibleTokenWithCustomFee, nonFungibleTokenWithCustomFee)),
						mintToken(nonFungibleToken, List.of(copyFromUtf8("memo1"))),
						mintToken(nonFungibleTokenWithCustomFee, List.of(copyFromUtf8("memo2"))),
						tokenAssociate(receiver, nonFungibleToken, nonFungibleTokenWithCustomFee),
						cryptoTransfer(movingUnique(nonFungibleTokenWithCustomFee, 1)
								.between(sender, nonTreasurySender))
								.payingWith(sender),
						cryptoTransfer(moving(1, fungibleTokenWithCustomFee).between(sender, nonTreasurySender))
								.payingWith(sender)
				)
				.when(
						cryptoTransfer(tinyBarsFromTo(sender, receiver, 100L))
								.payingWith(sender)
								.blankMemo()
								.via(hbarXferTxn),
						cryptoTransfer(moving(1, fungibleToken).between(sender, receiver))
								.blankMemo()
								.payingWith(sender)
								.via(htsXferTxn),
						cryptoTransfer(movingUnique(nonFungibleToken, 1).between(sender, receiver))
								.blankMemo()
								.payingWith(sender)
								.via(nftXferTxn),
						cryptoTransfer(moving(1, fungibleTokenWithCustomFee)
								.between(nonTreasurySender, receiver)
						)
								.blankMemo()
								.fee(ONE_HBAR)
								.payingWith(nonTreasurySender)
								.via(htsXferTxnWithCustomFee),
						cryptoTransfer(movingUnique(nonFungibleTokenWithCustomFee, 1)
								.between(nonTreasurySender, receiver)
						)
								.blankMemo()
								.fee(ONE_HBAR)
								.payingWith(nonTreasurySender)
								.via(nftXferTxnWithCustomFee)
				)
				.then(
						validateChargedUsdWithin(hbarXferTxn, expectedHbarXferPriceUsd, 0.01),
						validateChargedUsdWithin(htsXferTxn, expectedHtsXferPriceUsd, 0.01),
						validateChargedUsdWithin(nftXferTxn, expectedNftXferPriceUsd, 0.01),
						validateChargedUsdWithin(htsXferTxnWithCustomFee, expectedHtsXferWithCustomFeePriceUsd, 0.1),
						validateChargedUsdWithin(nftXferTxnWithCustomFee, expectedNftXferWithCustomFeePriceUsd, 0.3)
				);
	}

	private HapiApiSpec okToSetInvalidPaymentHeaderForCostAnswer() {
		return defaultHapiSpec("OkToSetInvalidPaymentHeaderForCostAnswer")
				.given(
						cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L))
								.via("misc")
				).when().then(
						getTxnRecord("misc").useEmptyTxnAsCostPayment(),
						getTxnRecord("misc").omittingAnyPaymentForCostAnswer()
				);
	}


	private HapiApiSpec tokenTransferFeesScaleAsExpected() {
		return defaultHapiSpec("TokenTransferFeesScaleAsExpected")
				.given(
						cryptoCreate("a"),
						cryptoCreate("b"),
						cryptoCreate("c").balance(0L),
						cryptoCreate("d").balance(0L),
						cryptoCreate("e").balance(0L),
						cryptoCreate("f").balance(0L),
						tokenCreate("A").treasury("a"),
						tokenCreate("B").treasury("b"),
						tokenCreate("C").treasury("c")
				).when(
						tokenAssociate("b", "A", "C"),
						tokenAssociate("c", "A", "B"),
						tokenAssociate("d", "A", "B", "C"),
						tokenAssociate("e", "A", "B", "C"),
						tokenAssociate("f", "A", "B", "C"),
						cryptoTransfer(tinyBarsFromTo("a", "b", 1))
								.via("pureCrypto")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(moving(1, "A").between("a", "b"))
								.via("oneTokenTwoAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(moving(2, "A").distributing("a", "b", "c"))
								.via("oneTokenThreeAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(moving(3, "A").distributing("a", "b", "c", "d"))
								.via("oneTokenFourAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(moving(4, "A").distributing("a", "b", "c", "d", "e"))
								.via("oneTokenFiveAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(moving(5, "A").distributing("a", "b", "c", "d", "e", "f"))
								.via("oneTokenSixAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(
								moving(1, "A").between("a", "c"),
								moving(1, "B").between("b", "d"))
								.via("twoTokensFourAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(
								moving(1, "A").between("a", "c"),
								moving(2, "B").distributing("b", "d", "e"))
								.via("twoTokensFiveAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(
								moving(1, "A").between("a", "c"),
								moving(3, "B").distributing("b", "d", "e", "f"))
								.via("twoTokensSixAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a"),
						cryptoTransfer(
								moving(1, "A").between("a", "d"),
								moving(1, "B").between("b", "e"),
								moving(1, "C").between("c", "f"))
								.via("threeTokensSixAccounts")
								.fee(ONE_HUNDRED_HBARS)
								.payingWith("a")
				).then(
						withOpContext((spec, opLog) -> {
							var ref = getTxnRecord("pureCrypto");
							var t1a2 = getTxnRecord("oneTokenTwoAccounts");
							var t1a3 = getTxnRecord("oneTokenThreeAccounts");
							var t1a4 = getTxnRecord("oneTokenFourAccounts");
							var t1a5 = getTxnRecord("oneTokenFiveAccounts");
							var t1a6 = getTxnRecord("oneTokenSixAccounts");
							var t2a4 = getTxnRecord("twoTokensFourAccounts");
							var t2a5 = getTxnRecord("twoTokensFiveAccounts");
							var t2a6 = getTxnRecord("twoTokensSixAccounts");
							var t3a6 = getTxnRecord("threeTokensSixAccounts");
							allRunFor(spec, ref, t1a2, t1a3, t1a4, t1a5, t1a6, t2a4, t2a5, t2a6, t3a6);

							var refFee = ref.getResponseRecord().getTransactionFee();
							var t1a2Fee = t1a2.getResponseRecord().getTransactionFee();
							var t1a3Fee = t1a3.getResponseRecord().getTransactionFee();
							var t1a4Fee = t1a4.getResponseRecord().getTransactionFee();
							var t1a5Fee = t1a5.getResponseRecord().getTransactionFee();
							var t1a6Fee = t1a6.getResponseRecord().getTransactionFee();
							var t2a4Fee = t2a4.getResponseRecord().getTransactionFee();
							var t2a5Fee = t2a5.getResponseRecord().getTransactionFee();
							var t2a6Fee = t2a6.getResponseRecord().getTransactionFee();
							var t3a6Fee = t3a6.getResponseRecord().getTransactionFee();

							var rates = spec.ratesProvider();
							opLog.info("\n0 tokens involved,\n" +
											"  2 account adjustments: {} tb, ${}\n" +
											"1 tokens involved,\n" +
											"  2 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"  3 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"  4 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"  5 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"  6 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"2 tokens involved,\n" +
											"  4 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"  5 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"  6 account adjustments: {} tb, ${} (~{}x pure crypto)\n" +
											"3 tokens involved,\n" +
											"  6 account adjustments: {} tb, ${} (~{}x pure crypto)\n",
									refFee, sdec(rates.toUsdWithActiveRates(refFee), 4),
									t1a2Fee, sdec(rates.toUsdWithActiveRates(t1a2Fee), 4),
									sdec((1.0 * t1a2Fee / refFee), 1),
									t1a3Fee, sdec(rates.toUsdWithActiveRates(t1a3Fee), 4),
									sdec((1.0 * t1a3Fee / refFee), 1),
									t1a4Fee, sdec(rates.toUsdWithActiveRates(t1a4Fee), 4),
									sdec((1.0 * t1a4Fee / refFee), 1),
									t1a5Fee, sdec(rates.toUsdWithActiveRates(t1a5Fee), 4),
									sdec((1.0 * t1a5Fee / refFee), 1),
									t1a6Fee, sdec(rates.toUsdWithActiveRates(t1a6Fee), 4),
									sdec((1.0 * t1a6Fee / refFee), 1),
									t2a4Fee, sdec(rates.toUsdWithActiveRates(t2a4Fee), 4),
									sdec((1.0 * t2a4Fee / refFee), 1),
									t2a5Fee, sdec(rates.toUsdWithActiveRates(t2a5Fee), 4),
									sdec((1.0 * t2a5Fee / refFee), 1),
									t2a6Fee, sdec(rates.toUsdWithActiveRates(t2a6Fee), 4),
									sdec((1.0 * t2a6Fee / refFee), 1),
									t3a6Fee, sdec(rates.toUsdWithActiveRates(t3a6Fee), 4),
									sdec((1.0 * t3a6Fee / refFee), 1));

							double pureHbarUsd = rates.toUsdWithActiveRates(refFee);
							double pureOneTokenTwoAccountsUsd = rates.toUsdWithActiveRates(t1a2Fee);
							double pureTwoTokensFourAccountsUsd = rates.toUsdWithActiveRates(t2a4Fee);
							double pureThreeTokensSixAccountsUsd = rates.toUsdWithActiveRates(t3a6Fee);
							assertEquals(
									10.0,
									pureOneTokenTwoAccountsUsd / pureHbarUsd,
									1.0);
							assertEquals(
									20.0,
									pureTwoTokensFourAccountsUsd / pureHbarUsd,
									2.0);
							assertEquals(
									30.0,
									pureThreeTokensSixAccountsUsd / pureHbarUsd,
									3.0);
						})
				);
	}

	public static String sdec(double d, int numDecimals) {
		var fmt = String.format(".0%df", numDecimals);
		return String.format("%" + fmt, d);
	}

	private HapiApiSpec transferToNonAccountEntitiesReturnsInvalidAccountId() {
		AtomicReference<String> invalidAccountId = new AtomicReference<>();

		return defaultHapiSpec("TransferToNonAccountEntitiesReturnsInvalidAccountId")
				.given(
						tokenCreate("token"),
						createTopic("something"),
						withOpContext((spec, opLog) -> {
							var topicId = spec.registry().getTopicID("something");
							invalidAccountId.set(asTopicString(topicId));
						})
				).when().then(
						sourcing(() -> cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, invalidAccountId.get(), 1L))
								.signedBy(DEFAULT_PAYER)
								.hasKnownStatus(INVALID_ACCOUNT_ID)),
						sourcing(() -> cryptoTransfer(moving(1, "token")
								.between(DEFAULT_PAYER, invalidAccountId.get()))
								.signedBy(DEFAULT_PAYER)
								.hasKnownStatus(INVALID_ACCOUNT_ID))
				);
	}

	private HapiApiSpec complexKeyAcctPaysForOwnTransfer() {
		SigControl ENOUGH_UNIQUE_SIGS = KeyShape.threshSigs(2,
				KeyShape.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, ON),
				KeyShape.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
		String NODE = HapiSpecSetup.getDefaultInstance().defaultNodeName();

		return defaultHapiSpec("ComplexKeyAcctPaysForOwnTransfer")
				.given(
						newKeyNamed("complexKey").shape(ENOUGH_UNIQUE_SIGS),
						cryptoCreate("payer").key("complexKey").balance(1_000_000_000L)
				).when().then(
						cryptoTransfer(
								tinyBarsFromTo("payer", NODE, 1_000_000L)
						).payingWith("payer").numPayerSigs(14).fee(ONE_HUNDRED_HBARS)
				);
	}

	private HapiApiSpec twoComplexKeysRequired() {
		SigControl PAYER_SHAPE = threshOf(2, threshOf(1, 7), threshOf(3, 7));
		SigControl RECEIVER_SHAPE = KeyShape.threshSigs(3, threshOf(2, 2), threshOf(3, 5), ON);

		SigControl payerSigs = KeyShape.threshSigs(2,
				KeyShape.threshSigs(1, ON, OFF, OFF, OFF, OFF, OFF, OFF),
				KeyShape.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
		SigControl receiverSigs = KeyShape.threshSigs(3,
				KeyShape.threshSigs(2, ON, ON),
				KeyShape.threshSigs(3, OFF, OFF, ON, ON, ON),
				ON);

		return defaultHapiSpec("TwoComplexKeysRequired")
				.given(
						newKeyNamed("payerKey").shape(PAYER_SHAPE),
						newKeyNamed("receiverKey").shape(RECEIVER_SHAPE),
						cryptoCreate("payer").key("payerKey").balance(100_000_000_000L),
						cryptoCreate("receiver")
								.receiverSigRequired(true)
								.key("receiverKey")
								.payingWith("payer")
				).when().then(
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, "receiver", 1_000L)
						).payingWith("payer").sigControl(
										forKey("payer", payerSigs),
										forKey("receiver", receiverSigs)
								).hasKnownStatus(SUCCESS)
								.fee(ONE_HUNDRED_HBARS)
				);
	}

	private HapiApiSpec specialAccountsBalanceCheck() {
		return defaultHapiSpec("SpecialAccountsBalanceCheck")
				.given().when().then(
						IntStream.concat(IntStream.range(1, 101), IntStream.range(900, 1001))
								.mapToObj(i -> getAccountBalance("0.0." + i).logged())
								.toArray(n -> new HapiSpecOperation[n])
				);
	}

	private HapiApiSpec transferWithMissingAccountGetsInvalidAccountId() {
		return defaultHapiSpec("TransferWithMissingAccount")
				.given(
						cryptoCreate("payeeSigReq").receiverSigRequired(true)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("1.2.3", "payeeSigReq", 1_000L)
						)
								.signedBy(DEFAULT_PAYER, "payeeSigReq")
								.hasKnownStatus(INVALID_ACCOUNT_ID)
				).then(
				);
	}

	private HapiApiSpec vanillaTransferSucceeds() {
		long initialBalance = HapiSpecSetup.getDefaultInstance().defaultBalance();

		return defaultHapiSpec("VanillaTransferSucceeds")
				.given(
						UtilVerbs.inParallel(
								cryptoCreate("payer"),
								cryptoCreate("payeeSigReq").receiverSigRequired(true),
								cryptoCreate("payeeNoSigReq")
						)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("payer", "payeeSigReq", 1_000L),
								tinyBarsFromTo("payer", "payeeNoSigReq", 2_000L)
						).via("transferTxn")
				).then(
						getAccountInfo("payer")
								.logged()
								.hasExpectedLedgerId("0x03")
								.has(accountWith().balance(initialBalance - 3_000L)),
						getAccountInfo("payeeSigReq").has(accountWith().balance(initialBalance + 1_000L)),
						getAccountDetails("payeeNoSigReq")
								.payingWith(GENESIS)
								.has(AccountDetailsAsserts.accountWith().balance(initialBalance + 2_000L).noAllowances())
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
