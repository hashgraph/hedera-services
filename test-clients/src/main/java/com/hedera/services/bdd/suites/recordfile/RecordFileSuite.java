package com.hedera.services.bdd.suites.recordfile;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.verifyRecordFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class RecordFileSuite extends HapiApiSuite {

    private static final Logger log = LogManager.getLogger(RecordFileSuite.class);

    private static final String TOKEN_TREASURY = "TokenTreasury";
    private static final String TOKEN = "Token";
    private static final String ALICE = "Alice";

    public static void main(String... args) {
        new RecordFileSuite().runSuiteSync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                new HapiApiSpec[]{
                        recordFileCheck()
                }
        );
    }

    private HapiApiSpec recordFileCheck() {
        final String firstTxn = "firstTxn";
        final String secondTxn = "secondTxn";
        final String thirdTxn = "thirdTxn";

        return defaultHapiSpec("recordFileCheck")
                .given(
                ).when(
                        cryptoCreate(TOKEN_TREASURY)
                                .via(firstTxn),

                        tokenCreate(TOKEN)
                                .initialSupply(50L)
                                .treasury(TOKEN_TREASURY)
                                .via(secondTxn),

                        cryptoCreate(ALICE)
                                .delayBy(6000)
                                .via(thirdTxn)

                ).then(
                        withOpContext((spec, opLog) -> {
                            final var txnRecord = getTxnRecord(firstTxn);
                            final var txnRecord2 = getTxnRecord(secondTxn);
                            final var txnRecord3 = getTxnRecord(thirdTxn);
                            allRunFor(spec, txnRecord, txnRecord2, txnRecord3);

                            final var timestamp = txnRecord.getResponseRecord().getConsensusTimestamp();
                            verifyRecordFile(timestamp, txnRecord.getResponseRecord(), txnRecord2.getResponseRecord())
                                    .execFor(spec);
                        })
                );
    }


    @Override
    protected Logger getResultsLogger() {
        return log;
    }

}
