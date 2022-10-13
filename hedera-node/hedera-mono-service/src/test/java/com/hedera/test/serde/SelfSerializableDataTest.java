/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.test.serde;

import static com.hedera.test.serde.SerializedForms.assertSameSerialization;
import static com.hedera.test.utils.SerdeUtils.deserializeFromBytes;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.virtual.annotations.StateSetter;
import com.hedera.test.utils.ClassLoaderHelper;
import com.hedera.test.utils.SeededPropertySource;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDet;
import com.swirlds.common.io.Versioned;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

/**
 * Implementation support for a JUnit5 test that validates a {@link SelfSerializable} type is still
 * able to deserialize itself from serialized forms from versions between {@link
 * SerializableDet#getMinimumSupportedVersion()} to {@link Versioned#getVersion()}.
 *
 * <p>A typical subclass (c.f., {@link
 * com.hedera.services.state.merkle.MerkleAccountStateSerdeTest}) will use a {@link
 * com.hedera.test.utils.SeededPropertySource} to create a collection of expected objects for each
 * supported version; likely via the {@code SelfSerializable}'s "many argument" constructor. It will
 * then keep hard-coded serialized forms that were created with each supported version, so this test
 * runner can verify that calling {@link SelfSerializable#deserialize(SerializableDataInputStream,
 * int)} on the serialized form with the parent version, <i>using the current code</i> still returns
 * the expected object.
 *
 * <p><i>Note:</i> The test subclass must be {@code public} so this runner can instantiate an
 * instance reflectively. (The JUnit5 {@link ExtensionContext} does not yet contain a test instance
 * at the point it instantiates the {@link ArgumentsProvider} referenced by an {@link
 * ArgumentsSource} annotation.)
 *
 * @param <T> the SelfSerializable type being tested
 */
public abstract class SelfSerializableDataTest<T extends SelfSerializable> {
    public static final int MIN_TEST_CASES_PER_VERSION = 5;

