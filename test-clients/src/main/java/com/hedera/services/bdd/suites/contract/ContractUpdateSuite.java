package com.hedera.services.bdd.suites.contract;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import java.time.Instant;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContractUpdateSuite extends HapiApiSuite {
  private static final Logger log = LogManager.getLogger(ContractUpdateSuite.class);

  private static final long defaultMaxLifetime =
      Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));

  public static void main(String... args) {
    new ContractUpdateSuite().runSuiteSync();
  }

  @Override
  protected List<HapiApiSpec> getSpecsInSuite() {
    return List.of(
        new HapiApiSpec[] {
          updateWithPendingNewKeySucceeds(),
          canSetImmutableWithEmptyKeyList(),
          updateWithBothMemoSettersWorks(),
          updateCannotSetUnreasonableLifetime(),
        });
  }

  private HapiApiSpec updateCannotSetUnreasonableLifetime() {
    final var smallBuffer = 12_345L;
    final var excessiveExpiry = defaultMaxLifetime + Instant.now().getEpochSecond() + smallBuffer;

    return defaultHapiSpec("UpdateCannotSetUnreasonableLifetime")
        .given(contractCreate("target"))
        .when()
        .then(
            contractUpdate("target")
                .newExpirySecs(excessiveExpiry)
                .hasKnownStatus(INVALID_EXPIRATION_TIME));
  }

  private HapiApiSpec updateWithBothMemoSettersWorks() {
    String firstMemo = "First";
    String secondMemo = "Second";
    String thirdMemo = "Third";
    return defaultHapiSpec("UpdateWithBothMemoSettersWorks")
        .given(
            newKeyNamed("newKey"),
            fileCreate("bytecode").path(ContractResources.BALANCE_LOOKUP_BYTECODE_PATH),
            contractCreate("target").entityMemo(firstMemo).bytecode("bytecode"))
        .when(
            contractUpdate("target").newMemo(secondMemo),
            contractUpdate("target")
                .newMemo(ZERO_BYTE_MEMO)
                .hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
            getContractInfo("target").has(contractWith().memo(secondMemo)))
        .then(
            contractUpdate("target").useDeprecatedMemoField().newMemo(thirdMemo),
            getContractInfo("target").has(contractWith().memo(thirdMemo)));
  }

  private HapiApiSpec updateWithPendingNewKeySucceeds() {
    return defaultHapiSpec("UpdateWithPendingNewKeySucceeds")
        .given(
            newKeyNamed("newKey"),
            fileCreate("bytecode").path(ContractResources.BALANCE_LOOKUP_BYTECODE_PATH),
            contractCreate("target").bytecode("bytecode"))
        .when(contractUpdate("target").newKey("newKey").deferStatusResolution())
        .then(
            contractUpdate("target")
                .via("txnRequiringSyncVerify")
                .signedBy(GENESIS, "newKey")
                .newMemo("So we outdanced thought..."));
  }

  private HapiApiSpec canSetImmutableWithEmptyKeyList() {
    return defaultHapiSpec("CanSetImmutableWithEmptyKeyList")
        .given(newKeyNamed("pristine"), contractCreate("toBeImmutable"))
        .when(
            contractUpdate("toBeImmutable")
                .improperlyEmptyingAdminKey()
                .hasKnownStatus(INVALID_ADMIN_KEY),
            contractUpdate("toBeImmutable").properlyEmptyingAdminKey())
        .then(
            contractUpdate("toBeImmutable")
                .newKey("pristine")
                .hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT));
  }

  @Override
  protected Logger getResultsLogger() {
    return log;
  }
}
