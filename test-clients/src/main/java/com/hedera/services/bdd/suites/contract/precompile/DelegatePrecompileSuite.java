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
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.legacy.core.CommonUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers.changingFungibleBalances;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.DELEGATE_BURN_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.DELEGATE_MINT_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.DELEGATE_TRANSFER_CALL_ABI;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
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
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class DelegatePrecompileSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(DelegatePrecompileSuite.class);
    private static final KeyShape SIMPLE_AND_DELEGATE_KEY_SHAPE = KeyShape.threshOf(1, KeyShape.SIMPLE,
            DELEGATE_CONTRACT);
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, DELEGATE_CONTRACT);
    private static final String TOKEN_TREASURY = "treasury";
    private static final String OUTER_CONTRACT = "Outer Contract";
    private static final String INNER_CONTRACT = "Inner Contract";
    private static final String ACCOUNT = "anybody";
    private static final String RECEIVER = "receiver";
    private static final String DELEGATE_KEY = "Delegate key";
    private static final String SIMPLE_AND_DELEGATE_KEY_NAME = "Simple And Delegate key";

    public static void main(String... args) {
        new DelegatePrecompileSuite().runSuiteSync();
    }

    @Override
    public boolean canRunAsync() {
        return false;
    }

    @Override
    protected List<HapiApiSpec> getSpecsInSuite() {
        return allOf(
                positiveSpecs(),
                negativeSpecs()
        );
    }

    List<HapiApiSpec> negativeSpecs() {
        return List.of(

        );
    }

    List<HapiApiSpec> positiveSpecs() {
        return List.of(
                delegateCallForTransfer(),
                delegateCallForBurn(),
                delegateCallForMint()
        );
    }


    private HapiApiSpec delegateCallForTransfer() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> receiverID = new AtomicReference<>();
        final var supplyKey = "supplyKey";


        return defaultHapiSpec("delegateCallForTransfer")
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

                                                newKeyNamed(SIMPLE_AND_DELEGATE_KEY_NAME).shape(SIMPLE_AND_DELEGATE_KEY_SHAPE.signedWith(sigs(ON, OUTER_CONTRACT))),
                                                cryptoUpdate(ACCOUNT).key(SIMPLE_AND_DELEGATE_KEY_NAME),

                                                contractCall(OUTER_CONTRACT, DELEGATE_TRANSFER_CALL_ABI,
                                                        asAddress(vanillaTokenTokenID.get()), asAddress(accountID.get()),
                                                        asAddress(receiverID.get()), 1L)
                                                        .payingWith(GENESIS)
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

    private HapiApiSpec delegateCallForBurn() {
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final var supplyKey = "supplyKey";

        return defaultHapiSpec("delegateCallForBurn")
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

                                                newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(OUTER_CONTRACT))),
                                                tokenUpdate(VANILLA_TOKEN).supplyKey(DELEGATE_KEY),

                                                contractCall(OUTER_CONTRACT, DELEGATE_BURN_CALL_ABI,
                                                        asAddress(vanillaTokenTokenID.get()), 0, List.of(1L))
                                                        .payingWith(GENESIS)
                                                        .via("delegateBurnCallWithDelegateContractKeyTxn")
                                                        .gas(5_000_000),

                                                childRecordsCheck("delegateBurnCallWithDelegateContractKeyTxn", SUCCESS, recordWith()
                                                        .status(SUCCESS)
                                                        .newTotalSupply(1)
                                                )
                                        )
                        )
                )
                .then(
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, 1)

                );
    }

    private HapiApiSpec delegateCallForMint() {
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final var supplyKey = "supplyKey";

        return defaultHapiSpec("delegateCallForMint")
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

                                                newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(OUTER_CONTRACT))),
                                                tokenUpdate(VANILLA_TOKEN).supplyKey(DELEGATE_KEY),

                                                contractCall(OUTER_CONTRACT, DELEGATE_MINT_CALL_ABI,
                                                        asAddress(vanillaTokenTokenID.get()), 1)
                                                        .payingWith(GENESIS)
                                                        .via("delegateBurnCallWithDelegateContractKeyTxn")
                                                        .gas(5_000_000),

                                                childRecordsCheck("delegateBurnCallWithDelegateContractKeyTxn", SUCCESS, recordWith()
                                                        .status(SUCCESS)
                                                        .tokenTransfers(
                                                                changingFungibleBalances()
                                                                        .including(VANILLA_TOKEN, TOKEN_TREASURY, 1)
                                                        )
                                                        .newTotalSupply(51)
                                                )
                                        )
                        )
                )
                .then(
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, 51)
                );
    }


    @NotNull
    private String getNestedContractAddress(String outerContract, HapiApiSpec spec) {
        return CommonUtils.calculateSolidityAddress(
                (int) spec.registry().getContractId(outerContract).getShardNum(),
                spec.registry().getContractId(outerContract).getRealmNum(),
                spec.registry().getContractId(outerContract).getContractNum());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

