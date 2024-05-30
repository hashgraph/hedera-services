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

import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCreate;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class ContractCallV1SecurityModelSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(ContractCallV1SecurityModelSuite.class);

    public static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";
    public static final String TRANSFERRING_CONTRACT = "Transferring";
    private static final String OWNER = "owner";
    private static final String CONTRACT_CALLER = "contractCaller";
    private static final String RECEIVABLE_SIG_REQ_ACCOUNT = "receivableSigReqAccount";
    private static final String RECEIVABLE_SIG_REQ_ACCOUNT_INFO = "receivableSigReqAccountInfo";
    private static final String TRANSFER_TO_ADDRESS = "transferToAddress";
    public static final String STATE_MUTABILITY_NONPAYABLE_TYPE_FUNCTION =
            " \"stateMutability\": \"nonpayable\", \"type\": \"function\" }";

    public static void main(String... args) {
        new ContractCallV1SecurityModelSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(
                contractTransferToSigReqAccountWithKeySucceeds(),
                canMintAndTransferInSameContractOperation(),
                workingHoursDemo(),
                lpFarmSimulation());
    }

    final Stream<DynamicTest> workingHoursDemo() {
        final var gasToOffer = 4_000_000;
        final var contract = "WorkingHours";
        final var ticketToken = "ticketToken";
        final var adminKey = "admin";
        final var treasury = "treasury";
        final var newSupplyKey = "newSupplyKey";

        final var ticketTaking = "ticketTaking";
        final var ticketWorking = "ticketWorking";
        final var mint = "minting";
        final var burn = "burning";
        final var preMints = List.of(ByteString.copyFromUtf8("HELLO"), ByteString.copyFromUtf8("GOODBYE"));

        final AtomicLong ticketSerialNo = new AtomicLong();

        return propertyPreservingHapiSpec("WorkingHoursDemo")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenBurn,TokenCreate,TokenMint,TokenUpdate",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(adminKey),
                        cryptoCreate(treasury),
                        cryptoCreate(OWNER).balance(ONE_MILLION_HBARS),
                        // we need a new user, expiry to 1 Jan 2100 costs 11M gas for token
                        // associate
                        tokenCreate(ticketToken)
                                .treasury(treasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .supplyType(TokenSupplyType.INFINITE)
                                .adminKey(adminKey)
                                .supplyKey(adminKey),
                        mintToken(ticketToken, preMints).via(mint),
                        burnToken(ticketToken, List.of(1L)).via(burn),
                        uploadInitCode(contract))
                .when(
                        withOpContext((spec, opLog) -> {
                            final var registry = spec.registry();
                            final var tokenId = registry.getTokenID(ticketToken);
                            final var treasuryId = registry.getAccountID(treasury);
                            final var creation = contractCreate(
                                            contract,
                                            asHeadlongAddress(asAddress(tokenId)),
                                            asHeadlongAddress(asAddress(treasuryId)))
                                    .gas(gasToOffer);
                            allRunFor(spec, creation);
                        }),
                        newKeyNamed(newSupplyKey).shape(KeyShape.CONTRACT.signedWith(contract)),
                        tokenUpdate(ticketToken).supplyKey(newSupplyKey).signedByPayerAnd(adminKey))
                .then(
                        /* Take a ticket */
                        contractCall(contract, "takeTicket")
                                .alsoSigningWithFullPrefix(OWNER, treasury)
                                .payingWith(OWNER)
                                .gas(4_000_000)
                                .via(ticketTaking)
                                .exposingResultTo(result -> {
                                    LOG.info("Explicit mint result is {}", result);
                                    ticketSerialNo.set(((Long) result[0]));
                                }),
                        getTxnRecord(ticketTaking),
                        getAccountBalance(OWNER).logged().hasTokenBalance(ticketToken, 1L),
                        /* Our ticket number is 3 (b/c of the two pre-mints), so we must call
                         * work twice before the contract will actually accept our ticket. */
                        sourcing(() -> contractCall(contract, "workTicket", ticketSerialNo.get())
                                .gas(2_000_000)
                                .alsoSigningWithFullPrefix(OWNER)
                                .payingWith(OWNER)),
                        getAccountBalance(OWNER).logged().hasTokenBalance(ticketToken, 1L),
                        sourcing(() -> contractCall(contract, "workTicket", ticketSerialNo.get())
                                .gas(2_000_000)
                                .alsoSigningWithFullPrefix(OWNER)
                                .payingWith(OWNER)
                                .via(ticketWorking)),
                        getAccountBalance(OWNER).hasTokenBalance(ticketToken, 0L),
                        getTokenInfo(ticketToken).hasTotalSupply(1L),
                        /* Review the history */
                        getTxnRecord(ticketTaking).andAllChildRecords().logged(),
                        getTxnRecord(ticketWorking).andAllChildRecords().logged());
    }

    final Stream<DynamicTest> canMintAndTransferInSameContractOperation() {
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
        final var nfToken = "nfToken";
        final var multiKey = "multiKey";
        final var aCivilian = "aCivilian";
        final var treasuryContract = "SomeERC721Scenarios";
        final var mintAndTransferTxn = "mintAndTransferTxn";
        final var mintAndTransferAndBurnTxn = "mintAndTransferAndBurnTxn";

        return propertyPreservingHapiSpec("CanMintAndTransferInSameContractOperation")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenCreate,TokenMint",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(multiKey),
                        cryptoCreate(aCivilian)
                                .exposingCreatedIdTo(id -> aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        uploadInitCode(treasuryContract),
                        contractCreate(treasuryContract).adminKey(multiKey),
                        tokenCreate(nfToken)
                                .supplyKey(multiKey)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(treasuryContract)
                                .initialSupply(0)
                                .exposingCreatedIdTo(idLit ->
                                        tokenMirrorAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(idLit)))),
                        mintToken(
                                nfToken,
                                List.of(
                                        // 1
                                        ByteString.copyFromUtf8("A penny for"),
                                        // 2
                                        ByteString.copyFromUtf8("the Old Guy"))),
                        tokenAssociate(aCivilian, nfToken),
                        cryptoTransfer(movingUnique(nfToken, 2L).between(treasuryContract, aCivilian)))
                .when(sourcing(() -> contractCall(
                                treasuryContract,
                                "nonSequiturMintAndTransfer",
                                asHeadlongAddress(tokenMirrorAddr.get()),
                                asHeadlongAddress(aCivilianMirrorAddr.get()))
                        .via(mintAndTransferTxn)
                        .gas(4_000_000)
                        .alsoSigningWithFullPrefix(multiKey)))
                .then(
                        getTokenInfo(nfToken).hasTotalSupply(4L),
                        getTokenNftInfo(nfToken, 3L)
                                .hasSerialNum(3L)
                                .hasAccountID(aCivilian)
                                .hasMetadata(ByteString.copyFrom(new byte[] {(byte) 0xee})),
                        getTokenNftInfo(nfToken, 4L)
                                .hasSerialNum(4L)
                                .hasAccountID(aCivilian)
                                .hasMetadata(ByteString.copyFrom(new byte[] {(byte) 0xff})),
                        sourcing(() -> contractCall(
                                        treasuryContract,
                                        "nonSequiturMintAndTransferAndBurn",
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()))
                                .via(mintAndTransferAndBurnTxn)
                                .gas(4_000_000)
                                .alsoSigningWithFullPrefix(multiKey, aCivilian)));
    }

    final Stream<DynamicTest> contractTransferToSigReqAccountWithKeySucceeds() {
        return propertyPreservingHapiSpec("ContractTransferToSigReqAccountWithKeySucceeds")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        cryptoCreate(CONTRACT_CALLER).balance(1_000_000_000_000L),
                        cryptoCreate(RECEIVABLE_SIG_REQ_ACCOUNT)
                                .balance(1_000_000_000_000L)
                                .receiverSigRequired(true),
                        getAccountInfo(CONTRACT_CALLER).savingSnapshot("contractCallerInfo"),
                        getAccountInfo(RECEIVABLE_SIG_REQ_ACCOUNT).savingSnapshot(RECEIVABLE_SIG_REQ_ACCOUNT_INFO),
                        uploadInitCode(TRANSFERRING_CONTRACT))
                .when(contractCreate(TRANSFERRING_CONTRACT).gas(300_000L).balance(5000L))
                .then(withOpContext((spec, opLog) -> {
                    final var accountAddress = spec.registry()
                            .getAccountInfo(RECEIVABLE_SIG_REQ_ACCOUNT_INFO)
                            .getContractAccountID();
                    final var receivableAccountKey = spec.registry()
                            .getAccountInfo(RECEIVABLE_SIG_REQ_ACCOUNT_INFO)
                            .getKey();
                    final var contractCallerKey =
                            spec.registry().getAccountInfo("contractCallerInfo").getKey();
                    spec.registry().saveKey("receivableKey", receivableAccountKey);
                    spec.registry().saveKey("contractCallerKey", contractCallerKey);
                    /* if any of the keys are missing, INVALID_SIGNATURE is returned */
                    final var call = contractCall(
                                    TRANSFERRING_CONTRACT,
                                    TRANSFER_TO_ADDRESS,
                                    asHeadlongAddress(accountAddress),
                                    BigInteger.ONE)
                            .payingWith(CONTRACT_CALLER)
                            .gas(300_000)
                            .alsoSigningWithFullPrefix("receivableKey");
                    /* calling with the receivableSigReqAccount should pass without adding keys */
                    final var callWithReceivable = contractCall(
                                    TRANSFERRING_CONTRACT,
                                    TRANSFER_TO_ADDRESS,
                                    asHeadlongAddress(accountAddress),
                                    BigInteger.ONE)
                            .payingWith(RECEIVABLE_SIG_REQ_ACCOUNT)
                            .gas(300_000)
                            .hasKnownStatus(SUCCESS);
                    allRunFor(spec, call, callWithReceivable);
                }));
    }

    final Stream<DynamicTest> lpFarmSimulation() {
        final var adminKey = "adminKey";
        final var gasToOffer = 4_000_000;
        final var farmInitcodeLoc = "src/main/resource/contract/bytecodes/farmInitcode.bin";
        final var consAbi = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": \"_devaddr\", \"type\":"
                + " \"address\" }, { \"internalType\": \"address\", \"name\": \"_rentPayer\","
                + " \"type\": \"address\" },     { \"internalType\": \"uint256\", \"name\":"
                + " \"_saucePerSecond\", \"type\": \"uint256\" }, { \"internalType\":"
                + " \"uint256\", \"name\": \"_hbarPerSecond\", \"type\": \"uint256\" }, {"
                + " \"internalType\": \"uint256\", \"name\": \"_maxSauceSupply\", \"type\":"
                + " \"uint256\" }, { \"internalType\": \"uint256\", \"name\":"
                + " \"_depositFeeTinyCents\", \"type\": \"uint256\" } ], \"stateMutability\":"
                + " \"nonpayable\", \"type\": \"constructor\" }";
        final var addPoolAbi = "{ \"inputs\": [ { \"internalType\": \"uint256\", \"name\": \"_allocPoint\","
                + " \"type\": \"uint256\" }, { \"internalType\": \"address\", \"name\":"
                + " \"_lpToken\", \"type\": \"address\" }       ], \"name\": \"add\","
                + " \"outputs\": [], \"stateMutability\": \"nonpayable\", \"type\":"
                + " \"function\" }";
        final var depositAbi = "{ \"inputs\": [ { \"internalType\": \"uint256\", \"name\": \"_pid\", \"type\":"
                + " \"uint256\" }, { \"internalType\": \"uint256\", \"name\": \"_amount\","
                + " \"type\": \"uint256\" } ], \"name\": \"deposit\", \"outputs\": [],"
                + " \"stateMutability\": \"payable\", \"type\": \"function\" }";
        final var withdrawAbi = "{ \"inputs\": [ { \"internalType\": \"uint256\", \"name\": \"_pid\", \"type\":"
                + " \"uint256\" }, { \"internalType\": \"uint256\", \"name\": \"_amount\","
                + " \"type\": \"uint256\" } ], \"name\": \"withdraw\", \"outputs\": [],"
                + STATE_MUTABILITY_NONPAYABLE_TYPE_FUNCTION;
        final var setSauceAbi = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": \"_sauce\", \"type\":"
                + " \"address\" } ], \"name\": \"setSauceAddress\", \"outputs\": [],"
                + STATE_MUTABILITY_NONPAYABLE_TYPE_FUNCTION;
        final var transferAbi = "{ \"inputs\": [ { \"internalType\": \"address\", \"name\": \"newOwner\", \"type\":"
                + " \"address\" } ], \"name\": \"transferOwnership\", \"outputs\": [],"
                + STATE_MUTABILITY_NONPAYABLE_TYPE_FUNCTION;
        final var initcode = "farmInitcode";
        final var farm = "farm";
        final var dev = "dev";
        final var lp = "lp";
        final var sauce = "sauce";
        final var rentPayer = "rentPayer";
        final AtomicReference<String> devAddr = new AtomicReference<>();
        final AtomicReference<String> ownerAddr = new AtomicReference<>();
        final AtomicReference<String> sauceAddr = new AtomicReference<>();
        final AtomicReference<String> lpTokenAddr = new AtomicReference<>();
        final AtomicReference<String> rentPayerAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("lpFarmSimulation")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenCreate,TokenMint,TokenUpdate",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(adminKey),
                        fileCreate(initcode),
                        cryptoCreate(OWNER)
                                .balance(ONE_MILLION_HBARS)
                                .exposingCreatedIdTo(id -> ownerAddr.set(asHexedSolidityAddress(id))),
                        cryptoCreate(dev)
                                .balance(ONE_MILLION_HBARS)
                                .exposingCreatedIdTo(id -> devAddr.set(asHexedSolidityAddress(id))),
                        cryptoCreate(rentPayer)
                                .balance(ONE_MILLION_HBARS)
                                .exposingCreatedIdTo(id -> rentPayerAddr.set(asHexedSolidityAddress(id))),
                        updateLargeFile(GENESIS, initcode, extractByteCode(farmInitcodeLoc)),
                        sourcing(() -> new HapiContractCreate(
                                        farm,
                                        consAbi,
                                        asHeadlongAddress(devAddr.get()),
                                        asHeadlongAddress(rentPayerAddr.get()),
                                        BigInteger.valueOf(4804540L),
                                        BigInteger.valueOf(10000L),
                                        BigInteger.valueOf(1000000000000000L),
                                        BigInteger.valueOf(2500000000L))
                                .bytecode(initcode)
                                .gas(500_000L)),
                        tokenCreate(sauce)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(300_000_000)
                                .maxSupply(1_000_000_000)
                                .treasury(farm)
                                .adminKey(adminKey)
                                .supplyKey(adminKey)
                                .exposingCreatedIdTo(idLit ->
                                        sauceAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(idLit)))),
                        tokenCreate(lp)
                                .treasury(dev)
                                .initialSupply(1_000_000_000)
                                .exposingCreatedIdTo(idLit ->
                                        lpTokenAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(idLit)))),
                        tokenAssociate(dev, sauce),
                        sourcing(
                                () -> contractCallWithFunctionAbi(farm, setSauceAbi, asHeadlongAddress(sauceAddr.get()))
                                        .gas(gasToOffer)
                                        .refusingEthConversion()),
                        sourcing(
                                () -> contractCallWithFunctionAbi(farm, transferAbi, asHeadlongAddress(ownerAddr.get()))
                                        .gas(gasToOffer)
                                        .refusingEthConversion()))
                .when(
                        sourcing(() -> contractCallWithFunctionAbi(
                                        farm,
                                        addPoolAbi,
                                        BigInteger.valueOf(2392L),
                                        asHeadlongAddress(lpTokenAddr.get()))
                                .via("add")
                                .payingWith(OWNER)
                                .gas(gasToOffer)
                                .refusingEthConversion()),
                        newKeyNamed("contractControl").shape(KeyShape.CONTRACT.signedWith(farm)),
                        tokenUpdate(sauce).supplyKey("contractControl").signedByPayerAnd(adminKey),
                        sourcing(() -> contractCallWithFunctionAbi(
                                        farm, depositAbi, BigInteger.ZERO, BigInteger.valueOf(100_000))
                                .sending(ONE_HUNDRED_HBARS)
                                .payingWith(dev)
                                .gas(gasToOffer)
                                .refusingEthConversion()),
                        sleepFor(1000),
                        sourcing(() -> contractCallWithFunctionAbi(
                                        farm, depositAbi, BigInteger.ZERO, BigInteger.valueOf(100_000))
                                .sending(ONE_HUNDRED_HBARS)
                                .payingWith(dev)
                                .gas(gasToOffer)
                                .via("second")
                                .refusingEthConversion()),
                        getTxnRecord("second").andAllChildRecords().logged())
                .then(sourcing(() -> contractCallWithFunctionAbi(
                                farm, withdrawAbi, BigInteger.ZERO, BigInteger.valueOf(200_000))
                        .payingWith(dev)
                        .gas(gasToOffer)
                        .refusingEthConversion()));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
