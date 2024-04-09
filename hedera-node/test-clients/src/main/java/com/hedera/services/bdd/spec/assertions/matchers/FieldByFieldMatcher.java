package com.hedera.services.bdd.spec.assertions.matchers;

import com.google.protobuf.GeneratedMessageV3;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.shaded.org.hamcrest.Description;
import org.testcontainers.shaded.org.hamcrest.Matcher;
import org.testcontainers.shaded.org.hamcrest.Matchers;
import org.testcontainers.shaded.org.hamcrest.TypeSafeMatcher;

public class FieldByFieldMatcher<T> extends TypeSafeMatcher<T> {
    private static final Logger log = LogManager.getLogger(FieldByFieldMatcher.class);

    private final T expected;
    private final Function<Object, Matcher<Object>> defaultMatcher;
    private final Map<String, Matcher<?>> customMatchers = new HashMap<>();
    private final Set<String> ignoredFields = new HashSet<>();

    private FieldByFieldMatcher(T expected) {
        this.expected = expected;
        this.defaultMatcher = Matchers::equalTo;
    }

    public static <T> FieldByFieldMatcher<T> withEqualFields(T expected) {
        return new FieldByFieldMatcher<>(expected);
    }

    public FieldByFieldMatcher<T> withCustomMatchersForFields(Map<String, Matcher<?>> customMatchers) {
        this.customMatchers.putAll(customMatchers);
        return this;
    }

    public FieldByFieldMatcher<T> ignoringFields(final String... fields) {
        ignoredFields.addAll(Arrays.asList(fields));
        return this;
    }

    @Override
    protected boolean matchesSafely(T actual) {
        return areEqual(expected, actual);
    }

    private boolean areEqual(Object expected, Object actual) {
        if (expected == actual) {
            return true;
        } else if (expected == null || actual == null) {
            return false;
        } else if (expected.getClass().isAssignableFrom(actual.getClass())) {
            try {
                final Map<String, Method> beanProperties = beanGetterProperties(expected);
                for (var beanProperty : beanProperties.entrySet()) {
                    if (!haveEqualFieldValue(expected, actual, beanProperty.getKey(), beanProperty.getValue())) {
                        return false;
                    }
                }
                return true;
            } catch (IntrospectionException e) {
                log.error(e.getMessage(), e);
                return false;
            }
        } else if (expected.getClass().isArray() && actual.getClass().isArray()) {
            return Arrays.equals((Object[]) expected, (Object[]) actual);
        } else {
            return defaultMatcher.apply(expected).matches(actual);
        }
    }

    private boolean haveEqualFieldValue(Object expected, Object actual, String fieldName, Method readMethod) {
        try {
            if (ignoredFields.contains(fieldName)) {
                return true;
            } else if (customMatchers.containsKey(fieldName)) {
                return customMatchers.get(fieldName).matches(readMethod.invoke(actual));
            } else {
                return defaultMatcher.apply(readMethod.invoke(expected)).matches(readMethod.invoke(actual));
            }
        } catch (Exception  e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    public static Map<String, Method> beanGetterProperties(Object bean) throws IntrospectionException {
        final var stopClass = GeneratedMessageV3.class;
        return Arrays.stream(Introspector.getBeanInfo(bean.getClass(), stopClass).getPropertyDescriptors())
                .filter(propertyDescriptor -> Objects.nonNull(propertyDescriptor.getReadMethod()))
                .collect(HashMap::new, (map, pd) -> map.put(pd.getName(), pd.getReadMethod()), HashMap::putAll);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(expected.toString());
    }
}