package com.hedera.services.bdd.suites.contract;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.io.Files;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class ContractPerformanceSuite extends HapiApiSuite {
  private static final Logger LOG = LogManager.getLogger(ContractPerformanceSuite.class);

  private static final String PERF_RESOURCES = "src/main/resource/contract/performance/";

  public static void main(String... args) {
    /* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
    //noinspection InstantiationOfUtilityClass
    new org.ethereum.crypto.HashUtil();

    new ContractPerformanceSuite().runSuiteAsync();
  }

  @Override
  protected List<HapiApiSpec> getSpecsInSuite() {
    List<String> perfTests;
    try {
      perfTests =
          Files.readLines(
                  new File(PERF_RESOURCES + "performanceContracts.txt"), Charset.defaultCharset())
              .stream()
              .filter(s -> !s.isEmpty() && !s.startsWith("#"))
              .collect(Collectors.toList());
    } catch (IOException e) {
      return List.of();
    }
    List<HapiApiSpec> hapiSpecs = new ArrayList<>();
    for (String test : perfTests) {
      hapiSpecs.add(
          defaultHapiSpec("Perf_" + test)
              .given(
                  fileUpdate(APP_PROPERTIES).payingWith(ADDRESS_BOOK_CONTROL),
                  fileCreate("bytecode").path(PERF_RESOURCES + test),
                  contractCreate(test).bytecode("bytecode"))
              .when()
              .then(
                  withOpContext(
                      (spec, opLog) -> {
                        var contractCall = contractCall(test, "<empty>").gas(35000000);
                        allRunFor(spec, contractCall);
                        Assertions.assertEquals(
                            ResponseCodeEnum.SUCCESS, contractCall.getLastReceipt().getStatus());
                      })));
    }
    return hapiSpecs;
  }

  @Override
  protected Logger getResultsLogger() {
    return LOG;
  }
}
