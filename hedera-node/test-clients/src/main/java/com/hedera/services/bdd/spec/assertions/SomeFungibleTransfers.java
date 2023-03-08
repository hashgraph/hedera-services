/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.assertions;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccountString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asTokenString;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;

public class SomeFungibleTransfers implements ErroringAssertsProvider<List<TokenTransferList>> {
    static final String FOR_TOKEN = "' for token '";
    private final Map<String, List<Pair<String, Long>>> changes = new HashMap<>();
    private boolean noOtherChangesTolerated = false;

    public static SomeFungibleTransfers changingFungibleBalances() {
        return new SomeFungibleTransfers();
    }

    public SomeFungibleTransfers including(String token, String account, long delta) {
        changes.computeIfAbsent(token, ignore -> new ArrayList<>()).add(Pair.of(account, delta));
        return this;
    }

    @Override
    public ErroringAsserts<List<TokenTransferList>> assertsFor(HapiSpec spec) {
        final List<Throwable> wrongFungibleChanges = new ArrayList<>();

        final Map<TokenID, String> tokenNameLookup = new HashMap<>();
        final Map<AccountID, String> accountNameLookup = new HashMap<>();
        final var registry = spec.registry();
        for (var entry : changes.entrySet()) {
            final var tokenName = entry.getKey();
            tokenNameLookup.put(registry.getTokenID(tokenName), tokenName);
            for (var change : entry.getValue()) {
                final var accountName = change.getLeft();
                accountNameLookup.put(registry.getAccountID(accountName), accountName);
            }
        }

        return tokenTransfers -> {
            for (var tokenTransfer : tokenTransfers) {
                final var token = tokenTransfer.getToken();
                if (!tokenNameLookup.containsKey(token)) {
                    if (noOtherChangesTolerated) {
                        try {
                            Assertions.fail("Unexpected changes for token "
                                    + asTokenString(token)
                                    + " --> "
                                    + tokenTransfer.getTransfersList());
                        } catch (Throwable t) {
                            wrongFungibleChanges.add(t);
                        }
                    }
                    continue;
                }
                final var fungibleChanges = tokenTransfer.getTransfersList();
                for (var fChange : fungibleChanges) {
                    final var account = fChange.getAccountID();
                    if (!accountNameLookup.containsKey(account)) {
                        if (noOtherChangesTolerated) {
                            try {
                                Assertions.fail("Unexpected change to balance of account "
                                        + asAccountString(account)
                                        + " for token "
                                        + asTokenString(token)
                                        + " --> "
                                        + fChange.getAmount());
                            } catch (Throwable t) {
                                wrongFungibleChanges.add(t);
                            }
                        }
                        continue;
                    }
                    final var tokenName = tokenNameLookup.get(token);
                    final var accountName = accountNameLookup.get(account);
                    final var actual = fChange.getAmount();
                    final var expected = expectedChange(tokenName, accountName);
                    try {
                        Assertions.assertEquals(
                                expected,
                                actual,
                                "Wrong change in account '" + accountName + FOR_TOKEN + tokenName + "'");
                    } catch (Throwable t) {
                        wrongFungibleChanges.add(t);
                    }
                    forgetExpectation(tokenName, accountName);
                }
            }

            for (var entry : changes.entrySet()) {
                final var token = entry.getKey();
                final var expectations = entry.getValue();
                try {
                    Assertions.assertTrue(
                            expectations.isEmpty(),
                            () -> "Expected changes for token '" + token + "', but got none of: " + expectations);
                } catch (Throwable t) {
                    wrongFungibleChanges.add(t);
                }
            }
            return wrongFungibleChanges;
        };
    }

    private long expectedChange(String token, String account) {
        final var expectations = changes.get(token);
        if (expectations == null) {
            throw new IllegalStateException(exceptionMessageNotExpectedChangeAccountForToken(account, token));
        }
        for (var change : expectations) {
            if (change.getKey().equals(account)) {
                return change.getValue();
            }
        }
        throw new IllegalStateException(exceptionMessageNotExpectedChangeAccountForToken(account, token));
    }

    private void forgetExpectation(String token, String account) {
        final var expectations = changes.get(token);
        if (expectations == null) {
            throw new IllegalStateException(exceptionMessageNotExpectedChangeAccountForToken(account, token));
        }
        for (var iter = expectations.iterator(); iter.hasNext(); ) {
            final var change = iter.next();
            if (change.getKey().equals(account)) {
                iter.remove();
                break;
            }
        }
    }

    private String exceptionMessageNotExpectedChangeAccountForToken(final String account, final String token) {
        return "No expected change in account '" + account + FOR_TOKEN + token + "'";
    }
}
