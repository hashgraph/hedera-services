/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.getDeduction;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.readableTransferList;
import static java.util.stream.Collectors.toSet;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.TinyBarTransfers;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;

public class TransferListAsserts extends BaseErroringAssertsProvider<TransferList> {
    public static TransferListAsserts exactParticipants(Function<HapiSpec, List<AccountID>> provider) {
        return new ExactParticipantsAssert(provider);
    }

    // non-standard initialization
    @SuppressWarnings("java:S3599")
    public static TransferListAsserts noCreditAboveNumber(ToLongFunction<HapiSpec> provider) {
        return new TransferListAsserts() {
            {
                registerProvider((spec, o) -> {
                    TransferList actual = (TransferList) o;
                    long maxAllowed = provider.applyAsLong(spec);
                    Assertions.assertTrue(
                            actual.getAccountAmountsList().stream()
                                    .filter(aa -> aa.getAmount() > 0)
                                    .allMatch(aa -> aa.getAccountID().getAccountNum() <= maxAllowed),
                            "Transfers include a credit above account 0.0." + maxAllowed);
                });
            }
        };
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static TransferListAsserts including(Function<HapiSpec, TransferList>... providers) {
        return new ExplicitTransferAsserts(Arrays.asList(providers));
    }

    public static TransferListAsserts includingDeduction(LongSupplier from, long amount) {
        return new DeductionAsserts(from, amount);
    }

    public static TransferListAsserts includingDeduction(String from, long amount) {
        return new SpecificDeductionAsserts(from, amount);
    }

    public static TransferListAsserts includingDeduction(String desc, String payer) {
        return new QualifyingDeductionAssert(desc, payer);
    }

    public static TransferListAsserts atLeastOneTransfer() {
        return new NonEmptyTransferAsserts();
    }

    public static TransferListAsserts missingPayments(Function<HapiSpec, Map.Entry<AccountID, Long>>... providers) {
        return new MissingPaymentAsserts(providers);
    }

    public static Function<HapiSpec, Map.Entry<AccountID, Long>> to(String account, Long amount) {
        return spec -> new AbstractMap.SimpleEntry<>(spec.registry().getAccountID(account), amount);
    }

    public static Function<HapiSpec, Map.Entry<AccountID, Long>> from(String account, Long amount) {
        return spec -> new AbstractMap.SimpleEntry<>(spec.registry().getAccountID(account), -1 * amount);
    }

    protected void assertInclusion(TransferList of, TransferList in) {
        if (!new TinyBarTransfers(of).test(in)) {
            Assertions.assertEquals(TxnUtils.printable(of), TxnUtils.printable(in), "Transfers missing from list!");
        }
    }
}

class MissingPaymentAsserts extends TransferListAsserts {
    public MissingPaymentAsserts(Function<HapiSpec, Map.Entry<AccountID, Long>>... providers) {
        registerProvider((spec, o) -> {
            TransferList actual = (TransferList) o;
            Set<String> missing = Stream.of(providers)
                    .map(provider -> asSig(provider.apply(spec)))
                    .collect(toSet());
            Set<String> nonAbsent = new HashSet<>();
            actual.getAccountAmountsList().stream().forEach(entry -> {
                String sig = asSig(new AbstractMap.SimpleEntry<>(entry.getAccountID(), entry.getAmount()));
                if (missing.contains(sig)) {
                    nonAbsent.add(sig);
                }
            });
            Assertions.assertTrue(nonAbsent.isEmpty(), "Payments not absent from list! " + nonAbsent);
        });
    }

    private String asSig(Map.Entry<AccountID, Long> entry) {
        return String.format(
                "%d.%d.%d|%d",
                entry.getKey().getShardNum(),
                entry.getKey().getRealmNum(),
                entry.getKey().getAccountNum(),
                entry.getValue());
    }
}

class ExactParticipantsAssert extends TransferListAsserts {
    public ExactParticipantsAssert(Function<HapiSpec, List<AccountID>> provider) {
        registerProvider((spec, o) -> {
            List<AccountID> expectedParticipants = provider.apply(spec);
            TransferList actual = (TransferList) o;
            Assertions.assertEquals(
                    expectedParticipants.size(), actual.getAccountAmountsCount(), "Wrong number of participants!");
            for (int i = 0, n = expectedParticipants.size(); i < n; i++) {
                Assertions.assertEquals(
                        expectedParticipants.get(i), actual.getAccountAmounts(i).getAccountID());
            }
        });
    }
}

class ExplicitTransferAsserts extends TransferListAsserts {
    public ExplicitTransferAsserts(List<Function<HapiSpec, TransferList>> providers) {
        providers.stream()
                .forEach(provider -> registerProvider((spec, o) -> {
                    TransferList expected = provider.apply(spec);
                    assertInclusion(expected, (TransferList) o);
                }));
    }
}

class QualifyingDeductionAssert extends TransferListAsserts {
    public QualifyingDeductionAssert(String desc, String payer) {
        registerProvider((spec, o) -> {
            var transfers = (TransferList) o;
            var hasQualifying = getDeduction(transfers, asId(payer, spec)).isPresent();
            if (!hasQualifying) {
                Assertions.fail("No qualifying " + desc + " from " + payer + " in " + readableTransferList(transfers));
            }
        });
    }
}

class NonEmptyTransferAsserts extends TransferListAsserts {
    public NonEmptyTransferAsserts() {
        registerProvider((spec, o) -> {
            TransferList transfers = (TransferList) o;
            Assertions.assertTrue(!transfers.getAccountAmountsList().isEmpty(), "Transfer list cannot be empty!");
        });
    }
}

class DeductionAsserts extends TransferListAsserts {
    public DeductionAsserts(LongSupplier from, long amount) {
        registerProvider((spec, o) -> {
            TransferList transfers = (TransferList) o;
            long num = from.getAsLong();
            Assertions.assertTrue(
                    transfers.getAccountAmountsList().stream()
                            .anyMatch(aa -> aa.getAmount() == -amount
                                    && aa.getAccountID().getAccountNum() == num),
                    String.format("No deduction of -%d tinyBars from 0.0.%d detected!", amount, num));
        });
    }
}

class SpecificDeductionAsserts extends TransferListAsserts {
    public SpecificDeductionAsserts(String account, long amount) {
        registerProvider((spec, o) -> {
            TransferList transfers = (TransferList) o;
            AccountID payer = asId(account, spec);
            Assertions.assertTrue(
                    transfers.getAccountAmountsList().stream()
                            .anyMatch(aa -> aa.getAmount() == -amount
                                    && aa.getAccountID().equals(payer)),
                    String.format("No deduction of -%d tinyBars from %s detected!", amount, account));
        });
    }
}
