/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.ACCEPTED_MONO_GAS_CALCULATION_DIFFERENCE;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_CONSTRUCTOR_PARAMETERS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_FUNCTION_PARAMETERS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.assertTxnRecordHasNoTraceabilityEnrichedContractFnResult;
import static com.hedera.services.bdd.suites.contract.Utils.expectedPrecompileGasFor;
import static com.hedera.services.bdd.suites.contract.Utils.getNestedContractAddress;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V2_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.genRandomBytes;
import static com.hedera.services.bdd.suites.utils.contracts.FunctionParameters.functionParameters;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;

import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.NonFungibleTransfers;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.utils.contracts.FunctionParameters;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(SMART_CONTRACT)
public class ContractMintHTSSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(ContractMintHTSSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final long TOTAL_SUPPLY = 1_000;
    private static final String CONTRACT_KEY = "ContractKey";
    private static final String TOKEN_TREASURY = "treasury";
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE =
            KeyShape.threshOf(1, KeyShape.SIMPLE, DELEGATE_CONTRACT);
    private static final String DELEGATE_KEY = "DelegateKey";
    private static final String MULTI_KEY = "purpose";

    public static final String MINT_CONTRACT = "MintContract";
    public static final String NEGATIVE_MINT_CONTRACT = "NegativeMintContract";
    public static final String MINT_NFT_CONTRACT = "MintNFTContract";
    private static final String NESTED_MINT_CONTRACT = "NestedMint";
    private static final String ACCOUNT = "anybody";
    private static final String DELEGATE_CONTRACT_KEY_NAME = "contractKey";
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String TEST_METADATA_1 = "Test metadata 1";
    private static final String TEST_METADATA_2 = "Test metadata 2";
    private static final String RECIPIENT = "recipient";
    public static final String MINT_FUNGIBLE_TOKEN_WITH_EVENT = "mintFungibleTokenWithEvent";

    public static void main(final String... args) {
        new ContractMintHTSSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiSpec> negativeSpecs() {
        return List.of(
                rollbackOnFailedMintAfterFungibleTransfer(),
                mintTokensWithExtremeValues(),
                mintTokensWithInvalidValues());
    }

    List<HapiSpec> positiveSpecs() {
        return List.of(transferNftAfterNestedMint());
    }

    @HapiTest
    final HapiSpec mintTokensWithExtremeValues() {
        var mintExtremeValue = "mintExtremeValue";
        var mintInvalidAddressType = "mintInvalidAddressType";

        var invalidTokenNFTTest = "invalidTokenNFTTest";
        var invalidTokenTest = "invalidTokenTest";
        return defaultHapiSpec("MintFungibleTokenWithInvalidAndExtremeValues")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT).maxAutomaticTokenAssociations(1),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(1000)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(NEGATIVE_MINT_CONTRACT),
                        contractCreate(NEGATIVE_MINT_CONTRACT).gas(GAS_TO_OFFER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        // Fungible Mint calls with extreme values
                        contractCall(
                                        NEGATIVE_MINT_CONTRACT,
                                        mintExtremeValue,
                                        new byte[][] {},
                                        false,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))))
                                .payingWith(GENESIS)
                                .via("mintExtremeValue")
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        emptyChildRecordsCheck("mintExtremeValue", CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        NEGATIVE_MINT_CONTRACT,
                                        mintExtremeValue,
                                        new byte[][] {},
                                        true,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))))
                                .payingWith(GENESIS)
                                .via("mintNegativeExtremeValue")
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        emptyChildRecordsCheck("mintNegativeExtremeValue", CONTRACT_REVERT_EXECUTED),
                        contractCall(NEGATIVE_MINT_CONTRACT, mintInvalidAddressType, new byte[][] {}, 100L)
                                .payingWith(GENESIS)
                                .via(invalidTokenTest)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER),
                        // NFT Mint calls with extreme values
                        contractCall(
                                        NEGATIVE_MINT_CONTRACT,
                                        mintExtremeValue,
                                        new byte[][] {TEST_METADATA_1.getBytes()},
                                        false,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))))
                                .payingWith(GENESIS)
                                .via("mintExtremeValueNFT")
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        emptyChildRecordsCheck("mintExtremeValueNFT", CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        NEGATIVE_MINT_CONTRACT,
                                        mintExtremeValue,
                                        new byte[][] {TEST_METADATA_1.getBytes()},
                                        true,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))))
                                .payingWith(GENESIS)
                                .via("mintNegativeExtremeValueNFT")
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        emptyChildRecordsCheck("mintNegativeExtremeValueNFT", CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        NEGATIVE_MINT_CONTRACT,
                                        mintInvalidAddressType,
                                        new byte[][] {TEST_METADATA_1.getBytes()},
                                        0L)
                                .payingWith(GENESIS)
                                .via(invalidTokenNFTTest)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER),
                        getTxnRecord(invalidTokenTest).andAllChildRecords().logged(),
                        getTxnRecord(invalidTokenNFTTest).andAllChildRecords().logged())))
                .then(
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(FUNGIBLE_TOKEN, 1_000),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NON_FUNGIBLE_TOKEN, 0),
                        childRecordsCheck(
                                invalidTokenTest, SUCCESS, recordWith().status(INVALID_TOKEN_ID)),
                        childRecordsCheck(
                                invalidTokenNFTTest, SUCCESS, recordWith().status(INVALID_TOKEN_ID)));
    }

    @HapiTest
    final HapiSpec mintTokensWithInvalidValues() {
        var mintToken = "mintToken";

        var fungibleMintWithMetadataTest = "fungibleMintWithMetadataTest";
        var mintWithZeroedAddressTest = "mintWithZeroedAddressTest";
        var mintWithZeroedAddressAndMetadataTest = "mintWithZeroedAddressAndMetadataTest";

        return defaultHapiSpec("MintFungibleTokenWithInvalidAndExtremeValues")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT).maxAutomaticTokenAssociations(1),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(1000)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(NEGATIVE_MINT_CONTRACT),
                        contractCreate(NEGATIVE_MINT_CONTRACT).gas(GAS_TO_OFFER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        newKeyNamed(CONTRACT_KEY).shape(CONTRACT.signedWith(NEGATIVE_MINT_CONTRACT)),
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                        tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                        contractCall(
                                        NEGATIVE_MINT_CONTRACT,
                                        mintToken,
                                        new byte[][] {genRandomBytes(100)},
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        1L)
                                .payingWith(GENESIS)
                                .via(fungibleMintWithMetadataTest)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        NEGATIVE_MINT_CONTRACT,
                                        mintToken,
                                        new byte[][] {},
                                        idAsHeadlongAddress(AccountID.newBuilder()
                                                .setAccountNum(0L)
                                                .build()),
                                        1L)
                                .payingWith(GENESIS)
                                .via(mintWithZeroedAddressTest)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        NEGATIVE_MINT_CONTRACT,
                                        mintToken,
                                        new byte[][] {genRandomBytes(100)},
                                        idAsHeadlongAddress(AccountID.newBuilder()
                                                .setAccountNum(0L)
                                                .build()),
                                        0L)
                                .payingWith(GENESIS)
                                .via(mintWithZeroedAddressAndMetadataTest)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTxnRecord(fungibleMintWithMetadataTest)
                                .andAllChildRecords()
                                .logged(),
                        getTxnRecord(mintWithZeroedAddressTest)
                                .andAllChildRecords()
                                .logged(),
                        getTxnRecord(mintWithZeroedAddressAndMetadataTest)
                                .andAllChildRecords()
                                .logged())))
                .then(
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(FUNGIBLE_TOKEN, 1_000),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NON_FUNGIBLE_TOKEN, 0),
                        childRecordsCheck(
                                fungibleMintWithMetadataTest,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TRANSACTION_BODY)),
                        childRecordsCheck(
                                mintWithZeroedAddressTest,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)),
                        childRecordsCheck(
                                mintWithZeroedAddressAndMetadataTest,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)));
    }

    @HapiTest
    final HapiSpec transferNftAfterNestedMint() {
        final var nestedTransferTxn = "nestedTransferTxn";
        final var v2SecuritySendNftAfterNestedMint = "v2SecuritySendNftAfterNestedMint";

        return defaultHapiSpec(
                        "TransferNftAfterNestedMint",
                        NONDETERMINISTIC_CONSTRUCTOR_PARAMETERS,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V2_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT).maxAutomaticTokenAssociations(1),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_MILLION_HBARS),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(NESTED_MINT_CONTRACT, MINT_NFT_CONTRACT),
                        contractCreate(MINT_NFT_CONTRACT).gas(GAS_TO_OFFER))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                        NESTED_MINT_CONTRACT,
                                        asHeadlongAddress(getNestedContractAddress(MINT_NFT_CONTRACT, spec)),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))))
                                .gas(GAS_TO_OFFER),
                        newKeyNamed(DELEGATE_CONTRACT_KEY_NAME)
                                .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, NESTED_MINT_CONTRACT))),
                        cryptoUpdate(TOKEN_TREASURY).key(DELEGATE_CONTRACT_KEY_NAME),
                        tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(DELEGATE_CONTRACT_KEY_NAME),
                        contractCall(
                                        NESTED_MINT_CONTRACT,
                                        "sendNFTAfterMint",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(TOKEN_TREASURY))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))),
                                        new byte[][] {TEST_METADATA_1.getBytes()},
                                        1L)
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .via(nestedTransferTxn)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTxnRecord(nestedTransferTxn).andAllChildRecords().logged(),
                        // Test Case: Account paying and signing a non fungible TOKEN MINT TRANSACTION,
                        // when the token is minted in the token treasury account
                        // SIGNER → call → CONTRACT A → delegatecall → CONTRACT B → call → PRECOMPILE
                        cryptoUpdate(ACCOUNT).key(DELEGATE_CONTRACT_KEY_NAME),
                        contractCall(
                                        NESTED_MINT_CONTRACT,
                                        "sendNFTAfterMint",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(TOKEN_TREASURY))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))),
                                        new byte[][] {TEST_METADATA_2.getBytes()},
                                        2L)
                                .payingWith(TOKEN_TREASURY)
                                .signedBy(TOKEN_TREASURY)
                                .via(v2SecuritySendNftAfterNestedMint)
                                .gas(3 * GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTxnRecord(v2SecuritySendNftAfterNestedMint)
                                .andAllChildRecords()
                                .logged(),
                        // Token total supply should be now 2, both transferred to the RECIPIENT account
                        getAccountBalance(RECIPIENT).hasTokenBalance(NON_FUNGIBLE_TOKEN, 2L),
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(2))))
                .then(
                        withOpContext((spec, opLog) -> {
                            if (!spec.isUsingEthCalls()) {
                                allRunFor(
                                        spec,
                                        assertTxnRecordHasNoTraceabilityEnrichedContractFnResult(nestedTransferTxn));
                            }
                        }),
                        withOpContext((spec, opLog) -> {
                            final var expectedGasUsage =
                                    expectedPrecompileGasFor(spec, TokenMint, TOKEN_NON_FUNGIBLE_UNIQUE);
                            allRunFor(
                                    spec,
                                    childRecordsCheck(
                                            nestedTransferTxn,
                                            SUCCESS,
                                            recordWith()
                                                    .status(SUCCESS)
                                                    .contractCallResult(resultWith()
                                                            .approxGasUsed(expectedGasUsage, 5)
                                                            .contractCallResult(htsPrecompileResult()
                                                                    .forFunction(FunctionType.HAPI_MINT)
                                                                    .withStatus(SUCCESS)
                                                                    .withTotalSupply(1L)
                                                                    .withSerialNumbers(1L))
                                                            .gas(3_836_587L)
                                                            .amount(0L)
                                                            .functionParameters(functionParameters()
                                                                    .forFunction(
                                                                            FunctionParameters.PrecompileFunction.MINT)
                                                                    .withTokenAddress(
                                                                            asAddress(
                                                                                    spec.registry()
                                                                                            .getTokenID(
                                                                                                    NON_FUNGIBLE_TOKEN)))
                                                                    .withAmount(0L)
                                                                    .withMetadata(List.of("Test metadata" + " 1"))
                                                                    .build())),
                                            recordWith()
                                                    .status(SUCCESS)
                                                    .contractCallResult(resultWith()
                                                            .contractCallResult(htsPrecompileResult()
                                                                    .withStatus(SUCCESS)))
                                                    .tokenTransfers(NonFungibleTransfers.changingNFTBalances()
                                                            .including(
                                                                    NON_FUNGIBLE_TOKEN,
                                                                    TOKEN_TREASURY,
                                                                    RECIPIENT,
                                                                    1))));
                        }));
    }

    @SuppressWarnings("java:S5669")
    @HapiTest
    final HapiSpec rollbackOnFailedMintAfterFungibleTransfer() {
        final var failedMintTxn = "failedMintTxn";

        return defaultHapiSpec(
                        "RollbackOnFailedMintAfterFungibleTransfer",
                        ACCEPTED_MONO_GAS_CALCULATION_DIFFERENCE,
                        NONDETERMINISTIC_CONSTRUCTOR_PARAMETERS,
                        NONDETERMINISTIC_FUNCTION_PARAMETERS,
                        NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(5 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
                        uploadInitCode(MINT_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                MINT_CONTRACT,
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)))),
                        newKeyNamed(DELEGATE_KEY)
                                .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, MINT_CONTRACT))),
                        cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
                        contractCall(
                                        MINT_CONTRACT,
                                        "revertMintAfterFailedMint",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))),
                                        20L)
                                .payingWith(GENESIS)
                                .via(failedMintTxn)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTxnRecord(failedMintTxn).andAllChildRecords().logged())))
                .then(
                        getAccountBalance(ACCOUNT).hasTokenBalance(FUNGIBLE_TOKEN, 200),
                        getAccountBalance(RECIPIENT).hasTokenBalance(FUNGIBLE_TOKEN, 0),
                        childRecordsCheck(
                                failedMintTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(REVERTED_SUCCESS),
                                recordWith()
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.HAPI_MINT)
                                                        .withStatus(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                                        .withTotalSupply(0L)
                                                        .withSerialNumbers()))));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
