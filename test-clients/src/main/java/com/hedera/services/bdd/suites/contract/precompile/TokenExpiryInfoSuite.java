package com.hedera.services.bdd.suites.contract.precompile;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.contracts.precompile.TokenKeyType;
import com.hedera.services.contracts.ParsingConstants.FunctionType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.precompile.WipeTokenAccountPrecompileSuite.GAS_TO_OFFER;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.expandByteArrayTo32Length;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TokenExpiryInfoSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(TokenExpiryInfoSuite.class);
    private static final String TOKEN_EXPIRY_CONTRACT = "TokenExpiryContract";
    private static final String AUTO_RENEW_ACCOUNT = "autoRenewAccount";
    private static final String UPDATED_AUTO_RENEW_ACCOUNT = "updatedAutoRenewAccount";
    private static final long DEFAULT_MAX_LIFETIME =
            Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));
    public static final long MONTH_IN_SECONDS = 2629800L;
    private static final String ADMIN_KEY = TokenKeyType.ADMIN_KEY.name();

    public static void main(String... args) {
        new TokenExpiryInfoSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(getExpiryInfoForToken(), updateExpiryInfoForToken());
    }

    private HapiApiSpec getExpiryInfoForToken() {

        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

        return defaultHapiSpec("GetExpiryInfoForToken")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        newKeyNamed(ADMIN_KEY),
                        uploadInitCode(TOKEN_EXPIRY_CONTRACT),
                        contractCreate(TOKEN_EXPIRY_CONTRACT).gas(1_000_000L),
                        tokenCreate(VANILLA_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .treasury(TOKEN_TREASURY)
                                .expiry(100)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(1000)
                                .initialSupply(500L)
                                .adminKey(ADMIN_KEY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_EXPIRY_CONTRACT,
                                                                "getExpiryInfoForToken",
                                                                Tuple.singleton(
                                                                        expandByteArrayTo32Length(
                                                                                asAddress(
                                                                                        vanillaTokenID
                                                                                                .get()))))
                                                        .via("expiryTxn")
                                                        .gas(GAS_TO_OFFER)
                                                        .payingWith(GENESIS))))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var getTokenInfoQuery = getTokenInfo(VANILLA_TOKEN);
                                    allRunFor(spec, getTokenInfoQuery);
                                    final var expirySecond =
                                            getTokenInfoQuery
                                                    .getResponse()
                                                    .getTokenGetInfo()
                                                    .getTokenInfo()
                                                    .getExpiry()
                                                    .getSeconds();
                                    allRunFor(
                                            spec,
                                            getTxnRecord("test").andAllChildRecords().logged(),
                                            childRecordsCheck(
                                                    "test",
                                                    SUCCESS,
                                                    recordWith()
                                                            .status(SUCCESS)
                                                            .contractCallResult(
                                                                    resultWith()
                                                                            .contractCallResult(
                                                                                    htsPrecompileResult()
                                                                                            .forFunction(
                                                                                                    FunctionType
                                                                                                            .HAPI_GET_TOKEN_EXPIRY_INFO)
                                                                                            .withStatus(
                                                                                                    SUCCESS)
                                                                                            .withExpiry(
                                                                                                    expirySecond,
                                                                                                    spec.registry()
                                                                                                            .getAccountID(
                                                                                                                    AUTO_RENEW_ACCOUNT),
                                                                                                    THREE_MONTHS_IN_SECONDS)))));
                                }));
    }

    @SuppressWarnings("java:S5960")
    private HapiApiSpec updateExpiryInfoForToken() {

        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> updatedAutoRenewAccountID = new AtomicReference<>();

        return defaultHapiSpec("updateExpiryInfoForToken")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        cryptoCreate(UPDATED_AUTO_RENEW_ACCOUNT)
                                .balance(0L)
                                .exposingCreatedIdTo(updatedAutoRenewAccountID::set),
                        newKeyNamed(ADMIN_KEY),
                        uploadInitCode(TOKEN_EXPIRY_CONTRACT),
                        contractCreate(TOKEN_EXPIRY_CONTRACT).gas(1_000_000L),
                        tokenCreate(VANILLA_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .treasury(TOKEN_TREASURY)
                                .expiry(100)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(1000)
                                .initialSupply(500L)
                                .adminKey(ADMIN_KEY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_EXPIRY_CONTRACT,
                                                                "updateExpiryInfoForToken",
                                                                asAddress(vanillaTokenID.get()),
                                                                DEFAULT_MAX_LIFETIME - 12_345L,
                                                                asAddress(
                                                                        updatedAutoRenewAccountID
                                                                                .get()),
                                                                MONTH_IN_SECONDS)
                                                        .alsoSigningWithFullPrefix(ADMIN_KEY)
                                                        .via("updateExpiryTxn")
                                                        .gas(GAS_TO_OFFER)
                                                        .payingWith(GENESIS))))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var getTokenInfoQuery = getTokenInfo(VANILLA_TOKEN);
                                    allRunFor(spec, getTokenInfoQuery);
                                    final var expirySecond =
                                            getTokenInfoQuery
                                                    .getResponse()
                                                    .getTokenGetInfo()
                                                    .getTokenInfo()
                                                    .getExpiry()
                                                    .getSeconds();
                                    final var autoRenewAccount =
                                            getTokenInfoQuery
                                                    .getResponse()
                                                    .getTokenGetInfo()
                                                    .getTokenInfo()
                                                    .getAutoRenewAccount();
                                    final var autoRenewPeriod =
                                            getTokenInfoQuery
                                                    .getResponse()
                                                    .getTokenGetInfo()
                                                    .getTokenInfo()
                                                    .getAutoRenewPeriod()
                                                    .getSeconds();
                                    assertEquals(expirySecond, DEFAULT_MAX_LIFETIME - 12_345L);
                                    assertEquals(
                                            autoRenewAccount,
                                            spec.registry()
                                                    .getAccountID(UPDATED_AUTO_RENEW_ACCOUNT));
                                    assertEquals(autoRenewPeriod, MONTH_IN_SECONDS);
                                }));
    }
}
