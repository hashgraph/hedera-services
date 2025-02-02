/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.lambda;

import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.lambda.LambdaInstaller.lambdaBytecode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(TOKEN)
public class HipExamplesTest {
    @HapiTest
    final Stream<DynamicTest> canUpdateExpiryOnlyOpWithoutAdminKey(
            @NonFungibleToken(numPreMints = 1) SpecNonFungibleToken cleverCoin) {
        return hapiTest(
                cryptoCreate("sphinx")
                        .maxAutomaticTokenAssociations(1)
                        .installing(
                                lambdaBytecode("OneTimeCodeTransferAllowance").atIndex(123)),
                cleverCoin.doWith(token -> cryptoTransfer(movingUnique(cleverCoin.name(), 1L)
                        .between(cleverCoin.treasury().name(), "sphinx"))));
    }
}
