/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.hapi;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocalWithFunctionAbi;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.updateSpecFor;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Tag;

@HapiTestSuite(fuzzyMatch = true)
@Tag(SMART_CONTRACT)
public class ContractCallLocalSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(ContractCallLocalSuite.class);
    private static final String CONTRACT = "CreateTrivial";
    private static final String OWNERSHIP_CHECK_CONTRACT = "OwnershipCheck";
    private static final String OWNERSHIP_CHECK_CONTRACT_IS_OWNER_FUNCTION = "isOwner";
    private static final String TOKEN = "TestToken";
    private static final String NFT_TOKEN = "NftToken";
    private static final String SUPPLY_KEY = "SupplyKey";
    private static final String FIRST_MEMO = "firstMemo";
    private static final String SECOND_MEMO = "secondMemo";
    private static final String SYMBOL = "ħT";
    private static final int DECIMALS = 13;

    public static void main(String... args) {
        new ContractCallLocalSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                successOnDeletedContract(),
                invalidContractID(),
                impureCallFails(),
                insufficientFeeFails(),
                lowBalanceFails(),
                erc20Query(),
                vanillaSuccess(),
                callLocalDoesNotCheckSignaturesNorPayer(),
                htsOwnershipCheckWorksWithAliasAddress());
    }

    @HapiTest
    final HapiSpec htsOwnershipCheckWorksWithAliasAddress() {
        final AtomicReference<AccountID> ecdsaAccountId = new AtomicReference<>();
        final AtomicReference<ByteString> ecdsaAccountIdLongZeroAddress = new AtomicReference<>();
        final AtomicReference<ByteString> ecdsaAccountIdAlias = new AtomicReference<>();
        final AtomicReference<com.esaulpaugh.headlong.abi.Address> nftOwnerAddress = new AtomicReference<>();
        final AtomicReference<com.esaulpaugh.headlong.abi.Address> senderAddress = new AtomicReference<>();

        return defaultHapiSpec("htsOwnershipCheckWorksWithAliasAddress", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        newKeyNamed(SUPPLY_KEY),
                        // Create an NFT
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0L)
                                .supplyKey(SUPPLY_KEY),
                        mintToken(NFT_TOKEN, List.of(metadata(FIRST_MEMO), metadata(SECOND_MEMO)), "nftMint"),
                        // Create an account with alias
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(TokenMovement.movingUnique(NFT_TOKEN, 1L)
                                .between(TOKEN_TREASURY, SECP_256K1_SOURCE_KEY)),
                        // Send some HBAR to the aliased account, it will need it to pay for the query
                        cryptoTransfer(
                                TokenMovement.movingHbar(ONE_HUNDRED_HBARS).between(GENESIS, SECP_256K1_SOURCE_KEY)),
                        // Calculate and log the aliased account addresses
                        withOpContext((spec, opLog) -> {
                            updateSpecFor(spec, SECP_256K1_SOURCE_KEY);
                            final var registry = spec.registry();
                            final var ecdsaKey = registry.getKey(SECP_256K1_SOURCE_KEY);
                            final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                            final var addressBytes = recoverAddressFromPubKey(tmp);
                            final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                            ecdsaAccountId.set(registry.getAccountID(SECP_256K1_SOURCE_KEY));
                            ecdsaAccountIdLongZeroAddress.set(
                                    ByteString.copyFrom(asSolidityAddress(ecdsaAccountId.get())));
                            ecdsaAccountIdAlias.set(evmAddressBytes);
                            var logIt = logIt("\nAccount ID: " + ecdsaAccountId.get() + "\n" + " Long-zero address: "
                                    + Address.wrap(Bytes.of(
                                            ecdsaAccountIdLongZeroAddress.get().toByteArray())) + "\n"
                                    + " Alias Recovered: "
                                    + Address.wrap(
                                            Bytes.of(ecdsaAccountIdAlias.get().toByteArray())));
                            allRunFor(spec, logIt);
                        }),
                        // Deploy the OwnershipCheck contract
                        uploadInitCode(OWNERSHIP_CHECK_CONTRACT),
                        contractCreate(OWNERSHIP_CHECK_CONTRACT))
                .when(withOpContext((spec, opLog) -> {
                    // Make the contract query with the Aliased account
                    var callLocal = contractCallLocal(
                                    OWNERSHIP_CHECK_CONTRACT,
                                    OWNERSHIP_CHECK_CONTRACT_IS_OWNER_FUNCTION,
                                    HapiParserUtil.asHeadlongAddress(
                                            asAddress(spec.registry().getTokenID(NFT_TOKEN))),
                                    1L)
                            .payingWith(SECP_256K1_SOURCE_KEY)
                            .nodePayment(10 * ONE_HBAR)
                            .exposingTypedResultsTo(results -> {
                                nftOwnerAddress.set((com.esaulpaugh.headlong.abi.Address) results[0]);
                                senderAddress.set((com.esaulpaugh.headlong.abi.Address) results[1]);
                            });
                    allRunFor(spec, callLocal);
                }))
                .then(
                        // Assert that the address of the query sender and the address of the nft owner returned by the
                        // HTS precompiled contract are the same
                        withOpContext((spec, opLog) -> assertEquals(
                                senderAddress.get(),
                                nftOwnerAddress.get(),
                                "Sender address should match the owner address.")));
    }

    @HapiTest
    final HapiSpec vanillaSuccess() {
        return defaultHapiSpec("vanillaSuccess", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT).adminKey(THRESHOLD))
                .when(contractCall(CONTRACT, "create").gas(785_000))
                .then(
                        sleepFor(3_000L),
                        contractCallLocal(CONTRACT, "getIndirect")
                                .has(resultWith()
                                        .resultViaFunctionName("getIndirect", CONTRACT, isLiteralResult(new Object[] {
                                            BigInteger.valueOf(7L)
                                        }))));
    }

    @HapiTest
    final HapiSpec impureCallFails() {
        return defaultHapiSpec("impureCallFails", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT).adminKey(THRESHOLD))
                .when()
                .then(
                        sleepFor(3_000L),
                        contractCallLocal(CONTRACT, "create")
                                .nodePayment(1_234_567)
                                .hasAnswerOnlyPrecheck(ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION));
    }

    @HapiTest
    final HapiSpec successOnDeletedContract() {
        return defaultHapiSpec("SuccessOnDeletedContract", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(contractDelete(CONTRACT))
                .then(contractCallLocal(CONTRACT, "create")
                        .nodePayment(1_234_567)
                        .hasAnswerOnlyPrecheck(OK));
    }

    @HapiTest
    final HapiSpec invalidContractID() {
        final var invalidContract = HapiSpecSetup.getDefaultInstance().invalidContractName();
        final var functionAbi = getABIFor(FUNCTION, "getIndirect", "CreateTrivial");
        return defaultHapiSpec("InvalidContractID", NONDETERMINISTIC_TRANSACTION_FEES)
                .given()
                .when()
                .then(
                        contractCallLocalWithFunctionAbi(invalidContract, functionAbi)
                                .nodePayment(1_234_567)
                                .hasAnswerOnlyPrecheck(INVALID_CONTRACT_ID),
                        contractCallLocalWithFunctionAbi("0.0.0", functionAbi)
                                .nodePayment(1_234_567)
                                .hasAnswerOnlyPrecheck(INVALID_CONTRACT_ID));
    }

    @HapiTest
    final HapiSpec insufficientFeeFails() {
        final long adequateQueryPayment = 500_000L;

        return defaultHapiSpec("insufficientFeeFails", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(cryptoCreate("payer"), uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(contractCall(CONTRACT, "create").gas(785_000))
                .then(
                        sleepFor(3_000L),
                        contractCallLocal(CONTRACT, "getIndirect")
                                .nodePayment(adequateQueryPayment)
                                .fee(0L)
                                .payingWith("payer")
                                .hasAnswerOnlyPrecheck(INSUFFICIENT_TX_FEE));
    }

    @HapiTest
    final HapiSpec lowBalanceFails() {
        final long adequateQueryPayment = 500_000_000L;

        return defaultHapiSpec("lowBalanceFails", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        cryptoCreate("payer"),
                        cryptoCreate("payer").balance(adequateQueryPayment),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT))
                .when(contractCall(CONTRACT, "create").gas(785_000))
                .then(
                        sleepFor(3_000L),
                        contractCallLocal(CONTRACT, "getIndirect")
                                .logged()
                                .payingWith("payer")
                                .nodePayment(adequateQueryPayment)
                                .hasAnswerOnlyPrecheck(INSUFFICIENT_PAYER_BALANCE),
                        getAccountBalance("payer").logged(),
                        sleepFor(1_000L),
                        getAccountBalance("payer").logged());
    }

    @HapiTest
    final HapiSpec erc20Query() {
        final var decimalsABI = "{\"constant\": true,\"inputs\": [],\"name\": \"decimals\","
                + "\"outputs\": [{\"name\": \"\",\"type\": \"uint8\"}],\"payable\": false,"
                + "\"type\": \"function\"}";

        return defaultHapiSpec("erc20Query", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(tokenCreate(TOKEN).decimals(DECIMALS).symbol(SYMBOL).asCallableContract())
                .when()
                .then(contractCallLocalWithFunctionAbi(TOKEN, decimalsABI)
                        .has(resultWith().resultThruAbi(decimalsABI, isLiteralResult(new Object[] {DECIMALS}))));
    }

    // https://github.com/hashgraph/hedera-services/pull/5485
    @HapiTest
    final HapiSpec callLocalDoesNotCheckSignaturesNorPayer() {
        return defaultHapiSpec("callLocalDoesNotCheckSignaturesNorPayer", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT).adminKey(THRESHOLD))
                .when(contractCall(CONTRACT, "create").gas(785_000))
                .then(withOpContext((spec, opLog) -> IntStream.range(0, 2000).forEach(i -> {
                    final var create = cryptoCreate("account #" + i).deferStatusResolution();
                    final var callLocal = contractCallLocal(CONTRACT, "getIndirect")
                            .nodePayment(ONE_HBAR)
                            .hasAnswerOnlyPrecheckFrom(OK, BUSY);
                    allRunFor(spec, create, callLocal);
                })));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
