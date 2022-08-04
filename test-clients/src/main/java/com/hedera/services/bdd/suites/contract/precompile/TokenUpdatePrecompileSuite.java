package com.hedera.services.bdd.suites.contract.precompile;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

public class TokenUpdatePrecompileSuite extends HapiApiSuite {

    private static final Logger log = LogManager.getLogger(TokenUpdatePrecompileSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final long AUTO_RENEW_PERIOD = 8_000_000L;
    private static final String ACCOUNT = "account";
    private static final String TOKEN_UPDATE_CONTRACT = "UpdateTokenInfoContract";
    private static final String FIRST_UPDATE_TXN = "firstUpdateTxn";
    private static final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;
    private static final String ED25519KEY = "ed25519key";
    private static final String ECDSA_KEY = "ecdsa";

    public static void main(String... args) {
        new TokenUpdatePrecompileSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(updateTokenWithKeysHappyPath());
    }

    private HapiApiSpec updateTokenWithKeysHappyPath() {
        final var TOKEN_UPDATE_CONTRACT_AS_KEY = "tokenCreateContractAsKey";
        final var TOKEN_UPDATE_DELEGATE_KEY = "tokenCreateContractAsKeyDelegate";
        final var ACCOUNT_TO_ASSOCIATE = "account3";
        final var ACCOUNT_TO_ASSOCIATE_KEY = "associateKey";
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final var customName = "customName";
        final var customSymbol = "Ω";
        final var customMemo = "Omega";

        return defaultHapiSpec("fungibleTokenCreateHappyPath")
                .given(
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(MULTI_KEY),
                        cryptoCreate(ACCOUNT_TO_ASSOCIATE).key(ACCOUNT_TO_ASSOCIATE_KEY),
                        uploadInitCode(TOKEN_UPDATE_CONTRACT),
                        contractCreate(TOKEN_UPDATE_CONTRACT),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .feeScheduleKey(MULTI_KEY)
                                .pauseKey(MULTI_KEY)
                                .wipeKey(MULTI_KEY)
                                .freezeKey(MULTI_KEY)
                                .kycKey(MULTI_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        grantTokenKyc(VANILLA_TOKEN, ACCOUNT),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_UPDATE_CONTRACT,
                                                                "updateTokenWithKeysAndExpiry",
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        ACCOUNT)),
                                                                spec.registry()
                                                                        .getKey(ED25519KEY)
                                                                        .getEd25519()
                                                                        .toByteArray(),
                                                                spec.registry()
                                                                        .getKey(ECDSA_KEY)
                                                                        .getECDSASecp256K1()
                                                                        .toByteArray(),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getContractId(
                                                                                        TOKEN_UPDATE_CONTRACT)),
                                                                asAddress(
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        ACCOUNT)),
                                                                AUTO_RENEW_PERIOD,
                                                                customName,
                                                                customSymbol,
                                                                customMemo)
                                                        .via(FIRST_UPDATE_TXN)
                                                        .gas(GAS_TO_OFFER)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .payingWith(ACCOUNT),
                                                newKeyNamed(TOKEN_UPDATE_DELEGATE_KEY)
                                                        .shape(
                                                                DELEGATE_CONTRACT.signedWith(
                                                                        TOKEN_UPDATE_CONTRACT)),
                                                newKeyNamed(TOKEN_UPDATE_CONTRACT_AS_KEY)
                                                        .shape(
                                                                CONTRACT.signedWith(
                                                                        TOKEN_UPDATE_CONTRACT)))))
                .then(
                        sourcing(
                                () ->
                                        getTokenInfo(VANILLA_TOKEN)
                                                .logged()
                                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                                .hasSymbol(customSymbol)
                                                .hasName(customName)
                                                .hasEntityMemo(customMemo)
                                                .hasTreasury(ACCOUNT)
                                                .hasAutoRenewAccount(ACCOUNT)
                                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                                .hasSupplyType(TokenSupplyType.INFINITE)
                                                .searchKeysGlobally()
                                                .hasAdminKey(ED25519KEY)
                                                .hasPauseKey(MULTI_KEY)
                                                .hasKycKey(ED25519KEY)
                                                .hasFreezeKey(ECDSA_KEY)
                                                .hasWipeKey(ECDSA_KEY)
                                                .hasFeeScheduleKey(TOKEN_UPDATE_DELEGATE_KEY)
                                                .hasSupplyKey(TOKEN_UPDATE_CONTRACT_AS_KEY)
                                                .hasPauseKey(TOKEN_UPDATE_CONTRACT_AS_KEY)));
    }
}
