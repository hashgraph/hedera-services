package com.hedera.test.serde;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDet;
import com.swirlds.common.io.Versioned;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static com.hedera.test.utils.SerdeUtils.deserializeFromBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Implementation support for a JUnit5 test that validates a {@link SelfSerializable} type is still able to
 * deserialize itself from serialized forms from versions between {@link SerializableDet#getMinimumSupportedVersion()}
 * to {@link Versioned#getVersion()}.
 *
 * <p>
 * A typical subclass (c.f., {@link com.hedera.services.state.merkle.MerkleAccountStateSerdeTest}) will use
 * a {@link com.hedera.test.utils.SeededPropertySource} to create a collection of expected objects for each supported
 * version; likely via the {@code SelfSerializable}'s "many argument" constructor. It will then keep hard-coded
 * serialized forms that were created with each supported version, so this test runner can verify that calling
 * {@link SelfSerializable#deserialize(SerializableDataInputStream, int)} on the serialized form with the parent
 * version, <i>using the current code</i> still returns the expected object.
 *
 * <p>
 * <i>Note:</i> The test subclass must be {@code public} so this runner can instantiate an instance reflectively.
 * (The JUnit5 {@link ExtensionContext} does not yet contain a test instance at the point it instantiates the
 * {@link ArgumentsProvider} referenced by an {@link ArgumentsSource} annotation.)
 *
 * @param <T>
 * 		the SelfSerializable type being tested
 */
public abstract class SelfSerializableDataTest<T extends SelfSerializable> {
	public static final int MIN_TEST_CASES_PER_VERSION = 5;

	/**
	 * A hook for the test class to register any {@link com.swirlds.common.constructable.RuntimeConstructable}
	 * types that it needs for its serde.
	 *
	 * @throws ConstructableRegistryException
	 */
	protected void registerConstructables() throws ConstructableRegistryException {
		// No-op
	}

	/**
	 * If non-empty, the test for use for equality assertions instead of JUnit5.
	 *
	 * @return the equals override, if any
	 */
	protected Optional<BiConsumer<T, T>> customAssertEquals() {
		return Optional.empty();
	}

	/**
	 * Provides the runtime type information that this class needs to automate the {@link ParameterizedTest} source.
	 *
	 * @return the runtime type of the SelfSerializable being tested
	 */
	protected abstract Class<T> getType();

	/**
	 * Returns how many test cases are available for the given version.
	 *
	 * @param version
	 * 		the version whose deserialization is being validated
	 * @return how many test cases are available
	 * @throws IllegalStateException
	 * 		if too few test cases are available
	 */
	protected abstract int getNumTestCasesFor(int version);

	/**
	 * Returns the serialized form created with a given version for a given test case.
	 *
	 * @param version the parent version that created the serialized form
	 * @param testCaseNo the zero-indexed number of test case for this version
	 * @return the serialized form
	 */
	protected abstract byte[] getSerializedForm(final int version, final int testCaseNo);

	/**
	 * Returns the expected object created with a given version for a given test case.
	 *
	 * @param version the parent version that created the expected object
	 * @param testCaseNo the zero-indexed number of test case for this version
	 * @return the expected object
	 */
	protected abstract T getExpectedObject(final int version, final int testCaseNo);

	@BeforeEach
	void setUp() throws ConstructableRegistryException {
		registerConstructables();
	}

	@ParameterizedTest
	@ArgumentsSource(SupportedVersionsArgumentsProvider.class)
	void serdeWorksForAllTestCases(final int version, final int testCaseNo) {
		final var serializedForm = getSerializedForm(version, testCaseNo);
		final var expectedObject = getExpectedObject(version, testCaseNo);

		final T actualObject = deserializeFromBytes(() -> instantiate(getType()), version, serializedForm);

		customAssertEquals().ifPresentOrElse(
				customAssert -> customAssert.accept(expectedObject, actualObject),
				() -> assertEquals(expectedObject, actualObject));
	}

	static class SupportedVersionsArgumentsProvider implements ArgumentsProvider {
		@Override
		public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
			final var testType = context.getRequiredTestClass();
			final var ref = (SelfSerializableDataTest<? extends SelfSerializable>) instantiate(testType);
			return testCasesFrom(ref).stream();
		}
	}

	private static <T extends SelfSerializable> List<Arguments> testCasesFrom(final SelfSerializableDataTest<T> ref) {
		final var refType = instantiate(ref.getType());
		final List<Arguments> argumentsList = new ArrayList<>();
		final var minVersion = refType.getMinimumSupportedVersion();
		for (int i = minVersion, n = refType.getVersion(); i <= n; i++) {
			final var testCasesForVersion = ref.getNumTestCasesFor(i);
			if (testCasesForVersion < MIN_TEST_CASES_PER_VERSION) {
				throw new IllegalStateException("Only " + testCasesForVersion
						+ " registered test cases for supported version " + i
						+ "( at least " + MIN_TEST_CASES_PER_VERSION + " required)");
			}
			for (int j = 0; j < testCasesForVersion; j++) {
				argumentsList.add(Arguments.of(i, j));
			}
		}
		return argumentsList;
	}

	static <R> R instantiate(final Class<R> type) {
		try {
			final var cons = noArgConstructorFor(type);
			return cons.newInstance();
		} catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
			throw new IllegalStateException("Could not instantiate " + type.getName() + " (is it a public class?)", e);
		}
	}

	private static <R> Constructor<R> noArgConstructorFor(final Class<R> type) {
		try {
			return type.getConstructor();
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException("No zero-args constructor available for " + type.getName(), e);
		}
	}
}
