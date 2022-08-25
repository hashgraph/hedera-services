/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.bdd.suites.crypto;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.accountIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asAccountString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asTopicString;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.usableTxnIdNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.aaWith;
import static com.hedera.services.bdd.suites.contract.Utils.accountId;
import static com.hedera.services.bdd.suites.contract.Utils.captureOneChildCreate2MetaFor;
import static com.hedera.services.bdd.suites.contract.Utils.ocWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoTransferSuite extends HapiApiSuite {

    private static final Logger LOG = LogManager.getLogger(CryptoTransferSuite.class);
    private static final String OWNER = "owner";
    private static final String OTHER_OWNER = "otherOwner";
    private static final String SPENDER = "spender";
    private static final String PAYER = "payer";
    private static final String SENDER = "sender";
    private static final String RECEIVER = "receiver";
    private static final String OTHER_RECEIVER = "otherReceiver";
    private static final String ANOTHER_RECEIVER = "anotherReceiver";
    private static final String FUNGIBLE_TOKEN = "fungible";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungible";
    private static final String TOKEN_WITH_CUSTOM_FEE = "tokenWithCustomFee";
    private static final String ADMIN_KEY = "adminKey";
    private static final String KYC_KEY = "kycKey";
    private static final String FREEZE_KEY = "freezeKey";
    private static final String SUPPLY_KEY = "supplyKey";
    private static final String WIPE_KEY = "wipeKey";
    private static final String PAUSE_KEY = "pauseKey";
    private static final String PARTY = "party";
    private static final String COUNTERPARTY = "counterparty";
    private static final String MULTI_KEY = "multi";
    private static final String NOT_TO_BE = "notToBe";
    private static final String TOKEN = "token";
    private static final String OWNING_PARTY = "owningParty";
    private static final String VALID_TXN = "validTxn";
    private static final String UNCHECKED_TXN = "uncheckedTxn";
    private static final String PAYEE_SIG_REQ = "payeeSigReq";
    private static final String TOKENS_INVOLVED_LOG_MESSAGE =
            """
0 tokens involved,
  2 account adjustments: {} tb, ${}"
1 tokens involved,
  2 account adjustments: {} tb, ${} (~{}x pure crypto)
  3 account adjustments: {} tb, ${} (~{}x pure crypto)
  4 account adjustments: {} tb, ${} (~{}x pure crypto)
  5 account adjustments: {} tb, ${} (~{}x pure crypto)
  6 account adjustments: {} tb, ${} (~{}x pure crypto)
2 tokens involved,
  4 account adjustments: {} tb, ${} (~{}x pure crypto)
  5 account adjustments: {} tb, ${} (~{}x pure crypto)
  6 account adjustments: {} tb, ${} (~{}x pure crypto)
3 tokens involved,
  6 account adjustments: {} tb, ${} (~{}x pure crypto)
                                                          """;
    public static final String HODL_XFER = "hodlXfer";
    public static final String PAYEE_NO_SIG_REQ = "payeeNoSigReq";

    public static void main(String... args) {
        new CryptoTransferSuite().runSuiteAsync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                transferWithMissingAccountGetsInvalidAccountId(),
                complexKeyAcctPaysForOwnTransfer(),
                twoComplexKeysRequired(),
                specialAccountsBalanceCheck(),
                tokenTransferFeesScaleAsExpected(),
                okToSetInvalidPaymentHeaderForCostAnswer(),
                baseCryptoTransferFeeChargedAsExpected(),
                autoAssociationRequiresOpenSlots(),
                royaltyCollectorsCanUseAutoAssociation(),
                royaltyCollectorsCannotUseAutoAssociationWithoutOpenSlots(),
                dissociatedRoyaltyCollectorsCanUseAutoAssociation(),
                hbarAndFungibleSelfTransfersRejectedBothInPrecheckAndHandle(),
                transferToNonAccountEntitiesReturnsInvalidAccountId(),
                nftSelfTransfersRejectedBothInPrecheckAndHandle(),
                checksExpectedDecimalsForFungibleTokenTransferList(),
                allowanceTransfersWorkAsExpected(),
                allowanceTransfersWithComplexTransfersWork(),
                canUseMirrorAliasesForNonContractXfers(),
                canUseEip1014AliasesForXfers(),
                cannotTransferFromImmutableAccounts(),
                nftTransfersCannotRepeatSerialNos(),
                vanillaTransferSucceeds());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    // https://github.com/hashgraph/hedera-services/issues/2875
    private HapiApiSpec canUseMirrorAliasesForNonContractXfers() {
        final var fungibleToken = "fungibleToken";
        final var nonFungibleToken = "nonFungibleToken";
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
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(PARTY).maxAutomaticTokenAssociations(2),
                        cryptoCreate(COUNTERPARTY).maxAutomaticTokenAssociations(2),
                        tokenCreate(fungibleToken).treasury(PARTY).initialSupply(1_000_000),
                        tokenCreate(nonFungibleToken)
                                .initialSupply(0)
                                .treasury(PARTY)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(MULTI_KEY),
                        mintToken(nonFungibleToken, List.of(copyFromUtf8("Please mind the vase."))),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var registry = spec.registry();
                                    ftId.set(registry.getTokenID(fungibleToken));
                                    nftId.set(registry.getTokenID(nonFungibleToken));
                                    partyId.set(registry.getAccountID(PARTY));
                                    counterId.set(registry.getAccountID(COUNTERPARTY));
                                    partyAlias.set(
                                            ByteString.copyFrom(asSolidityAddress(partyId.get())));
                                    counterAlias.set(
                                            ByteString.copyFrom(
                                                    asSolidityAddress(counterId.get())));
                                }))
                .when(
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.setTransfers(
                                                        TransferList.newBuilder()
                                                                .addAccountAmounts(
                                                                        aaWith(
                                                                                partyAlias.get(),
                                                                                -1))
                                                                .addAccountAmounts(
                                                                        aaWith(partyId.get(), -1))
                                                                .addAccountAmounts(
                                                                        aaWith(
                                                                                counterId.get(),
                                                                                +2))))
                                .signedBy(DEFAULT_PAYER, PARTY)
                                .hasKnownStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        // Check signing requirements aren't distorted by aliases
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.setTransfers(
                                                        TransferList.newBuilder()
                                                                .addAccountAmounts(
                                                                        aaWith(
                                                                                partyAlias.get(),
                                                                                -2))
                                                                .addAccountAmounts(
                                                                        aaWith(
                                                                                counterId.get(),
                                                                                +2))))
                                .signedBy(DEFAULT_PAYER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.addTokenTransfers(
                                                        TokenTransferList.newBuilder()
                                                                .setToken(nftId.get())
                                                                .addNftTransfers(
                                                                        ocWith(
                                                                                accountId(
                                                                                        partyAlias
                                                                                                .get()),
                                                                                counterId.get(),
                                                                                1L))))
                                .signedBy(DEFAULT_PAYER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.addTokenTransfers(
                                                        TokenTransferList.newBuilder()
                                                                .setToken(ftId.get())
                                                                .addTransfers(
                                                                        aaWith(
                                                                                partyAlias.get(),
                                                                                -500))
                                                                .addTransfers(
                                                                        aaWith(
                                                                                counterAlias.get(),
                                                                                +500))))
                                .signedBy(DEFAULT_PAYER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        // Now do the actual transfers
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.setTransfers(
                                                        TransferList.newBuilder()
                                                                .addAccountAmounts(
                                                                        aaWith(
                                                                                partyAlias.get(),
                                                                                -2))
                                                                .addAccountAmounts(
                                                                        aaWith(
                                                                                counterAlias.get(),
                                                                                +2))))
                                .signedBy(DEFAULT_PAYER, PARTY)
                                .via(hbarXfer),
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.addTokenTransfers(
                                                        TokenTransferList.newBuilder()
                                                                .setToken(nftId.get())
                                                                .addNftTransfers(
                                                                        ocWith(
                                                                                accountId(
                                                                                        partyAlias
                                                                                                .get()),
                                                                                accountId(
                                                                                        counterAlias
                                                                                                .get()),
                                                                                1L))))
                                .signedBy(DEFAULT_PAYER, PARTY)
                                .via(nftXfer),
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.addTokenTransfers(
                                                        TokenTransferList.newBuilder()
                                                                .setToken(ftId.get())
                                                                .addTransfers(
                                                                        aaWith(
                                                                                partyAlias.get(),
                                                                                -500))
                                                                .addTransfers(
                                                                        aaWith(
                                                                                counterAlias.get(),
                                                                                +500))))
                                .signedBy(DEFAULT_PAYER, PARTY)
                                .via(ftXfer))
                .then(
                        getTxnRecord(hbarXfer).logged(),
                        getTxnRecord(nftXfer).logged(),
                        getTxnRecord(ftXfer).logged());
    }

    @SuppressWarnings("java:S5669")
    private HapiApiSpec canUseEip1014AliasesForXfers() {
        final var partyCreation2 = "partyCreation2";
        final var counterCreation2 = "counterCreation2";
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

        final byte[] salt =
                unhex("aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011aabbccddeeff0011");
        final byte[] otherSalt =
                unhex("aabbccddee880011aabbccddee880011aabbccddee880011aabbccddee880011");

        return defaultHapiSpec("CanUseEip1014AliasesForXfers")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        uploadInitCode(contract),
                        contractCreate(contract).adminKey(MULTI_KEY).payingWith(GENESIS),
                        contractCall(contract, "buildDonor", salt)
                                .sending(1000)
                                .payingWith(GENESIS)
                                .gas(2_000_000L)
                                .via(partyCreation2),
                        captureOneChildCreate2MetaFor(
                                PARTY, partyCreation2, partyMirrorAddr, partyAliasAddr),
                        contractCall(contract, "buildDonor", otherSalt)
                                .sending(1000)
                                .payingWith(GENESIS)
                                .gas(2_000_000L)
                                .via(counterCreation2),
                        captureOneChildCreate2MetaFor(
                                COUNTERPARTY,
                                counterCreation2,
                                counterMirrorAddr,
                                counterAliasAddr),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1_000_000),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(MULTI_KEY),
                        mintToken(
                                NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8("Please mind the vase."))),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var registry = spec.registry();
                                    ftId.set(registry.getTokenID(FUNGIBLE_TOKEN));
                                    nftId.set(registry.getTokenID(NON_FUNGIBLE_TOKEN));
                                    partyId.set(
                                            accountIdFromHexedMirrorAddress(partyMirrorAddr.get()));
                                    partyLiteral.set(asAccountString(partyId.get()));
                                    counterId.set(
                                            accountIdFromHexedMirrorAddress(
                                                    counterMirrorAddr.get()));
                                    counterLiteral.set(asAccountString(counterId.get()));
                                }))
                .when(
                        sourcing(
                                () ->
                                        tokenAssociate(
                                                        partyLiteral.get(),
                                                        List.of(FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN))
                                                .signedBy(DEFAULT_PAYER, MULTI_KEY)),
                        sourcing(
                                () ->
                                        tokenAssociate(
                                                        counterLiteral.get(),
                                                        List.of(FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN))
                                                .signedBy(DEFAULT_PAYER, MULTI_KEY)),
                        sourcing(() -> getContractInfo(partyLiteral.get()).logged()),
                        sourcing(
                                () ->
                                        cryptoTransfer(
                                                        moving(500_000, FUNGIBLE_TOKEN)
                                                                .between(
                                                                        TOKEN_TREASURY,
                                                                        partyLiteral.get()),
                                                        movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                                                .between(
                                                                        TOKEN_TREASURY,
                                                                        partyLiteral.get()))
                                                .signedBy(DEFAULT_PAYER, TOKEN_TREASURY)),
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.setTransfers(
                                                        TransferList.newBuilder()
                                                                .addAccountAmounts(
                                                                        aaWith(
                                                                                partyAliasAddr
                                                                                        .get(),
                                                                                -1))
                                                                .addAccountAmounts(
                                                                        aaWith(partyId.get(), -1))
                                                                .addAccountAmounts(
                                                                        aaWith(
                                                                                counterId.get(),
                                                                                +2))))
                                .signedBy(DEFAULT_PAYER, MULTI_KEY)
                                .hasKnownStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        // Check signing requirements aren't distorted by aliases
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.setTransfers(
                                                        TransferList.newBuilder()
                                                                .addAccountAmounts(
                                                                        aaWith(
                                                                                partyAliasAddr
                                                                                        .get(),
                                                                                -2))
                                                                .addAccountAmounts(
                                                                        aaWith(
                                                                                counterAliasAddr
                                                                                        .get(),
                                                                                +2))))
                                .signedBy(DEFAULT_PAYER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.addTokenTransfers(
                                                        TokenTransferList.newBuilder()
                                                                .setToken(nftId.get())
                                                                .addNftTransfers(
                                                                        ocWith(
                                                                                accountId(
                                                                                        partyAliasAddr
                                                                                                .get()),
                                                                                counterId.get(),
                                                                                1L))))
                                .signedBy(DEFAULT_PAYER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.addTokenTransfers(
                                                        TokenTransferList.newBuilder()
                                                                .setToken(ftId.get())
                                                                .addTransfers(
                                                                        aaWith(
                                                                                partyAliasAddr
                                                                                        .get(),
                                                                                -500))
                                                                .addTransfers(
                                                                        aaWith(
                                                                                counterAliasAddr
                                                                                        .get(),
                                                                                +500))))
                                .signedBy(DEFAULT_PAYER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        // Now do the actual transfers
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.setTransfers(
                                                        TransferList.newBuilder()
                                                                .addAccountAmounts(
                                                                        aaWith(
                                                                                partyAliasAddr
                                                                                        .get(),
                                                                                -2))
                                                                .addAccountAmounts(
                                                                        aaWith(
                                                                                counterAliasAddr
                                                                                        .get(),
                                                                                +2))))
                                .signedBy(DEFAULT_PAYER, MULTI_KEY)
                                .via(hbarXfer),
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.addTokenTransfers(
                                                        TokenTransferList.newBuilder()
                                                                .setToken(nftId.get())
                                                                .addNftTransfers(
                                                                        ocWith(
                                                                                accountId(
                                                                                        partyAliasAddr
                                                                                                .get()),
                                                                                accountId(
                                                                                        counterAliasAddr
                                                                                                .get()),
                                                                                1L))))
                                .signedBy(DEFAULT_PAYER, MULTI_KEY)
                                .via(nftXfer),
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.addTokenTransfers(
                                                        TokenTransferList.newBuilder()
                                                                .setToken(ftId.get())
                                                                .addTransfers(
                                                                        aaWith(
                                                                                partyAliasAddr
                                                                                        .get(),
                                                                                -500))
                                                                .addTransfers(
                                                                        aaWith(
                                                                                counterAliasAddr
                                                                                        .get(),
                                                                                +500))))
                                .signedBy(DEFAULT_PAYER, MULTI_KEY)
                                .via(ftXfer))
                .then(
                        sourcing(
                                () ->
                                        getTxnRecord(hbarXfer)
                                                .hasPriority(
                                                        recordWith()
                                                                .transfers(
                                                                        including(
                                                                                tinyBarsFromTo(
                                                                                        partyLiteral
                                                                                                .get(),
                                                                                        counterLiteral
                                                                                                .get(),
                                                                                        2))))),
                        sourcing(
                                () ->
                                        getTxnRecord(nftXfer)
                                                .hasPriority(
                                                        recordWith()
                                                                .tokenTransfers(
                                                                        includingNonfungibleMovement(
                                                                                movingUnique(
                                                                                                NON_FUNGIBLE_TOKEN,
                                                                                                1L)
                                                                                        .between(
                                                                                                partyLiteral
                                                                                                        .get(),
                                                                                                counterLiteral
                                                                                                        .get()))))),
                        sourcing(
                                () ->
                                        getTxnRecord(ftXfer)
                                                .hasPriority(
                                                        recordWith()
                                                                .tokenTransfers(
                                                                        includingFungibleMovement(
                                                                                moving(
                                                                                                500,
                                                                                                FUNGIBLE_TOKEN)
                                                                                        .between(
                                                                                                partyLiteral
                                                                                                        .get(),
                                                                                                counterLiteral
                                                                                                        .get()))))));
    }

    private HapiApiSpec cannotTransferFromImmutableAccounts() {
        final var contract = "PayableConstructor";
        final var multiKey = "swiss";

        return defaultHapiSpec("CannotTransferFromImmutableAccounts")
                .given(
                        newKeyNamed(multiKey),
                        uploadInitCode(contract),
                        contractCreate(contract).balance(ONE_HBAR).immutable().payingWith(GENESIS))
                .when()
                .then(
                        // Even the treasury cannot withdraw from an immutable contract
                        cryptoTransfer(tinyBarsFromTo(contract, FUNDING, ONE_HBAR))
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .fee(ONE_HBAR)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        // Even the treasury cannot withdraw staking funds
                        cryptoTransfer(tinyBarsFromTo(STAKING_REWARD, FUNDING, ONE_HBAR))
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .fee(ONE_HBAR)
                                .hasKnownStatus(INVALID_ACCOUNT_ID),
                        cryptoTransfer(tinyBarsFromTo(NODE_REWARD, FUNDING, ONE_HBAR))
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .fee(ONE_HBAR)
                                .hasKnownStatus(INVALID_ACCOUNT_ID),
                        // Immutable accounts cannot be updated or deleted
                        cryptoUpdate(STAKING_REWARD)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .fee(ONE_HBAR)
                                .hasKnownStatus(INVALID_ACCOUNT_ID),
                        cryptoDelete(STAKING_REWARD)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .fee(ONE_HBAR)
                                .hasKnownStatus(INVALID_ACCOUNT_ID),
                        // Immutable accounts cannot serve any role for tokens
                        tokenCreate(TOKEN).adminKey(multiKey),
                        tokenAssociate(NODE_REWARD, TOKEN)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .fee(ONE_HBAR)
                                .hasKnownStatus(INVALID_ACCOUNT_ID),
                        tokenUpdate(TOKEN)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS, multiKey)
                                .fee(ONE_HBAR)
                                .treasury(STAKING_REWARD)
                                .hasKnownStatus(INVALID_ACCOUNT_ID),
                        tokenCreate(NOT_TO_BE)
                                .treasury(STAKING_REWARD)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .fee(ONE_HBAR)
                                .hasKnownStatus(INVALID_ACCOUNT_ID),
                        tokenCreate(NOT_TO_BE)
                                .autoRenewAccount(NODE_REWARD)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .fee(ONE_HBAR)
                                .hasKnownStatus(INVALID_AUTORENEW_ACCOUNT),
                        tokenCreate(NOT_TO_BE)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .fee(ONE_HBAR)
                                .withCustom(fixedHbarFee(5 * ONE_HBAR, STAKING_REWARD))
                                .hasKnownStatus(INVALID_CUSTOM_FEE_COLLECTOR),
                        // Immutable accounts cannot be topic auto-renew accounts
                        createTopic(NOT_TO_BE)
                                .autoRenewAccountId(NODE_REWARD)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .fee(ONE_HBAR)
                                .hasKnownStatus(INVALID_AUTORENEW_ACCOUNT),
                        // Immutable accounts cannot be schedule transaction payers
                        scheduleCreate(
                                        NOT_TO_BE,
                                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1)))
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .fee(ONE_HBAR)
                                .designatingPayer(STAKING_REWARD)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasKnownStatus(INVALID_ACCOUNT_ID),
                        // Immutable accounts cannot approve or adjust allowances
                        cryptoApproveAllowance()
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .fee(ONE_HBAR)
                                .addCryptoAllowance(NODE_REWARD, FUNDING, 100L)
                                .hasKnownStatus(INVALID_ALLOWANCE_OWNER_ID));
    }

    private HapiApiSpec allowanceTransfersWithComplexTransfersWork() {
        return defaultHapiSpec("AllowanceTransfersWithComplexTransfersWork")
                .given(
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(OTHER_OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(0L),
                        cryptoCreate(OTHER_RECEIVER).balance(ONE_HBAR),
                        cryptoCreate(ANOTHER_RECEIVER).balance(0L),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(10000)
                                .initialSupply(5000)
                                .adminKey(ADMIN_KEY)
                                .kycKey(KYC_KEY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(SUPPLY_KEY)
                                .adminKey(ADMIN_KEY)
                                .kycKey(KYC_KEY)
                                .initialSupply(0L),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c"),
                                        ByteString.copyFromUtf8("d"),
                                        ByteString.copyFromUtf8("e"))))
                .when(
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(OTHER_OWNER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(RECEIVER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(SPENDER, FUNGIBLE_TOKEN),
                        tokenAssociate(ANOTHER_RECEIVER, FUNGIBLE_TOKEN),
                        grantTokenKyc(FUNGIBLE_TOKEN, OWNER),
                        grantTokenKyc(FUNGIBLE_TOKEN, OTHER_OWNER),
                        grantTokenKyc(FUNGIBLE_TOKEN, RECEIVER),
                        grantTokenKyc(FUNGIBLE_TOKEN, ANOTHER_RECEIVER),
                        grantTokenKyc(FUNGIBLE_TOKEN, SPENDER),
                        grantTokenKyc(NON_FUNGIBLE_TOKEN, OWNER),
                        grantTokenKyc(NON_FUNGIBLE_TOKEN, OTHER_OWNER),
                        grantTokenKyc(NON_FUNGIBLE_TOKEN, RECEIVER),
                        cryptoTransfer(
                                moving(100, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SPENDER),
                                moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER),
                                movingUnique(NON_FUNGIBLE_TOKEN, 1, 2)
                                        .between(TOKEN_TREASURY, OWNER),
                                moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OTHER_OWNER),
                                movingUnique(NON_FUNGIBLE_TOKEN, 3, 4)
                                        .between(TOKEN_TREASURY, OTHER_OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 10 * ONE_HBAR)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 500)
                                .addNftAllowance(
                                        OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L, 2L))
                                .fee(ONE_HUNDRED_HBARS),
                        cryptoApproveAllowance()
                                .payingWith(OTHER_OWNER)
                                .addCryptoAllowance(OTHER_OWNER, SPENDER, 5 * ONE_HBAR)
                                .addTokenAllowance(OTHER_OWNER, FUNGIBLE_TOKEN, SPENDER, 100)
                                .addNftAllowance(
                                        OTHER_OWNER, NON_FUNGIBLE_TOKEN, SPENDER, true, List.of(3L))
                                .fee(ONE_HUNDRED_HBARS))
                .then(
                        cryptoTransfer(
                                        movingHbar(ONE_HBAR).between(SPENDER, RECEIVER),
                                        movingHbar(ONE_HBAR)
                                                .between(OTHER_RECEIVER, ANOTHER_RECEIVER),
                                        movingHbar(ONE_HBAR).between(OWNER, RECEIVER),
                                        movingHbar(ONE_HBAR).between(OTHER_OWNER, RECEIVER),
                                        movingHbarWithAllowance(ONE_HBAR).between(OWNER, RECEIVER),
                                        movingHbarWithAllowance(ONE_HBAR)
                                                .between(OTHER_OWNER, RECEIVER),
                                        moving(50, FUNGIBLE_TOKEN)
                                                .between(RECEIVER, ANOTHER_RECEIVER),
                                        moving(50, FUNGIBLE_TOKEN).between(SPENDER, RECEIVER),
                                        moving(50, FUNGIBLE_TOKEN).between(OWNER, RECEIVER),
                                        moving(15, FUNGIBLE_TOKEN).between(OTHER_OWNER, RECEIVER),
                                        movingWithAllowance(30, FUNGIBLE_TOKEN)
                                                .between(OWNER, RECEIVER),
                                        movingWithAllowance(10, FUNGIBLE_TOKEN)
                                                .between(OTHER_OWNER, RECEIVER),
                                        movingWithAllowance(5, FUNGIBLE_TOKEN)
                                                .between(OTHER_OWNER, OWNER),
                                        movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                                .between(OWNER, RECEIVER),
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 2L)
                                                .between(OWNER, RECEIVER),
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 4L)
                                                .between(OTHER_OWNER, RECEIVER),
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 3L)
                                                .between(OTHER_OWNER, RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER, OWNER, OTHER_RECEIVER, OTHER_OWNER)
                                .via("complexAllowanceTransfer"),
                        getTxnRecord("complexAllowanceTransfer").logged(),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .hasToken(relationshipWith(FUNGIBLE_TOKEN).balance(925))
                                .hasToken(relationshipWith(NON_FUNGIBLE_TOKEN).balance(0))
                                .has(
                                        AccountDetailsAsserts.accountWith()
                                                .balanceLessThan(98 * ONE_HBAR)
                                                .cryptoAllowancesContaining(SPENDER, 9 * ONE_HBAR)
                                                .tokenAllowancesContaining(
                                                        FUNGIBLE_TOKEN, SPENDER, 475)),
                        getAccountDetails(OTHER_OWNER)
                                .payingWith(GENESIS)
                                .hasToken(relationshipWith(FUNGIBLE_TOKEN).balance(970))
                                .hasToken(relationshipWith(NON_FUNGIBLE_TOKEN).balance(0))
                                .has(
                                        AccountDetailsAsserts.accountWith()
                                                .balanceLessThan(98 * ONE_HBAR)
                                                .cryptoAllowancesContaining(SPENDER, 4 * ONE_HBAR)
                                                .tokenAllowancesContaining(
                                                        FUNGIBLE_TOKEN, SPENDER, 85)
                                                .nftApprovedAllowancesContaining(
                                                        NON_FUNGIBLE_TOKEN, SPENDER)),
                        getAccountInfo(RECEIVER)
                                .hasToken(relationshipWith(FUNGIBLE_TOKEN).balance(105))
                                .hasToken(relationshipWith(NON_FUNGIBLE_TOKEN).balance(4))
                                .has(accountWith().balance(5 * ONE_HBAR)),
                        getAccountInfo(ANOTHER_RECEIVER)
                                .hasToken(relationshipWith(FUNGIBLE_TOKEN).balance(50))
                                .has(accountWith().balance(ONE_HBAR)));
    }

    private HapiApiSpec allowanceTransfersWorkAsExpected() {
        return defaultHapiSpec("AllowanceTransfersWorkAsExpected")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .overridingProps(
                                        Map.of(
                                                "hedera.allowances.maxTransactionLimit", "20",
                                                "hedera.allowances.maxAccountLimit", "100")),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(PAUSE_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(WIPE_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(OTHER_RECEIVER)
                                .balance(ONE_HBAR)
                                .maxAutomaticTokenAssociations(1),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(10000)
                                .initialSupply(5000)
                                .adminKey(ADMIN_KEY)
                                .pauseKey(PAUSE_KEY)
                                .kycKey(KYC_KEY)
                                .freezeKey(FREEZE_KEY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(SUPPLY_KEY)
                                .adminKey(ADMIN_KEY)
                                .freezeKey(FREEZE_KEY)
                                .wipeKey(WIPE_KEY)
                                .pauseKey(PAUSE_KEY)
                                .initialSupply(0L),
                        tokenCreate(TOKEN_WITH_CUSTOM_FEE)
                                .treasury(TOKEN_TREASURY)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(1000)
                                .maxSupply(5000)
                                .adminKey(ADMIN_KEY)
                                .withCustom(fixedHtsFee(10, "0.0.0", TOKEN_TREASURY)),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c"),
                                        ByteString.copyFromUtf8("d"),
                                        ByteString.copyFromUtf8("e"),
                                        ByteString.copyFromUtf8("f"))))
                .when(
                        tokenAssociate(
                                OWNER, FUNGIBLE_TOKEN, NON_FUNGIBLE_TOKEN, TOKEN_WITH_CUSTOM_FEE),
                        tokenAssociate(
                                RECEIVER,
                                FUNGIBLE_TOKEN,
                                NON_FUNGIBLE_TOKEN,
                                TOKEN_WITH_CUSTOM_FEE),
                        grantTokenKyc(FUNGIBLE_TOKEN, OWNER),
                        grantTokenKyc(FUNGIBLE_TOKEN, RECEIVER),
                        cryptoTransfer(
                                moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER),
                                moving(15, TOKEN_WITH_CUSTOM_FEE).between(TOKEN_TREASURY, OWNER),
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L, 4L, 5L, 6L)
                                        .between(TOKEN_TREASURY, OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 10 * ONE_HBAR)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 1500)
                                .addTokenAllowance(OWNER, TOKEN_WITH_CUSTOM_FEE, SPENDER, 100)
                                .addNftAllowance(
                                        OWNER,
                                        NON_FUNGIBLE_TOKEN,
                                        SPENDER,
                                        false,
                                        List.of(1L, 2L, 3L, 4L, 6L))
                                .fee(ONE_HUNDRED_HBARS))
                .then(
                        cryptoTransfer(
                                        movingWithAllowance(10, TOKEN_WITH_CUSTOM_FEE)
                                                .between(OWNER, RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER)
                                .fee(ONE_HBAR)
                                .hasKnownStatus(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE),
                        cryptoTransfer(
                                        movingWithAllowance(100, FUNGIBLE_TOKEN)
                                                .between(OWNER, OWNER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER)
                                .dontFullyAggregateTokenTransfers()
                                .hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        cryptoTransfer(
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 3)
                                                .between(OWNER, OTHER_RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER),
                        cryptoTransfer(
                                        movingWithAllowance(100, FUNGIBLE_TOKEN)
                                                .between(OWNER, OTHER_RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER)
                                .hasKnownStatus(NO_REMAINING_AUTOMATIC_ASSOCIATIONS),
                        cryptoUpdate(OTHER_RECEIVER)
                                .receiverSigRequired(true)
                                .maxAutomaticAssociations(2),
                        cryptoTransfer(
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 4)
                                                .between(OWNER, OTHER_RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoTransfer(
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 4)
                                                .between(OWNER, OTHER_RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER, OTHER_RECEIVER),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 6).between(OWNER, RECEIVER)),
                        cryptoTransfer(
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 6)
                                                .between(OWNER, RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                        cryptoTransfer(
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 6)
                                                .between(RECEIVER, OWNER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 6).between(RECEIVER, OWNER)),
                        cryptoTransfer(
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 6)
                                                .between(OWNER, RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 6).between(OWNER, RECEIVER)),
                        tokenAssociate(OTHER_RECEIVER, FUNGIBLE_TOKEN),
                        grantTokenKyc(FUNGIBLE_TOKEN, OTHER_RECEIVER),
                        cryptoTransfer(
                                        movingWithAllowance(1100, FUNGIBLE_TOKEN)
                                                .between(OWNER, OTHER_RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER, OTHER_RECEIVER)
                                .hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE),
                        cryptoTransfer(allowanceTinyBarsFromTo(OWNER, RECEIVER, 5 * ONE_HBAR))
                                .payingWith(DEFAULT_PAYER)
                                .signedBy(DEFAULT_PAYER)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                        tokenPause(FUNGIBLE_TOKEN),
                        cryptoTransfer(
                                        movingWithAllowance(50, FUNGIBLE_TOKEN)
                                                .between(OWNER, RECEIVER),
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1)
                                                .between(OWNER, RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER)
                                .hasKnownStatus(TOKEN_IS_PAUSED),
                        tokenUnpause(FUNGIBLE_TOKEN),
                        tokenFreeze(FUNGIBLE_TOKEN, OWNER),
                        cryptoTransfer(
                                        movingWithAllowance(50, FUNGIBLE_TOKEN)
                                                .between(OWNER, RECEIVER),
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1)
                                                .between(OWNER, RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER)
                                .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
                        tokenUnfreeze(FUNGIBLE_TOKEN, OWNER),
                        revokeTokenKyc(FUNGIBLE_TOKEN, RECEIVER),
                        cryptoTransfer(
                                        movingWithAllowance(50, FUNGIBLE_TOKEN)
                                                .between(OWNER, RECEIVER),
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1)
                                                .between(OWNER, RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER)
                                .hasKnownStatus(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN),
                        grantTokenKyc(FUNGIBLE_TOKEN, RECEIVER),
                        cryptoTransfer(
                                        allowanceTinyBarsFromTo(OWNER, RECEIVER, 5 * ONE_HBAR),
                                        tinyBarsFromTo(SPENDER, RECEIVER, ONE_HBAR))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER),
                        cryptoTransfer(
                                        movingWithAllowance(50, FUNGIBLE_TOKEN)
                                                .between(OWNER, RECEIVER),
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1)
                                                .between(OWNER, RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER),
                        cryptoTransfer(allowanceTinyBarsFromTo(OWNER, RECEIVER, 5 * ONE_HBAR + 1))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER)
                                .hasKnownStatus(AMOUNT_EXCEEDS_ALLOWANCE),
                        cryptoTransfer(
                                        movingWithAllowance(50, FUNGIBLE_TOKEN)
                                                .between(OWNER, RECEIVER),
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 5)
                                                .between(OWNER, RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(
                                        AccountDetailsAsserts.accountWith()
                                                .tokenAllowancesContaining(
                                                        FUNGIBLE_TOKEN, SPENDER, 1450))
                                .hasToken(relationshipWith(FUNGIBLE_TOKEN).balance(950L)),
                        cryptoTransfer(moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)),
                        cryptoTransfer(
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 2L)
                                                .between(OWNER, RECEIVER),
                                        movingWithAllowance(1451, FUNGIBLE_TOKEN)
                                                .between(OWNER, RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER)
                                .hasKnownStatus(AMOUNT_EXCEEDS_ALLOWANCE),
                        getAccountInfo(OWNER)
                                .hasToken(relationshipWith(NON_FUNGIBLE_TOKEN).balance(2)),
                        cryptoTransfer(allowanceTinyBarsFromTo(OWNER, RECEIVER, 5 * ONE_HBAR))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER),
                        cryptoTransfer(
                                        movingWithAllowance(50, FUNGIBLE_TOKEN)
                                                .between(OWNER, RECEIVER),
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 2L)
                                                .between(OWNER, RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER),
                        cryptoTransfer(allowanceTinyBarsFromTo(OWNER, RECEIVER, 5 * ONE_HBAR))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 2L).between(RECEIVER, OWNER)),
                        cryptoTransfer(
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 2L)
                                                .between(OWNER, RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(
                                        OWNER, NON_FUNGIBLE_TOKEN, SPENDER, true, List.of())
                                .fee(ONE_HUNDRED_HBARS),
                        cryptoTransfer(
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 2L)
                                                .between(OWNER, RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 2L).between(RECEIVER, OWNER)),
                        cryptoTransfer(
                                        movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 2L)
                                                .between(OWNER, RECEIVER))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(
                                        AccountDetailsAsserts.accountWith()
                                                .cryptoAllowancesCount(0)
                                                .tokenAllowancesContaining(
                                                        FUNGIBLE_TOKEN, SPENDER, 1400)
                                                .nftApprovedAllowancesContaining(
                                                        NON_FUNGIBLE_TOKEN, SPENDER)));
    }

    private HapiApiSpec checksExpectedDecimalsForFungibleTokenTransferList() {
        return defaultHapiSpec("checksExpectedDecimalsForFungibleTokenTransferList")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNING_PARTY).maxAutomaticTokenAssociations(123),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .decimals(2)
                                .initialSupply(1234)
                                .via("tokenCreate"),
                        getTxnRecord("tokenCreate")
                                .hasNewTokenAssociation(FUNGIBLE_TOKEN, TOKEN_TREASURY),
                        cryptoTransfer(
                                        moving(100, FUNGIBLE_TOKEN)
                                                .between(TOKEN_TREASURY, OWNING_PARTY))
                                .via("initialXfer"),
                        getTxnRecord("initialXfer")
                                .hasNewTokenAssociation(FUNGIBLE_TOKEN, OWNING_PARTY))
                .when(
                        getAccountInfo(OWNING_PARTY).savingSnapshot(OWNING_PARTY),
                        cryptoTransfer(
                                        movingWithDecimals(10, FUNGIBLE_TOKEN, 4)
                                                .betweenWithDecimals(TOKEN_TREASURY, OWNING_PARTY))
                                .signedBy(DEFAULT_PAYER, OWNING_PARTY, TOKEN_TREASURY)
                                .hasKnownStatus(UNEXPECTED_TOKEN_DECIMALS)
                                .via("failedTxn"),
                        cryptoTransfer(
                                        movingWithDecimals(20, FUNGIBLE_TOKEN, 2)
                                                .betweenWithDecimals(TOKEN_TREASURY, OWNING_PARTY))
                                .signedBy(DEFAULT_PAYER, OWNING_PARTY, TOKEN_TREASURY)
                                .hasKnownStatus(SUCCESS)
                                .via(VALID_TXN),
                        usableTxnIdNamed(UNCHECKED_TXN).payerId(DEFAULT_PAYER),
                        uncheckedSubmit(
                                        cryptoTransfer(
                                                        movingWithDecimals(10, FUNGIBLE_TOKEN, 4)
                                                                .betweenWithDecimals(
                                                                        TOKEN_TREASURY,
                                                                        OWNING_PARTY))
                                                .signedBy(
                                                        DEFAULT_PAYER, OWNING_PARTY, TOKEN_TREASURY)
                                                .txnId(UNCHECKED_TXN))
                                .payingWith(GENESIS))
                .then(
                        sleepFor(5_000),
                        getReceipt(UNCHECKED_TXN)
                                .hasPriorityStatus(UNEXPECTED_TOKEN_DECIMALS)
                                .logged(),
                        getReceipt(VALID_TXN).hasPriorityStatus(SUCCESS),
                        getTxnRecord(VALID_TXN).logged(),
                        getAccountInfo(OWNING_PARTY)
                                .hasAlreadyUsedAutomaticAssociations(1)
                                .hasToken(relationshipWith(FUNGIBLE_TOKEN).balance(120))
                                .logged());
    }

    private HapiApiSpec nftTransfersCannotRepeatSerialNos() {
        final var aParty = "aParty";
        final var bParty = "bParty";
        final var cParty = "cParty";
        final var dParty = "dParty";
        final var multipurpose = MULTI_KEY;
        final var nftType = "nftType";
        final var hotTxn = "hotTxn";
        final var mintTxn = "mintTxn";

        return defaultHapiSpec("NftTransfersCannotRepeatSerialNos")
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
                        getTxnRecord(mintTxn).logged())
                .when(cryptoTransfer(movingUnique(nftType, 1L).between(TOKEN_TREASURY, aParty)))
                .then(
                        cryptoTransfer(
                                        (spec, b) -> {
                                            final var registry = spec.registry();
                                            final var aId = registry.getAccountID(aParty);
                                            final var bId = registry.getAccountID(bParty);
                                            final var cId = registry.getAccountID(cParty);
                                            final var dId = registry.getAccountID(dParty);
                                            b.addTokenTransfers(
                                                    TokenTransferList.newBuilder()
                                                            .setToken(registry.getTokenID(nftType))
                                                            .addNftTransfers(ocWith(aId, bId, 1))
                                                            .addNftTransfers(ocWith(bId, cId, 1))
                                                            .addNftTransfers(ocWith(cId, dId, 1)));
                                        })
                                .via(hotTxn)
                                .signedBy(DEFAULT_PAYER, aParty, bParty, cParty)
                                .hasPrecheck(INVALID_ACCOUNT_AMOUNTS));
    }

    private HapiApiSpec nftSelfTransfersRejectedBothInPrecheckAndHandle() {
        final var owningParty = OWNING_PARTY;
        final var multipurpose = MULTI_KEY;
        final var nftType = "nftType";
        final var uncheckedTxn = UNCHECKED_TXN;

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
                        mintToken(
                                nftType,
                                List.of(
                                        copyFromUtf8("We"),
                                        copyFromUtf8("are"),
                                        copyFromUtf8("the"))),
                        cryptoTransfer(
                                movingUnique(nftType, 1L, 2L).between(TOKEN_TREASURY, owningParty)))
                .when(
                        getAccountInfo(owningParty).savingSnapshot(owningParty),
                        cryptoTransfer(movingUnique(nftType, 1L).between(owningParty, owningParty))
                                .signedBy(DEFAULT_PAYER, owningParty)
                                .hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        usableTxnIdNamed(uncheckedTxn).payerId(DEFAULT_PAYER),
                        uncheckedSubmit(
                                        cryptoTransfer(
                                                        movingUnique(nftType, 1L)
                                                                .between(owningParty, owningParty))
                                                .signedBy(DEFAULT_PAYER, owningParty)
                                                .txnId(uncheckedTxn))
                                .payingWith(GENESIS))
                .then(
                        sleepFor(2_000),
                        getReceipt(uncheckedTxn)
                                .hasPriorityStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        getAccountInfo(owningParty)
                                .has(accountWith().noChangesFromSnapshot(owningParty)));
    }

    private HapiApiSpec hbarAndFungibleSelfTransfersRejectedBothInPrecheckAndHandle() {
        final var uncheckedHbarTxn = "uncheckedHbarTxn";
        final var uncheckedFtTxn = "uncheckedFtTxn";

        return defaultHapiSpec("HbarAndFungibleSelfTransfersRejectedBothInPrecheckAndHandle")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(OWNING_PARTY).maxAutomaticTokenAssociations(123),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1234),
                        cryptoTransfer(
                                moving(100, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNING_PARTY)))
                .when(
                        getAccountInfo(OWNING_PARTY).savingSnapshot(OWNING_PARTY),
                        cryptoTransfer(tinyBarsFromTo(OWNING_PARTY, OWNING_PARTY, 1))
                                .signedBy(DEFAULT_PAYER, OWNING_PARTY)
                                .hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        cryptoTransfer(
                                        moving(1, FUNGIBLE_TOKEN)
                                                .between(OWNING_PARTY, OWNING_PARTY))
                                .signedBy(DEFAULT_PAYER, OWNING_PARTY)
                                .dontFullyAggregateTokenTransfers()
                                .hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        /* And bypassing precheck */
                        usableTxnIdNamed(uncheckedHbarTxn).payerId(DEFAULT_PAYER),
                        usableTxnIdNamed(uncheckedFtTxn).payerId(DEFAULT_PAYER),
                        uncheckedSubmit(
                                        cryptoTransfer(
                                                        tinyBarsFromTo(
                                                                OWNING_PARTY, OWNING_PARTY, 1))
                                                .signedBy(DEFAULT_PAYER, OWNING_PARTY)
                                                .txnId(uncheckedHbarTxn))
                                .payingWith(GENESIS),
                        uncheckedSubmit(
                                        cryptoTransfer(
                                                        moving(1, FUNGIBLE_TOKEN)
                                                                .between(
                                                                        OWNING_PARTY, OWNING_PARTY))
                                                .signedBy(DEFAULT_PAYER, OWNING_PARTY)
                                                .dontFullyAggregateTokenTransfers()
                                                .txnId(uncheckedFtTxn))
                                .payingWith(GENESIS))
                .then(
                        sleepFor(5_000),
                        getReceipt(uncheckedHbarTxn)
                                .hasPriorityStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        getReceipt(uncheckedFtTxn)
                                .hasPriorityStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
                        getAccountInfo(OWNING_PARTY)
                                .has(accountWith().noChangesFromSnapshot(OWNING_PARTY)));
    }

    private HapiApiSpec dissociatedRoyaltyCollectorsCanUseAutoAssociation() {
        final var commonWithCustomFees = "commonWithCustomFees";
        final var fractionalCollector = "fractionalCollector";
        final var selfDenominatedCollector = "selfDenominatedCollector";
        final var plentyOfSlots = 10;

        return defaultHapiSpec("RoyaltyCollectorsCanUseAutoAssociation")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(fractionalCollector)
                                .maxAutomaticTokenAssociations(plentyOfSlots),
                        cryptoCreate(selfDenominatedCollector)
                                .maxAutomaticTokenAssociations(plentyOfSlots),
                        cryptoCreate(PARTY).maxAutomaticTokenAssociations(plentyOfSlots),
                        cryptoCreate(COUNTERPARTY).maxAutomaticTokenAssociations(plentyOfSlots),
                        newKeyNamed(MULTI_KEY),
                        getAccountInfo(PARTY).savingSnapshot(PARTY),
                        getAccountInfo(COUNTERPARTY).savingSnapshot(COUNTERPARTY),
                        getAccountInfo(fractionalCollector).savingSnapshot(fractionalCollector),
                        getAccountInfo(selfDenominatedCollector)
                                .savingSnapshot(selfDenominatedCollector))
                .when(
                        tokenCreate(commonWithCustomFees)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .withCustom(
                                        fractionalFee(
                                                1,
                                                10,
                                                0,
                                                OptionalLong.empty(),
                                                fractionalCollector))
                                .withCustom(fixedHtsFee(5, "0.0.0", selfDenominatedCollector))
                                .initialSupply(Long.MAX_VALUE)
                                .signedBy(
                                        DEFAULT_PAYER,
                                        TOKEN_TREASURY,
                                        fractionalCollector,
                                        selfDenominatedCollector),
                        cryptoTransfer(
                                moving(1_000_000, commonWithCustomFees)
                                        .between(TOKEN_TREASURY, PARTY)),
                        tokenDissociate(fractionalCollector, commonWithCustomFees),
                        tokenDissociate(selfDenominatedCollector, commonWithCustomFees))
                .then(
                        cryptoTransfer(
                                        moving(1000, commonWithCustomFees)
                                                .between(PARTY, COUNTERPARTY))
                                .fee(ONE_HBAR)
                                .via(HODL_XFER),
                        getTxnRecord(HODL_XFER)
                                .hasPriority(
                                        recordWith()
                                                .autoAssociated(
                                                        accountTokenPairsInAnyOrder(
                                                                List.of(
                                                                        /* The counterparty auto-associates to the fungible type */
                                                                        Pair.of(
                                                                                COUNTERPARTY,
                                                                                commonWithCustomFees),
                                                                        /* Both royalty collectors re-auto-associate */
                                                                        Pair.of(
                                                                                fractionalCollector,
                                                                                commonWithCustomFees),
                                                                        Pair.of(
                                                                                selfDenominatedCollector,
                                                                                commonWithCustomFees))))),
                        getAccountInfo(fractionalCollector)
                                .has(
                                        accountWith()
                                                .newAssociationsFromSnapshot(
                                                        PARTY,
                                                        List.of(
                                                                relationshipWith(
                                                                                commonWithCustomFees)
                                                                        .balance(100)))),
                        getAccountInfo(selfDenominatedCollector)
                                .has(
                                        accountWith()
                                                .newAssociationsFromSnapshot(
                                                        PARTY,
                                                        List.of(
                                                                relationshipWith(
                                                                                commonWithCustomFees)
                                                                        .balance(5)))));
    }

    private HapiApiSpec royaltyCollectorsCanUseAutoAssociation() {
        final var uniqueWithRoyalty = "uniqueWithRoyalty";
        final var firstFungible = "firstFungible";
        final var secondFungible = "secondFungible";
        final var firstRoyaltyCollector = "firstRoyaltyCollector";
        final var secondRoyaltyCollector = "secondRoyaltyCollector";
        final var plentyOfSlots = 10;
        final var exchangeAmount = 12 * 15;
        final var firstRoyaltyAmount = exchangeAmount / 12;
        final var secondRoyaltyAmount = exchangeAmount / 15;
        final var netExchangeAmount = exchangeAmount - firstRoyaltyAmount - secondRoyaltyAmount;

        return defaultHapiSpec("RoyaltyCollectorsCanUseAutoAssociation")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(firstRoyaltyCollector)
                                .maxAutomaticTokenAssociations(plentyOfSlots),
                        cryptoCreate(secondRoyaltyCollector)
                                .maxAutomaticTokenAssociations(plentyOfSlots),
                        cryptoCreate(PARTY).maxAutomaticTokenAssociations(plentyOfSlots),
                        cryptoCreate(COUNTERPARTY).maxAutomaticTokenAssociations(plentyOfSlots),
                        newKeyNamed(MULTI_KEY),
                        getAccountInfo(PARTY).savingSnapshot(PARTY),
                        getAccountInfo(COUNTERPARTY).savingSnapshot(COUNTERPARTY),
                        getAccountInfo(firstRoyaltyCollector).savingSnapshot(firstRoyaltyCollector),
                        getAccountInfo(secondRoyaltyCollector)
                                .savingSnapshot(secondRoyaltyCollector))
                .when(
                        tokenCreate(firstFungible)
                                .treasury(TOKEN_TREASURY)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(123456789),
                        tokenCreate(secondFungible)
                                .treasury(TOKEN_TREASURY)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(123456789),
                        cryptoTransfer(
                                moving(1000, firstFungible).between(TOKEN_TREASURY, COUNTERPARTY),
                                moving(1000, secondFungible).between(TOKEN_TREASURY, COUNTERPARTY)),
                        tokenCreate(uniqueWithRoyalty)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(MULTI_KEY)
                                .withCustom(royaltyFeeNoFallback(1, 12, firstRoyaltyCollector))
                                .withCustom(royaltyFeeNoFallback(1, 15, secondRoyaltyCollector))
                                .initialSupply(0L),
                        mintToken(uniqueWithRoyalty, List.of(copyFromUtf8("HODL"))),
                        cryptoTransfer(
                                movingUnique(uniqueWithRoyalty, 1L).between(TOKEN_TREASURY, PARTY)))
                .then(
                        cryptoTransfer(
                                        movingUnique(uniqueWithRoyalty, 1L)
                                                .between(PARTY, COUNTERPARTY),
                                        moving(12 * 15L, firstFungible)
                                                .between(COUNTERPARTY, PARTY),
                                        moving(12 * 15L, secondFungible)
                                                .between(COUNTERPARTY, PARTY))
                                .fee(ONE_HBAR)
                                .via(HODL_XFER),
                        getTxnRecord(HODL_XFER)
                                .hasPriority(
                                        recordWith()
                                                .autoAssociated(
                                                        accountTokenPairsInAnyOrder(
                                                                List.of(
                                                                        /* The counterparty auto-associates to the non-fungible type */
                                                                        Pair.of(
                                                                                COUNTERPARTY,
                                                                                uniqueWithRoyalty),
                                                                        /* The sending party auto-associates to both fungibles */
                                                                        Pair.of(
                                                                                PARTY,
                                                                                firstFungible),
                                                                        Pair.of(
                                                                                PARTY,
                                                                                secondFungible),
                                                                        /* Both royalty collectors auto-associate to both fungibles */
                                                                        Pair.of(
                                                                                firstRoyaltyCollector,
                                                                                firstFungible),
                                                                        Pair.of(
                                                                                secondRoyaltyCollector,
                                                                                firstFungible),
                                                                        Pair.of(
                                                                                firstRoyaltyCollector,
                                                                                secondFungible),
                                                                        Pair.of(
                                                                                secondRoyaltyCollector,
                                                                                secondFungible))))),
                        getAccountInfo(PARTY)
                                .has(
                                        accountWith()
                                                .newAssociationsFromSnapshot(
                                                        PARTY,
                                                        List.of(
                                                                relationshipWith(uniqueWithRoyalty)
                                                                        .balance(0),
                                                                relationshipWith(firstFungible)
                                                                        .balance(netExchangeAmount),
                                                                relationshipWith(secondFungible)
                                                                        .balance(
                                                                                netExchangeAmount)))),
                        getAccountInfo(COUNTERPARTY)
                                .has(
                                        accountWith()
                                                .newAssociationsFromSnapshot(
                                                        PARTY,
                                                        List.of(
                                                                relationshipWith(uniqueWithRoyalty)
                                                                        .balance(1),
                                                                relationshipWith(firstFungible)
                                                                        .balance(
                                                                                1000L
                                                                                        - exchangeAmount),
                                                                relationshipWith(secondFungible)
                                                                        .balance(
                                                                                1000L
                                                                                        - exchangeAmount)))),
                        getAccountInfo(firstRoyaltyCollector)
                                .has(
                                        accountWith()
                                                .newAssociationsFromSnapshot(
                                                        PARTY,
                                                        List.of(
                                                                relationshipWith(firstFungible)
                                                                        .balance(
                                                                                exchangeAmount
                                                                                        / 12),
                                                                relationshipWith(secondFungible)
                                                                        .balance(
                                                                                exchangeAmount
                                                                                        / 12)))),
                        getAccountInfo(secondRoyaltyCollector)
                                .has(
                                        accountWith()
                                                .newAssociationsFromSnapshot(
                                                        PARTY,
                                                        List.of(
                                                                relationshipWith(firstFungible)
                                                                        .balance(
                                                                                exchangeAmount
                                                                                        / 15),
                                                                relationshipWith(secondFungible)
                                                                        .balance(
                                                                                exchangeAmount
                                                                                        / 15)))));
    }

    private HapiApiSpec royaltyCollectorsCannotUseAutoAssociationWithoutOpenSlots() {
        final var uniqueWithRoyalty = "uniqueWithRoyalty";
        final var someFungible = "firstFungible";
        final var royaltyCollectorNoSlots = "royaltyCollectorNoSlots";
        final var party = PARTY;
        final var counterparty = COUNTERPARTY;
        final var multipurpose = MULTI_KEY;
        final var hodlXfer = HODL_XFER;

        return defaultHapiSpec("RoyaltyCollectorsCanUseAutoAssociationWithoutOpenSlots")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(royaltyCollectorNoSlots),
                        cryptoCreate(party).maxAutomaticTokenAssociations(123),
                        cryptoCreate(counterparty).maxAutomaticTokenAssociations(123),
                        newKeyNamed(multipurpose),
                        getAccountInfo(party).savingSnapshot(party),
                        getAccountInfo(counterparty).savingSnapshot(counterparty),
                        getAccountInfo(royaltyCollectorNoSlots)
                                .savingSnapshot(royaltyCollectorNoSlots))
                .when(
                        tokenCreate(someFungible)
                                .treasury(TOKEN_TREASURY)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(123456789),
                        cryptoTransfer(
                                moving(1000, someFungible).between(TOKEN_TREASURY, counterparty)),
                        tokenCreate(uniqueWithRoyalty)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(multipurpose)
                                .withCustom(royaltyFeeNoFallback(1, 12, royaltyCollectorNoSlots))
                                .initialSupply(0L),
                        mintToken(uniqueWithRoyalty, List.of(copyFromUtf8("HODL"))),
                        cryptoTransfer(
                                movingUnique(uniqueWithRoyalty, 1L).between(TOKEN_TREASURY, party)))
                .then(
                        cryptoTransfer(
                                        movingUnique(uniqueWithRoyalty, 1L)
                                                .between(party, counterparty),
                                        moving(123, someFungible).between(counterparty, party))
                                .fee(ONE_HBAR)
                                .via(hodlXfer)
                                .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        getTxnRecord(hodlXfer)
                                .hasPriority(
                                        recordWith()
                                                .autoAssociated(
                                                        accountTokenPairsInAnyOrder(List.of()))),
                        getAccountInfo(party)
                                .has(
                                        accountWith()
                                                .newAssociationsFromSnapshot(
                                                        party,
                                                        List.of(
                                                                relationshipWith(uniqueWithRoyalty)
                                                                        .balance(1)))));
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
                        cryptoCreate(treasury).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(firstUser).balance(ONE_HBAR).maxAutomaticTokenAssociations(1),
                        cryptoCreate(secondUser).balance(ONE_HBAR).maxAutomaticTokenAssociations(2))
                .when(
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
                                .logged())
                .then(
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
                        cryptoTransfer(moving(1, tokenB).between(treasury, firstUser)));
    }

    private HapiApiSpec baseCryptoTransferFeeChargedAsExpected() {
        final var expectedHbarXferPriceUsd = 0.0001;
        final var expectedHtsXferPriceUsd = 0.001;
        final var expectedNftXferPriceUsd = 0.001;
        final var expectedHtsXferWithCustomFeePriceUsd = 0.002;
        final var expectedNftXferWithCustomFeePriceUsd = 0.002;
        final var transferAmount = 1L;
        final var customFeeCollector = "customFeeCollector";
        final var nonTreasurySender = "nonTreasurySender";
        final var hbarXferTxn = "hbarXferTxn";
        final var fungibleToken = "fungibleToken";
        final var fungibleTokenWithCustomFee = "fungibleTokenWithCustomFee";
        final var htsXferTxn = "htsXferTxn";
        final var htsXferTxnWithCustomFee = "htsXferTxnWithCustomFee";
        final var nonFungibleToken = "nonFungibleToken";
        final var nonFungibleTokenWithCustomFee = "nonFungibleTokenWithCustomFee";
        final var nftXferTxn = "nftXferTxn";
        final var nftXferTxnWithCustomFee = "nftXferTxnWithCustomFee";

        return defaultHapiSpec("BaseCryptoTransferIsChargedAsExpected")
                .given(
                        cryptoCreate(nonTreasurySender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(customFeeCollector),
                        tokenCreate(fungibleToken)
                                .treasury(SENDER)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(100L),
                        tokenCreate(fungibleTokenWithCustomFee)
                                .treasury(SENDER)
                                .tokenType(FUNGIBLE_COMMON)
                                .withCustom(fixedHbarFee(transferAmount, customFeeCollector))
                                .initialSupply(100L),
                        tokenAssociate(RECEIVER, fungibleToken, fungibleTokenWithCustomFee),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(nonFungibleToken)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(SENDER),
                        tokenCreate(nonFungibleTokenWithCustomFee)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .withCustom(fixedHbarFee(transferAmount, customFeeCollector))
                                .treasury(SENDER),
                        tokenAssociate(
                                nonTreasurySender,
                                List.of(fungibleTokenWithCustomFee, nonFungibleTokenWithCustomFee)),
                        mintToken(nonFungibleToken, List.of(copyFromUtf8("memo1"))),
                        mintToken(nonFungibleTokenWithCustomFee, List.of(copyFromUtf8("memo2"))),
                        tokenAssociate(RECEIVER, nonFungibleToken, nonFungibleTokenWithCustomFee),
                        cryptoTransfer(
                                        movingUnique(nonFungibleTokenWithCustomFee, 1)
                                                .between(SENDER, nonTreasurySender))
                                .payingWith(SENDER),
                        cryptoTransfer(
                                        moving(1, fungibleTokenWithCustomFee)
                                                .between(SENDER, nonTreasurySender))
                                .payingWith(SENDER))
                .when(
                        cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 100L))
                                .payingWith(SENDER)
                                .blankMemo()
                                .via(hbarXferTxn),
                        cryptoTransfer(moving(1, fungibleToken).between(SENDER, RECEIVER))
                                .blankMemo()
                                .payingWith(SENDER)
                                .via(htsXferTxn),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1).between(SENDER, RECEIVER))
                                .blankMemo()
                                .payingWith(SENDER)
                                .via(nftXferTxn),
                        cryptoTransfer(
                                        moving(1, fungibleTokenWithCustomFee)
                                                .between(nonTreasurySender, RECEIVER))
                                .blankMemo()
                                .fee(ONE_HBAR)
                                .payingWith(nonTreasurySender)
                                .via(htsXferTxnWithCustomFee),
                        cryptoTransfer(
                                        movingUnique(nonFungibleTokenWithCustomFee, 1)
                                                .between(nonTreasurySender, RECEIVER))
                                .blankMemo()
                                .fee(ONE_HBAR)
                                .payingWith(nonTreasurySender)
                                .via(nftXferTxnWithCustomFee))
                .then(
                        validateChargedUsdWithin(hbarXferTxn, expectedHbarXferPriceUsd, 0.01),
                        validateChargedUsdWithin(htsXferTxn, expectedHtsXferPriceUsd, 0.01),
                        validateChargedUsdWithin(nftXferTxn, expectedNftXferPriceUsd, 0.01),
                        validateChargedUsdWithin(
                                htsXferTxnWithCustomFee, expectedHtsXferWithCustomFeePriceUsd, 0.1),
                        validateChargedUsdWithin(
                                nftXferTxnWithCustomFee,
                                expectedNftXferWithCustomFeePriceUsd,
                                0.3));
    }

    private HapiApiSpec okToSetInvalidPaymentHeaderForCostAnswer() {
        return defaultHapiSpec("OkToSetInvalidPaymentHeaderForCostAnswer")
                .given(cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)).via("misc"))
                .when()
                .then(
                        getTxnRecord("misc").useEmptyTxnAsCostPayment(),
                        getTxnRecord("misc").omittingAnyPaymentForCostAnswer());
    }

    @SuppressWarnings("java:S5960")
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
                        tokenCreate("C").treasury("c"))
                .when(
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
                                .payingWith("a"))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
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
                                    allRunFor(
                                            spec, ref, t1a2, t1a3, t1a4, t1a5, t1a6, t2a4, t2a5,
                                            t2a6, t3a6);

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
                                    opLog.info(
                                            TOKENS_INVOLVED_LOG_MESSAGE,
                                            refFee,
                                            sdec(rates.toUsdWithActiveRates(refFee), 4),
                                            t1a2Fee,
                                            sdec(rates.toUsdWithActiveRates(t1a2Fee), 4),
                                            sdec((1.0 * t1a2Fee / refFee), 1),
                                            t1a3Fee,
                                            sdec(rates.toUsdWithActiveRates(t1a3Fee), 4),
                                            sdec((1.0 * t1a3Fee / refFee), 1),
                                            t1a4Fee,
                                            sdec(rates.toUsdWithActiveRates(t1a4Fee), 4),
                                            sdec((1.0 * t1a4Fee / refFee), 1),
                                            t1a5Fee,
                                            sdec(rates.toUsdWithActiveRates(t1a5Fee), 4),
                                            sdec((1.0 * t1a5Fee / refFee), 1),
                                            t1a6Fee,
                                            sdec(rates.toUsdWithActiveRates(t1a6Fee), 4),
                                            sdec((1.0 * t1a6Fee / refFee), 1),
                                            t2a4Fee,
                                            sdec(rates.toUsdWithActiveRates(t2a4Fee), 4),
                                            sdec((1.0 * t2a4Fee / refFee), 1),
                                            t2a5Fee,
                                            sdec(rates.toUsdWithActiveRates(t2a5Fee), 4),
                                            sdec((1.0 * t2a5Fee / refFee), 1),
                                            t2a6Fee,
                                            sdec(rates.toUsdWithActiveRates(t2a6Fee), 4),
                                            sdec((1.0 * t2a6Fee / refFee), 1),
                                            t3a6Fee,
                                            sdec(rates.toUsdWithActiveRates(t3a6Fee), 4),
                                            sdec((1.0 * t3a6Fee / refFee), 1));

                                    double pureHbarUsd = rates.toUsdWithActiveRates(refFee);
                                    double pureOneTokenTwoAccountsUsd =
                                            rates.toUsdWithActiveRates(t1a2Fee);
                                    double pureTwoTokensFourAccountsUsd =
                                            rates.toUsdWithActiveRates(t2a4Fee);
                                    double pureThreeTokensSixAccountsUsd =
                                            rates.toUsdWithActiveRates(t3a6Fee);
                                    assertEquals(
                                            10.0, pureOneTokenTwoAccountsUsd / pureHbarUsd, 1.0);
                                    assertEquals(
                                            20.0, pureTwoTokensFourAccountsUsd / pureHbarUsd, 2.0);
                                    assertEquals(
                                            30.0, pureThreeTokensSixAccountsUsd / pureHbarUsd, 3.0);
                                }));
    }

    public static String sdec(double d, int numDecimals) {
        var fmt = "%" + String.format(".0%df", numDecimals);
        return String.format(fmt, d);
    }

    private HapiApiSpec transferToNonAccountEntitiesReturnsInvalidAccountId() {
        AtomicReference<String> invalidAccountId = new AtomicReference<>();

        return defaultHapiSpec("TransferToNonAccountEntitiesReturnsInvalidAccountId")
                .given(
                        tokenCreate(TOKEN),
                        createTopic("something"),
                        withOpContext(
                                (spec, opLog) -> {
                                    var topicId = spec.registry().getTopicID("something");
                                    invalidAccountId.set(asTopicString(topicId));
                                }))
                .when()
                .then(
                        sourcing(
                                () ->
                                        cryptoTransfer(
                                                        tinyBarsFromTo(
                                                                DEFAULT_PAYER,
                                                                invalidAccountId.get(),
                                                                1L))
                                                .signedBy(DEFAULT_PAYER)
                                                .hasKnownStatus(INVALID_ACCOUNT_ID)),
                        sourcing(
                                () ->
                                        cryptoTransfer(
                                                        moving(1, TOKEN)
                                                                .between(
                                                                        DEFAULT_PAYER,
                                                                        invalidAccountId.get()))
                                                .signedBy(DEFAULT_PAYER)
                                                .hasKnownStatus(INVALID_ACCOUNT_ID)));
    }

    private HapiApiSpec complexKeyAcctPaysForOwnTransfer() {
        SigControl enoughUniqueSigs =
                SigControl.threshSigs(
                        2,
                        SigControl.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, ON),
                        SigControl.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
        String node = HapiSpecSetup.getDefaultInstance().defaultNodeName();

        return defaultHapiSpec("ComplexKeyAcctPaysForOwnTransfer")
                .given(
                        newKeyNamed("complexKey").shape(enoughUniqueSigs),
                        cryptoCreate(PAYER).key("complexKey").balance(1_000_000_000L))
                .when()
                .then(
                        cryptoTransfer(tinyBarsFromTo(PAYER, node, 1_000_000L))
                                .payingWith(PAYER)
                                .numPayerSigs(14)
                                .fee(ONE_HUNDRED_HBARS));
    }

    private HapiApiSpec twoComplexKeysRequired() {
        SigControl payerShape = threshOf(2, threshOf(1, 7), threshOf(3, 7));
        SigControl receiverShape = SigControl.threshSigs(3, threshOf(2, 2), threshOf(3, 5), ON);

        SigControl payerSigs =
                SigControl.threshSigs(
                        2,
                        SigControl.threshSigs(1, ON, OFF, OFF, OFF, OFF, OFF, OFF),
                        SigControl.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
        SigControl receiverSigs =
                SigControl.threshSigs(
                        3,
                        SigControl.threshSigs(2, ON, ON),
                        SigControl.threshSigs(3, OFF, OFF, ON, ON, ON),
                        ON);

        return defaultHapiSpec("TwoComplexKeysRequired")
                .given(
                        newKeyNamed("payerKey").shape(payerShape),
                        newKeyNamed("receiverKey").shape(receiverShape),
                        cryptoCreate(PAYER).key("payerKey").balance(100_000_000_000L),
                        cryptoCreate(RECEIVER)
                                .receiverSigRequired(true)
                                .key("receiverKey")
                                .payingWith(PAYER))
                .when()
                .then(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, RECEIVER, 1_000L))
                                .payingWith(PAYER)
                                .sigControl(
                                        forKey(PAYER, payerSigs), forKey(RECEIVER, receiverSigs))
                                .hasKnownStatus(SUCCESS)
                                .fee(ONE_HUNDRED_HBARS));
    }

    private HapiApiSpec specialAccountsBalanceCheck() {
        return defaultHapiSpec("SpecialAccountsBalanceCheck")
                .given()
                .when()
                .then(
                        IntStream.concat(IntStream.range(1, 101), IntStream.range(900, 1001))
                                .mapToObj(i -> getAccountBalance("0.0." + i).logged())
                                .toArray(HapiSpecOperation[]::new));
    }

    private HapiApiSpec transferWithMissingAccountGetsInvalidAccountId() {
        return defaultHapiSpec("TransferWithMissingAccount")
                .given(cryptoCreate(PAYEE_SIG_REQ).receiverSigRequired(true))
                .when(
                        cryptoTransfer(tinyBarsFromTo("1.2.3", PAYEE_SIG_REQ, 1_000L))
                                .signedBy(DEFAULT_PAYER, PAYEE_SIG_REQ)
                                .hasKnownStatus(INVALID_ACCOUNT_ID))
                .then();
    }

    private HapiApiSpec vanillaTransferSucceeds() {
        long initialBalance = HapiSpecSetup.getDefaultInstance().defaultBalance();

        return defaultHapiSpec("VanillaTransferSucceeds")
                .given(
                        cryptoCreate("somebody")
                                .maxAutomaticTokenAssociations(5001)
                                .hasPrecheck(
                                        REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),
                        UtilVerbs.inParallel(
                                cryptoCreate(PAYER),
                                cryptoCreate(PAYEE_SIG_REQ).receiverSigRequired(true),
                                cryptoCreate(PAYEE_NO_SIG_REQ)))
                .when(
                        cryptoTransfer(
                                        tinyBarsFromTo(PAYER, PAYEE_SIG_REQ, 1_000L),
                                        tinyBarsFromTo(PAYER, PAYEE_NO_SIG_REQ, 2_000L))
                                .via("transferTxn"))
                .then(
                        getAccountInfo(PAYER)
                                .logged()
                                .hasExpectedLedgerId("0x03")
                                .has(accountWith().balance(initialBalance - 3_000L)),
                        getAccountInfo(PAYEE_SIG_REQ)
                                .has(accountWith().balance(initialBalance + 1_000L)),
                        getAccountDetails(PAYEE_NO_SIG_REQ)
                                .payingWith(GENESIS)
                                .has(
                                        AccountDetailsAsserts.accountWith()
                                                .balance(initialBalance + 2_000L)
                                                .noAllowances()));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
