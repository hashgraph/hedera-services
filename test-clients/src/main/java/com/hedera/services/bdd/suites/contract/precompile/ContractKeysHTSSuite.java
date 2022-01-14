package com.hedera.services.bdd.suites.contract.precompile;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.NonFungibleTransfers;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.CommonUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers.changingFungibleBalances;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.BURN_TOKEN_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.DELEGATE_BURN_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.DELEGATE_MINT_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.DELEGATE_TRANSFER_CALL_ABI;
import static com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers.changingFungibleBalances;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.BURN_TOKEN_ORDINARY_CALL;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MINT_TOKEN_ORDINARY_CALL;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SINGLE_TOKEN_ASSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SINGLE_TOKEN_DISSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.STATIC_BURN_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.STATIC_MINT_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.STATIC_TRANSFER_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.TRANSFER_NFT_ORDINARY_CALL;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class ContractKeysHTSSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(ContractKeysHTSSuite.class);
    private static final String TOKEN_TREASURY = "treasury";

    private static final String NFT = "nft";
    private static final String CONTRACT = "theContract";

    private static final String ACCOUNT = "sender";
    private static final String RECEIVER = "receiver";

    private static final String UNIVERSAL_KEY = "multipurpose";

    private static final KeyShape CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, KeyShape.CONTRACT);
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT);

    private static final String OUTER_CONTRACT = "Outer Contract";
    private static final String INNER_CONTRACT = "Inner Contract";

    private static final String DELEGATE_KEY = "Delegate key";
    private static final String CONTRACT_KEY = "Contract key";

    public static void main(String... args) {
        new ContractKeysHTSSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunAsync() {
        return true;
    }

    @Override
    protected List<HapiApiSpec> getSpecsInSuite() {
        return allOf(
				HSCS_KEY_1(),
				HSCS_KEY_2(),
				HSCS_KEY_3(),
				HSCS_KEY_5(),
                HSCS_KEY_6(),
                HSCS_KEY_7(),
				HSCS_KEY_8(),
				HSCS_KEY_10()
        );
    }

    List<HapiApiSpec> HSCS_KEY_1() {
        return List.of(
                callForMintWithContractKey(),
                callForTransferWithContractKey(),
                callForAssociateWithContractKey(),
                callForDissociateWithContractKey(),
                callForBurnWithContractKey()
        );
    }

    List<HapiApiSpec> HSCS_KEY_2() {
        return List.of(
                staticCallForTransferWithContractKey(),
                staticCallForBurnWithContractKey(),
                staticCallForMintWithContractKey(),
                delegateCallForTransferWithContractKey(),
                delegateCallForBurnWithContractKey(),
                delegateCallForMintWithContractKey()
        );
    }

    List<HapiApiSpec> HSCS_KEY_3() {
        return List.of(
                callForMintWithDelegateContractKey(),
                callForTransferWithDelegateContractKey(),
                callForAssociateWithDelegateContractKey(),
                callForDissociateWithDelegateContractKey(),
                callForBurnWithDelegateContractKey()
        );
    }

    List<HapiApiSpec> HSCS_KEY_5() {
        return List.of(
                staticCallForTransferWithDelegateContractKey(),
                staticCallForBurnWithDelegateContractKey(),
                staticCallForMintWithDelegateContractKey()
        );
    }

    List<HapiApiSpec> HSCS_KEY_6() {
        return List.of(
                burnWithKeyAsPartOf1OfXThreshold()
        );
    }

    List<HapiApiSpec> HSCS_KEY_7() {
        return List.of(
                transferWithKeyAsPartOf2OfXThreshold()
        );
    }

	List<HapiApiSpec> HSCS_KEY_8() {
		return List.of(
				HSCS_KEY_BURN_TOKEN_WITH_FULL_PREFIX_AND_PARTIAL_PREFIX_KEYS()
		);
	}

	List<HapiApiSpec> HSCS_KEY_10() {
		return List.of(
				HSCS_KEY_MIXED_FRAMES_SCENARIOS()
		);
	}

    private HapiApiSpec burnWithKeyAsPartOf1OfXThreshold() {
        final var theContract = "burn token";
        final var multiKey = "purpose";
        final String ALICE = "Alice";
        final String TOKEN = "Token";
        final var DELEGATE_CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT);
        final var CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, KeyShape.CONTRACT);

        return defaultHapiSpec("burnWithKeyAsPartOf1OfXThreshold")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(multiKey)
                                .adminKey(multiKey)
                                .treasury(TOKEN_TREASURY),
                        fileCreate("bytecode").payingWith(ALICE),
                        updateLargeFile(ALICE, "bytecode", extractByteCode(ContractResources.BURN_TOKEN)),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(theContract, ContractResources.BURN_TOKEN_CONSTRUCTOR_ABI,
                                                        asAddress(spec.registry().getTokenID(TOKEN)))
                                                        .payingWith(ALICE)
                                                        .bytecode("bytecode")
                                                        .via("creationTx")
                                                        .gas(28_000))),
                        getTxnRecord("creationTx").logged()
                )
                .when(
                        newKeyNamed("delegateContractKey").shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, theContract))),
                        tokenUpdate(TOKEN)
                                .supplyKey("delegateContractKey"),

                        contractCall(theContract, BURN_TOKEN_ABI, 1, new ArrayList<Long>())
                                .via("burn with delegate contract key")
                                .gas(48_000),

                        childRecordsCheck("burn with delegate contract key", SUCCESS, recordWith()
                                .status(SUCCESS)
                                .tokenTransfers(
                                        changingFungibleBalances()
                                                .including(TOKEN, TOKEN_TREASURY, -1)
                                )
                                .newTotalSupply(49)
                        ),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 49)

                )
                .then(
                        newKeyNamed("contractKey").shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, theContract))),
                        tokenUpdate(TOKEN)
                                .supplyKey("contractKey"),

                        contractCall(theContract, BURN_TOKEN_ABI, 1, new ArrayList<Long>())
                                .via("burn with contract key")
                                .gas(48_000),

                        childRecordsCheck("burn with contract key", SUCCESS, recordWith()
                                .status(SUCCESS)
                                .tokenTransfers(
                                        changingFungibleBalances()
                                                .including(TOKEN, TOKEN_TREASURY, -1)
                                )
                        )
                );

    }

    private HapiApiSpec transferWithKeyAsPartOf2OfXThreshold() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> receiverID = new AtomicReference<>();
        final var supplyKey = "supplyKey";
        final var DELEGATE_CONTRACT_KEY_SHAPE = KeyShape.threshOf(2, SIMPLE, SIMPLE, DELEGATE_CONTRACT, KeyShape.CONTRACT);

        return defaultHapiSpec("transferWithKeyAsPartOf2OfXThreshold")
                .given(
                        newKeyNamed(supplyKey),
                        fileCreate(INNER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, INNER_CONTRACT, extractByteCode(ContractResources.SERVICE_CONTRACT)),
                        fileCreate(OUTER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, OUTER_CONTRACT, extractByteCode(ContractResources.DELEGATE_CONTRACT)),
                        contractCreate(INNER_CONTRACT)
                                .bytecode(INNER_CONTRACT)
                                .gas(100_000),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(supplyKey)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
                        cryptoCreate(ACCOUNT)
                                .exposingCreatedIdTo(accountID::set),
                        cryptoCreate(RECEIVER)
                                .exposingCreatedIdTo(receiverID::set),
                        tokenAssociate(INNER_CONTRACT, VANILLA_TOKEN),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        tokenAssociate(RECEIVER, VANILLA_TOKEN),
                        cryptoTransfer(movingUnique(VANILLA_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT))
                                .payingWith(GENESIS)
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(OUTER_CONTRACT, ContractResources.DELEGATE_CONTRACT_CONSTRUCTOR,
                                                        getNestedContractAddress(INNER_CONTRACT, spec))
                                                        .bytecode(OUTER_CONTRACT)
                                                        .gas(100_000),
                                                tokenAssociate(OUTER_CONTRACT, VANILLA_TOKEN),

                                                newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ON, OUTER_CONTRACT, INNER_CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),

                                                contractCall(OUTER_CONTRACT, DELEGATE_TRANSFER_CALL_ABI,
                                                        asAddress(vanillaTokenTokenID.get()), asAddress(accountID.get()),
                                                        asAddress(receiverID.get()), 1L)
                                                        .payingWith(GENESIS)
                                                        .alsoSigningWithFullPrefix(ACCOUNT)
                                                        .via("delegateTransferCallWithDelegateContractKeyTxn")
                                                        .gas(5_000_000)
                                        )
                        )
                ).then(
                        childRecordsCheck("delegateTransferCallWithDelegateContractKeyTxn", SUCCESS, recordWith()
                                .status(SUCCESS)),
                        getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 0),
                        getAccountBalance(RECEIVER).hasTokenBalance(VANILLA_TOKEN, 1)
                );
    }


    private HapiApiSpec delegateCallForTransferWithContractKey() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> receiverID = new AtomicReference<>();
        final var supplyKey = "supplyKey";


        return defaultHapiSpec("delegateCallForTransferWithContractKey")
                .given(
                        newKeyNamed(supplyKey),
                        fileCreate(INNER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, INNER_CONTRACT, extractByteCode(ContractResources.SERVICE_CONTRACT)),
                        fileCreate(OUTER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, OUTER_CONTRACT, extractByteCode(ContractResources.DELEGATE_CONTRACT)),
                        contractCreate(INNER_CONTRACT)
                                .bytecode(INNER_CONTRACT)
                                .gas(100_000),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(supplyKey)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
                        cryptoCreate(ACCOUNT)
                                .exposingCreatedIdTo(accountID::set),
                        cryptoCreate(RECEIVER)
                                .exposingCreatedIdTo(receiverID::set),
                        tokenAssociate(INNER_CONTRACT, VANILLA_TOKEN),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        tokenAssociate(RECEIVER, VANILLA_TOKEN),
                        cryptoTransfer(movingUnique(VANILLA_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT))
                                .payingWith(GENESIS)
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(OUTER_CONTRACT, ContractResources.DELEGATE_CONTRACT_CONSTRUCTOR,
                                                        getNestedContractAddress(INNER_CONTRACT, spec))
                                                        .bytecode(OUTER_CONTRACT)
                                                        .gas(100_000),
                                                tokenAssociate(OUTER_CONTRACT, VANILLA_TOKEN),

                                                newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, OUTER_CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),

                                                contractCall(OUTER_CONTRACT, DELEGATE_TRANSFER_CALL_ABI,
                                                        asAddress(vanillaTokenTokenID.get()), asAddress(accountID.get()),
                                                        asAddress(receiverID.get()), 1L)
                                                        .payingWith(GENESIS)
                                                        .via("delegateTransferCallWithContractKeyTxn")
                                                        .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
                                                        .gas(5_000_000)
                                        )
                        )

                ).then(
                        childRecordsCheck("delegateTransferCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED, recordWith()
                                .status(INVALID_SIGNATURE)),
                        getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 1),
                        getAccountBalance(RECEIVER).hasTokenBalance(VANILLA_TOKEN, 0)
                );
    }

    private HapiApiSpec delegateCallForBurnWithContractKey() {
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final var supplyKey = "supplyKey";

        return defaultHapiSpec("delegateCallForBurnWithContractKey")
                .given(
                        newKeyNamed(supplyKey),
                        fileCreate(INNER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, INNER_CONTRACT, extractByteCode(ContractResources.SERVICE_CONTRACT)),
                        fileCreate(OUTER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, OUTER_CONTRACT, extractByteCode(ContractResources.DELEGATE_CONTRACT)),
                        contractCreate(INNER_CONTRACT)
                                .bytecode(INNER_CONTRACT)
                                .gas(100_000),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(supplyKey)
                                .adminKey(supplyKey)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0L)
                                .exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("Second!")))
                )
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(OUTER_CONTRACT, ContractResources.DELEGATE_CONTRACT_CONSTRUCTOR,
                                                        getNestedContractAddress(INNER_CONTRACT, spec))
                                                        .bytecode(OUTER_CONTRACT)
                                                        .gas(100_000),

                                                newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, OUTER_CONTRACT))),
                                                tokenUpdate(VANILLA_TOKEN).supplyKey(CONTRACT_KEY),

                                                contractCall(OUTER_CONTRACT, DELEGATE_BURN_CALL_ABI,
                                                        asAddress(vanillaTokenTokenID.get()), 0, List.of(1L))
                                                        .payingWith(GENESIS)
                                                        .via("delegateBurnCallWithContractKeyTxn")
                                                        .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
                                                        .gas(5_000_000)
                                        )
                        )
                )
                .then(
                        childRecordsCheck("delegateBurnCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED, recordWith()
                                .status(INVALID_SIGNATURE)),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, 2)
                );
    }

    private HapiApiSpec delegateCallForMintWithContractKey() {
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final var supplyKey = "supplyKey";

        return defaultHapiSpec("delegateCallForMintWithContractKey")
                .given(
                        newKeyNamed(supplyKey),
                        fileCreate(INNER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, INNER_CONTRACT, extractByteCode(ContractResources.SERVICE_CONTRACT)),
                        fileCreate(OUTER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, OUTER_CONTRACT, extractByteCode(ContractResources.DELEGATE_CONTRACT)),
                        contractCreate(INNER_CONTRACT)
                                .bytecode(INNER_CONTRACT)
                                .gas(100_000),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyKey(supplyKey)
                                .adminKey(supplyKey)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(50L)
                                .exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id)))
                )
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(OUTER_CONTRACT, ContractResources.DELEGATE_CONTRACT_CONSTRUCTOR,
                                                        getNestedContractAddress(INNER_CONTRACT, spec))
                                                        .bytecode(OUTER_CONTRACT)
                                                        .gas(100_000),

                                                newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, OUTER_CONTRACT))),
                                                tokenUpdate(VANILLA_TOKEN).supplyKey(CONTRACT_KEY),

                                                contractCall(OUTER_CONTRACT, DELEGATE_MINT_CALL_ABI,
                                                        asAddress(vanillaTokenTokenID.get()), 1)
                                                        .payingWith(GENESIS)
                                                        .via("delegateBurnCallWithContractKeyTxn")
                                                        .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
                                                        .gas(5_000_000)
                                        )
                        )
                )
                .then(
                        childRecordsCheck("delegateBurnCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED, recordWith()
                                .status(INVALID_SIGNATURE)),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, 50)
                );
    }


    private HapiApiSpec staticCallForTransferWithContractKey() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> receiverID = new AtomicReference<>();
        final var supplyKey = "supplyKey";


        return defaultHapiSpec("staticCallForTransferWithContractKey")
                .given(
                        newKeyNamed(supplyKey),
                        fileCreate(INNER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, INNER_CONTRACT, extractByteCode(ContractResources.SERVICE_CONTRACT)),
                        fileCreate(OUTER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, OUTER_CONTRACT, extractByteCode(ContractResources.STATIC_CONTRACT)),
                        contractCreate(INNER_CONTRACT)
                                .bytecode(INNER_CONTRACT)
                                .gas(100_000),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(supplyKey)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
                        cryptoCreate(ACCOUNT)
                                .exposingCreatedIdTo(accountID::set),
                        cryptoCreate(RECEIVER)
                                .exposingCreatedIdTo(receiverID::set),
                        tokenAssociate(INNER_CONTRACT, VANILLA_TOKEN),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        tokenAssociate(RECEIVER, VANILLA_TOKEN),
                        cryptoTransfer(movingUnique(VANILLA_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT))
                                .payingWith(GENESIS)
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(OUTER_CONTRACT, ContractResources.STATIC_CONTRACT_CONSTRUCTOR,
                                                        getNestedContractAddress(INNER_CONTRACT, spec))
                                                        .bytecode(OUTER_CONTRACT)
                                                        .gas(100_000),
                                                tokenAssociate(OUTER_CONTRACT, VANILLA_TOKEN),

                                                newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, OUTER_CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),

                                                contractCall(OUTER_CONTRACT, STATIC_TRANSFER_CALL_ABI,
                                                        asAddress(vanillaTokenTokenID.get()), asAddress(accountID.get()),
                                                        asAddress(receiverID.get()), 1L)
                                                        .payingWith(GENESIS)
                                                        .via("staticTransferCallWithContractKeyTxn")
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                        .gas(5_000_000)
                                        )
                        )

                ).then(
                        emptyChildRecordsCheck("staticTransferCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED)
                );
    }

    private HapiApiSpec staticCallForBurnWithContractKey() {
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final var supplyKey = "supplyKey";

        return defaultHapiSpec("staticCallForBurnWithContractKey")
                .given(
                        newKeyNamed(supplyKey),
                        fileCreate(INNER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, INNER_CONTRACT, extractByteCode(ContractResources.SERVICE_CONTRACT)),
                        fileCreate(OUTER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, OUTER_CONTRACT, extractByteCode(ContractResources.STATIC_CONTRACT)),
                        contractCreate(INNER_CONTRACT)
                                .bytecode(INNER_CONTRACT)
                                .gas(100_000),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(supplyKey)
                                .adminKey(supplyKey)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0L)
                                .exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("Second!")))
                )
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(OUTER_CONTRACT, ContractResources.STATIC_CONTRACT_CONSTRUCTOR,
                                                        getNestedContractAddress(INNER_CONTRACT, spec))
                                                        .bytecode(OUTER_CONTRACT)
                                                        .gas(100_000),

                                                newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, OUTER_CONTRACT))),
                                                tokenUpdate(VANILLA_TOKEN).supplyKey(CONTRACT_KEY),

                                                contractCall(OUTER_CONTRACT, STATIC_BURN_CALL_ABI,
                                                        asAddress(vanillaTokenTokenID.get()), 0, List.of(1L))
                                                        .payingWith(GENESIS)
                                                        .via("staticBurnCallWithContractKeyTxn")
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                        .gas(5_000_000)
                                        )
                        )
                )
                .then(
                        emptyChildRecordsCheck("staticBurnCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED)

                );
    }

    private HapiApiSpec staticCallForMintWithContractKey() {
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final var supplyKey = "supplyKey";

        return defaultHapiSpec("staticCallForMintWithContractKey")
                .given(
                        newKeyNamed(supplyKey),
                        fileCreate(INNER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, INNER_CONTRACT, extractByteCode(ContractResources.SERVICE_CONTRACT)),
                        fileCreate(OUTER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, OUTER_CONTRACT, extractByteCode(ContractResources.STATIC_CONTRACT)),
                        contractCreate(INNER_CONTRACT)
                                .bytecode(INNER_CONTRACT)
                                .gas(100_000),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyKey(supplyKey)
                                .adminKey(supplyKey)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(50L)
                                .exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id)))
                )
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(OUTER_CONTRACT, ContractResources.STATIC_CONTRACT_CONSTRUCTOR,
                                                        getNestedContractAddress(INNER_CONTRACT, spec))
                                                        .bytecode(OUTER_CONTRACT)
                                                        .gas(100_000),

                                                newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, OUTER_CONTRACT))),
                                                tokenUpdate(VANILLA_TOKEN).supplyKey(CONTRACT_KEY),

                                                contractCall(OUTER_CONTRACT, STATIC_MINT_CALL_ABI,
                                                        asAddress(vanillaTokenTokenID.get()), 1)
                                                        .payingWith(GENESIS)
                                                        .via("staticBurnCallWithContractKeyTxn")
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                        .gas(5_000_000)

                                        )
                        )
                )
                .then(
                        emptyChildRecordsCheck("staticBurnCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED)
                );
    }

    private HapiApiSpec staticCallForTransferWithDelegateContractKey() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> receiverID = new AtomicReference<>();
        final var supplyKey = "supplyKey";


        return defaultHapiSpec("staticCallForTransferWithDelegateContractKey")
                .given(
                        newKeyNamed(supplyKey),
                        fileCreate(INNER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, INNER_CONTRACT, extractByteCode(ContractResources.SERVICE_CONTRACT)),
                        fileCreate(OUTER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, OUTER_CONTRACT, extractByteCode(ContractResources.STATIC_CONTRACT)),
                        contractCreate(INNER_CONTRACT)
                                .bytecode(INNER_CONTRACT)
                                .gas(100_000),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(supplyKey)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
                        cryptoCreate(ACCOUNT)
                                .exposingCreatedIdTo(accountID::set),
                        cryptoCreate(RECEIVER)
                                .exposingCreatedIdTo(receiverID::set),
                        tokenAssociate(INNER_CONTRACT, VANILLA_TOKEN),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        tokenAssociate(RECEIVER, VANILLA_TOKEN),
                        cryptoTransfer(movingUnique(VANILLA_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT))
                                .payingWith(GENESIS)
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(OUTER_CONTRACT, ContractResources.STATIC_CONTRACT_CONSTRUCTOR,
                                                        getNestedContractAddress(INNER_CONTRACT, spec))
                                                        .bytecode(OUTER_CONTRACT)
                                                        .gas(100_000),
                                                tokenAssociate(OUTER_CONTRACT, VANILLA_TOKEN),

                                                newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, OUTER_CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),

                                                contractCall(OUTER_CONTRACT, STATIC_TRANSFER_CALL_ABI,
                                                        asAddress(vanillaTokenTokenID.get()), asAddress(accountID.get()),
                                                        asAddress(receiverID.get()), 1L)
                                                        .payingWith(GENESIS)
                                                        .via("staticTransferCallWithDelegateContractKeyTxn")
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                        .gas(5_000_000)
                                        )
                        )

                ).then(
                        emptyChildRecordsCheck("staticTransferCallWithDelegateContractKeyTxn", CONTRACT_REVERT_EXECUTED)
                );
    }

    private HapiApiSpec staticCallForBurnWithDelegateContractKey() {
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final var supplyKey = "supplyKey";

        return defaultHapiSpec("staticCallForBurnWithDelegateContractKey")
                .given(
                        newKeyNamed(supplyKey),
                        fileCreate(INNER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, INNER_CONTRACT, extractByteCode(ContractResources.SERVICE_CONTRACT)),
                        fileCreate(OUTER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, OUTER_CONTRACT, extractByteCode(ContractResources.STATIC_CONTRACT)),
                        contractCreate(INNER_CONTRACT)
                                .bytecode(INNER_CONTRACT)
                                .gas(100_000),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(supplyKey)
                                .adminKey(supplyKey)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0L)
                                .exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("Second!")))
                )
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(OUTER_CONTRACT, ContractResources.STATIC_CONTRACT_CONSTRUCTOR,
                                                        getNestedContractAddress(INNER_CONTRACT, spec))
                                                        .bytecode(OUTER_CONTRACT)
                                                        .gas(100_000),

                                                newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, OUTER_CONTRACT))),
                                                tokenUpdate(VANILLA_TOKEN).supplyKey(DELEGATE_KEY),

                                                contractCall(OUTER_CONTRACT, STATIC_BURN_CALL_ABI,
                                                        asAddress(vanillaTokenTokenID.get()), 0, List.of(1L))
                                                        .payingWith(GENESIS)
                                                        .via("staticBurnCallWithDelegateContractKeyTxn")
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                        .gas(5_000_000)
                                        )
                        )
                )
                .then(
                        emptyChildRecordsCheck("staticBurnCallWithDelegateContractKeyTxn", CONTRACT_REVERT_EXECUTED)

                );
    }

    private HapiApiSpec staticCallForMintWithDelegateContractKey() {
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final var supplyKey = "supplyKey";

        return defaultHapiSpec("staticCallForMintWithDelegateContractKey")
                .given(
                        newKeyNamed(supplyKey),
                        fileCreate(INNER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, INNER_CONTRACT, extractByteCode(ContractResources.SERVICE_CONTRACT)),
                        fileCreate(OUTER_CONTRACT).payingWith(GENESIS),
                        updateLargeFile(GENESIS, OUTER_CONTRACT, extractByteCode(ContractResources.STATIC_CONTRACT)),
                        contractCreate(INNER_CONTRACT)
                                .bytecode(INNER_CONTRACT)
                                .gas(100_000),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyKey(supplyKey)
                                .adminKey(supplyKey)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(50L)
                                .exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id)))
                )
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(OUTER_CONTRACT, ContractResources.STATIC_CONTRACT_CONSTRUCTOR,
                                                        getNestedContractAddress(INNER_CONTRACT, spec))
                                                        .bytecode(OUTER_CONTRACT)
                                                        .gas(100_000),

                                                newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, OUTER_CONTRACT))),
                                                tokenUpdate(VANILLA_TOKEN).supplyKey(DELEGATE_KEY),

                                                contractCall(OUTER_CONTRACT, STATIC_MINT_CALL_ABI,
                                                        asAddress(vanillaTokenTokenID.get()), 1)
                                                        .payingWith(GENESIS)
                                                        .via("staticBurnCallWithDelegateContractKeyTxn")
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                        .gas(5_000_000)

                                        )
                        )
                )
                .then(
                        emptyChildRecordsCheck("staticBurnCallWithDelegateContractKeyTxn", CONTRACT_REVERT_EXECUTED)
                );
    }

    private HapiApiSpec callForMintWithContractKey() {
        final var theAccount = "anybody";
        final var mintContractByteCode = "mintContractByteCode";
        final var amount = 10L;
        final var fungibleToken = "fungibleToken";
        final var multiKey = "purpose";
        final var theContract = "mintContract";
        final var firstMintTxn = "firstMintTxn";

        final AtomicLong fungibleNum = new AtomicLong();

        return defaultHapiSpec("callForMintWithContractKey")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        fileCreate(mintContractByteCode).payingWith(theAccount),
                        updateLargeFile(theAccount, mintContractByteCode, extractByteCode(ContractResources.ORDINARY_CALLS_CONTRACT)),
                        tokenCreate(fungibleToken)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(multiKey)
                                .supplyKey(multiKey)
                                .exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2]))
                ).when(
                        sourcing(() -> contractCreate(theContract)
                                .bytecode(mintContractByteCode).payingWith(theAccount)
                                .gas(300_000L))
                ).then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,

                                                newKeyNamed("contractKey").shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
                                                        theContract))),
                                                tokenUpdate(fungibleToken)
                                                        .supplyKey("contractKey"),

                                                contractCall(theContract, MINT_TOKEN_ORDINARY_CALL,
                                                        asAddress(spec.registry().getTokenID(fungibleToken)), amount,
                                                        new byte[]{})
                                                        .via(firstMintTxn)
                                                        .payingWith(theAccount)
                                        )),

                        childRecordsCheck(firstMintTxn, SUCCESS, recordWith()
                                .status(SUCCESS)
                                .tokenTransfers(
                                        changingFungibleBalances()
                                                .including(fungibleToken, TOKEN_TREASURY, 10)
                                )
                                .newTotalSupply(10)
                        ),

                        getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
                        getTokenInfo(fungibleToken).hasTotalSupply(amount),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(fungibleToken, amount)
                );
    }


    private HapiApiSpec callForMintWithDelegateContractKey() {
        final var theAccount = "anybody";
        final var mintContractByteCode = "mintContractByteCode";
        final var amount = 10L;
        final var fungibleToken = "fungibleToken";
        final var multiKey = "purpose";
        final var theContract = "mintContract";
        final var firstMintTxn = "firstMintTxn";

        final AtomicLong fungibleNum = new AtomicLong();

        return defaultHapiSpec("callForMintWithDelegateContractKey")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        fileCreate(mintContractByteCode).payingWith(theAccount),
                        updateLargeFile(theAccount, mintContractByteCode, extractByteCode(ContractResources.ORDINARY_CALLS_CONTRACT)),
                        tokenCreate(fungibleToken)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(multiKey)
                                .supplyKey(multiKey)
                                .exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2]))
                ).when(
                        sourcing(() -> contractCreate(theContract)
                                .bytecode(mintContractByteCode).payingWith(theAccount)
                                .gas(300_000L))
                ).then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,

                                                newKeyNamed("delegateContractKey").shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
                                                        theContract))),
                                                tokenUpdate(fungibleToken)
                                                        .supplyKey("delegateContractKey"),

                                                contractCall(theContract, MINT_TOKEN_ORDINARY_CALL,
                                                        asAddress(spec.registry().getTokenID(fungibleToken)), amount,
                                                        new byte[]{})
                                                        .via(firstMintTxn)
                                                        .payingWith(theAccount)
                                        )),

                        childRecordsCheck(firstMintTxn, SUCCESS, recordWith()
                                .status(SUCCESS)
                                .tokenTransfers(
                                        changingFungibleBalances()
                                                .including(fungibleToken, TOKEN_TREASURY, 10)
                                )
                                .newTotalSupply(10)
                        ),

                        getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
                        getTokenInfo(fungibleToken).hasTotalSupply(amount),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(fungibleToken, amount)
                );
    }

    private HapiApiSpec callForTransferWithContractKey() {
        return defaultHapiSpec("callForTransferWithContractKey")
                .given(
                        newKeyNamed(UNIVERSAL_KEY),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(UNIVERSAL_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(ACCOUNT, NFT),
                        mintToken(NFT, List.of(metadata("firstMemo"), metadata("secondMemo"))),
                        fileCreate("bytecode").payingWith(ACCOUNT),
                        updateLargeFile(ACCOUNT, "bytecode", extractByteCode(ContractResources.ORDINARY_CALLS_CONTRACT))
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(CONTRACT)
                                                        .payingWith(ACCOUNT)
                                                        .bytecode("bytecode")
                                                        .via("creationTx")
                                                        .gas(28_000),
                                                getTxnRecord("creationTx").logged(),

                                                newKeyNamed("contractKey").shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
                                                        CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key("contractKey"),

                                                tokenAssociate(CONTRACT, List.of(NFT)),
                                                tokenAssociate(RECEIVER, List.of(NFT)),
                                                cryptoTransfer(movingUnique(NFT, 1).between(TOKEN_TREASURY, ACCOUNT)),
                                                contractCall(CONTRACT, TRANSFER_NFT_ORDINARY_CALL,
                                                        asAddress(spec.registry().getTokenID(NFT)),
                                                        asAddress(spec.registry().getAccountID(ACCOUNT)),
                                                        asAddress(spec.registry().getAccountID(RECEIVER)),
                                                        1L
                                                )
                                                        .fee(ONE_HBAR)
                                                        .hasKnownStatus(SUCCESS)
                                                        .payingWith(GENESIS)
                                                        .gas(48_000)
                                                        .via("distributeTx"),
                                                getTxnRecord("distributeTx").andAllChildRecords().logged()))
                ).then(
                        getTokenInfo(NFT).hasTotalSupply(2),
                        getAccountInfo(RECEIVER).hasOwnedNfts(1),
                        getAccountBalance(RECEIVER).hasTokenBalance(NFT, 1),
                        getAccountInfo(ACCOUNT).hasOwnedNfts(0),
                        getAccountBalance(ACCOUNT).hasTokenBalance(NFT, 0),

                        childRecordsCheck("distributeTx", SUCCESS, recordWith()
                                .status(SUCCESS)
                                .tokenTransfers(
                                        NonFungibleTransfers.changingNFTBalances()
                                                .including(NFT, ACCOUNT, RECEIVER, 1L)
                                ))
                );
    }

    private HapiApiSpec callForTransferWithDelegateContractKey() {
        return defaultHapiSpec("callForTransferWithDelegateContractKey")
                .given(
                        newKeyNamed(UNIVERSAL_KEY),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(UNIVERSAL_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(ACCOUNT, NFT),
                        mintToken(NFT, List.of(metadata("firstMemo"), metadata("secondMemo"))),
                        fileCreate("bytecode").payingWith(ACCOUNT),
                        updateLargeFile(ACCOUNT, "bytecode", extractByteCode(ContractResources.ORDINARY_CALLS_CONTRACT))
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(CONTRACT)
                                                        .payingWith(ACCOUNT)
                                                        .bytecode("bytecode")
                                                        .via("creationTx")
                                                        .gas(28_000),
                                                getTxnRecord("creationTx").logged(),

                                                newKeyNamed("delegateContractKey").shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
                                                        CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key("delegateContractKey"),

                                                tokenAssociate(CONTRACT, List.of(NFT)),
                                                tokenAssociate(RECEIVER, List.of(NFT)),
                                                cryptoTransfer(movingUnique(NFT, 1).between(TOKEN_TREASURY, ACCOUNT)),
                                                contractCall(CONTRACT, TRANSFER_NFT_ORDINARY_CALL,
                                                        asAddress(spec.registry().getTokenID(NFT)),
                                                        asAddress(spec.registry().getAccountID(ACCOUNT)),
                                                        asAddress(spec.registry().getAccountID(RECEIVER)),
                                                        1L
                                                )
                                                        .fee(ONE_HBAR)
                                                        .hasKnownStatus(SUCCESS)
                                                        .payingWith(GENESIS)
                                                        .gas(48_000)
                                                        .via("distributeTx"),
                                                getTxnRecord("distributeTx").andAllChildRecords().logged()))
                ).then(
                        getTokenInfo(NFT).hasTotalSupply(2),
                        getAccountInfo(RECEIVER).hasOwnedNfts(1),
                        getAccountBalance(RECEIVER).hasTokenBalance(NFT, 1),
                        getAccountInfo(ACCOUNT).hasOwnedNfts(0),
                        getAccountBalance(ACCOUNT).hasTokenBalance(NFT, 0),

                        childRecordsCheck("distributeTx", SUCCESS, recordWith()
                                .status(SUCCESS)
                                .tokenTransfers(
                                        NonFungibleTransfers.changingNFTBalances()
                                                .including(NFT, ACCOUNT, RECEIVER, 1L)
                                ))
                );
    }

	private HapiApiSpec HSCS_KEY_BURN_TOKEN_WITH_FULL_PREFIX_AND_PARTIAL_PREFIX_KEYS() {
		final var theAccount = "anybody";
		final var burnContractByteCode = "burnContractByteCode";
		final var amount = 99L;
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var theContract = "mintContract";
		final var firstBurnTxn = "firstBurnTxn";
		final var secondBurnTxn = "secondBurnTxn";

		final AtomicLong fungibleNum = new AtomicLong();

		return defaultHapiSpec("HSCS_KEY_BURN_TOKEN_WITH_FULL_PREFIX_AND_PARTIAL_PREFIX_KEYS")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						fileCreate(burnContractByteCode).payingWith(theAccount),
						updateLargeFile(theAccount, burnContractByteCode, extractByteCode(ContractResources.ORDINARY_CALLS_CONTRACT)),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(100)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2]))
				).when(
						sourcing(() -> contractCreate(theContract)
								.bytecode(burnContractByteCode).payingWith(theAccount)
								.gas(300_000L))
				).then(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(theContract, BURN_TOKEN_ORDINARY_CALL,
														asAddress(spec.registry().getTokenID(fungibleToken)),
														1, new ArrayList<Long>())
														.via(firstBurnTxn).payingWith(theAccount).
														signedBy(multiKey).
														signedBy(theAccount).
														hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(theContract, BURN_TOKEN_ORDINARY_CALL,
														asAddress(spec.registry().getTokenID(fungibleToken)),
														1, new ArrayList<Long>())
														.via(secondBurnTxn).payingWith(theAccount)
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(SUCCESS))),
						getTxnRecord(firstBurnTxn).andAllChildRecords().logged(),
						getTxnRecord(secondBurnTxn).andAllChildRecords().logged(),
						getTokenInfo(fungibleToken).hasTotalSupply(amount),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(fungibleToken, amount)
				);
	}

    private HapiApiSpec callForAssociateWithDelegateContractKey() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final String THE_CONTRACT = "Associate/Dissociate Contract";

        return defaultHapiSpec("callAssociateWithDelegateContractKey")
                .given(
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        fileCreate(THE_CONTRACT),
                        updateLargeFile(ACCOUNT, THE_CONTRACT,
                                extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),

                                                newKeyNamed("delegateContractKey").shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key("delegateContractKey"),

                                                contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
                                                        asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("vanillaTokenAssociateTxn")
                                                        .hasKnownStatus(SUCCESS)
                                        )
                        )
                ).then(
                        childRecordsCheck("vanillaTokenAssociateTxn", SUCCESS, recordWith()
                                .status(SUCCESS)),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
                );
    }

    private HapiApiSpec callForAssociateWithContractKey() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final String THE_CONTRACT = "Associate/Dissociate Contract";

        return defaultHapiSpec("callAssociateWithContractKey")
                .given(
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        fileCreate(THE_CONTRACT),
                        updateLargeFile(ACCOUNT, THE_CONTRACT,
                                extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),

                                                newKeyNamed("contractKey").shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key("contractKey"),

                                                contractCall(THE_CONTRACT, SINGLE_TOKEN_ASSOCIATE,
                                                        asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("vanillaTokenAssociateTxn")
                                                        .hasKnownStatus(SUCCESS)
                                        )
                        )
                ).then(
                        childRecordsCheck("vanillaTokenAssociateTxn", SUCCESS, recordWith()
                                .status(SUCCESS)),
                        getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
                );
    }


    public HapiApiSpec callForDissociateWithDelegateContractKey() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final String THE_CONTRACT = "Associate/Dissociate Contract";
        final long TOTAL_SUPPLY = 1_000;

        return defaultHapiSpec("callDissociateWithDelegateContractKey")
                .given(
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        fileCreate(THE_CONTRACT),
                        updateLargeFile(ACCOUNT, THE_CONTRACT,
                                extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
                        cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(TOTAL_SUPPLY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
                                                newKeyNamed("delegateContractKey").shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key("delegateContractKey"),
                                                cryptoUpdate(TOKEN_TREASURY).key("delegateContractKey"),

                                                tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                                                cryptoTransfer(
                                                        moving(1, VANILLA_TOKEN)
                                                                .between(TOKEN_TREASURY, ACCOUNT)),

                                                contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
                                                        asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("nonZeroTokenBalanceDissociateWithDelegateContractKeyFailedTxn")
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),

                                                cryptoTransfer(
                                                        moving(1, VANILLA_TOKEN)
                                                                .between(ACCOUNT, TOKEN_TREASURY)),

                                                contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
                                                        asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("tokenDissociateWithDelegateContractKeyHappyTxn")
                                                        .hasKnownStatus(SUCCESS)
                                        )
                        )
                ).then(
                        childRecordsCheck("nonZeroTokenBalanceDissociateWithDelegateContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED, recordWith()
                                .status(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)),
                        childRecordsCheck("tokenDissociateWithDelegateContractKeyHappyTxn", SUCCESS, recordWith()
                                .status(SUCCESS)),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN).logged()
                );
    }

    public HapiApiSpec callForDissociateWithContractKey() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final String THE_CONTRACT = "Associate/Dissociate Contract";
        final long TOTAL_SUPPLY = 1_000;

        return defaultHapiSpec("callDissociateWithContractKey")
                .given(
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        fileCreate(THE_CONTRACT),
                        updateLargeFile(ACCOUNT, THE_CONTRACT,
                                extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
                        cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(TOTAL_SUPPLY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
                ).when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
                                                newKeyNamed("contractKey").shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key("contractKey"),
                                                cryptoUpdate(TOKEN_TREASURY).key("contractKey"),

                                                tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                                                cryptoTransfer(
                                                        moving(1, VANILLA_TOKEN)
                                                                .between(TOKEN_TREASURY, ACCOUNT)),

                                                contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
                                                        asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("nonZeroTokenBalanceDissociateWithContractKeyFailedTxn")
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),

                                                cryptoTransfer(
                                                        moving(1, VANILLA_TOKEN)
                                                                .between(ACCOUNT, TOKEN_TREASURY)),

                                                contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
                                                        asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("tokenDissociateWithContractKeyHappyTxn")
                                                        .hasKnownStatus(SUCCESS)
                                        )
                        )
                ).then(
                        childRecordsCheck("nonZeroTokenBalanceDissociateWithContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED, recordWith()
                                .status(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)),
                        childRecordsCheck("tokenDissociateWithContractKeyHappyTxn", SUCCESS, recordWith()
                                .status(SUCCESS)),
                        getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN).logged()
                );
    }

    private HapiApiSpec callForBurnWithDelegateContractKey() {
        final var theContract = "burn token";
        final var multiKey = "purpose";
        final String ALICE = "Alice";
        final String TOKEN = "Token";

        return defaultHapiSpec("callBurnWithDelegateContractKey")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(multiKey)
                                .adminKey(multiKey)
                                .treasury(TOKEN_TREASURY),
                        fileCreate("bytecode").payingWith(ALICE),
                        updateLargeFile(ALICE, "bytecode", extractByteCode(ContractResources.BURN_TOKEN)),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(theContract, ContractResources.BURN_TOKEN_CONSTRUCTOR_ABI,
                                                        asAddress(spec.registry().getTokenID(TOKEN)))
                                                        .payingWith(ALICE)
                                                        .bytecode("bytecode")
                                                        .via("creationTx")
                                                        .gas(28_000))),
                        getTxnRecord("creationTx").logged()
                )
                .when(

                        newKeyNamed("delegateContractKey").shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, theContract))),
                        tokenUpdate(TOKEN)
                                .supplyKey("delegateContractKey"),

                        contractCall(theContract, BURN_TOKEN_ABI, 1, new ArrayList<Long>())
                                .via("burn with contract key")
                                .gas(48_000),

                        childRecordsCheck("burn with contract key", SUCCESS, recordWith()
                                .status(SUCCESS)
                                .tokenTransfers(
                                        changingFungibleBalances()
                                                .including(TOKEN, TOKEN_TREASURY, -1)
                                )
                                .newTotalSupply(49)
                        )

                )
                .then(
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 49)

                );
    }

    private HapiApiSpec callForBurnWithContractKey() {
        final var theContract = "burn token";
        final var multiKey = "purpose";
        final String ALICE = "Alice";
        final String TOKEN = "Token";

        return defaultHapiSpec("callBurnWithContractKey")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(multiKey)
                                .adminKey(multiKey)
                                .treasury(TOKEN_TREASURY),
                        fileCreate("bytecode").payingWith(ALICE),
                        updateLargeFile(ALICE, "bytecode", extractByteCode(ContractResources.BURN_TOKEN)),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(theContract, ContractResources.BURN_TOKEN_CONSTRUCTOR_ABI,
                                                        asAddress(spec.registry().getTokenID(TOKEN)))
                                                        .payingWith(ALICE)
                                                        .bytecode("bytecode")
                                                        .via("creationTx")
                                                        .gas(28_000))),
                        getTxnRecord("creationTx").logged()
                )
                .when(

                        newKeyNamed("contractKey").shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, theContract))),
                        tokenUpdate(TOKEN)
                                .supplyKey("contractKey"),

                        contractCall(theContract, BURN_TOKEN_ABI, 1, new ArrayList<Long>())
                                .via("burn with contract key")
                                .gas(48_000),

                        childRecordsCheck("burn with contract key", SUCCESS, recordWith()
                                .status(SUCCESS)
                                .tokenTransfers(
                                        changingFungibleBalances()
                                                .including(TOKEN, TOKEN_TREASURY, -1)
                                )
                                .newTotalSupply(49)
                        )

                )
                .then(
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 49)

                );
    }

	private HapiApiSpec HSCS_KEY_MIXED_FRAMES_SCENARIOS() {
		final var theAccount = "theAccount";
		final var fungibleToken = "fungibleToken";
		final var innerContract = "MixedMintTokenContract";
		final var outerContract = "MixedFramesScenarios";
		final var multiKey = "purpose";
		final var delegateContractDelegateContractShape = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT,
				DELEGATE_CONTRACT);
		final var contractDelegateContractShape = KeyShape.threshOf(1, SIMPLE, KeyShape.CONTRACT, DELEGATE_CONTRACT);
		final var delegateContractDelegateContractKey = "delegateContractDelegateContractKey";
		final var contractDelegateContractKey = "contractDelegateContractKey";

		return defaultHapiSpec("HSCS_KEY_MIXED_FRAMES_SCENARIOS")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(50L)
								.supplyKey(multiKey)
								.adminKey(multiKey)
								.treasury(TOKEN_TREASURY),
						fileCreate(innerContract).payingWith(theAccount),
						updateLargeFile(theAccount, innerContract, extractByteCode(ContractResources.MIXED_MINT_TOKEN_CONTRACT)),
						fileCreate(outerContract).payingWith(theAccount),
						updateLargeFile(theAccount, outerContract, extractByteCode(ContractResources.MIXED_FRAMES_SCENARIOS)),
						contractCreate(innerContract)
								.bytecode(innerContract)
								.gas(100_000),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, ContractResources.MIXED_FRAMES_SCENARIOS_CONS_ABI,
														getNestedContractAddress(innerContract, spec))
														.payingWith(theAccount)
														.bytecode(outerContract)
														.via("creationTx")
														.gas(100_000))),
						getTxnRecord("creationTx").logged()
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(delegateContractDelegateContractKey).shape(delegateContractDelegateContractShape.signedWith(sigs(ON,
														innerContract, outerContract))),
												tokenUpdate(fungibleToken)
														.supplyKey(delegateContractDelegateContractKey).logged(),
												contractCall(outerContract,
														ContractResources.BURN_CALL_AFTER_NESTED_MINT_CALL_WITH_PRECOMPILE_CALL,
														1, asAddress(spec.registry().getTokenID(fungibleToken)))
														.payingWith(theAccount)
														.via("burnCallAfterNestedMintCallWithPrecompileCall").logged(),
												contractCall(outerContract,
														ContractResources.BURN_DELEGATE_CALL_AFTER_NESTED_MINT_CALL_WITH_PRECOMPILE_DELEGATE_CALL,
														1, asAddress(spec.registry().getTokenID(fungibleToken)))
														.payingWith(theAccount)
														.via(
																"burnDelegateCallAfterNestedMintCallWithPrecompileDelegateCall").logged(),
												contractCall(outerContract,
														ContractResources.BURN_DELEGATE_CALL_AFTER_NESTED_MINT_DELEGATE_CALL_WITH_PRECOMPILE_DELEGATE_CALL,
														1, asAddress(spec.registry().getTokenID(fungibleToken)))
														.payingWith(theAccount)
														.via(
																"burnDelegateCallAfterNestedMintDelegateCallWithPrecompileDelegateCall").logged(),
												contractCall(outerContract,
														ContractResources.BURN_CALL_AFTER_NESTED_MINT_DELEGATE_CALL_WITH_PRECOMPILE_DELEGATE_CALL,
														1, asAddress(spec.registry().getTokenID(fungibleToken)))
														.payingWith(theAccount)
														.via(
																"burnCallAfterNestedMintDelegateCallWithPrecompileDelegateCall").logged()
										)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(contractDelegateContractKey).shape(contractDelegateContractShape.signedWith(sigs(ON,
														innerContract, outerContract))),
												tokenUpdate(fungibleToken)
														.supplyKey(contractDelegateContractKey).logged(),
												contractCall(outerContract,
														ContractResources.BURN_DELEGATE_CALL_AFTER_NESTED_MINT_CALL_WITH_PRECOMPILE_CALL,
														1, asAddress(spec.registry().getTokenID(fungibleToken)))
														.payingWith(theAccount)
														.via("burnDelegateCallAfterNestedMintCallWithPrecompileCall").logged(),
												contractCall(outerContract,
														ContractResources.BURN_DELEGATE_CALL_AFTER_NESTED_MINT_DELEGATE_CALL_WITH_PRECOMPILE_CALL,
														1, asAddress(spec.registry().getTokenID(fungibleToken)))
														.payingWith(theAccount)
														.via(
																"burnDelegateCallAfterNestedMintDelegateCallWithPrecompileCall").logged(),
												contractCall(outerContract,
														ContractResources.BURN_CALL_AFTER_NESTED_MINT_DELEGATE_CALL_WITH_PRECOMPILE_CALL,
														1, asAddress(spec.registry().getTokenID(fungibleToken)))
														.payingWith(theAccount)
														.via(
																"burnCallAfterNestedMintDelegateCallWithPrecompileCall").logged()

										)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(contractDelegateContractKey).shape(contractDelegateContractShape.signedWith(sigs(ON,
														outerContract, innerContract))),
												tokenUpdate(fungibleToken)
														.supplyKey(contractDelegateContractKey).logged(),
												contractCall(outerContract, ContractResources.BURN_CALL_AFTER_NESTED_MINT_CALL_WITH_PRECOMPILE_DELEGATE_CALL,
														1, asAddress(spec.registry().getTokenID(fungibleToken)))
														.payingWith(theAccount)
														.via("burnCallAfterNestedMintCallWithPrecompileDelegateCall").logged()
										)),
						childRecordsCheck("burnCallAfterNestedMintCallWithPrecompileCall", SUCCESS, recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, 1)
										)
										.newTotalSupply(51),
								recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						),
						childRecordsCheck("burnDelegateCallAfterNestedMintCallWithPrecompileCall", SUCCESS, recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, 1)
										)
										.newTotalSupply(51),
								recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						),
						childRecordsCheck("burnDelegateCallAfterNestedMintDelegateCallWithPrecompileCall", SUCCESS, recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, 1)
										)
										.newTotalSupply(51),
								recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						),
						childRecordsCheck("burnCallAfterNestedMintDelegateCallWithPrecompileCall", SUCCESS, recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, 1)
										)
										.newTotalSupply(51),
								recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						),
						childRecordsCheck("burnCallAfterNestedMintCallWithPrecompileDelegateCall", SUCCESS, recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, 1)
										)
										.newTotalSupply(51),
								recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						),
						childRecordsCheck("burnDelegateCallAfterNestedMintCallWithPrecompileDelegateCall", SUCCESS, recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, 1)
										)
										.newTotalSupply(51),
								recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						),
						childRecordsCheck("burnDelegateCallAfterNestedMintDelegateCallWithPrecompileDelegateCall", SUCCESS, recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, 1)
										)
										.newTotalSupply(51),
								recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						),
						childRecordsCheck("burnCallAfterNestedMintDelegateCallWithPrecompileDelegateCall", SUCCESS, recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, 1)
										)
										.newTotalSupply(51),
								recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						)
				)
				.then(
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(fungibleToken, 50)
				);
	}

	private HapiApiSpec HSCS_KEY_2_TRANSFER() {
		return defaultHapiSpec("HSCS_KEY_2_TRANSFER")
				.given(
				).when(
				).then(
				);
	}

	@NotNull
	private String getNestedContractAddress(String contract, HapiApiSpec spec) {
		return CommonUtils.calculateSolidityAddress(
				(int) spec.registry().getContractId(contract).getShardNum(),
				spec.registry().getContractId(contract).getRealmNum(),
				spec.registry().getContractId(contract).getContractNum());
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}