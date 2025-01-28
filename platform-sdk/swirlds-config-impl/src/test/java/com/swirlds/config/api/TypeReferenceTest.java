package com.swirlds.config.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypeReferenceTest {

    @Test
    void testSimpleParameterizedType() {
        TypeReference<List<String>> typeReference = new TypeReference<>() {};

        assertEquals(List.class, typeReference.getRawClass(), "Raw class should be List");
        assertEquals(String.class, typeReference.getArgumentTypes()[0], "Type should be String");
    }

    @Test
    void testNestedParameterizedType() {
        final TypeReference<List<List<String>>> typeReference = new TypeReference<>() {};
        final TypeReference<List<String>> innerTypeReference = new TypeReference<>() {};

        assertEquals(List.class, typeReference.getRawClass(), "Raw class should be List");
        assertEquals(innerTypeReference.getType(), typeReference.getArgumentTypes()[0], "Type should be List");
    }

    @Test
    void testMultipleParameterizedType() {
        TypeReference<Map<Locale, Integer>> typeReference = new TypeReference<>() {};

        assertEquals(Map.class, typeReference.getRawClass(), "Raw class should be Map");
        assertEquals(Locale.class, typeReference.getArgumentTypes()[0], "Type should be Locale");
        assertEquals(Integer.class, typeReference.getArgumentTypes()[1], "Type should be Integer");
    }

    @Test
    void testSimpleType() {
        TypeReference<Integer> typeReference = new TypeReference<>() {};

        assertEquals(Integer.class, typeReference.getRawClass(), "Raw class should be Integer");
        assertEquals(0, typeReference.getArgumentTypes().length, "Argument types should be empty");
    }
}