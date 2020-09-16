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

import com.hedera.services.bdd.spec.HapiApiSpec;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BaseErroringAssertsProvider<T> implements ErroringAssertsProvider<T> {
	List<Function<HapiApiSpec, Function<T, Optional<Throwable>>>> testProviders = new ArrayList<>();

	protected void registerProvider(AssertUtils.ThrowingAssert throwing) {
		testProviders.add(spec -> instance -> {
			try {
				throwing.assertThrowable(spec, instance);
			} catch (Throwable t) {
				return Optional.of(t);
			}
			return Optional.empty();
		});
	}

	/* Helper for asserting something about a ContractID, FileID, AccountID, etc. */
	@SuppressWarnings("unchecked")
	protected <R> void registerIdLookupAssert(String key, Function<T, R> getActual, Class<R> cls, String err) {
		registerProvider((spec, o) -> {
			R expected = spec.registry().getId(key, cls);
			R actual = getActual.apply((T)o);
			Assert.assertEquals(err, expected, actual);
		});
	}

	@Override
	public ErroringAsserts<T> assertsFor(HapiApiSpec spec) {
		return new BaseErroringAsserts<>(testProviders.stream()
				.map(p -> p.apply(spec))
				.collect(Collectors.toList()));
	}
}
