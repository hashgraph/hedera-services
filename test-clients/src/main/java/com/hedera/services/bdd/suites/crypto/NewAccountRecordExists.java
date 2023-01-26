package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.SigControl.SECP256K1_ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.reduceFeeFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiSuite;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NewAccountRecordExists extends HapiSuite {
    private static final Logger log = LogManager.getLogger(NewAccountRecordExists.class);

    public static void main(String... args) {
        new NewAccountRecordExists().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(newAccountIsReflectedInRecordStream());
    }

    private HapiSpec newAccountIsReflectedInRecordStream() {
        final var balance = 1_234_567L;
        final var novelKey = "novelKey";
        final var memo = "It was the best of times";
        final AtomicReference<Instant> consensusTime = new AtomicReference<>();
        return defaultHapiSpec("NewAccountIsReflectedInRecordStream")
                .given(
                        newKeyNamed(novelKey).shape(SECP256K1_ON)
                ).when(
                        cryptoCreate("novel")
                                .key(novelKey)
                                .balance(balance)
                                .entityMemo(memo)
                                .exposingCreatedIdTo()
                ).then(
                        sleepFor(2000L),
                        // Ensure the record stream file with our CryptoCreate has been written to disk
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1))
                );
    }
    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