    /**
     * A hook for the test class to register any {@link
     * com.swirlds.common.constructable.RuntimeConstructable} types that it needs for its serde.
     *
     * @throws ConstructableRegistryException
     */
    protected void registerConstructables() {
        // No-op. By default, all classes in the classpath will be registered.
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
     * Provides the runtime type information that this class needs to automate the {@link
     * ParameterizedTest} source.
     *
     * @return the runtime type of the SelfSerializable being tested
     */
    protected abstract Class<T> getType();

    /**
     * Returns how many test cases are available for the given version.
     *
     * @param version the version whose deserialization is being validated
     * @return how many test cases are available
     * @throws IllegalStateException if too few test cases are available
     */
    protected int getNumTestCasesFor(int version) {
        return MIN_TEST_CASES_PER_VERSION;
    }

    /**
     * Returns the serialized form created with a given version for a given test case.
     *
     * @param version the parent version that created the serialized form
     * @param testCaseNo the zero-indexed number of test case for this version
     * @return the serialized form
     */
    protected byte[] getSerializedForm(final int version, final int testCaseNo) {
        return SerializedForms.loadForm(getType(), version, testCaseNo);
    }

    /**
     * Returns the expected object created with a given version for a given test case.
     *
     * @param version the parent version that created the expected object
     * @param testCaseNo the zero-indexed number of test case for this version
     * @return the expected object
     */
    protected T getExpectedObject(final int version, final int testCaseNo) {
        return getExpectedObject(SeededPropertySource.forSerdeTest(version, testCaseNo));
    }

    /**
     * Returns the expected object created with a given seeded property source.
     *
     * @param propertySource the property source to use
     * @return the expected object
     */
    protected abstract T getExpectedObject(final SeededPropertySource propertySource);

    @BeforeEach
    void setUp() {
        registerConstructables();
    }

    @BeforeAll
    static void setUpClass() {
        ClassLoaderHelper.loadClassPathDependencies();
    }

    @ParameterizedTest
    @ArgumentsSource(SupportedVersionsArgumentsProvider.class)
    void deserializationWorksForAllSupportedVersions(final int version, final int testCaseNo) {
        final var serializedForm = getSerializedForm(version, testCaseNo);
        final var expectedObject = getExpectedObject(version, testCaseNo);

        final T actualObject =
                deserializeFromBytes(() -> instantiate(getType()), version, serializedForm);

        customAssertEquals()
                .ifPresentOrElse(
                        customAssert -> customAssert.accept(expectedObject, actualObject),
                        () -> assertEquals(expectedObject, actualObject));
    }

    @ParameterizedTest
    @ArgumentsSource(CurrentVersionArgumentsProvider.class)
    void serializationHasNoRegressionWithCurrentVersion(final int version, final int testCaseNo) {
        assertSameSerialization(getType(), this::getExpectedObject, version, testCaseNo);
    }

    @ParameterizedTest
    @ArgumentsSource(GettersAndSettersArgumentsProvider.class)
    void gettersAndSettersWork(
            final Object mutableSubject,
            @Nullable final Method getter,
            @Nullable final Method setter) {
        if (getter == null || setter == null) {
            return;
        }
        final var param = validatedSetterParam(setter);
        try {
            setter.invoke(mutableSubject, param);
            final var result = getter.invoke(mutableSubject);
            assertEquals(
                    param,
                    result,
                    "Set "
                            + param
                            + " via "
                            + setter.getName()
                            + " but got "
                            + result
                            + " via "
                            + getter.getName());
        } catch (IllegalAccessException | InvocationTargetException fatal) {
            throw new RuntimeException(fatal);
        }
    }

    protected Object validatedSetterParam(final Method setter) {
        final var paramTypes = setter.getParameterTypes();
        assertEquals(1, paramTypes.length, "A state setter should have one parameter");
        return typicalValueOf(paramTypes[0]);
    }

    static class SupportedVersionsArgumentsProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
            final var testType = context.getRequiredTestClass();
            final var ref =
                    (SelfSerializableDataTest<? extends SelfSerializable>) instantiate(testType);
            return allTestCasesFrom(ref).stream();
        }
    }

    protected static class CurrentVersionArgumentsProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
            final var testType = context.getRequiredTestClass();
            final var ref =
                    (SelfSerializableDataTest<? extends SelfSerializable>) instantiate(testType);
            return currentTestCasesFrom(ref).stream();
        }
    }

    static class GettersAndSettersArgumentsProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
            final var testType = context.getRequiredTestClass();
            final var ref =
                    (SelfSerializableDataTest<? extends SelfSerializable>) instantiate(testType);
            final var subjectType = ref.getType();
            return getterSetterTestCasesFor(subjectType);
        }
    }

    private static <T extends SelfSerializable> List<Arguments> allTestCasesFrom(
            final SelfSerializableDataTest<T> refTest) {
        return testCasesFrom(refTest, false);
    }

    private static <T extends SelfSerializable> List<Arguments> currentTestCasesFrom(
            final SelfSerializableDataTest<T> refTest) {
        return testCasesFrom(refTest, true);
    }

    private static Stream<Arguments> getterSetterTestCasesFor(final Class<?> virtualValueType) {
        final var mutableSubject = instantiate(virtualValueType);
        return Stream.concat(
                Arrays.stream(virtualValueType.getDeclaredMethods())
                        .filter(m -> m.getAnnotation(StateSetter.class) != null)
                        .map(m -> getterSetterArgs(mutableSubject, virtualValueType, m)),
                Stream.of(Arguments.of(mutableSubject, null, null)));
    }

    private static Arguments getterSetterArgs(
            final Object subject, final Class<?> virtualValueType, final Method setter) {
        var getterName = setter.getName().substring(3);
        if (getterName.startsWith("Is")) {
            getterName = "is" + getterName.substring(2);
        } else {
            getterName = "get" + getterName;
        }
        try {
            final var getter = virtualValueType.getMethod(getterName);
            return Arguments.of(subject, getter, setter);
        } catch (final NoSuchMethodException fatal) {
            throw new RuntimeException(fatal);
        }
    }

    private static <T extends SelfSerializable> List<Arguments> testCasesFrom(
            final SelfSerializableDataTest<T> refTest, final boolean onlyCurrent) {
        final var ref = instantiate(refTest.getType());
        final List<Arguments> argumentsList = new ArrayList<>();
        final var version = ref.getVersion();
        final var minVersion = onlyCurrent ? version : ref.getMinimumSupportedVersion();
        for (int i = minVersion; i <= version; i++) {
            final var testCasesForVersion = refTest.getNumTestCasesFor(i);
            if (testCasesForVersion < MIN_TEST_CASES_PER_VERSION) {
                throw new IllegalStateException(
                        "Only "
                                + testCasesForVersion
                                + " registered test cases for supported version "
                                + i
                                + "( at least "
                                + MIN_TEST_CASES_PER_VERSION
                                + " required)");
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
            throw new IllegalStateException(
                    "Could not instantiate " + type.getName() + " (is it a public class?)", e);
        }
    }

    private static <R> Constructor<R> noArgConstructorFor(final Class<R> type) {
        try {
            return type.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "No zero-args constructor available for " + type.getName(), e);
        }
    }

    private Object typicalValueOf(final Class<?> type) {
        if (byte.class.equals(type)) {
            return (byte) 32;
        } else if (int.class.equals(type)) {
            return 666;
        } else if (long.class.equals(type)) {
            return 666L;
        } else if (boolean.class.equals(type)) {
            return true;
        } else if (JKey.class.equals(type)) {
            return new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
        } else if (ByteString.class.equals(type)) {
            return ByteString.copyFromUtf8("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        } else if (Set.class.equals(type)) {
            return Collections.emptySet();
        } else if (Map.class.equals(type)) {
            return Collections.emptyMap();
        } else if (type.isArray()) {
            final var innerType = type.componentType();
            final var array = Array.newInstance(innerType, 1);
            final var tokenValue = typicalValueOf(innerType);
            Array.set(array, 0, tokenValue);
            return array;
        } else {
            return instantiate(type);
        }
    }
}
