package com.hedera.test.serde;

import com.hedera.services.state.virtual.annotations.StateSetter;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.virtualmap.VirtualValue;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

import static com.hedera.test.serde.SerializedForms.assertSameBufferSerialization;
import static com.hedera.test.utils.SerdeUtils.deserializeFromBuffer;
import static org.junit.jupiter.api.Assertions.*;

public abstract class VirtualValueDataTest<T extends VirtualValue> extends SelfSerializableDataTest<T> {
    @ParameterizedTest
    @ArgumentsSource(CurrentVersionArgumentsProvider.class)
    void bufferSerializationHasNoRegressionWithCurrentVersion(final int version, final int testCaseNo) {
        assertSameBufferSerialization(getType(), this::getExpectedObject, version, testCaseNo);
    }

    @ParameterizedTest
    @ArgumentsSource(SupportedVersionsArgumentsProvider.class)
    void bufferDeserializationWorksForAllSupportedVersions(final int version, final int testCaseNo) {
        final var serializedForm = getSerializedForm(version, testCaseNo);
        final var expectedObject = getExpectedObject(version, testCaseNo);

        final T actualObject =
                deserializeFromBuffer(() -> instantiate(getType()), version, serializedForm);

        customAssertEquals()
                .ifPresentOrElse(
                        customAssert -> customAssert.accept(expectedObject, actualObject),
                        () -> assertEquals(expectedObject, actualObject));
    }

    @ParameterizedTest
    @ArgumentsSource(AsReadOnlyArgumentsProvider.class)
    void immutabilityContractsMetForVirtualValue(
            final VirtualValue readOnlySubject,
            final VirtualValue copiedSubject,
            final Method setter) {
        assertTrue(readOnlySubject.isImmutable());
        assertTrue(copiedSubject.isImmutable());

        final var param = validatedSetterParam(setter);
        final var e = assertThrows(InvocationTargetException.class,
                () -> setter.invoke(readOnlySubject, param),
                "A read-only subject must be immutable");
        assertInstanceOf(MutabilityException.class, e.getCause());
        final var f = assertThrows(InvocationTargetException.class,
                () -> setter.invoke(copiedSubject, param),
                "A copied subject must be immutable");
        assertInstanceOf(MutabilityException.class, f.getCause());
    }

    protected static class AsReadOnlyArgumentsProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
            final var testType = context.getRequiredTestClass();
            final var ref =
                    (VirtualValueDataTest<? extends VirtualValue>) instantiate(testType);
            final var subjectType = ref.getType();
            if (VirtualValue.class.isAssignableFrom(subjectType)) {
                return mutabilityTestCasesFor(subjectType.asSubclass(VirtualValue.class));
            } else {
                return Stream.empty();
            }
        }
    }

    private static <T extends VirtualValue> Stream<Arguments> mutabilityTestCasesFor(
            final Class<T> virtualValueType) {
        final var readOnlySubject = instantiate(virtualValueType).asReadOnly();
        final var copiedSubject = instantiate(virtualValueType);
        copiedSubject.copy();
        return Arrays.stream(virtualValueType.getDeclaredMethods())
                .filter(m -> m.getAnnotation(StateSetter.class) != null)
                .map(m -> Arguments.of(readOnlySubject, copiedSubject, m));
    }
}
