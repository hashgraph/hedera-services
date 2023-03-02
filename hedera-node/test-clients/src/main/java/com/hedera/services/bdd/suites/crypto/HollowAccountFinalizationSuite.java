/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.aaWith;
import static com.hedera.services.bdd.suites.contract.hapi.ContractUpdateSuite.ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountInfo;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HollowAccountFinalizationSuite extends HapiSuite {
    private static final Logger LOG = LogManager.getLogger(HollowAccountFinalizationSuite.class);
    private static final String ANOTHER_SECP_256K1_SOURCE_KEY = "anotherSecp256k1Alias";
    private static final String PAY_RECEIVABLE = "PayReceivable";
    private static final long INITIAL_BALANCE = 1000L;
    private static final String LAZY_MEMO = "lazy-created account";
    private static final String TRANSFER_TXN = "transferTxn";
    private static final String TRANSFER_TXN_2 = "transferTxn2";
    private static final String PARTY = "party";
    private static final String LAZY_CREATE_SPONSOR = "lazyCreateSponsor";
    private static final String CRYPTO_TRANSFER_RECEIVER = "cryptoTransferReceiver";
    private static final String FT_XFER = "ftXfer";
    private static final String TOKEN_TREASURY = "treasury";
    private static final String VANILLA_TOKEN = "TokenD";

    public static void main(String... args) {
        new HollowAccountFinalizationSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                hollowAccountCompletionWithCryptoTransfer(),
                hollowAccountCompletionWithContractCreate(),
                hollowAccountCompletionWithContractCall(),
                hollowAccountCompletionWithTokenAssociation(),
                hollowAccountCompletionWithTokenTransfer(),
                hollowAccountCompletionViaSignatureInSigMapIsNotAllowed(),
                hollowAccountCompletionWhenHollowAccountSigRequiredInOtherReqSigs(),
                tooManyHollowAccountFinalizationsShouldFail(),
                completedHollowAccountsTransfer(),
                hollowAccountFinalizationWhenAccountNotPresentInPreHandle(),
                hollowAccountOccursFinalizationOnlyOnceWhenMultipleFinalizationTensComeInAtTheSameTime());
    }

    private HapiSpec hollowAccountCompletionWithTokenTransfer() {
        final var fungibleToken = "fungibleToken";
        final AtomicReference<TokenID> ftId = new AtomicReference<>();
        final AtomicReference<AccountID> partyId = new AtomicReference<>();
        final AtomicReference<ByteString> partyAlias = new AtomicReference<>();
        final AtomicReference<ByteString> counterAlias = new AtomicReference<>();

        return defaultHapiSpec("HollowAccountCompletionWithTokenTransfer")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(PARTY).balance(INITIAL_BALANCE * ONE_HBAR).maxAutomaticTokenAssociations(2),
                        tokenCreate(fungibleToken).treasury(PARTY).initialSupply(1_000_000),
                        withOpContext((spec, opLog) -> {
                            final var registry = spec.registry();
                            final var ecdsaKey = registry.getKey(SECP_256K1_SOURCE_KEY);
                            final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                            final var addressBytes = recoverAddressFromPubKey(tmp);
                            final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                            ftId.set(registry.getTokenID(fungibleToken));
                            partyId.set(registry.getAccountID(PARTY));
                            partyAlias.set(ByteString.copyFrom(asSolidityAddress(partyId.get())));
                            counterAlias.set(evmAddressBytes);
                        }))
                .when(withOpContext((spec, opLog) -> {
                    /* hollow account created with fungible token transfer as expected */
                    final var cryptoTransferWithLazyCreate = cryptoTransfer(
                                    (s, b) -> b.addTokenTransfers(TokenTransferList.newBuilder()
                                            .setToken(ftId.get())
                                            .addTransfers(aaWith(partyAlias.get(), -500))
                                            .addTransfers(aaWith(counterAlias.get(), +500))))
                            .signedBy(DEFAULT_PAYER, PARTY)
                            .via(FT_XFER);

                    final var getHollowAccountInfoAfterCreation = getAliasedAccountInfo(counterAlias.get())
                            .hasToken(relationshipWith(fungibleToken).balance(500))
                            .has(accountWith()
                                    .hasEmptyKey()
                                    .noAlias()
                                    .autoRenew(THREE_MONTHS_IN_SECONDS)
                                    .receiverSigReq(false)
                                    .memo(LAZY_MEMO));

                    final HapiGetTxnRecord hapiGetTxnRecord =
                            getTxnRecord(FT_XFER).andAllChildRecords().logged();

                    allRunFor(spec, cryptoTransferWithLazyCreate, getHollowAccountInfoAfterCreation, hapiGetTxnRecord);

                    final AccountID newAccountID =
                            hapiGetTxnRecord.getChildRecord(0).getReceipt().getAccountID();

                    spec.registry().saveAccountId(SECP_256K1_SOURCE_KEY, newAccountID);
                }))
                .then(withOpContext((spec, opLog) -> {
                    final var hbarTransfer = cryptoTransfer(
                                    tinyBarsFromTo(PARTY, counterAlias.get(), ONE_HUNDRED_HBARS))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN_2);

                    /* complete hollow account creation with fungible token transfer */
                    final var fungibleTokenTransfer = cryptoTransfer(
                                    moving(5, fungibleToken).between(PARTY, counterAlias.get()))
                            .payingWith(SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SECP_256K1_SOURCE_KEY))
                            .via(TRANSFER_TXN_2);

                    final var cryptoCreateWithEVMAddress = cryptoCreate(PARTY)
                            .alias(counterAlias.get())
                            .hasPrecheck(INVALID_ALIAS_KEY)
                            .balance(ONE_HBAR);

                    final var cryptoCreateWithECDSAKeyAlias = cryptoCreate(PARTY)
                            .alias(spec.registry().getKey(SECP_256K1_SOURCE_KEY).toByteString())
                            .hasPrecheck(INVALID_ALIAS_KEY)
                            .balance(ONE_HBAR);

                    final var getCompletedHollowAccountInfo = getAliasedAccountInfo(counterAlias.get())
                            .hasToken(relationshipWith(fungibleToken).balance(505))
                            .has(accountWith().key(SECP_256K1_SOURCE_KEY).noAlias());

                    final var hapiGetTxnRecord = getTxnRecord(FT_XFER)
                            .hasChildRecordCount(1)
                            .hasChildRecords(recordWith().status(SUCCESS).memo(LAZY_MEMO));

                    allRunFor(
                            spec,
                            hbarTransfer,
                            fungibleTokenTransfer,
                            cryptoCreateWithEVMAddress,
                            cryptoCreateWithECDSAKeyAlias,
                            getCompletedHollowAccountInfo,
                            hapiGetTxnRecord);
                }));
    }

    private HapiSpec hollowAccountCompletionWithTokenAssociation() {
        return defaultHapiSpec("HollowAccountCompletionWithTokenAssociation")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        tokenCreate(VANILLA_TOKEN).treasury(TOKEN_TREASURY),
                        cryptoCreate("test"))
                .when()
                .then(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    final var op = cryptoTransfer(tinyBarsFromTo(LAZY_CREATE_SPONSOR, evmAddress, ONE_HUNDRED_HBARS))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN);

                    final HapiGetTxnRecord hapiGetTxnRecord =
                            getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged();

                    allRunFor(spec, op, hapiGetTxnRecord);

                    final AccountID newAccountID =
                            hapiGetTxnRecord.getChildRecord(0).getReceipt().getAccountID();
                    spec.registry().saveAccountId(SECP_256K1_SOURCE_KEY, newAccountID);

                    final var op2 = tokenAssociate("test", VANILLA_TOKEN)
                            .payingWith(SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SECP_256K1_SOURCE_KEY))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN_2);

                    final var op3 = getAliasedAccountInfo(evmAddress)
                            .has(accountWith().key(SECP_256K1_SOURCE_KEY).noAlias());

                    final HapiGetTxnRecord hapiGetSecondTxnRecord =
                            getTxnRecord(TRANSFER_TXN_2).andAllChildRecords().logged();

                    allRunFor(spec, op2, op3, hapiGetSecondTxnRecord);
                }));
    }

    private HapiSpec hollowAccountFinalizationWhenAccountNotPresentInPreHandle() {
        final var ECDSA_2 = "ECDSA_2";
        return defaultHapiSpec("hollowAccountFinalizationWhenAccountNotPresentInPreHandle")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(ECDSA_2).shape(SECP_256K1_SHAPE),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        tokenCreate(VANILLA_TOKEN).treasury(TOKEN_TREASURY),
                        cryptoCreate("test"))
                .when()
                .then(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var ecdsaKey2 = spec.registry().getKey(ECDSA_2);
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    final var op = cryptoTransfer(tinyBarsFromTo(LAZY_CREATE_SPONSOR, evmAddress, ONE_HUNDRED_HBARS))
                            .hasKnownStatus(SUCCESS)
                            .deferStatusResolution()
                            .via(TRANSFER_TXN);
                    // transfer from the hollow account to receiver
                    // the txns requires signature from the hollow account
                    final var op2 = cryptoTransfer(tinyBarsFromTo(evmAddress, ecdsaKey2.toByteString(), ONE_HBAR))
                            .signedBy(GENESIS, SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SECP_256K1_SOURCE_KEY))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN_2);

                    allRunFor(spec, op, op2);

                    final var op3 = getAliasedAccountInfo(evmAddress)
                            .has(accountWith().key(SECP_256K1_SOURCE_KEY).noAlias());

                    final HapiGetTxnRecord hapiGetSecondTxnRecord =
                            getTxnRecord(TRANSFER_TXN_2).andAllChildRecords().logged();

                    allRunFor(spec, op3, hapiGetSecondTxnRecord);
                }));
    }

    private HapiSpec hollowAccountOccursFinalizationOnlyOnceWhenMultipleFinalizationTensComeInAtTheSameTime() {
        final var ECDSA_2 = "ECDSA_2";
        return defaultHapiSpec("hollowAccountOccursFinalizationOnlyOnceWhenMultipleFinalizationTensComeInAtTheSameTime")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(ECDSA_2).shape(SECP_256K1_SHAPE),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        tokenCreate(VANILLA_TOKEN).treasury(TOKEN_TREASURY),
                        cryptoCreate("test"))
                .when()
                .then(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var ecdsaKey2 = spec.registry().getKey(ECDSA_2);
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    final var op = cryptoTransfer(tinyBarsFromTo(LAZY_CREATE_SPONSOR, evmAddress, ONE_HUNDRED_HBARS))
                            .hasKnownStatus(SUCCESS)
                            .deferStatusResolution()
                            .via(TRANSFER_TXN);
                    // finalize the account via transfer from the hollow account to receiver
                    // the txns requires signature from the hollow account
                    final var op2 = cryptoTransfer(tinyBarsFromTo(evmAddress, ecdsaKey2.toByteString(), ONE_HBAR))
                            .signedBy(GENESIS, SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SECP_256K1_SOURCE_KEY))
                            .hasKnownStatus(SUCCESS)
                            .deferStatusResolution()
                            .via(TRANSFER_TXN_2);
                    // send the finalization transaction again immediately after the 1st one
                    final var op3 = cryptoTransfer(tinyBarsFromTo(evmAddress, ecdsaKey2.toByteString(), ONE_HBAR))
                            .signedBy(GENESIS, SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SECP_256K1_SOURCE_KEY))
                            .hasKnownStatus(SUCCESS)
                            .via("shouldNotFinalize");

                    allRunFor(spec, op, op2, op3);

                    final var op4 = getAliasedAccountInfo(evmAddress)
                            .has(accountWith().key(SECP_256K1_SOURCE_KEY).noAlias());

                    final var hapiGetSecondTxnRecord = childRecordsCheck(
                            TRANSFER_TXN_2,
                            SUCCESS,
                            recordWith().status(SUCCESS),
                            recordWith().status(SUCCESS));

                    allRunFor(spec, op4, hapiGetSecondTxnRecord, emptyChildRecordsCheck("shouldNotFinalize", SUCCESS));
                }));
    }

    private HapiSpec hollowAccountCompletionWithCryptoTransfer() {
        return defaultHapiSpec("HollowAccountCompletionWithCryptoTransfer")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                        cryptoCreate(CRYPTO_TRANSFER_RECEIVER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    final var op = cryptoTransfer(tinyBarsFromTo(LAZY_CREATE_SPONSOR, evmAddress, ONE_HUNDRED_HBARS))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN);
                    final var op2 = getAliasedAccountInfo(evmAddress)
                            .has(accountWith()
                                    .hasEmptyKey()
                                    .expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0, 0)
                                    .autoRenew(THREE_MONTHS_IN_SECONDS)
                                    .receiverSigReq(false)
                                    .memo(LAZY_MEMO));
                    final HapiGetTxnRecord hapiGetTxnRecord =
                            getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged();
                    allRunFor(spec, op, op2, hapiGetTxnRecord);

                    final AccountID newAccountID =
                            hapiGetTxnRecord.getChildRecord(0).getReceipt().getAccountID();
                    spec.registry().saveAccountId(SECP_256K1_SOURCE_KEY, newAccountID);
                }))
                .then(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(SECP_256K1_SOURCE_KEY);

                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(
                            ecdsaKey.getECDSASecp256K1().toByteArray()));

                    final var op3 = cryptoTransfer(
                                    tinyBarsFromTo(LAZY_CREATE_SPONSOR, CRYPTO_TRANSFER_RECEIVER, ONE_HUNDRED_HBARS))
                            .payingWith(SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SECP_256K1_SOURCE_KEY))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN_2);

                    final var cryptoCreateWithEVMAddress = cryptoCreate(PARTY)
                            .alias(evmAddress)
                            .hasPrecheck(INVALID_ALIAS_KEY)
                            .balance(ONE_HBAR);

                    final var cryptoCreateWithECDSAKeyAlias = cryptoCreate(PARTY)
                            .alias(ecdsaKey.toByteString())
                            .hasPrecheck(INVALID_ALIAS_KEY)
                            .balance(ONE_HBAR);

                    final var op4 = getAliasedAccountInfo(evmAddress)
                            .has(accountWith().key(SECP_256K1_SOURCE_KEY).noAlias());

                    final HapiGetTxnRecord hapiGetSecondTxnRecord =
                            getTxnRecord(TRANSFER_TXN_2).andAllChildRecords().logged();
                    allRunFor(
                            spec,
                            op3,
                            cryptoCreateWithEVMAddress,
                            cryptoCreateWithECDSAKeyAlias,
                            op4,
                            hapiGetSecondTxnRecord);
                }));
    }

    private HapiSpec hollowAccountCompletionWhenHollowAccountSigRequiredInOtherReqSigs() {
        return defaultHapiSpec("hollowAccountCompletionWhenHollowAccountSigRequiredInOtherReqSigs")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                        cryptoCreate(CRYPTO_TRANSFER_RECEIVER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(withOpContext((spec, opLog) -> {
                    // create hollow account
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    final var op = cryptoTransfer(tinyBarsFromTo(LAZY_CREATE_SPONSOR, evmAddress, ONE_HUNDRED_HBARS))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN);
                    final var op2 = getAliasedAccountInfo(evmAddress)
                            .has(accountWith()
                                    .hasEmptyKey()
                                    .expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0, 0)
                                    .autoRenew(THREE_MONTHS_IN_SECONDS)
                                    .receiverSigReq(false)
                                    .memo(LAZY_MEMO));
                    allRunFor(spec, op, op2);
                }))
                .then(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry().getKey(SECP_256K1_SOURCE_KEY);

                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(
                            ecdsaKey.getECDSASecp256K1().toByteArray()));
                    // transfer from the hollow account to receiver
                    // the txns requires signature from the hollow account
                    final var op3 = cryptoTransfer(
                                    tinyBarsFromToWithAlias(SECP_256K1_SOURCE_KEY, CRYPTO_TRANSFER_RECEIVER, ONE_HBAR))
                            .signedBy(GENESIS, SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SECP_256K1_SOURCE_KEY))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN_2);
                    final var op4 = getAliasedAccountInfo(evmAddress)
                            .has(accountWith()
                                    .key(SECP_256K1_SOURCE_KEY)
                                    .evmAddress(evmAddress)
                                    .noAlias());

                    final var childRecordCheck = childRecordsCheck(
                            TRANSFER_TXN_2, SUCCESS, recordWith().status(SUCCESS));
                    allRunFor(spec, op3, op4, childRecordCheck);
                }));
    }

    private HapiSpec hollowAccountCompletionWithContractCreate() {
        final String CONTRACT = "CreateTrivial";
        return defaultHapiSpec("HollowAccountCompletionWithContractCreate")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                        newKeyNamed(ADMIN_KEY),
                        uploadInitCode(CONTRACT))
                .when()
                .then(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    final var op = cryptoTransfer(tinyBarsFromTo(LAZY_CREATE_SPONSOR, evmAddress, ONE_HUNDRED_HBARS))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN);

                    final HapiGetTxnRecord hapiGetTxnRecord =
                            getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged();

                    allRunFor(spec, op, hapiGetTxnRecord);

                    final AccountID newAccountID =
                            hapiGetTxnRecord.getChildRecord(0).getReceipt().getAccountID();
                    spec.registry().saveAccountId(SECP_256K1_SOURCE_KEY, newAccountID);

                    final var op2 = contractCreate(CONTRACT)
                            .adminKey(ADMIN_KEY)
                            .payingWith(SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SECP_256K1_SOURCE_KEY))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN_2);

                    final var op3 = getAliasedAccountInfo(evmAddress)
                            .has(accountWith().key(SECP_256K1_SOURCE_KEY).noAlias());

                    final HapiGetTxnRecord hapiGetSecondTxnRecord =
                            getTxnRecord(TRANSFER_TXN_2).andAllChildRecords().logged();

                    allRunFor(spec, op2, op3, hapiGetSecondTxnRecord);
                }));
    }

    private HapiSpec hollowAccountCompletionWithContractCall() {
        final long DEPOSIT_AMOUNT = 1000;
        return defaultHapiSpec("HollowAccountCompletionWithContractCall")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                        newKeyNamed(ADMIN_KEY),
                        uploadInitCode(PAY_RECEIVABLE),
                        contractCreate(PAY_RECEIVABLE).adminKey(ADMIN_KEY))
                .when()
                .then(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    final var op = cryptoTransfer(tinyBarsFromTo(LAZY_CREATE_SPONSOR, evmAddress, ONE_HUNDRED_HBARS))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN);

                    final HapiGetTxnRecord hapiGetTxnRecord =
                            getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged();

                    allRunFor(spec, op, hapiGetTxnRecord);

                    final AccountID newAccountID =
                            hapiGetTxnRecord.getChildRecord(0).getReceipt().getAccountID();
                    spec.registry().saveAccountId(SECP_256K1_SOURCE_KEY, newAccountID);

                    final var op2 = contractCall(PAY_RECEIVABLE)
                            .sending(DEPOSIT_AMOUNT)
                            .payingWith(SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SECP_256K1_SOURCE_KEY))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN_2);

                    final var op3 = getAliasedAccountInfo(evmAddress)
                            .has(accountWith().key(SECP_256K1_SOURCE_KEY).noAlias());

                    final HapiGetTxnRecord hapiGetSecondTxnRecord =
                            getTxnRecord(TRANSFER_TXN_2).andAllChildRecords().logged();

                    allRunFor(spec, op2, op3, hapiGetSecondTxnRecord);
                }));
    }

    private HapiSpec hollowAccountCompletionViaSignatureInSigMapIsNotAllowed() {
        final long DEPOSIT_AMOUNT = 1000;
        return defaultHapiSpec("hollowAccountCompletionViaSignatureInSigMapIsNotAllowed")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                        newKeyNamed(ADMIN_KEY),
                        uploadInitCode(PAY_RECEIVABLE),
                        contractCreate(PAY_RECEIVABLE).adminKey(ADMIN_KEY))
                .when()
                .then(withOpContext((spec, opLog) -> {
                    // create a hollow account
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    final var op = cryptoTransfer(tinyBarsFromTo(LAZY_CREATE_SPONSOR, evmAddress, ONE_HUNDRED_HBARS))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN);
                    allRunFor(spec, op);
                    // send a ContractCall signed by the ecdsa
                    // key of the hollow account's evmAddress
                    final var op2 = contractCall(PAY_RECEIVABLE)
                            .sending(DEPOSIT_AMOUNT)
                            .signedBy(GENESIS, SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SECP_256K1_SOURCE_KEY))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN_2);
                    final var op3 = getAliasedAccountInfo(evmAddress)
                            .has(accountWith()
                                    .key(EMPTY_KEY)
                                    .evmAddress(evmAddress)
                                    .noAlias());
                    final var checkRecords = emptyChildRecordsCheck(TRANSFER_TXN_2, SUCCESS);
                    allRunFor(spec, op2, op3, checkRecords);
                }));
    }

    private HapiSpec tooManyHollowAccountFinalizationsShouldFail() {
        final var ECDSA_KEY_1 = "ECDSA_KEY_1";
        final var ECDSA_KEY_2 = "ECDSA_KEY_2";
        final var ECDSA_KEY_3 = "ECDSA_KEY_3";
        final var ECDSA_KEY_4 = "ECDSA_KEY_4";
        final var RECIPIENT_KEY = "ECDSA_KEY_5";
        return defaultHapiSpec("tooManyHollowAccountFinalizationsShouldFail")
                .given(
                        newKeyNamed(ECDSA_KEY_1).shape(SECP_256K1_SHAPE),
                        newKeyNamed(ECDSA_KEY_2).shape(SECP_256K1_SHAPE),
                        newKeyNamed(ECDSA_KEY_3).shape(SECP_256K1_SHAPE),
                        newKeyNamed(ECDSA_KEY_4).shape(SECP_256K1_SHAPE),
                        newKeyNamed(RECIPIENT_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                        newKeyNamed(ADMIN_KEY),
                        uploadInitCode(PAY_RECEIVABLE),
                        contractCreate(PAY_RECEIVABLE).adminKey(ADMIN_KEY))
                .when()
                .then(withOpContext((spec, opLog) -> {
                    // create hollow accounts
                    allRunFor(
                            spec,
                            sendToEvmAddressFromECDSAKey(spec, ECDSA_KEY_1),
                            sendToEvmAddressFromECDSAKey(spec, ECDSA_KEY_2),
                            sendToEvmAddressFromECDSAKey(spec, ECDSA_KEY_3),
                            sendToEvmAddressFromECDSAKey(spec, ECDSA_KEY_4));
                    // send a CryptoTransfer signed by all the ecdsa
                    // keys of the hollow accounts;
                    final var op2 = cryptoTransfer(sendFromEvmAddressFromECDSAKey(
                                            spec,
                                            spec.registry()
                                                    .getKey(RECIPIENT_KEY)
                                                    .toByteString(),
                                            ECDSA_KEY_1,
                                            ECDSA_KEY_2,
                                            ECDSA_KEY_3,
                                            ECDSA_KEY_4)
                                    .toArray(Function[]::new))
                            .signedBy(GENESIS, ECDSA_KEY_1, ECDSA_KEY_2, ECDSA_KEY_3, ECDSA_KEY_4)
                            .sigMapPrefixes(
                                    uniqueWithFullPrefixesFor(ECDSA_KEY_1, ECDSA_KEY_2, ECDSA_KEY_3, ECDSA_KEY_4))
                            .hasKnownStatus(MAX_CHILD_RECORDS_EXCEEDED)
                            .via(TRANSFER_TXN_2);
                    // no finalization child records should be exported, since too
                    // many finalizations
                    // were requested
                    final var op3 = emptyChildRecordsCheck(TRANSFER_TXN_2, MAX_CHILD_RECORDS_EXCEEDED);
                    allRunFor(
                            spec,
                            op2,
                            op3,
                            assertStillHollow(spec, ECDSA_KEY_1),
                            assertStillHollow(spec, ECDSA_KEY_2),
                            assertStillHollow(spec, ECDSA_KEY_3),
                            assertStillHollow(spec, ECDSA_KEY_4));
                }));
    }

    private HapiSpec completedHollowAccountsTransfer() {
        return defaultHapiSpec("CompletedHollowAccountsTransfer")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(ANOTHER_SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR),
                        cryptoCreate(CRYPTO_TRANSFER_RECEIVER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(withOpContext((spec, opLog) -> {
                    final var firstECDSAKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var firstEVMAddress = ByteString.copyFrom(recoverAddressFromPubKey(firstECDSAKey));
                    final var op = cryptoTransfer(
                                    tinyBarsFromTo(LAZY_CREATE_SPONSOR, firstEVMAddress, ONE_HUNDRED_HBARS))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN);

                    final HapiGetTxnRecord hapiGetTxnRecord =
                            getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged();
                    allRunFor(spec, op, hapiGetTxnRecord);

                    final AccountID newAccountID =
                            hapiGetTxnRecord.getChildRecord(0).getReceipt().getAccountID();
                    spec.registry().saveAccountId(SECP_256K1_SOURCE_KEY, newAccountID);

                    final var secondECDSAKey = spec.registry()
                            .getKey(ANOTHER_SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var secondEVMAddress = ByteString.copyFrom(recoverAddressFromPubKey(secondECDSAKey));
                    final var op2 = cryptoTransfer(
                                    tinyBarsFromTo(LAZY_CREATE_SPONSOR, secondEVMAddress, ONE_HUNDRED_HBARS))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN_2);
                    final HapiGetTxnRecord secondHapiGetTxnRecord =
                            getTxnRecord(TRANSFER_TXN_2).andAllChildRecords().logged();
                    allRunFor(spec, op2, secondHapiGetTxnRecord);

                    final AccountID anotherNewAccountID = secondHapiGetTxnRecord
                            .getChildRecord(0)
                            .getReceipt()
                            .getAccountID();
                    spec.registry().saveAccountId(ANOTHER_SECP_256K1_SOURCE_KEY, anotherNewAccountID);

                    final var op3 = cryptoTransfer(
                                    tinyBarsFromTo(LAZY_CREATE_SPONSOR, CRYPTO_TRANSFER_RECEIVER, ONE_HUNDRED_HBARS))
                            .payingWith(SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(SECP_256K1_SOURCE_KEY))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN + "3");

                    allRunFor(spec, op3);

                    final var op4 = cryptoTransfer(
                                    tinyBarsFromTo(LAZY_CREATE_SPONSOR, CRYPTO_TRANSFER_RECEIVER, ONE_HUNDRED_HBARS))
                            .payingWith(ANOTHER_SECP_256K1_SOURCE_KEY)
                            .sigMapPrefixes(uniqueWithFullPrefixesFor(ANOTHER_SECP_256K1_SOURCE_KEY))
                            .hasKnownStatus(SUCCESS)
                            .via(TRANSFER_TXN + "4");

                    allRunFor(spec, op4);
                }))
                .then(withOpContext((spec, opLog) -> {
                    final var firstECDSAKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var firstEVMAddress = ByteString.copyFrom(recoverAddressFromPubKey(firstECDSAKey));

                    final var secondECDSAKey = spec.registry()
                            .getKey(ANOTHER_SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var secondEVMAddress = ByteString.copyFrom(recoverAddressFromPubKey(secondECDSAKey));

                    var op5 = cryptoTransfer(tinyBarsFromTo(firstEVMAddress, secondEVMAddress, FIVE_HBARS))
                            .payingWith(SECP_256K1_SOURCE_KEY)
                            .via(TRANSFER_TXN + "5");

                    var op6 = getTxnRecord(TRANSFER_TXN + "5")
                            .andAllChildRecords()
                            .logged();

                    allRunFor(spec, op5, op6);
                }));
    }

    private HapiCryptoTransfer sendToEvmAddressFromECDSAKey(final HapiSpec spec, final String key) {
        final var ecdsaKey = spec.registry().getKey(key).getECDSASecp256K1().toByteArray();
        final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
        return cryptoTransfer(tinyBarsFromTo(LAZY_CREATE_SPONSOR, evmAddress, ONE_HBAR))
                .hasKnownStatus(SUCCESS)
                .via(TRANSFER_TXN);
    }

    private List<Function<HapiSpec, TransferList>> sendFromEvmAddressFromECDSAKey(
            final HapiSpec spec, final ByteString recipient, final String... keys) {
        List<Function<HapiSpec, TransferList>> transfers = new ArrayList<>();
        for (final var key : keys) {
            final var ecdsaKey = spec.registry().getKey(key).getECDSASecp256K1().toByteArray();
            final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
            transfers.add(tinyBarsFromTo(evmAddress, recipient, ONE_HBAR));
        }
        return transfers;
    }

    private HapiGetAccountInfo assertStillHollow(final HapiSpec spec, final String key) {
        final var ecdsaKey = spec.registry().getKey(key).getECDSASecp256K1().toByteArray();
        final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
        return getAliasedAccountInfo(evmAddress)
                .has(accountWith().hasEmptyKey().evmAddress(evmAddress).noAlias());
    }
}
