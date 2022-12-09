package com.hedera.services.bdd.suites.leaky;

import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCustomCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadSingleInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.resetToDefault;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.ACCOUNT_INFO;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.ACCOUNT_INFO_AFTER_CALL;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CALL_TX;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CALL_TX_REC;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CONTRACTS_MAX_GAS_PER_SEC;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CONTRACT_FROM;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.DEFAULT_MAX_AUTO_RENEW_PERIOD;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.DEPOSIT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.SIMPLE_UPDATE_CONTRACT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.TRANSFERRING_CONTRACT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.TRANSFER_TO_CALLER;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCreateSuite.EMPTY_CONSTRUCTOR_CONTRACT;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.ADMIN_KEY;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.ANOTHER_SPENDER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.FUNGIBLE_TOKEN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.FUNGIBLE_TOKEN_MINT_TXN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.HEDERA_ALLOWANCES_MAX_TRANSACTION_LIMIT;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.NFT_TOKEN_MINT_TXN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.NON_FUNGIBLE_TOKEN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.OWNER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.SECOND_SPENDER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.SPENDER;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.THIRD_SPENDER;
import static com.hedera.services.bdd.suites.token.TokenPauseSpecs.DEFAULT_MIN_AUTO_RENEW_PERIOD;
import static com.hedera.services.bdd.suites.token.TokenPauseSpecs.LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION;
import static com.hedera.services.bdd.suites.token.TokenPauseSpecs.TokenIdOrderingAsserts.withOrderedTokenIds;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.SUPPLY_KEY;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.TRANSFER_TXN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.fee.FeeBuilder;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class LeakyCryptoTestsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(LeakyCryptoTestsSuite.class);

    private static final String FALSE = "false";

    public static void main(String... args) {
        new LeakyCryptoTestsSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                canDissociateFromMultipleExpiredTokens(),
                cannotExceedAccountAllowanceLimit(),
                cannotExceedAllowancesTransactionLimit()
        );
    }

    public HapiSpec canDissociateFromMultipleExpiredTokens() {
        final var civilian = "civilian";
        final long initialSupply = 100L;
        final long nonZeroXfer = 10L;
        final var dissociateTxn = "dissociateTxn";
        final var numTokens = 10;
        final IntFunction<String> tokenNameFn = i -> "fungible" + i;
        final String[] assocOrder = new String[numTokens];
        Arrays.setAll(assocOrder, tokenNameFn);
        final String[] dissocOrder = new String[numTokens];
        Arrays.setAll(dissocOrder, i -> tokenNameFn.apply(numTokens - 1 - i));

        return defaultHapiSpec("CanDissociateFromMultipleExpiredTokens")
                .given(
                        overriding(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION, "1"),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(civilian).balance(0L),
                        blockingOrder(
                                IntStream.range(0, numTokens)
                                        .mapToObj(
                                                i ->
                                                        tokenCreate(tokenNameFn.apply(i))
                                                                .autoRenewAccount(DEFAULT_PAYER)
                                                                .autoRenewPeriod(1L)
                                                                .initialSupply(initialSupply)
                                                                .treasury(TOKEN_TREASURY))
                                        .toArray(HapiSpecOperation[]::new)),
                        tokenAssociate(civilian, List.of(assocOrder)),
                        blockingOrder(
                                IntStream.range(0, numTokens)
                                        .mapToObj(
                                                i ->
                                                        cryptoTransfer(
                                                                moving(
                                                                        nonZeroXfer,
                                                                        tokenNameFn.apply(
                                                                                i))
                                                                        .between(
                                                                                TOKEN_TREASURY,
                                                                                civilian)))
                                        .toArray(HapiSpecOperation[]::new)))
                .when(sleepFor(1_000L), tokenDissociate(civilian, dissocOrder).via(dissociateTxn))
                .then(
                        getTxnRecord(dissociateTxn)
                                .hasPriority(
                                        recordWith()
                                                .tokenTransfers(withOrderedTokenIds(assocOrder))),
                        overriding(
                                LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION,
                                DEFAULT_MIN_AUTO_RENEW_PERIOD));
    }


    private HapiSpec cannotExceedAccountAllowanceLimit() {
        return defaultHapiSpec("CannotExceedAccountAllowanceLimit")
                .given(
                        overridingTwo(
                                HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT, "3",
                                HEDERA_ALLOWANCES_MAX_TRANSACTION_LIMIT, "5"),
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(OWNER)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(ANOTHER_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SECOND_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c")))
                                .via(NFT_TOKEN_MINT_TXN),
                        mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L)
                                        .between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .addCryptoAllowance(OWNER, SECOND_SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(
                                        OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .fee(ONE_HBAR),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(2)
                                                .tokenAllowancesCount(1)
                                                .nftApprovedForAllAllowancesCount(0)))
                .then(
                        cryptoCreate(THIRD_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, ANOTHER_SPENDER, 100L)
                                .fee(ONE_HBAR)
                                .hasKnownStatus(MAX_ALLOWANCES_EXCEEDED),
                        // reset
                        overridingTwo(
                                HEDERA_ALLOWANCES_MAX_TRANSACTION_LIMIT, "20",
                                HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT, "100"));
    }


    private HapiSpec cannotExceedAllowancesTransactionLimit() {
        return defaultHapiSpec("CannotExceedAllowancesTransactionLimit")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        overridingTwo(
                                HEDERA_ALLOWANCES_MAX_TRANSACTION_LIMIT, "4",
                                HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT, "5"),
                        cryptoCreate(OWNER)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(ANOTHER_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SECOND_SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(SUPPLY_KEY)
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c")))
                                .via(NFT_TOKEN_MINT_TXN),
                        mintToken(FUNGIBLE_TOKEN, 500L).via(FUNGIBLE_TOKEN_MINT_TXN),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L)
                                        .between(TOKEN_TREASURY, OWNER)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .addCryptoAllowance(OWNER, ANOTHER_SPENDER, 100L)
                                .addCryptoAllowance(OWNER, SECOND_SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addNftAllowance(
                                        OWNER, NON_FUNGIBLE_TOKEN, SPENDER, false, List.of(1L))
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addNftAllowance(
                                        OWNER,
                                        NON_FUNGIBLE_TOKEN,
                                        SPENDER,
                                        false,
                                        List.of(1L, 1L, 1L, 1L, 1L))
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .addCryptoAllowance(OWNER, SPENDER, 200L)
                                .addCryptoAllowance(OWNER, SPENDER, 100L)
                                .addCryptoAllowance(OWNER, SPENDER, 200L)
                                .addCryptoAllowance(OWNER, SPENDER, 200L)
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100L)
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED))
                .then(
                        // reset
                        overridingTwo(
                                HEDERA_ALLOWANCES_MAX_TRANSACTION_LIMIT, "20",
                                HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT, "100"));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
