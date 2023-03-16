/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiPropertySource.asContractString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.contractIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUniqueWithAllowance;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.reduceFeeFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asHexedAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.Utils.captureChildCreate2MetaFor;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hedera.services.bdd.suites.utils.contracts.AddressResult.hexedAddress;
import static com.hedera.services.bdd.suites.utils.contracts.BoolResult.flag;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.esaulpaugh.headlong.abi.Address;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class ERCPrecompileSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ERCPrecompileSuite.class);
    private static final long GAS_TO_OFFER = 1_000_000L;
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String MULTI_KEY = "purpose";
    private static final String ERC_20_CONTRACT_NAME = "erc20Contract";
    private static final String OWNER = "owner";
    private static final String ACCOUNT = "anybody";
    public static final String RECIPIENT = "recipient";
    private static final String FIRST = "FIRST";
    private static final String TOKEN_NAME = "TokenA";
    private static final ByteString FIRST_META = ByteString.copyFrom(FIRST.getBytes(StandardCharsets.UTF_8));
    private static final ByteString SECOND_META = ByteString.copyFrom(FIRST.getBytes(StandardCharsets.UTF_8));
    private static final String TRANSFER_SIG_NAME = "transferSig";
    public static final String ERC_20_CONTRACT = "ERC20Contract";
    private static final String ERC_721_CONTRACT = "ERC721Contract";
    private static final String NAME_TXN = "nameTxn";
    private static final String SYMBOL_TXN = "symbolTxn";
    private static final String TOTAL_SUPPLY_TXN = "totalSupplyTxn";
    private static final String BALANCE_OF_TXN = "balanceOfTxn";
    private static final String ALLOWANCE_TXN = "allowanceTxn";
    private static final String TRANSFER_TXN = "transferTxn";
    public static final String TRANSFER_FROM_ACCOUNT_TXN = "transferFromAccountTxn";
    private static final String BASE_APPROVE_TXN = "baseApproveTxn";
    private static final String IS_APPROVED_FOR_ALL = "outerIsApprovedForAll";
    private static final String GET_ALLOWANCE = "getAllowance";
    private static final String ALLOWANCE = "allowance";
    private static final String SYMBOL = "symbol";
    private static final String DECIMALS = "decimals";
    private static final String TOTAL_SUPPLY = "totalSupply";
    private static final String BALANCE_OF = "balanceOf";
    private static final String TRANSFER = "transfer";
    private static final String APPROVE = "approve";
    private static final String OWNER_OF = "ownerOf";
    private static final String TOKEN_URI = "tokenURI";
    private static final String TOKEN = "token";
    private static final String SPENDER = "spender";
    private static final String NF_TOKEN = "nfToken";
    public static final String TRANSFER_FROM = "transferFrom";
    private static final String ERC_20_ABI = "ERC20ABI";
    private static final String ERC_721_ABI = "ERC721ABI";
    private static final String MULTI_KEY_NAME = "multiKey";
    private static final String A_CIVILIAN = "aCivilian";
    private static final String B_CIVILIAN = "bCivilian";
    private static final String DO_TRANSFER_FROM = "doTransferFrom";
    private static final String GET_APPROVED = "outerGetApproved";
    private static final String GET_BALANCE_OF = "getBalanceOf";
    private static final String MISSING_FROM = "MISSING_FROM";
    private static final String MISSING_TO = "MISSING_TO";
    private static final String SOME_ERC_20_SCENARIOS = "SomeERC20Scenarios";
    private static final String SOME_ERC_721_SCENARIOS = "SomeERC721Scenarios";
    private static final String GET_OWNER_OF = "getOwnerOf";
    private static final String OPERATOR_DOES_NOT_EXISTS = "OPERATOR_DOES_NOT_EXISTS";
    private static final String SET_APPROVAL_FOR_ALL = "outerSetApprovalForAll";
    private static final String REVOKE_SPECIFIC_APPROVAL = "revokeSpecificApproval";
    private static final String MSG_SENDER_IS_NOT_THE_SAME_AS_FROM = "MSG_SENDER_IS_NOT_THE_SAME_AS_FROM";
    private static final String MSG_SENDER_IS_THE_SAME_AS_FROM = "MSG_SENDER_IS_THE_SAME_AS_FROM";
    private static final String MISSING_TOKEN = "MISSING_TOKEN";
    private static final String WITH_SPENDER = "WITH_SPENDER";
    private static final String DO_SPECIFIC_APPROVAL = "doSpecificApproval";
    private static final String NFT_TOKEN_MINT = "nftTokenMint";
    static final String TRANSFER_SIGNATURE = "Transfer(address,address,uint256)";

    public static void main(String... args) {
        new ERCPrecompileSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(erc20(), erc721());
    }

    List<HapiSpec> erc20() {
        return List.of(
                getErc20TokenName(),
                getErc20TokenSymbol(),
                getErc20TokenDecimals(),
                getErc20TotalSupply(),
                getErc20BalanceOfAccount(),
                transferErc20Token(),
                erc20Allowance(),
                erc20Approve(),
                someERC20ApproveAllowanceScenariosPass(),
                someERC20NegativeTransferFromScenariosPass(),
                someERC20ApproveAllowanceScenarioInOneCall(),
                getErc20TokenDecimalsFromErc721TokenFails(),
                transferErc20TokenFromErc721TokenFails(),
                transferErc20TokenReceiverContract(),
                transferErc20TokenAliasedSender(),
                directCallsWorkForERC20(),
                erc20TransferFrom(),
                erc20TransferFromSelf(),
                getErc20TokenNameExceedingLimits(),
                transferErc20TokenFromContract());
    }

    List<HapiSpec> erc721() {
        return List.of(
                getErc721TokenName(),
                getErc721Symbol(),
                getErc721TokenURI(),
                getErc721OwnerOf(),
                getErc721BalanceOf(),
                getErc721TotalSupply(),
                erc721TokenApprove(),
                erc721GetApproved(),
                getErc721TokenURIFromErc20TokenFails(),
                getErc721OwnerOfFromErc20TokenFails(),
                directCallsWorkForERC721(),
                someERC721ApproveAndRemoveScenariosPass(),
                someERC721NegativeTransferFromScenariosPass(),
                erc721TransferFromWithApproval(),
                erc721TransferFromWithApproveForAll(),
                someERC721GetApprovedScenariosPass(),
                someERC721BalanceOfScenariosPass(),
                someERC721OwnerOfScenariosPass(),
                someERC721IsApprovedForAllScenariosPass(),
                getErc721IsApprovedForAll(),
                someERC721SetApprovedForAllScenariosPass());
    }

    private HapiSpec getErc20TokenName() {
        return defaultHapiSpec("ERC_20_NAME")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(5)
                                .name(TOKEN_NAME)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_20_CONTRACT,
                                        "name",
                                        asHeadlongAddress(
                                                asHexedAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))))
                                .payingWith(ACCOUNT)
                                .via(NAME_TXN)
                                .gas(4_000_000)
                                .hasKnownStatus(SUCCESS))))
                .then(childRecordsCheck(
                        NAME_TXN,
                        SUCCESS,
                        recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith()
                                        .contractCallResult(htsPrecompileResult()
                                                .forFunction(FunctionType.ERC_NAME)
                                                .withName(TOKEN_NAME)))));
    }

    private HapiSpec getErc20TokenSymbol() {
        final var tokenSymbol = "F";
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return defaultHapiSpec("ERC_20_SYMBOL")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(5)
                                .symbol(tokenSymbol)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(
                                        id -> tokenAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(id)))),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_20_CONTRACT,
                                        SYMBOL,
                                        asHeadlongAddress(
                                                asHexedAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))))
                                .payingWith(ACCOUNT)
                                .via(SYMBOL_TXN)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                SYMBOL_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_SYMBOL)
                                                        .withSymbol(tokenSymbol)))),
                        sourcing(() -> contractCallLocal(ERC_20_CONTRACT, SYMBOL, asHeadlongAddress(tokenAddr.get()))));
    }

    private HapiSpec getErc20TokenDecimals() {
        final var decimals = 10;
        final var decimalsTxn = "decimalsTxn";
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return defaultHapiSpec("ERC_20_DECIMALS")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(5)
                                .decimals(decimals)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(
                                        id -> tokenAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(id)))),
                        fileCreate(ERC_20_CONTRACT_NAME),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_20_CONTRACT,
                                        DECIMALS,
                                        asHeadlongAddress(
                                                asHexedAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))))
                                .payingWith(ACCOUNT)
                                .via(decimalsTxn)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                decimalsTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_DECIMALS)
                                                        .withDecimals(decimals)))),
                        sourcing(() ->
                                contractCallLocal(ERC_20_CONTRACT, DECIMALS, asHeadlongAddress(tokenAddr.get()))));
    }

    private HapiSpec getErc20TotalSupply() {
        final var totalSupply = 50;
        final var supplyTxn = "supplyTxn";
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return defaultHapiSpec("ERC_20_TOTAL_SUPPLY")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(totalSupply)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(
                                        id -> tokenAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(id)))),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_20_CONTRACT,
                                        TOTAL_SUPPLY,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))))
                                .payingWith(ACCOUNT)
                                .via(supplyTxn)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                supplyTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_TOTAL_SUPPLY)
                                                        .withTotalSupply(totalSupply)))),
                        sourcing(() ->
                                contractCallLocal(ERC_20_CONTRACT, DECIMALS, asHeadlongAddress(tokenAddr.get()))));
    }

    private HapiSpec getErc20BalanceOfAccount() {
        final var balanceTxn = "balanceTxn";
        final var zeroBalanceTxn = "zBalanceTxn";
        final AtomicReference<String> tokenAddr = new AtomicReference<>();
        final AtomicReference<String> accountAddr = new AtomicReference<>();

        return defaultHapiSpec("ERC_20_BALANCE_OF")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .exposingCreatedIdTo(id -> accountAddr.set(asHexedSolidityAddress(id))),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(5)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(
                                        id -> tokenAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(id)))),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_20_CONTRACT,
                                        BALANCE_OF,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))))
                                .payingWith(ACCOUNT)
                                .via(zeroBalanceTxn)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER),
                        tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(moving(3, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
                        contractCall(
                                        ERC_20_CONTRACT,
                                        BALANCE_OF,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))))
                                .payingWith(ACCOUNT)
                                .via(balanceTxn)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER))))
                .then(
                        /* expect 0 returned from balanceOf() if the account and token are not associated */
                        childRecordsCheck(
                                zeroBalanceTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_BALANCE)
                                                        .withBalance(0)))),
                        childRecordsCheck(
                                balanceTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_BALANCE)
                                                        .withBalance(3)))),
                        sourcing(() -> contractCallLocal(
                                ERC_20_CONTRACT,
                                BALANCE_OF,
                                asHeadlongAddress(tokenAddr.get()),
                                asHeadlongAddress(accountAddr.get()))));
    }

    private HapiSpec transferErc20Token() {
        final AtomicReference<String> tokenAddr = new AtomicReference<>();
        final AtomicReference<String> accountAddr = new AtomicReference<>();

        return defaultHapiSpec("ERC_20_TRANSFER")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .exposingCreatedIdTo(id -> accountAddr.set(asHexedSolidityAddress(id))),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(5)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(id -> tokenAddr.set(
                                        HapiPropertySource.asHexedSolidityAddress(HapiPropertySource.asToken(id)))),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT),
                        tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(ERC_20_CONTRACT, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ERC_20_CONTRACT)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_20_CONTRACT,
                                        TRANSFER,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))),
                                        BigInteger.TWO)
                                .via(TRANSFER_TXN)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS))))
                .then(
                        getContractInfo(ERC_20_CONTRACT).saveToRegistry(ERC_20_CONTRACT),
                        getAccountInfo(RECIPIENT).savingSnapshot(RECIPIENT),
                        withOpContext((spec, log) -> {
                            final var sender = spec.registry()
                                    .getContractInfo(ERC_20_CONTRACT)
                                    .getContractID();
                            final var receiver =
                                    spec.registry().getAccountInfo(RECIPIENT).getAccountID();
                            final var idOfToken = "0.0."
                                    + (spec.registry()
                                            .getTokenID(FUNGIBLE_TOKEN)
                                            .getTokenNum());
                            var txnRecord = getTxnRecord(TRANSFER_TXN)
                                    .hasPriority(recordWith()
                                            .contractCallResult(resultWith()
                                                    .logs(inOrder(logWith()
                                                            .contract(idOfToken)
                                                            .withTopicsInOrder(List.of(
                                                                    eventSignatureOf(TRANSFER_SIGNATURE),
                                                                    parsedToByteString(sender.getContractNum()),
                                                                    parsedToByteString(receiver.getAccountNum())))
                                                            .longValue(2)))))
                                    .andAllChildRecords()
                                    .logged();
                            allRunFor(spec, txnRecord);
                        }),
                        childRecordsCheck(
                                TRANSFER_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_TRANSFER)
                                                        .withErcFungibleTransferStatus(true)))),
                        getAccountBalance(ERC_20_CONTRACT).hasTokenBalance(FUNGIBLE_TOKEN, 3),
                        getAccountBalance(RECIPIENT).hasTokenBalance(FUNGIBLE_TOKEN, 2),
                        sourcing(() -> contractCallLocal(
                                        ERC_20_CONTRACT,
                                        TRANSFER,
                                        asHeadlongAddress(tokenAddr.get()),
                                        asHeadlongAddress(accountAddr.get()),
                                        BigInteger.ONE)
                                .hasAnswerOnlyPrecheck(CONTRACT_REVERT_EXECUTED)));
    }

    private HapiSpec transferErc20TokenReceiverContract() {
        final var nestedContract = "NestedERC20Contract";

        return defaultHapiSpec("ERC_20_TRANSFER_RECEIVER_CONTRACT")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_MILLION_HBARS),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(5)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT, nestedContract),
                        contractCreate(ERC_20_CONTRACT),
                        contractCreate(nestedContract),
                        tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(ERC_20_CONTRACT, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(nestedContract, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ERC_20_CONTRACT)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_20_CONTRACT,
                                        TRANSFER,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(nestedContract))),
                                        BigInteger.TWO)
                                .via(TRANSFER_TXN)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS))))
                .then(
                        getContractInfo(ERC_20_CONTRACT).saveToRegistry(ERC_20_CONTRACT),
                        getContractInfo(nestedContract).saveToRegistry(nestedContract),
                        withOpContext((spec, log) -> {
                            final var sender = spec.registry()
                                    .getContractInfo(ERC_20_CONTRACT)
                                    .getContractID();
                            final var receiver = spec.registry()
                                    .getContractInfo(nestedContract)
                                    .getContractID();

                            var txnRecord = getTxnRecord(TRANSFER_TXN)
                                    .hasPriority(recordWith()
                                            .contractCallResult(resultWith()
                                                    .logs(inOrder(logWith()
                                                            .withTopicsInOrder(List.of(
                                                                    eventSignatureOf(TRANSFER_SIGNATURE),
                                                                    parsedToByteString(sender.getContractNum()),
                                                                    parsedToByteString(receiver.getContractNum())))
                                                            .longValue(2)))))
                                    .andAllChildRecords()
                                    .logged();
                            allRunFor(spec, txnRecord);
                        }),
                        childRecordsCheck(
                                TRANSFER_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_TRANSFER)
                                                        .withErcFungibleTransferStatus(true)))),
                        getAccountBalance(ERC_20_CONTRACT).hasTokenBalance(FUNGIBLE_TOKEN, 3),
                        getAccountBalance(nestedContract).hasTokenBalance(FUNGIBLE_TOKEN, 2));
    }

    private HapiSpec transferErc20TokenAliasedSender() {
        final var aliasedTransferTxn = "aliasedTransferTxn";
        final var addLiquidityTxn = "addLiquidityTxn";
        final var create2Txn = "create2Txn";

        final var ACCOUNT_A = "AccountA";
        final var ACCOUNT_B = "AccountB";

        final var ALIASED_TRANSFER = "AliasedTransfer";
        final byte[][] ALIASED_ADDRESS = new byte[1][1];

        final AtomicReference<String> childMirror = new AtomicReference<>();
        final AtomicReference<String> childEip1014 = new AtomicReference<>();

        return defaultHapiSpec("ERC_20_TRANSFER_ALIASED_SENDER")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER),
                        cryptoCreate(ACCOUNT),
                        cryptoCreate(ACCOUNT_A).key(MULTI_KEY).balance(ONE_MILLION_HBARS),
                        cryptoCreate(ACCOUNT_B).balance(ONE_MILLION_HBARS),
                        tokenCreate(TOKEN_NAME)
                                .adminKey(MULTI_KEY)
                                .initialSupply(10000)
                                .treasury(ACCOUNT_A),
                        tokenAssociate(ACCOUNT_B, TOKEN_NAME),
                        uploadInitCode(ALIASED_TRANSFER),
                        contractCreate(ALIASED_TRANSFER).gas(300_000),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCall(
                                                ALIASED_TRANSFER,
                                                "deployWithCREATE2",
                                                asHeadlongAddress(asHexedAddress(
                                                        spec.registry().getTokenID(TOKEN_NAME))))
                                        .exposingResultTo(result -> {
                                            final var res = (Address) result[0];
                                            ALIASED_ADDRESS[0] = res.value().toByteArray();
                                        })
                                        .payingWith(ACCOUNT)
                                        .alsoSigningWithFullPrefix(MULTI_KEY)
                                        .via(create2Txn)
                                        .gas(GAS_TO_OFFER)
                                        .hasKnownStatus(SUCCESS))))
                .when(
                        captureChildCreate2MetaFor(2, 0, "setup", create2Txn, childMirror, childEip1014),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCall(
                                                ALIASED_TRANSFER,
                                                "giveTokensToOperator",
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getTokenID(TOKEN_NAME))),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getAccountID(ACCOUNT_A))),
                                                1500L)
                                        .payingWith(ACCOUNT)
                                        .alsoSigningWithFullPrefix(MULTI_KEY)
                                        .via(addLiquidityTxn)
                                        .gas(GAS_TO_OFFER)
                                        .hasKnownStatus(SUCCESS))),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCall(
                                                ALIASED_TRANSFER,
                                                TRANSFER,
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getAccountID(ACCOUNT_B))),
                                                BigInteger.valueOf(1000))
                                        .payingWith(ACCOUNT)
                                        .alsoSigningWithFullPrefix(MULTI_KEY)
                                        .via(aliasedTransferTxn)
                                        .gas(GAS_TO_OFFER)
                                        .hasKnownStatus(SUCCESS))))
                .then(
                        sourcing(() -> getContractInfo(
                                        asContractString(contractIdFromHexedMirrorAddress(childMirror.get())))
                                .hasToken(ExpectedTokenRel.relationshipWith(TOKEN_NAME)
                                        .balance(500))
                                .logged()),
                        getAccountBalance(ACCOUNT_B).hasTokenBalance(TOKEN_NAME, 1000),
                        getAccountBalance(ACCOUNT_A).hasTokenBalance(TOKEN_NAME, 8500));
    }

    private HapiSpec transferErc20TokenFromContract() {
        final var transferFromOtherContractWithSignaturesTxn = "transferFromOtherContractWithSignaturesTxn";
        final var nestedContract = "NestedERC20Contract";

        return defaultHapiSpec("ERC_20_TRANSFER_FROM_CONTRACT")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_MILLION_HBARS),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(35)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT, nestedContract),
                        newKeyNamed(TRANSFER_SIG_NAME).shape(SIMPLE.signedWith(ON)),
                        contractCreate(ERC_20_CONTRACT).adminKey(TRANSFER_SIG_NAME),
                        contractCreate(nestedContract).adminKey(TRANSFER_SIG_NAME))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(ERC_20_CONTRACT, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(nestedContract, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(TokenMovement.moving(20, FUNGIBLE_TOKEN)
                                        .between(TOKEN_TREASURY, ERC_20_CONTRACT))
                                .payingWith(ACCOUNT),
                        contractCall(
                                        ERC_20_CONTRACT,
                                        TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(ERC_20_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(nestedContract))),
                                        BigInteger.valueOf(5))
                                .via(TRANSFER_TXN)
                                .hasKnownStatus(SUCCESS),
                        contractCall(
                                        ERC_20_CONTRACT,
                                        TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(ERC_20_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(nestedContract))),
                                        BigInteger.valueOf(5))
                                .payingWith(GENESIS)
                                .alsoSigningWithFullPrefix(TRANSFER_SIG_NAME)
                                .via(transferFromOtherContractWithSignaturesTxn))))
                .then(
                        getContractInfo(ERC_20_CONTRACT).saveToRegistry(ERC_20_CONTRACT),
                        getContractInfo(nestedContract).saveToRegistry(nestedContract),
                        withOpContext((spec, log) -> {
                            final var sender = spec.registry()
                                    .getContractInfo(ERC_20_CONTRACT)
                                    .getContractID();
                            final var receiver = spec.registry()
                                    .getContractInfo(nestedContract)
                                    .getContractID();

                            var transferRecord = getTxnRecord(TRANSFER_TXN)
                                    .hasPriority(recordWith()
                                            .contractCallResult(resultWith()
                                                    .logs(inOrder(logWith()
                                                            .withTopicsInOrder(List.of(
                                                                    eventSignatureOf(TRANSFER_SIGNATURE),
                                                                    parsedToByteString(sender.getContractNum()),
                                                                    parsedToByteString(receiver.getContractNum())))
                                                            .longValue(5)))))
                                    .andAllChildRecords();

                            var transferFromOtherContractWithSignaturesTxnRecord = getTxnRecord(
                                            transferFromOtherContractWithSignaturesTxn)
                                    .hasPriority(recordWith()
                                            .contractCallResult(resultWith()
                                                    .logs(inOrder(logWith()
                                                            .withTopicsInOrder(List.of(
                                                                    eventSignatureOf(TRANSFER_SIGNATURE),
                                                                    parsedToByteString(sender.getContractNum()),
                                                                    parsedToByteString(receiver.getContractNum())))
                                                            .longValue(5)))))
                                    .andAllChildRecords();

                            allRunFor(spec, transferRecord, transferFromOtherContractWithSignaturesTxnRecord);
                        }),
                        childRecordsCheck(
                                TRANSFER_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_TRANSFER)
                                                        .withErcFungibleTransferStatus(true)))),
                        childRecordsCheck(
                                transferFromOtherContractWithSignaturesTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_TRANSFER)
                                                        .withErcFungibleTransferStatus(true)))),
                        getAccountBalance(ERC_20_CONTRACT).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                        getAccountBalance(nestedContract).hasTokenBalance(FUNGIBLE_TOKEN, 10));
    }

    private HapiSpec erc20Allowance() {
        return defaultHapiSpec("ERC_20_ALLOWANCE")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(SPENDER),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 2L)
                                .via(BASE_APPROVE_TXN)
                                .logged()
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_20_CONTRACT,
                                        ALLOWANCE,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SPENDER))))
                                .payingWith(OWNER)
                                .via(ALLOWANCE_TXN)
                                .hasKnownStatus(SUCCESS))))
                .then(
                        getTxnRecord(ALLOWANCE_TXN).andAllChildRecords().logged(),
                        childRecordsCheck(
                                ALLOWANCE_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_ALLOWANCE)
                                                        .withAllowance(2)))));
    }

    private HapiSpec erc20Approve() {
        final var approveTxn = "approveTxn";

        return defaultHapiSpec("ERC_20_APPROVE")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(SPENDER),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(ERC_20_CONTRACT, FUNGIBLE_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_20_CONTRACT,
                                        APPROVE,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(SPENDER))),
                                        BigInteger.valueOf(10))
                                .payingWith(OWNER)
                                .gas(4_000_000L)
                                .via(approveTxn)
                                .hasKnownStatus(SUCCESS))))
                .then(
                        childRecordsCheck(approveTxn, SUCCESS, recordWith().status(SUCCESS)),
                        getTxnRecord(approveTxn).andAllChildRecords().logged());
    }

    private HapiSpec getErc20TokenDecimalsFromErc721TokenFails() {
        final var invalidDecimalsTxn = "decimalsFromErc721Txn";

        return defaultHapiSpec("ERC_20_DECIMALS_FROM_ERC_721_TOKEN")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
                        fileCreate(ERC_20_CONTRACT_NAME),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_20_CONTRACT,
                                        DECIMALS,
                                        asHeadlongAddress(
                                                asHexedAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))))
                                .payingWith(ACCOUNT)
                                .via(invalidDecimalsTxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER))))
                .then(getTxnRecord(invalidDecimalsTxn).andAllChildRecords().logged());
    }

    private HapiSpec transferErc20TokenFromErc721TokenFails() {
        return defaultHapiSpec("ERC_20_TRANSFER_FROM_ERC_721_TOKEN")
                .given(
                        overriding("hedera.allowances.isEnabled", "true"),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_MILLION_HBARS),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
                        tokenAssociate(ACCOUNT, List.of(NON_FUNGIBLE_TOKEN)),
                        tokenAssociate(RECIPIENT, List.of(NON_FUNGIBLE_TOKEN)),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(TOKEN_TREASURY, ACCOUNT))
                                .payingWith(ACCOUNT),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_20_CONTRACT,
                                        TRANSFER,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))),
                                        BigInteger.TWO)
                                .payingWith(ACCOUNT)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .via(TRANSFER_TXN)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged());
    }

    private HapiSpec getErc721TokenName() {
        return defaultHapiSpec("ERC_721_NAME")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .name(TOKEN_NAME)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
                        uploadInitCode(ERC_721_CONTRACT),
                        contractCreate(ERC_721_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_721_CONTRACT,
                                        "name",
                                        asHeadlongAddress(
                                                asHexedAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))))
                                .payingWith(ACCOUNT)
                                .via(NAME_TXN)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER))))
                .then(childRecordsCheck(
                        NAME_TXN,
                        SUCCESS,
                        recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith()
                                        .contractCallResult(htsPrecompileResult()
                                                .forFunction(FunctionType.ERC_NAME)
                                                .withName(TOKEN_NAME)))));
    }

    private HapiSpec getErc20TokenNameExceedingLimits() {
        final var REDUCED_NETWORK_FEE = 1L;
        final var REDUCED_NODE_FEE = 1L;
        final var REDUCED_SERVICE_FEE = 1L;
        final var INIT_ACCOUNT_BALANCE = 100 * ONE_HUNDRED_HBARS;
        return defaultHapiSpec("getErc20TokenNameExceedingLimits")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(INIT_ACCOUNT_BALANCE),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(5)
                                .name(TOKEN_NAME)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT))
                .when(
                        balanceSnapshot("accountSnapshot", ACCOUNT),
                        reduceFeeFor(
                                HederaFunctionality.ContractCall,
                                REDUCED_NODE_FEE,
                                REDUCED_NETWORK_FEE,
                                REDUCED_SERVICE_FEE),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCall(
                                                ERC_20_CONTRACT,
                                                "nameNTimes",
                                                asHeadlongAddress(asHexedAddress(
                                                        spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                                BigInteger.valueOf(51))
                                        .payingWith(ACCOUNT)
                                        .via(NAME_TXN)
                                        .gas(4_000_000)
                                        .hasKnownStatus(MAX_CHILD_RECORDS_EXCEEDED))))
                .then(
                        getTxnRecord(NAME_TXN)
                                .andAllChildRecords()
                                .logged()
                                .hasPriority(recordWith()
                                        .contractCallResult(resultWith()
                                                .error(Bytes.of(MAX_CHILD_RECORDS_EXCEEDED
                                                                .name()
                                                                .getBytes())
                                                        .toHexString())
                                                .gasUsed(4_000_000))),
                        getAccountDetails(ACCOUNT)
                                .has(accountDetailsWith()
                                        .balanceLessThan(
                                                INIT_ACCOUNT_BALANCE - REDUCED_NETWORK_FEE - REDUCED_NODE_FEE)));
    }

    private HapiSpec getErc721Symbol() {
        final var tokenSymbol = "N";

        return defaultHapiSpec("ERC_721_SYMBOL")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .symbol(tokenSymbol)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
                        uploadInitCode(ERC_721_CONTRACT),
                        contractCreate(ERC_721_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_721_CONTRACT,
                                        SYMBOL,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))))
                                .payingWith(ACCOUNT)
                                .via(SYMBOL_TXN)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER))))
                .then(childRecordsCheck(
                        SYMBOL_TXN,
                        SUCCESS,
                        recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith()
                                        .contractCallResult(htsPrecompileResult()
                                                .forFunction(FunctionType.ERC_SYMBOL)
                                                .withSymbol(tokenSymbol)))));
    }

    private HapiSpec getErc721TokenURI() {
        final var tokenURITxn = "tokenURITxn";
        final var nonExistingTokenURITxn = "nonExistingTokenURITxn";
        final var ERC721MetadataNonExistingToken = "ERC721Metadata: URI query for nonexistent token";

        return defaultHapiSpec("ERC_721_TOKEN_URI")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
                        uploadInitCode(ERC_721_CONTRACT),
                        contractCreate(ERC_721_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_721_CONTRACT,
                                        TOKEN_URI,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        BigInteger.ONE)
                                .payingWith(ACCOUNT)
                                .via(tokenURITxn)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER),
                        contractCall(
                                        ERC_721_CONTRACT,
                                        TOKEN_URI,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        BigInteger.TWO)
                                .payingWith(ACCOUNT)
                                .via(nonExistingTokenURITxn)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                tokenURITxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_TOKEN_URI)
                                                        .withTokenUri(FIRST)))),
                        childRecordsCheck(
                                nonExistingTokenURITxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_TOKEN_URI)
                                                        .withTokenUri(ERC721MetadataNonExistingToken)))));
    }

    private HapiSpec getErc721TotalSupply() {
        return defaultHapiSpec("ERC_721_TOTAL_SUPPLY")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
                        uploadInitCode(ERC_721_CONTRACT),
                        contractCreate(ERC_721_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_721_CONTRACT,
                                        TOTAL_SUPPLY,
                                        asHeadlongAddress(
                                                asHexedAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))))
                                .payingWith(ACCOUNT)
                                .via(TOTAL_SUPPLY_TXN)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER))))
                .then(childRecordsCheck(
                        TOTAL_SUPPLY_TXN,
                        SUCCESS,
                        recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith()
                                        .contractCallResult(htsPrecompileResult()
                                                .forFunction(FunctionType.ERC_TOTAL_SUPPLY)
                                                .withTotalSupply(1)))));
    }

    private HapiSpec getErc721BalanceOf() {
        final var zeroBalanceOfTxn = "zbalanceOfTxn";

        return defaultHapiSpec("ERC_721_BALANCE_OF")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
                        uploadInitCode(ERC_721_CONTRACT),
                        contractCreate(ERC_721_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_721_CONTRACT,
                                        BALANCE_OF,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))))
                                .payingWith(OWNER)
                                .via(zeroBalanceOfTxn)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER),
                        tokenAssociate(OWNER, List.of(NON_FUNGIBLE_TOKEN)),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(TOKEN_TREASURY, OWNER)),
                        contractCall(
                                        ERC_721_CONTRACT,
                                        BALANCE_OF,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))))
                                .payingWith(OWNER)
                                .via(BALANCE_OF_TXN)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER))))
                .then(
                        /* expect 0 returned from balanceOf() if the account and token are not associated */
                        childRecordsCheck(
                                zeroBalanceOfTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_BALANCE)
                                                        .withBalance(0)))),
                        childRecordsCheck(
                                BALANCE_OF_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_BALANCE)
                                                        .withBalance(1)))));
    }

    private HapiSpec getErc721OwnerOf() {
        final var ownerOfTxn = "ownerOfTxn";
        final AtomicReference<byte[]> ownerAddr = new AtomicReference<>();
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return defaultHapiSpec("ERC_721_OWNER_OF")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(
                                        id -> tokenAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(id)))),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
                        tokenAssociate(OWNER, List.of(NON_FUNGIBLE_TOKEN)),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(TOKEN_TREASURY, OWNER)),
                        uploadInitCode(ERC_721_CONTRACT),
                        contractCreate(ERC_721_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_721_CONTRACT,
                                        OWNER_OF,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        BigInteger.ONE)
                                .payingWith(OWNER)
                                .via(ownerOfTxn)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER))))
                .then(
                        withOpContext((spec, opLog) -> {
                            ownerAddr.set(asAddress(spec.registry().getAccountID(OWNER)));
                            allRunFor(
                                    spec,
                                    childRecordsCheck(
                                            ownerOfTxn,
                                            SUCCESS,
                                            recordWith()
                                                    .status(SUCCESS)
                                                    .contractCallResult(resultWith()
                                                            .contractCallResult(htsPrecompileResult()
                                                                    .forFunction(FunctionType.ERC_OWNER)
                                                                    .withOwner(ownerAddr.get())))));
                        }),
                        sourcing(() -> contractCallLocal(
                                        ERC_721_CONTRACT, OWNER_OF, asHeadlongAddress(tokenAddr.get()), BigInteger.ONE)
                                .payingWith(OWNER)
                                .gas(GAS_TO_OFFER)));
    }

    private HapiSpec getErc721TokenURIFromErc20TokenFails() {
        final var invalidTokenURITxn = "tokenURITxnFromErc20";

        return defaultHapiSpec("ERC_721_TOKEN_URI_FROM_ERC_20_TOKEN")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(10)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_721_CONTRACT),
                        contractCreate(ERC_721_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_721_CONTRACT,
                                        TOKEN_URI,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        BigInteger.ONE)
                                .payingWith(ACCOUNT)
                                .via(invalidTokenURITxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER))))
                .then(getTxnRecord(invalidTokenURITxn).andAllChildRecords().logged());
    }

    private HapiSpec getErc721OwnerOfFromErc20TokenFails() {
        final var invalidOwnerOfTxn = "ownerOfTxnFromErc20Token";

        return defaultHapiSpec("ERC_721_OWNER_OF_FROM_ERC_20_TOKEN")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(10)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        tokenAssociate(OWNER, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(moving(3, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)),
                        uploadInitCode(ERC_721_CONTRACT),
                        contractCreate(ERC_721_CONTRACT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_721_CONTRACT,
                                        OWNER_OF,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        BigInteger.ONE)
                                .payingWith(OWNER)
                                .via(invalidOwnerOfTxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .gas(GAS_TO_OFFER))))
                .then(getTxnRecord(invalidOwnerOfTxn).andAllChildRecords().logged());
    }

    private HapiSpec directCallsWorkForERC20() {
        final AtomicReference<String> tokenNum = new AtomicReference<>();

        final var tokenSymbol = "FDFGF";
        final var tokenDecimals = 10;
        final var tokenTotalSupply = 5;
        final var tokenTransferAmount = 3;

        final var decimalsTxn = "decimalsTxn";

        return defaultHapiSpec("DirectCallsWorkForERC20")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(tokenTotalSupply)
                                .name(TOKEN_NAME)
                                .symbol(tokenSymbol)
                                .decimals(tokenDecimals)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(tokenNum::set),
                        tokenAssociate(ACCOUNT, FUNGIBLE_TOKEN),
                        tokenAssociate(RECIPIENT, FUNGIBLE_TOKEN),
                        cryptoTransfer(
                                moving(tokenTransferAmount, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT)))
                .when(withOpContext((spec, ignore) -> {
                    var tokenAddress = asHexedSolidityAddress(asToken(tokenNum.get()));
                    allRunFor(
                            spec,
                            contractCallWithFunctionAbi(
                                            tokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    "name",
                                                    ERC_20_ABI))
                                    .via(NAME_TXN),
                            contractCallWithFunctionAbi(
                                            tokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    SYMBOL,
                                                    ERC_20_ABI))
                                    .via(SYMBOL_TXN),
                            contractCallWithFunctionAbi(
                                            tokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    DECIMALS,
                                                    ERC_20_ABI))
                                    .via(decimalsTxn),
                            contractCallWithFunctionAbi(
                                            tokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    TOTAL_SUPPLY,
                                                    ERC_20_ABI))
                                    .via(TOTAL_SUPPLY_TXN),
                            contractCallWithFunctionAbi(
                                            tokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    BALANCE_OF,
                                                    ERC_20_ABI),
                                            asHeadlongAddress(asHexedSolidityAddress(
                                                    spec.registry().getAccountID(ACCOUNT))))
                                    .via(BALANCE_OF_TXN),
                            contractCallWithFunctionAbi(
                                            tokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    TRANSFER,
                                                    ERC_20_ABI),
                                            asHeadlongAddress(asHexedSolidityAddress(
                                                    spec.registry().getAccountID(RECIPIENT))),
                                            BigInteger.valueOf(tokenTransferAmount))
                                    .via(TRANSFER_TXN)
                                    .payingWith(ACCOUNT));
                }))
                .then(withOpContext((spec, ignore) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                NAME_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_NAME)
                                                        .withName(TOKEN_NAME)))),
                        childRecordsCheck(
                                SYMBOL_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_SYMBOL)
                                                        .withSymbol(tokenSymbol)))),
                        childRecordsCheck(
                                decimalsTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_DECIMALS)
                                                        .withDecimals(tokenDecimals)))),
                        childRecordsCheck(
                                TOTAL_SUPPLY_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_TOTAL_SUPPLY)
                                                        .withTotalSupply(tokenTotalSupply)))),
                        childRecordsCheck(
                                BALANCE_OF_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_BALANCE)
                                                        .withBalance(tokenTransferAmount)))),
                        childRecordsCheck(
                                TRANSFER_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_TRANSFER)
                                                        .withErcFungibleTransferStatus(true)))))));
    }

    private HapiSpec someERC721NegativeTransferFromScenariosPass() {
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> contractMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> bCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("SomeERC721NegativeTransferFromScenariosPass")
                .given(
                        newKeyNamed(MULTI_KEY_NAME),
                        cryptoCreate(A_CIVILIAN)
                                .exposingCreatedIdTo(id -> aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        cryptoCreate(B_CIVILIAN)
                                .exposingCreatedIdTo(id -> bCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        uploadInitCode(SOME_ERC_721_SCENARIOS),
                        contractCreate(SOME_ERC_721_SCENARIOS).adminKey(MULTI_KEY_NAME),
                        tokenCreate(NF_TOKEN)
                                .supplyKey(MULTI_KEY_NAME)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(SOME_ERC_721_SCENARIOS)
                                .initialSupply(0)
                                .exposingCreatedIdTo(idLit ->
                                        tokenMirrorAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(idLit)))),
                        mintToken(NF_TOKEN, List.of(ByteString.copyFromUtf8("I"))),
                        tokenAssociate(A_CIVILIAN, NF_TOKEN),
                        tokenAssociate(B_CIVILIAN, NF_TOKEN))
                .when(
                        withOpContext((spec, opLog) -> {
                            zCivilianMirrorAddr.set(asHexedSolidityAddress(AccountID.newBuilder()
                                    .setAccountNum(666_666_666L)
                                    .build()));
                            contractMirrorAddr.set(
                                    asHexedSolidityAddress(spec.registry().getAccountID(SOME_ERC_721_SCENARIOS)));
                        }),
                        // --- Negative cases for transfer ---
                        // * Can't transfer a non-existent serial number
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        "iMustOwnAfterReceiving",
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        BigInteger.valueOf(5))
                                .payingWith(B_CIVILIAN)
                                .via("D")
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // * Can't transfer with missing "from"
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        TRANSFER_FROM,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(zCivilianMirrorAddr.get()),
                                        asHeadlongAddress(bCivilianMirrorAddr.get()),
                                        BigInteger.ONE)
                                .payingWith(GENESIS)
                                .via(MISSING_FROM)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        TRANSFER_FROM,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(contractMirrorAddr.get()),
                                        asHeadlongAddress(bCivilianMirrorAddr.get()),
                                        BigInteger.ONE)
                                .payingWith(GENESIS)
                                .via(MSG_SENDER_IS_THE_SAME_AS_FROM)),
                        cryptoTransfer(movingUnique(NF_TOKEN, 1L).between(B_CIVILIAN, A_CIVILIAN)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        TRANSFER_FROM,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        asHeadlongAddress(bCivilianMirrorAddr.get()),
                                        BigInteger.ONE)
                                .payingWith(GENESIS)
                                .via(MSG_SENDER_IS_NOT_THE_SAME_AS_FROM)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        cryptoApproveAllowance()
                                .payingWith(A_CIVILIAN)
                                .addNftAllowance(A_CIVILIAN, NF_TOKEN, SOME_ERC_721_SCENARIOS, false, List.of(1L))
                                .signedBy(DEFAULT_PAYER, A_CIVILIAN)
                                .fee(ONE_HBAR),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        TRANSFER_FROM,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(contractMirrorAddr.get()),
                                        asHeadlongAddress(bCivilianMirrorAddr.get()),
                                        BigInteger.ONE)
                                .payingWith(GENESIS)
                                .via("SERIAL_NOT_OWNED_BY_FROM")
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(
                        childRecordsCheck(
                                MISSING_FROM,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ACCOUNT_ID)),
                        childRecordsCheck(
                                "SERIAL_NOT_OWNED_BY_FROM",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO)),
                        childRecordsCheck(
                                MSG_SENDER_IS_THE_SAME_AS_FROM,
                                SUCCESS,
                                recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                MSG_SENDER_IS_NOT_THE_SAME_AS_FROM,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)));
    }

    private HapiSpec someERC721ApproveAndRemoveScenariosPass() {
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> bCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("SomeERC721ApproveAndRemoveScenariosPass")
                .given(
                        newKeyNamed(MULTI_KEY_NAME),
                        cryptoCreate(A_CIVILIAN)
                                .exposingCreatedIdTo(id -> aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        cryptoCreate(B_CIVILIAN)
                                .exposingCreatedIdTo(id -> bCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        uploadInitCode(SOME_ERC_721_SCENARIOS),
                        contractCreate(SOME_ERC_721_SCENARIOS).adminKey(MULTI_KEY_NAME),
                        tokenCreate(NF_TOKEN)
                                .supplyKey(MULTI_KEY_NAME)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(SOME_ERC_721_SCENARIOS)
                                .initialSupply(0)
                                .exposingCreatedIdTo(idLit ->
                                        tokenMirrorAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(idLit)))),
                        mintToken(
                                NF_TOKEN,
                                List.of(
                                        // 1
                                        ByteString.copyFromUtf8("I"),
                                        // 2
                                        ByteString.copyFromUtf8("turn"),
                                        // 3
                                        ByteString.copyFromUtf8("the"),
                                        // 4
                                        ByteString.copyFromUtf8("page"),
                                        // 5
                                        ByteString.copyFromUtf8("and"),
                                        // 6
                                        ByteString.copyFromUtf8("read"),
                                        // 7
                                        ByteString.copyFromUtf8("I dream of silent verses"))),
                        tokenAssociate(A_CIVILIAN, NF_TOKEN),
                        tokenAssociate(B_CIVILIAN, NF_TOKEN))
                .when(
                        withOpContext((spec, opLog) -> {
                            zCivilianMirrorAddr.set(asHexedSolidityAddress(AccountID.newBuilder()
                                    .setAccountNum(666_666_666L)
                                    .build()));
                        }),
                        // --- Negative cases for approve ---
                        // * Can't approve a non-existent serial number
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        DO_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        BigInteger.valueOf(666))
                                .via("MISSING_SERIAL_NO")
                                .gas(1_000_000)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // * Can't approve a non-existent spender
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        DO_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(zCivilianMirrorAddr.get()),
                                        BigInteger.valueOf(5))
                                .via(MISSING_TO)
                                .gas(1_000_000)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        getTokenNftInfo(NF_TOKEN, 5L).logged(),
                        childRecordsCheck(
                                MISSING_TO,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_ALLOWANCE_SPENDER_ID)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(INVALID_ALLOWANCE_SPENDER_ID)))),
                        // * Can't approve if msg.sender != owner and not an operator
                        cryptoTransfer(movingUnique(NF_TOKEN, 1L, 2L).between(SOME_ERC_721_SCENARIOS, A_CIVILIAN)),
                        cryptoTransfer(movingUnique(NF_TOKEN, 3L, 4L).between(SOME_ERC_721_SCENARIOS, B_CIVILIAN)),
                        getTokenNftInfo(NF_TOKEN, 1L).hasAccountID(A_CIVILIAN),
                        getTokenNftInfo(NF_TOKEN, 2L).hasAccountID(A_CIVILIAN),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        DO_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        BigInteger.valueOf(3))
                                .via("NOT_AN_OPERATOR")
                                .gas(1_000_000)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // * Can't revoke if not owner or approvedForAll
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        REVOKE_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        BigInteger.ONE)
                                .via("MISSING_REVOKE")
                                .gas(1_000_000)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        cryptoApproveAllowance()
                                .payingWith(B_CIVILIAN)
                                .addNftAllowance(B_CIVILIAN, NF_TOKEN, SOME_ERC_721_SCENARIOS, false, List.of(3L))
                                .signedBy(DEFAULT_PAYER, B_CIVILIAN)
                                .fee(ONE_HBAR),
                        // * Still can't approve if msg.sender != owner and not an operator
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        DO_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        BigInteger.valueOf(3))
                                .via("E")
                                .gas(1_000_000)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // --- Positive cases for approve ---
                        // * owner == msg.sender can approve
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        DO_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(bCivilianMirrorAddr.get()),
                                        BigInteger.valueOf(6))
                                .via("EXTANT_TO")
                                .gas(1_000_000)),
                        getTokenNftInfo(NF_TOKEN, 6L).hasSpenderID(B_CIVILIAN),
                        // Approve the contract as an operator of aCivilian's NFTs
                        cryptoApproveAllowance()
                                .payingWith(A_CIVILIAN)
                                .addNftAllowance(A_CIVILIAN, NF_TOKEN, SOME_ERC_721_SCENARIOS, true, List.of())
                                .signedBy(DEFAULT_PAYER, A_CIVILIAN)
                                .fee(ONE_HBAR),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        REVOKE_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        BigInteger.ONE)
                                .via("B")
                                .gas(1_000_000)),
                        // These should work because the contract is an operator for aCivilian
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        DO_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(bCivilianMirrorAddr.get()),
                                        BigInteger.TWO)
                                .via("C")
                                .gas(1_000_000)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        "iMustOwnAfterReceiving",
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        BigInteger.valueOf(5))
                                .payingWith(B_CIVILIAN)
                                .via("D")),
                        getTxnRecord("D").andAllChildRecords().logged())
                .then(
                        // Now make contract operator for bCivilian, approve aCivilian, have it grab
                        // serial number 3
                        cryptoApproveAllowance()
                                .payingWith(B_CIVILIAN)
                                .addNftAllowance(B_CIVILIAN, NF_TOKEN, SOME_ERC_721_SCENARIOS, true, List.of())
                                .signedBy(DEFAULT_PAYER, B_CIVILIAN)
                                .fee(ONE_HBAR),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        DO_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        BigInteger.valueOf(3))
                                .gas(1_000_000)),
                        cryptoTransfer(movingUniqueWithAllowance(NF_TOKEN, 3L).between(B_CIVILIAN, A_CIVILIAN))
                                .payingWith(A_CIVILIAN)
                                .fee(ONE_HBAR),
                        getTokenNftInfo(NF_TOKEN, 3L).hasAccountID(A_CIVILIAN),
                        cryptoApproveAllowance()
                                .payingWith(B_CIVILIAN)
                                .addNftAllowance(B_CIVILIAN, NF_TOKEN, A_CIVILIAN, false, List.of(5L))
                                .signedBy(DEFAULT_PAYER, B_CIVILIAN)
                                .fee(ONE_HBAR),
                        getTokenNftInfo(NF_TOKEN, 5L).hasAccountID(B_CIVILIAN).hasSpenderID(A_CIVILIAN),
                        // * Because contract is operator for bCivilian, it can revoke aCivilian as
                        // spender for 5L
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        REVOKE_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        BigInteger.valueOf(5))
                                .gas(1_000_000)),
                        getTokenNftInfo(NF_TOKEN, 5L).hasAccountID(B_CIVILIAN).hasNoSpender());
    }

    private HapiSpec someERC20ApproveAllowanceScenariosPass() {
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> contractMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> bCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("someERC20ApproveAllowanceScenariosPass")
                .given(
                        newKeyNamed(MULTI_KEY_NAME),
                        cryptoCreate(A_CIVILIAN)
                                .exposingCreatedIdTo(id -> aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        cryptoCreate(B_CIVILIAN)
                                .exposingCreatedIdTo(id -> bCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        uploadInitCode(SOME_ERC_20_SCENARIOS),
                        contractCreate(SOME_ERC_20_SCENARIOS).adminKey(MULTI_KEY_NAME),
                        tokenCreate(TOKEN)
                                .supplyKey(MULTI_KEY_NAME)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(B_CIVILIAN)
                                .initialSupply(10)
                                .exposingCreatedIdTo(idLit ->
                                        tokenMirrorAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(idLit)))))
                .when(
                        withOpContext((spec, opLog) -> {
                            zCivilianMirrorAddr.set(asHexedSolidityAddress(AccountID.newBuilder()
                                    .setAccountNum(666_666_666L)
                                    .build()));
                            contractMirrorAddr.set(
                                    asHexedSolidityAddress(spec.registry().getAccountID(SOME_ERC_20_SCENARIOS)));
                        }),
                        sourcing(() -> contractCall(
                                        SOME_ERC_20_SCENARIOS,
                                        DO_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        BigInteger.ZERO)
                                .via("ACCOUNT_NOT_ASSOCIATED_TXN")
                                .gas(1_000_000)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        tokenAssociate(SOME_ERC_20_SCENARIOS, TOKEN),
                        sourcing(() -> contractCall(
                                        SOME_ERC_20_SCENARIOS,
                                        DO_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(zCivilianMirrorAddr.get()),
                                        BigInteger.valueOf(5))
                                .via(MISSING_TO)
                                .gas(1_000_000)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_20_SCENARIOS,
                                        DO_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(contractMirrorAddr.get()),
                                        BigInteger.valueOf(5))
                                .via("SPENDER_SAME_AS_OWNER_TXN")
                                .gas(1_000_000)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_20_SCENARIOS,
                                        DO_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        BigInteger.valueOf(5))
                                .via("SUCCESSFUL_APPROVE_TXN")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_20_SCENARIOS,
                                        GET_ALLOWANCE,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(contractMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()))
                                .via("ALLOWANCE_TXN")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)),
                        sourcing(() -> contractCallLocal(
                                SOME_ERC_20_SCENARIOS,
                                GET_ALLOWANCE,
                                asHeadlongAddress(tokenMirrorAddr.get()),
                                asHeadlongAddress(contractMirrorAddr.get()),
                                asHeadlongAddress(aCivilianMirrorAddr.get()))),
                        sourcing(() -> contractCall(
                                        SOME_ERC_20_SCENARIOS,
                                        DO_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        BigInteger.ZERO)
                                .via("SUCCESSFUL_REVOKE_TXN")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_20_SCENARIOS,
                                        GET_ALLOWANCE,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(contractMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()))
                                .via("ALLOWANCE_AFTER_REVOKE_TXN")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_20_SCENARIOS,
                                        GET_ALLOWANCE,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(zCivilianMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()))
                                .via("MISSING_OWNER_ID")
                                .gas(1_000_000)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(
                        childRecordsCheck(
                                "ACCOUNT_NOT_ASSOCIATED_TXN",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)),
                        childRecordsCheck(
                                MISSING_TO,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ALLOWANCE_SPENDER_ID)),
                        childRecordsCheck(
                                "SPENDER_SAME_AS_OWNER_TXN",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_ACCOUNT_SAME_AS_OWNER)),
                        childRecordsCheck(
                                "SUCCESSFUL_APPROVE_TXN", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "SUCCESSFUL_REVOKE_TXN", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "MISSING_OWNER_ID",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ALLOWANCE_OWNER_ID)),
                        childRecordsCheck(
                                "ALLOWANCE_TXN",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_ALLOWANCE)
                                                        .withAllowance(5L)))),
                        childRecordsCheck(
                                "ALLOWANCE_AFTER_REVOKE_TXN",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_ALLOWANCE)
                                                        .withAllowance(0L)))));
    }

    private HapiSpec someERC20NegativeTransferFromScenariosPass() {
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> contractMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> bCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("someERC20NegativeTransferFromScenariosPass")
                .given(
                        newKeyNamed(MULTI_KEY_NAME),
                        cryptoCreate(A_CIVILIAN)
                                .exposingCreatedIdTo(id -> aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        cryptoCreate(B_CIVILIAN)
                                .exposingCreatedIdTo(id -> bCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        uploadInitCode(SOME_ERC_20_SCENARIOS),
                        contractCreate(SOME_ERC_20_SCENARIOS).adminKey(MULTI_KEY_NAME),
                        tokenCreate(TOKEN)
                                .supplyKey(MULTI_KEY_NAME)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(SOME_ERC_20_SCENARIOS)
                                .initialSupply(10)
                                .exposingCreatedIdTo(idLit ->
                                        tokenMirrorAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(idLit)))))
                .when(
                        withOpContext((spec, opLog) -> {
                            zCivilianMirrorAddr.set(asHexedSolidityAddress(AccountID.newBuilder()
                                    .setAccountNum(666_666_666L)
                                    .build()));
                            contractMirrorAddr.set(
                                    asHexedSolidityAddress(spec.registry().getAccountID(SOME_ERC_20_SCENARIOS)));
                        }),
                        sourcing(() -> contractCall(
                                        SOME_ERC_20_SCENARIOS,
                                        DO_TRANSFER_FROM,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(contractMirrorAddr.get()),
                                        asHeadlongAddress(bCivilianMirrorAddr.get()),
                                        BigInteger.ONE)
                                .payingWith(GENESIS)
                                .via("TOKEN_NOT_ASSOCIATED_TO_ACCOUNT_TXN")
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        tokenAssociate(B_CIVILIAN, TOKEN),
                        sourcing(() -> contractCall(
                                        SOME_ERC_20_SCENARIOS,
                                        DO_TRANSFER_FROM,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(zCivilianMirrorAddr.get()),
                                        asHeadlongAddress(bCivilianMirrorAddr.get()),
                                        BigInteger.ONE)
                                .payingWith(GENESIS)
                                .via(MISSING_FROM)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_20_SCENARIOS,
                                        DO_TRANSFER_FROM,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(contractMirrorAddr.get()),
                                        asHeadlongAddress(bCivilianMirrorAddr.get()),
                                        BigInteger.ONE)
                                .payingWith(GENESIS)
                                .via(MSG_SENDER_IS_THE_SAME_AS_FROM)
                                .hasKnownStatus(SUCCESS)),
                        cryptoTransfer(moving(9L, TOKEN).between(SOME_ERC_20_SCENARIOS, B_CIVILIAN)),
                        tokenAssociate(A_CIVILIAN, TOKEN),
                        sourcing(() -> contractCall(
                                        SOME_ERC_20_SCENARIOS,
                                        DO_TRANSFER_FROM,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(bCivilianMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        BigInteger.ONE)
                                .payingWith(GENESIS)
                                .via(MSG_SENDER_IS_NOT_THE_SAME_AS_FROM)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        cryptoApproveAllowance()
                                .payingWith(B_CIVILIAN)
                                .addTokenAllowance(B_CIVILIAN, TOKEN, SOME_ERC_20_SCENARIOS, 1L)
                                .signedBy(DEFAULT_PAYER, B_CIVILIAN)
                                .fee(ONE_HBAR),
                        sourcing(() -> contractCall(
                                        SOME_ERC_20_SCENARIOS,
                                        DO_TRANSFER_FROM,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(bCivilianMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        BigInteger.valueOf(5))
                                .payingWith(GENESIS)
                                .via("TRY_TO_TRANSFER_MORE_THAN_APPROVED_AMOUNT_TXN")
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        cryptoApproveAllowance()
                                .payingWith(B_CIVILIAN)
                                .addTokenAllowance(B_CIVILIAN, TOKEN, SOME_ERC_20_SCENARIOS, 20L)
                                .signedBy(DEFAULT_PAYER, B_CIVILIAN)
                                .fee(ONE_HBAR),
                        sourcing(() -> contractCall(
                                        SOME_ERC_20_SCENARIOS,
                                        DO_TRANSFER_FROM,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(bCivilianMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        BigInteger.valueOf(20))
                                .payingWith(GENESIS)
                                .via("TRY_TO_TRANSFER_MORE_THAN_OWNERS_BALANCE_TXN")
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(
                        childRecordsCheck(
                                "TOKEN_NOT_ASSOCIATED_TO_ACCOUNT_TXN",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)),
                        childRecordsCheck(
                                MISSING_FROM,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_ACCOUNT_ID)),
                        childRecordsCheck(
                                MSG_SENDER_IS_THE_SAME_AS_FROM,
                                SUCCESS,
                                recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                MSG_SENDER_IS_NOT_THE_SAME_AS_FROM,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)),
                        childRecordsCheck(
                                "TRY_TO_TRANSFER_MORE_THAN_APPROVED_AMOUNT_TXN",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(AMOUNT_EXCEEDS_ALLOWANCE)),
                        childRecordsCheck(
                                "TRY_TO_TRANSFER_MORE_THAN_OWNERS_BALANCE_TXN",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INSUFFICIENT_TOKEN_BALANCE)));
    }

    private HapiSpec someERC20ApproveAllowanceScenarioInOneCall() {
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> contractMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> bCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("someERC20ApproveAllowanceScenarioInOneCall")
                .given(
                        newKeyNamed(MULTI_KEY_NAME),
                        cryptoCreate(A_CIVILIAN)
                                .exposingCreatedIdTo(id -> aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        cryptoCreate(B_CIVILIAN)
                                .exposingCreatedIdTo(id -> bCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        uploadInitCode(SOME_ERC_20_SCENARIOS),
                        contractCreate(SOME_ERC_20_SCENARIOS).adminKey(MULTI_KEY_NAME),
                        tokenCreate(TOKEN)
                                .supplyKey(MULTI_KEY_NAME)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(B_CIVILIAN)
                                .initialSupply(10)
                                .exposingCreatedIdTo(idLit ->
                                        tokenMirrorAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(idLit)))),
                        tokenAssociate(SOME_ERC_20_SCENARIOS, TOKEN))
                .when(
                        withOpContext((spec, opLog) -> {
                            zCivilianMirrorAddr.set(asHexedSolidityAddress(AccountID.newBuilder()
                                    .setAccountNum(666_666_666L)
                                    .build()));
                            contractMirrorAddr.set(
                                    asHexedSolidityAddress(spec.registry().getAccountID(SOME_ERC_20_SCENARIOS)));
                        }),
                        sourcing(() -> contractCall(
                                        SOME_ERC_20_SCENARIOS,
                                        "approveAndGetAllowanceAmount",
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        BigInteger.valueOf(5))
                                .via("APPROVE_AND_GET_ALLOWANCE_TXN")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)
                                .logged()))
                .then(childRecordsCheck(
                        "APPROVE_AND_GET_ALLOWANCE_TXN",
                        SUCCESS,
                        recordWith().status(SUCCESS),
                        recordWith()
                                .status(SUCCESS)
                                .contractCallResult(resultWith()
                                        .contractCallResult(htsPrecompileResult()
                                                .forFunction(FunctionType.ERC_ALLOWANCE)
                                                .withAllowance(5L)))));
    }

    private HapiSpec directCallsWorkForERC721() {

        final AtomicReference<String> tokenNum = new AtomicReference<>();

        final var tokenSymbol = "FDFDFD";
        final var tokenTotalSupply = 1;

        final var tokenURITxn = "tokenURITxn";
        final var ownerOfTxn = "ownerOfTxn";

        return defaultHapiSpec("DirectCallsWorkForERC721")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .name(TOKEN_NAME)
                                .symbol(tokenSymbol)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(tokenNum::set),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
                        tokenAssociate(ACCOUNT, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(RECIPIENT, NON_FUNGIBLE_TOKEN),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(TOKEN_TREASURY, ACCOUNT)))
                .when(withOpContext((spec, ignore) -> {
                    var tokenAddress = asHexedSolidityAddress(asToken(tokenNum.get()));
                    allRunFor(
                            spec,
                            contractCallWithFunctionAbi(
                                            tokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    "name",
                                                    ERC_721_ABI))
                                    .via(NAME_TXN),
                            contractCallWithFunctionAbi(
                                            tokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    SYMBOL,
                                                    ERC_721_ABI))
                                    .via(SYMBOL_TXN),
                            contractCallWithFunctionAbi(
                                            tokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    TOKEN_URI,
                                                    ERC_721_ABI),
                                            BigInteger.ONE)
                                    .via(tokenURITxn),
                            contractCallWithFunctionAbi(
                                            tokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    TOTAL_SUPPLY,
                                                    ERC_721_ABI))
                                    .via(TOTAL_SUPPLY_TXN),
                            contractCallWithFunctionAbi(
                                            tokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    BALANCE_OF,
                                                    ERC_721_ABI),
                                            asHeadlongAddress(asHexedSolidityAddress(
                                                    spec.registry().getAccountID(ACCOUNT))))
                                    .via(BALANCE_OF_TXN),
                            contractCallWithFunctionAbi(
                                            tokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    OWNER_OF,
                                                    ERC_721_ABI),
                                            BigInteger.ONE)
                                    .via(ownerOfTxn));
                }))
                .then(withOpContext((spec, ignore) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                NAME_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_NAME)
                                                        .withName(TOKEN_NAME)))),
                        childRecordsCheck(
                                SYMBOL_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_SYMBOL)
                                                        .withSymbol(tokenSymbol)))),
                        childRecordsCheck(
                                tokenURITxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_TOKEN_URI)
                                                        .withTokenUri(FIRST)))),
                        childRecordsCheck(
                                TOTAL_SUPPLY_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_TOTAL_SUPPLY)
                                                        .withTotalSupply(tokenTotalSupply)))),
                        childRecordsCheck(
                                BALANCE_OF_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_BALANCE)
                                                        .withBalance(1)))),
                        childRecordsCheck(
                                ownerOfTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_OWNER)
                                                        .withOwner(asAddress(
                                                                spec.registry().getAccountID(ACCOUNT)))))))));
    }

    private HapiSpec someERC721GetApprovedScenariosPass() {
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zTokenMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("someERC721GetApprovedScenariosPass")
                .given(
                        newKeyNamed(MULTI_KEY_NAME),
                        cryptoCreate(A_CIVILIAN)
                                .exposingCreatedIdTo(id -> aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        uploadInitCode(SOME_ERC_721_SCENARIOS),
                        contractCreate(SOME_ERC_721_SCENARIOS).adminKey(MULTI_KEY_NAME),
                        tokenCreate(NF_TOKEN)
                                .supplyKey(MULTI_KEY_NAME)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(SOME_ERC_721_SCENARIOS)
                                .initialSupply(0)
                                .exposingCreatedIdTo(idLit ->
                                        tokenMirrorAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(idLit)))),
                        mintToken(
                                NF_TOKEN,
                                List.of(
                                        // 1
                                        ByteString.copyFromUtf8("A"),
                                        // 2
                                        ByteString.copyFromUtf8("B"))),
                        tokenAssociate(A_CIVILIAN, NF_TOKEN))
                .when(
                        withOpContext((spec, opLog) -> {
                            zCivilianMirrorAddr.set(asHexedSolidityAddress(AccountID.newBuilder()
                                    .setAccountNum(666_666_666L)
                                    .build()));
                            zTokenMirrorAddr.set(asHexedSolidityAddress(
                                    TokenID.newBuilder().setTokenNum(666_666L).build()));
                        }),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        GET_APPROVED,
                                        asHeadlongAddress(zTokenMirrorAddr.get()),
                                        BigInteger.ONE)
                                .via(MISSING_TOKEN)
                                .gas(1_000_000)
                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        DO_SPECIFIC_APPROVAL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        BigInteger.ONE)
                                .gas(1_000_000)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        GET_APPROVED,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        BigInteger.valueOf(55))
                                .via("MISSING_SERIAL")
                                .gas(1_000_000)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        getTokenNftInfo(NF_TOKEN, 1L).logged(),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        GET_APPROVED,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        BigInteger.TWO)
                                .via("MISSING_SPENDER")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        GET_APPROVED,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        BigInteger.ONE)
                                .via(WITH_SPENDER)
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)),
                        getTxnRecord(WITH_SPENDER).andAllChildRecords().logged(),
                        sourcing(() -> contractCallLocal(
                                        SOME_ERC_721_SCENARIOS,
                                        GET_APPROVED,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        BigInteger.ONE)
                                .logged()
                                .gas(1_000_000)
                                .has(resultWith().contractCallResult(hexedAddress(aCivilianMirrorAddr.get())))))
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                "MISSING_SPENDER",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_GET_APPROVED)
                                                        .withSpender(new byte[0])))),
                        childRecordsCheck(
                                WITH_SPENDER,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_GET_APPROVED)
                                                        .withSpender(asAddress(
                                                                spec.registry().getAccountID(A_CIVILIAN)))))))));
    }

    private HapiSpec someERC721BalanceOfScenariosPass() {
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> bCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zTokenMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("someERC721BalanceOfScenariosPass")
                .given(
                        newKeyNamed(MULTI_KEY_NAME),
                        cryptoCreate(A_CIVILIAN)
                                .exposingCreatedIdTo(id -> aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        cryptoCreate(B_CIVILIAN)
                                .exposingCreatedIdTo(id -> bCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        uploadInitCode(SOME_ERC_721_SCENARIOS),
                        contractCreate(SOME_ERC_721_SCENARIOS).adminKey(MULTI_KEY_NAME),
                        tokenCreate(NF_TOKEN)
                                .supplyKey(MULTI_KEY_NAME)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(SOME_ERC_721_SCENARIOS)
                                .initialSupply(0)
                                .exposingCreatedIdTo(idLit ->
                                        tokenMirrorAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(idLit)))),
                        mintToken(
                                NF_TOKEN,
                                List.of(
                                        // 1
                                        ByteString.copyFromUtf8("A"),
                                        // 2
                                        ByteString.copyFromUtf8("B"))),
                        tokenAssociate(A_CIVILIAN, NF_TOKEN),
                        cryptoTransfer(movingUnique(NF_TOKEN, 1L, 2L).between(SOME_ERC_721_SCENARIOS, A_CIVILIAN)))
                .when(
                        withOpContext((spec, opLog) -> zTokenMirrorAddr.set(asHexedSolidityAddress(
                                TokenID.newBuilder().setTokenNum(666_666L).build()))),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        GET_BALANCE_OF,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()))
                                .via("BALANCE_OF")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        GET_BALANCE_OF,
                                        asHeadlongAddress(zTokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()))
                                .via(MISSING_TOKEN)
                                .gas(1_000_000)
                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        GET_BALANCE_OF,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(bCivilianMirrorAddr.get()))
                                .via("NOT_ASSOCIATED")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)))
                .then(
                        childRecordsCheck(
                                "BALANCE_OF",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_BALANCE)
                                                        .withBalance(2)))),
                        childRecordsCheck(
                                "NOT_ASSOCIATED",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_BALANCE)
                                                        .withBalance(0)))));
    }

    private HapiSpec someERC721OwnerOfScenariosPass() {
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zTokenMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("someERC721OwnerOfScenariosPass")
                .given(
                        newKeyNamed(MULTI_KEY_NAME),
                        cryptoCreate(A_CIVILIAN)
                                .exposingCreatedIdTo(id -> aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        uploadInitCode(SOME_ERC_721_SCENARIOS),
                        contractCreate(SOME_ERC_721_SCENARIOS).adminKey(MULTI_KEY_NAME),
                        tokenCreate(NF_TOKEN)
                                .supplyKey(MULTI_KEY_NAME)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(SOME_ERC_721_SCENARIOS)
                                .initialSupply(0)
                                .exposingCreatedIdTo(idLit ->
                                        tokenMirrorAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(idLit)))),
                        mintToken(
                                NF_TOKEN,
                                List.of(
                                        // 1
                                        ByteString.copyFromUtf8("A"),
                                        // 2
                                        ByteString.copyFromUtf8("B"))),
                        tokenAssociate(A_CIVILIAN, NF_TOKEN))
                .when(
                        withOpContext((spec, opLog) -> {
                            zCivilianMirrorAddr.set(asHexedSolidityAddress(AccountID.newBuilder()
                                    .setAccountNum(666_666_666L)
                                    .build()));
                            zTokenMirrorAddr.set(asHexedSolidityAddress(
                                    TokenID.newBuilder().setTokenNum(666_666L).build()));
                        }),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        GET_OWNER_OF,
                                        asHeadlongAddress(zTokenMirrorAddr.get()),
                                        BigInteger.ONE)
                                .via(MISSING_TOKEN)
                                .gas(1_000_000)
                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        GET_OWNER_OF,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        BigInteger.valueOf(55))
                                .gas(1_000_000)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        GET_OWNER_OF,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        BigInteger.TWO)
                                .via("TREASURY_OWNER")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)),
                        cryptoTransfer(movingUnique(NF_TOKEN, 1L).between(SOME_ERC_721_SCENARIOS, A_CIVILIAN)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        GET_OWNER_OF,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        BigInteger.ONE)
                                .via("CIVILIAN_OWNER")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)))
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                "TREASURY_OWNER",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_OWNER)
                                                        .withOwner(asAddress(spec.registry()
                                                                .getAccountID(SOME_ERC_721_SCENARIOS)))))),
                        childRecordsCheck(
                                "CIVILIAN_OWNER",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_GET_APPROVED)
                                                        .withSpender(asAddress(
                                                                spec.registry().getAccountID(A_CIVILIAN)))))))));
    }

    private HapiSpec someERC721IsApprovedForAllScenariosPass() {
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> contractMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zTokenMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("someERC721IsApprovedForAllScenariosPass")
                .given(
                        newKeyNamed(MULTI_KEY_NAME),
                        cryptoCreate(A_CIVILIAN)
                                .exposingCreatedIdTo(id -> aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        uploadInitCode(SOME_ERC_721_SCENARIOS),
                        contractCreate(SOME_ERC_721_SCENARIOS).adminKey(MULTI_KEY_NAME),
                        tokenCreate(NF_TOKEN)
                                .supplyKey(MULTI_KEY_NAME)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(SOME_ERC_721_SCENARIOS)
                                .initialSupply(0)
                                .exposingCreatedIdTo(idLit ->
                                        tokenMirrorAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(idLit)))),
                        mintToken(
                                NF_TOKEN,
                                List.of(
                                        // 1
                                        ByteString.copyFromUtf8("A"),
                                        // 2
                                        ByteString.copyFromUtf8("B"))),
                        tokenAssociate(A_CIVILIAN, NF_TOKEN),
                        cryptoTransfer(movingUnique(NF_TOKEN, 1L, 2L).between(SOME_ERC_721_SCENARIOS, A_CIVILIAN)))
                .when(
                        withOpContext((spec, opLog) -> {
                            zCivilianMirrorAddr.set(asHexedSolidityAddress(AccountID.newBuilder()
                                    .setAccountNum(666_666_666L)
                                    .build()));
                            zTokenMirrorAddr.set(asHexedSolidityAddress(
                                    TokenID.newBuilder().setTokenNum(666_666L).build()));
                            contractMirrorAddr.set(
                                    asHexedSolidityAddress(spec.registry().getAccountID(SOME_ERC_721_SCENARIOS)));
                        }),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        IS_APPROVED_FOR_ALL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(zCivilianMirrorAddr.get()),
                                        asHeadlongAddress(contractMirrorAddr.get()))
                                .via("OWNER_DOES_NOT_EXISTS")
                                .gas(1_000_000)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        IS_APPROVED_FOR_ALL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        asHeadlongAddress(zCivilianMirrorAddr.get()))
                                .via(OPERATOR_DOES_NOT_EXISTS)
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        IS_APPROVED_FOR_ALL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        asHeadlongAddress(contractMirrorAddr.get()))
                                .via("OPERATOR_IS_NOT_APPROVED")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)),
                        cryptoApproveAllowance()
                                .payingWith(A_CIVILIAN)
                                .addNftAllowance(A_CIVILIAN, NF_TOKEN, SOME_ERC_721_SCENARIOS, true, List.of())
                                .signedBy(DEFAULT_PAYER, A_CIVILIAN)
                                .fee(ONE_HBAR),
                        getAccountDetails(A_CIVILIAN)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(0)
                                        .nftApprovedForAllAllowancesCount(1)
                                        .tokenAllowancesCount(0)
                                        .nftApprovedAllowancesContaining(NF_TOKEN, SOME_ERC_721_SCENARIOS)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        IS_APPROVED_FOR_ALL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        asHeadlongAddress(contractMirrorAddr.get()))
                                .via("OPERATOR_IS_APPROVED_FOR_ALL")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)),
                        sourcing(() -> contractCallLocal(
                                        SOME_ERC_721_SCENARIOS,
                                        IS_APPROVED_FOR_ALL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        asHeadlongAddress(contractMirrorAddr.get()))
                                .gas(1_000_000)
                                .has(resultWith().contractCallResult(flag(true)))))
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                OPERATOR_DOES_NOT_EXISTS,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_IS_APPROVED_FOR_ALL)
                                                        .withIsApprovedForAll(false)))),
                        childRecordsCheck(
                                "OPERATOR_IS_NOT_APPROVED",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_IS_APPROVED_FOR_ALL)
                                                        .withIsApprovedForAll(false)))),
                        childRecordsCheck(
                                "OPERATOR_IS_APPROVED_FOR_ALL",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_IS_APPROVED_FOR_ALL)
                                                        .withIsApprovedForAll(true)))))));
    }

    private HapiSpec someERC721SetApprovedForAllScenariosPass() {
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> contractMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> zTokenMirrorAddr = new AtomicReference<>();

        return defaultHapiSpec("someERC721SetApprovedForAllScenariosPass")
                .given(
                        newKeyNamed(MULTI_KEY_NAME),
                        cryptoCreate(A_CIVILIAN)
                                .exposingCreatedIdTo(id -> aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
                        uploadInitCode(SOME_ERC_721_SCENARIOS),
                        contractCreate(SOME_ERC_721_SCENARIOS).adminKey(MULTI_KEY_NAME),
                        tokenCreate(NF_TOKEN)
                                .supplyKey(MULTI_KEY_NAME)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(SOME_ERC_721_SCENARIOS)
                                .initialSupply(0)
                                .exposingCreatedIdTo(idLit ->
                                        tokenMirrorAddr.set(asHexedSolidityAddress(HapiPropertySource.asToken(idLit)))),
                        mintToken(
                                NF_TOKEN,
                                List.of(
                                        // 1
                                        ByteString.copyFromUtf8("A"),
                                        // 2
                                        ByteString.copyFromUtf8("B"))),
                        tokenAssociate(A_CIVILIAN, NF_TOKEN))
                .when(
                        withOpContext((spec, opLog) -> {
                            zCivilianMirrorAddr.set(asHexedSolidityAddress(AccountID.newBuilder()
                                    .setAccountNum(666_666_666L)
                                    .build()));
                            zTokenMirrorAddr.set(asHexedSolidityAddress(
                                    TokenID.newBuilder().setTokenNum(666_666L).build()));
                            contractMirrorAddr.set(
                                    asHexedSolidityAddress(spec.registry().getAccountID(SOME_ERC_721_SCENARIOS)));
                        }),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        SET_APPROVAL_FOR_ALL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(contractMirrorAddr.get()),
                                        true)
                                .via("OPERATOR_SAME_AS_MSG_SENDER")
                                .gas(1_000_000)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        SET_APPROVAL_FOR_ALL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(zCivilianMirrorAddr.get()),
                                        true)
                                .via(OPERATOR_DOES_NOT_EXISTS)
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        SET_APPROVAL_FOR_ALL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        true)
                                .via("OPERATOR_EXISTS")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        IS_APPROVED_FOR_ALL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(contractMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()))
                                .via("SUCCESSFULLY_APPROVED_CHECK_TXN")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        SET_APPROVAL_FOR_ALL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()),
                                        false)
                                .via("OPERATOR_EXISTS_REVOKE_APPROVE_FOR_ALL")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)),
                        sourcing(() -> contractCall(
                                        SOME_ERC_721_SCENARIOS,
                                        IS_APPROVED_FOR_ALL,
                                        asHeadlongAddress(tokenMirrorAddr.get()),
                                        asHeadlongAddress(contractMirrorAddr.get()),
                                        asHeadlongAddress(aCivilianMirrorAddr.get()))
                                .via("SUCCESSFULLY_REVOKED_CHECK_TXN")
                                .gas(1_000_000)
                                .hasKnownStatus(SUCCESS)))
                .then(
                        childRecordsCheck(
                                "OPERATOR_SAME_AS_MSG_SENDER",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(SPENDER_ACCOUNT_SAME_AS_OWNER)),
                        childRecordsCheck(
                                OPERATOR_DOES_NOT_EXISTS, SUCCESS, recordWith().status(INVALID_ALLOWANCE_SPENDER_ID)),
                        childRecordsCheck(
                                "OPERATOR_EXISTS", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "OPERATOR_EXISTS_REVOKE_APPROVE_FOR_ALL",
                                SUCCESS,
                                recordWith().status(SUCCESS)),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                childRecordsCheck(
                                        "SUCCESSFULLY_APPROVED_CHECK_TXN",
                                        SUCCESS,
                                        recordWith()
                                                .status(SUCCESS)
                                                .contractCallResult(resultWith()
                                                        .contractCallResult(htsPrecompileResult()
                                                                .forFunction(FunctionType.ERC_IS_APPROVED_FOR_ALL)
                                                                .withIsApprovedForAll(true)))),
                                childRecordsCheck(
                                        "SUCCESSFULLY_REVOKED_CHECK_TXN",
                                        SUCCESS,
                                        recordWith()
                                                .status(SUCCESS)
                                                .contractCallResult(resultWith()
                                                        .contractCallResult(htsPrecompileResult()
                                                                .forFunction(FunctionType.ERC_IS_APPROVED_FOR_ALL)
                                                                .withIsApprovedForAll(false)))))));
    }

    private HapiSpec getErc721IsApprovedForAll() {
        final var notApprovedTxn = "notApprovedTxn";
        final var approvedForAllTxn = "approvedForAllTxn";

        return defaultHapiSpec("getErc721IsApprovedForAll")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(ByteString.copyFromUtf8("A"), ByteString.copyFromUtf8("B"))),
                        uploadInitCode(ERC_721_CONTRACT),
                        contractCreate(ERC_721_CONTRACT),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L).between(TOKEN_TREASURY, OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, RECIPIENT, true, List.of(1L, 2L))
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith()
                                        .cryptoAllowancesCount(0)
                                        .nftApprovedForAllAllowancesCount(1)
                                        .tokenAllowancesCount(0)
                                        .nftApprovedAllowancesContaining(NON_FUNGIBLE_TOKEN, RECIPIENT)),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasSpenderID(RECIPIENT),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasSpenderID(RECIPIENT))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_721_CONTRACT,
                                        "isApprovedForAll",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))))
                                .payingWith(OWNER)
                                .via(approvedForAllTxn)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER),
                        contractCall(
                                        ERC_721_CONTRACT,
                                        "isApprovedForAll",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))))
                                .payingWith(OWNER)
                                .via(notApprovedTxn)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                approvedForAllTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_IS_APPROVED_FOR_ALL)
                                                        .withIsApprovedForAll(true)))),
                        childRecordsCheck(
                                notApprovedTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_IS_APPROVED_FOR_ALL)
                                                        .withIsApprovedForAll(false)))));
    }

    private HapiSpec erc721TokenApprove() {
        return defaultHapiSpec("Erc721TokenApprove")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_721_CONTRACT),
                        contractCreate(ERC_721_CONTRACT),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
                        tokenAssociate(ACCOUNT, List.of(NON_FUNGIBLE_TOKEN)),
                        tokenAssociate(RECIPIENT, List.of(NON_FUNGIBLE_TOKEN)),
                        tokenAssociate(ERC_721_CONTRACT, List.of(NON_FUNGIBLE_TOKEN)),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(TOKEN_TREASURY, ERC_721_CONTRACT)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_721_CONTRACT,
                                        APPROVE,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))),
                                        BigInteger.ONE)
                                .payingWith(ACCOUNT)
                                .via(NAME_TXN)
                                .hasKnownStatus(SUCCESS)
                                .gas(GAS_TO_OFFER))))
                .then(getTxnRecord(NAME_TXN).andAllChildRecords().logged());
    }

    private HapiSpec erc721GetApproved() {
        final var theSpender2 = "spender2";

        return defaultHapiSpec("Erc721GetApproved")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER),
                        cryptoCreate(theSpender2),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .initialSupply(0)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .treasury(TOKEN_TREASURY),
                        uploadInitCode(ERC_721_CONTRACT),
                        contractCreate(ERC_721_CONTRACT),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(ByteString.copyFromUtf8("a")))
                                .via(NFT_TOKEN_MINT),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(TOKEN_TREASURY, OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .via(BASE_APPROVE_TXN)
                                .logged()
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        ERC_721_CONTRACT,
                                        "getApproved",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        BigInteger.ONE)
                                .payingWith(OWNER)
                                .via(ALLOWANCE_TXN)
                                .hasKnownStatus(SUCCESS))))
                .then(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                childRecordsCheck(
                                        ALLOWANCE_TXN,
                                        SUCCESS,
                                        recordWith()
                                                .status(SUCCESS)
                                                .contractCallResult(resultWith()
                                                        .contractCallResult(htsPrecompileResult()
                                                                .forFunction(FunctionType.ERC_GET_APPROVED)
                                                                .withSpender(asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(SPENDER)))))))),
                        getTxnRecord(ALLOWANCE_TXN).andAllChildRecords().logged());
    }

    private HapiSpec erc20TransferFrom() {
        final var allowanceTxn2 = "allowanceTxn2";

        return defaultHapiSpec("ERC_20_ALLOWANCE")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(RECIPIENT, FUNGIBLE_TOKEN),
                        tokenAssociate(ERC_20_CONTRACT, FUNGIBLE_TOKEN),
                        cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        // ERC_20_CONTRACT is approved as spender of
                        // fungible tokens for OWNER
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, ERC_20_CONTRACT, 2L)
                                .via(BASE_APPROVE_TXN)
                                .logged()
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR),
                        // Check that ERC_20_CONTRACT has allowance of 2
                        contractCall(
                                        ERC_20_CONTRACT,
                                        ALLOWANCE,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ERC_20_CONTRACT))))
                                .gas(500_000L)
                                .via(ALLOWANCE_TXN)
                                .hasKnownStatus(SUCCESS),
                        // ERC_20_CONTRACT calls the precompile transferFrom
                        // as the spender
                        contractCall(
                                        ERC_20_CONTRACT,
                                        TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))),
                                        BigInteger.TWO)
                                .gas(500_000L)
                                .via(TRANSFER_FROM_ACCOUNT_TXN)
                                .hasKnownStatus(SUCCESS),
                        // ERC_20_CONTRACT should have spent its allowance
                        contractCall(
                                        ERC_20_CONTRACT,
                                        ALLOWANCE,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ERC_20_CONTRACT))))
                                .gas(500_000L)
                                .via(allowanceTxn2)
                                .hasKnownStatus(SUCCESS))))
                .then(
                        childRecordsCheck(
                                ALLOWANCE_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_ALLOWANCE)
                                                        .withAllowance(2)))),
                        childRecordsCheck(
                                allowanceTxn2,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .forFunction(FunctionType.ERC_ALLOWANCE)
                                                        .withAllowance(0)))));
    }

    private HapiSpec erc20TransferFromSelf() {
        return defaultHapiSpec("Erc20TransferFromSelf")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_20_CONTRACT),
                        contractCreate(ERC_20_CONTRACT),
                        tokenAssociate(RECIPIENT, FUNGIBLE_TOKEN),
                        tokenAssociate(ERC_20_CONTRACT, FUNGIBLE_TOKEN),
                        cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ERC_20_CONTRACT)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        // ERC_20_CONTRACT should be able to transfer its
                        // own tokens
                        contractCall(
                                        ERC_20_CONTRACT,
                                        TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ERC_20_CONTRACT))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))),
                                        BigInteger.TWO)
                                .gas(500_000L)
                                .via(TRANSFER_FROM_ACCOUNT_TXN)
                                .hasKnownStatus(SUCCESS))))
                .then(getAccountBalance(RECIPIENT).hasTokenBalance(FUNGIBLE_TOKEN, 2));
    }

    private HapiSpec erc721TransferFromWithApproval() {
        return defaultHapiSpec("ERC_721_TRANSFER_FROM_WITH_APPROVAL")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(SPENDER),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_721_CONTRACT),
                        contractCreate(ERC_721_CONTRACT),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(SPENDER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(RECIPIENT, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(ERC_721_CONTRACT, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META, SECOND_META)),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(TOKEN_TREASURY, OWNER)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, ERC_721_CONTRACT, false, List.of(1L))
                                .via(BASE_APPROVE_TXN)
                                .logged()
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasSpenderID(ERC_721_CONTRACT),
                        contractCall(
                                        ERC_721_CONTRACT,
                                        TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))),
                                        BigInteger.ONE)
                                .via(TRANSFER_FROM_ACCOUNT_TXN)
                                .hasKnownStatus(SUCCESS),
                        getTxnRecord(TRANSFER_FROM_ACCOUNT_TXN)
                                .andAllChildRecords()
                                .logged(),
                        getAccountDetails(RECIPIENT).logged(),
                        getAccountDetails(OWNER).logged(),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasNoSpender())))
                .then(
                        getAccountInfo(OWNER).savingSnapshot(OWNER),
                        getAccountInfo(RECIPIENT).savingSnapshot(RECIPIENT),
                        withOpContext((spec, log) -> {
                            final var sender =
                                    spec.registry().getAccountInfo(OWNER).getAccountID();
                            final var receiver =
                                    spec.registry().getAccountInfo(RECIPIENT).getAccountID();
                            final var idOfToken = "0.0."
                                    + (spec.registry()
                                            .getTokenID(NON_FUNGIBLE_TOKEN)
                                            .getTokenNum());
                            var txnRecord = getTxnRecord(TRANSFER_FROM_ACCOUNT_TXN)
                                    .hasPriority(recordWith()
                                            .contractCallResult(resultWith()
                                                    .logs(inOrder(logWith()
                                                            .contract(idOfToken)
                                                            .withTopicsInOrder(List.of(
                                                                    eventSignatureOf(TRANSFER_SIGNATURE),
                                                                    parsedToByteString(sender.getAccountNum()),
                                                                    parsedToByteString(receiver.getAccountNum()),
                                                                    parsedToByteString(1L)))))))
                                    .andAllChildRecords()
                                    .logged();
                            allRunFor(spec, txnRecord);
                        }));
    }

    private HapiSpec erc721TransferFromWithApproveForAll() {
        return defaultHapiSpec("ERC_721_TRANSFER_FROM_WITH_APPROVAL_FOR_ALL")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(SPENDER),
                        cryptoCreate(RECIPIENT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(ERC_721_CONTRACT),
                        contractCreate(ERC_721_CONTRACT),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(SPENDER, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(RECIPIENT, NON_FUNGIBLE_TOKEN),
                        tokenAssociate(ERC_721_CONTRACT, NON_FUNGIBLE_TOKEN),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META, SECOND_META)),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L).between(TOKEN_TREASURY, OWNER)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, ERC_721_CONTRACT, true, List.of(1L, 2L))
                                .via(BASE_APPROVE_TXN)
                                .logged()
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasSpenderID(ERC_721_CONTRACT),
                        getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasSpenderID(ERC_721_CONTRACT),
                        getAccountDetails(OWNER).logged(),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().nftApprovedForAllAllowancesCount(1)),
                        contractCall(
                                        ERC_721_CONTRACT,
                                        TRANSFER_FROM,
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(OWNER))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(RECIPIENT))),
                                        BigInteger.ONE)
                                .via(TRANSFER_FROM_ACCOUNT_TXN)
                                .hasKnownStatus(SUCCESS),
                        getAccountDetails(RECIPIENT).logged(),
                        getAccountDetails(OWNER).logged())))
                .then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
