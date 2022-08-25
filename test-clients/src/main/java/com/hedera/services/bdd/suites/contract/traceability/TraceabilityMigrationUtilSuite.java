package com.hedera.services.bdd.suites.contract.traceability;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getExecTime;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TraceabilityMigrationUtilSuite extends HapiApiSuite {
  private static final Logger log = LogManager.getLogger(TraceabilityMigrationUtilSuite.class);


  public static void main(String... args) {
    new TraceabilityMigrationUtilSuite().runSuiteSync();
  }

  @Override
  protected Logger getResultsLogger() {
    return log;
  }

  @Override
  public List<HapiApiSpec> getSpecsInSuite() {
    return List.of(create1000Contracts());
  }

  HapiApiSpec create1000Contracts() {
    final int NUM_CONTRACTS = 1000;
    final var traceabilityMigrationContract = "TraceabilityMigrationContract";
    return defaultHapiSpec("create1000Contracts")
        .given(
            uploadInitCode(traceabilityMigrationContract))
        .when()
        .then(
            UtilVerbs.inParallel(
                asOpArray(
                    NUM_CONTRACTS,
                    i ->
                        contractCreate(traceabilityMigrationContract, i)
                            .gas(4_000_000)
                            .advertisingCreation()
                ))
        );
  }

  HapiApiSpec triggerMigration() {
    final var trackMe = "trackMe";
    return defaultHapiSpec("triggerMigration")
        .given(
            cryptoCreate("migrateTraceability")
                .via(trackMe))
        .when()
        .then(
            getExecTime(trackMe).logged().assertingNoneLongerThan(1, ChronoUnit.SECONDS));
  }
}
