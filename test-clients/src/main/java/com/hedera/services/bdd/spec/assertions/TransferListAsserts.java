package com.hedera.services.bdd.spec.assertions;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.fees.TinyBarTransfers;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import org.junit.Assert;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.getDeduction;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.readableTransferList;
import static java.util.stream.Collectors.toSet;

public class TransferListAsserts extends BaseErroringAssertsProvider<TransferList> {
	public static TransferListAsserts including(Function<HapiApiSpec, TransferList>... providers) {
		return new ExplicitTransferAsserts(Arrays.asList(providers));
	}
	public static TransferListAsserts includingDeduction(LongSupplier from, long amount) {
		return new DeductionAsserts(from, amount);
	}
	public static TransferListAsserts includingDeduction(String desc, String payer) {
		return new QualifyingDeductionAssert(desc, payer);
	}
	public static TransferListAsserts atLeastOneTransfer() {
		return new NonEmptyTransferAsserts();
	}
	public static TransferListAsserts missingPayments(Function<HapiApiSpec, Map.Entry<AccountID, Long>>... providers) {
		return new MissingPaymentAsserts(providers);
	}
	public static Function<HapiApiSpec, Map.Entry<AccountID, Long>> to(String account, Long amount) {
		return spec -> new AbstractMap.SimpleEntry<>(spec.registry().getAccountID(account), amount);
	}
	public static Function<HapiApiSpec, Map.Entry<AccountID, Long>> from(String account, Long amount) {
		return spec -> new AbstractMap.SimpleEntry<>(spec.registry().getAccountID(account), -1 * amount);
	}

	protected void assertInclusion(TransferList of, TransferList in) {
		if (!new TinyBarTransfers(of).test(in)) {
			Assert.assertEquals("Transfers missing from list!",
					TxnUtils.printable(of),
					TxnUtils.printable(in));
		}
	}
}

class MissingPaymentAsserts extends TransferListAsserts {
	public MissingPaymentAsserts(Function<HapiApiSpec, Map.Entry<AccountID, Long>>... providers) {
		registerProvider((spec, o) -> {
			TransferList actual = (TransferList)o;
			Set<String> missing = Stream.of(providers).map(provider -> asSig(provider.apply(spec))).collect(toSet());
			Set<String> nonAbsent = new HashSet<>();
			actual.getAccountAmountsList().stream().forEach(entry ->  {
				String sig = asSig(new AbstractMap.SimpleEntry<>(entry.getAccountID(), entry.getAmount()));
				if (missing.contains(sig)) {
					nonAbsent.add(sig);
				}
			});
			Assert.assertTrue("Payments not absent from list! " + nonAbsent, nonAbsent.isEmpty());
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

class ExplicitTransferAsserts extends TransferListAsserts {
	public ExplicitTransferAsserts(List<Function<HapiApiSpec, TransferList>> providers) {
		providers.stream().forEach(provider -> {
			registerProvider((spec, o) -> {
				TransferList expected = provider.apply(spec);
				assertInclusion(expected, (TransferList)o);
			});
		});
	}
}

class QualifyingDeductionAssert extends TransferListAsserts {
	public QualifyingDeductionAssert(String desc, String payer) {
		registerProvider((spec, o) -> {
			var transfers = (TransferList)o;
			var hasQualifying = getDeduction(transfers, asId(payer, spec)).isPresent();
			if (!hasQualifying) {
				Assert.fail("No qualifying " + desc + " from " + payer + " in " + readableTransferList(transfers));
			}
		});
	}
}

class NonEmptyTransferAsserts extends TransferListAsserts {
	public NonEmptyTransferAsserts() {
		registerProvider((spec, o) -> {
			TransferList transfers = (TransferList)o;
			Assert.assertTrue("Transfer list cannot be empty!", !transfers.getAccountAmountsList().isEmpty());
		});
	}
}

class DeductionAsserts extends TransferListAsserts {
	public DeductionAsserts(LongSupplier from, long amount) {
		registerProvider((sepc, o) -> {
			TransferList transfers = (TransferList)o;
			long num = from.getAsLong();
			Assert.assertTrue(
					String.format("No deduction of -%d tinyBars from 0.0.%d detected!", amount, num),
					transfers.getAccountAmountsList()
							.stream()
							.anyMatch(aa -> aa.getAmount() == -amount && aa.getAccountID().getAccountNum() == num));
		});
	}
}
