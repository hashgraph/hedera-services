package com.hedera.services.bdd.suites.file;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class R029xIssuesSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(R029xIssuesSuite.class);


    public static void main(String... args) {
        new R029xIssuesSuite().runSuiteSync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                new HapiApiSpec[] {
//                        excessiveMaxAutoAssociationsAreFullyCharged(),
//                        cannotSendValueToTokenAccount(),
                        cannotUseMoreThanChildContractLimit(),
                });
    }

    private HapiApiSpec excessiveMaxAutoAssociationsAreFullyCharged() {
        final var civilian = "civilian";
        final var other = "other";
        final var creation = "creation";
        final var tooMany = 500_000_000;
        return defaultHapiSpec("ExcessiveMaxAutoAssociationsAreFullyCharged")
                .given(
                        cryptoCreate(civilian).balance(100 * ONE_HUNDRED_HBARS)
                )
                .when(
                        cryptoCreate(other)
                                .payingWith(civilian)
                                .maxAutomaticTokenAssociations(tooMany).via(creation)
                )
                .then(
                        getTxnRecord(creation).hasPriority(recordWith().feeGreaterThan(10 * ONE_HUNDRED_HBARS))
                );
    }

    private HapiApiSpec cannotSendValueToTokenAccount() {
        final var multiKey = "multiKey";
        final var nonFungibleToken = "NFT";
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        return defaultHapiSpec("CannotSendValueToTokenAccount")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(nonFungibleToken)
                                .supplyType(TokenSupplyType.INFINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .supplyKey(multiKey)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                tokenMirrorAddr.set(
                                                        asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(
                                                                        idLit))))
                )
                .when( )
                .then(
                        sourcing((() -> contractCall(tokenMirrorAddr.get()).sending(1L)))
                );
    }

    private HapiApiSpec cannotUseMoreThanChildContractLimit() {
        final var numChildren = 51;
        final var fungible = "fungible";
        final var contract = "ManyChildren";
        final var manyBalances = "manyBalances";
        final AtomicReference<String> treasuryMirrorAddr = new AtomicReference<>();
        final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
        return defaultHapiSpec("CannotUseMoreThanChildContractLimit")
                .given(
                        cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(
                        id -> treasuryMirrorAddr.set(asHexedSolidityAddress(id))),
                               tokenCreate(fungible).treasury(TOKEN_TREASURY),
                        tokenCreate(fungible)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1234567)
                                .exposingCreatedIdTo(
                                        idLit ->
                                                tokenMirrorAddr.set(
                                                        asHexedSolidityAddress(
                                                                HapiPropertySource.asToken(
                                                                        idLit))))
                )
                .when(
        uploadInitCode(contract),
                contractCreate(contract),
                sourcing(() -> contractCall(contract,
                        "checkBalanceRepeatedly",
                        tokenMirrorAddr.get(), treasuryMirrorAddr.get(), numChildren)
                        .via(manyBalances))
                ).then(
                        getTxnRecord(manyBalances).andAllChildRecords().logged()
                );
    }



    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
