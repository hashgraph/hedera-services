/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.context.properties.EntityType;
import com.hedera.services.context.properties.Profile;
import com.hedera.services.context.properties.SupplierMapPropertySource;
import com.hedera.services.exceptions.UnparseablePropertyException;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SupplierMapPropertySourceTest {
    private final String INT_PROP = "a.int.prop";
    private final String LONG_PROP = "a.long.prop";
    private final String STRING_PROP = "a.string.prop";
    private final String DOUBLE_PROP = "a.double.prop";
    private final String CS_STRINGS_PROP = "a.cs_strings.prop";
    private final String PROFILE_PROP = "a.profile.prop";
    private final String BOOLEAN_PROP = "a.boolean.prop";
    private final String MISSING_PROP = "a.missing.prop";
    private final String BAD_ACCOUNT_PROP = "a.bad.account";
    private final String GOOD_ACCOUNT_PROP = "a.good.account";
    private final String ENTITY_TYPES_PROP = "some.types";
    private final SupplierMapPropertySource subject =
            new SupplierMapPropertySource(
                    Map.of(
                            INT_PROP, () -> 1,
                            LONG_PROP, () -> 1L,
                            DOUBLE_PROP, () -> 1.0d,
                            STRING_PROP, () -> "cellar door",
                            PROFILE_PROP, () -> Profile.DEV,
                            BOOLEAN_PROP, () -> Boolean.TRUE,
                            CS_STRINGS_PROP, () -> List.of("a", "b", "c"),
                            BAD_ACCOUNT_PROP, () -> "asdf",
                            GOOD_ACCOUNT_PROP, () -> "0.0.2",
                            ENTITY_TYPES_PROP, () -> EnumSet.of(EntityType.FILE)));

    @Test
    void getsEntityTypes() {
        assertEquals(EnumSet.of(EntityType.FILE), subject.getTypesProperty(ENTITY_TYPES_PROP));
    }

    @Test
    void testsForPresence() {
        // expect:
        assertTrue(subject.containsProperty(LONG_PROP));
        assertFalse(subject.containsProperty(MISSING_PROP));
    }

    @Test
    void getsParseableAccount() {
        // expect:
        assertEquals(
                AccountID.newBuilder().setAccountNum(2L).build(),
                subject.getAccountProperty(GOOD_ACCOUNT_PROP));
    }

    @Test
    void allPropertyNames() {
        assertNotNull(subject.allPropertyNames());
        var propSet =
                Set.of(
                        "a.double.prop",
                        "a.string.prop",
                        "a.profile.prop",
                        "a.boolean.prop",
                        "a.bad.account",
                        "a.long.prop",
                        "a.good.account",
                        "a.int.prop",
                        "some.types",
                        "a.cs_strings.prop");
        assertEquals(propSet, subject.allPropertyNames());
    }

    @Test
    void throwsOnUnparseableAccount() {
        // setup:
        UnparseablePropertyException e = null;

        // when:
        try {
            subject.getAccountProperty(BAD_ACCOUNT_PROP);
        } catch (UnparseablePropertyException upe) {
            e = upe;
        }

        // then:
        assertEquals(
                UnparseablePropertyException.messageFor(BAD_ACCOUNT_PROP, "asdf"), e.getMessage());
    }

    @Test
    void getsKnownProperty() {
        // expect:
        assertEquals(1L, subject.getProperty(LONG_PROP));
    }

    @Test
    void castsToExpectedType() {
        // expect:
        assertThrows(ClassCastException.class, () -> subject.getIntProperty(DOUBLE_PROP));
        assertDoesNotThrow(() -> subject.getDoubleProperty(DOUBLE_PROP));
        assertThrows(ClassCastException.class, () -> subject.getIntProperty(STRING_PROP));
        assertDoesNotThrow(() -> subject.getIntProperty(INT_PROP));
        assertThrows(ClassCastException.class, () -> subject.getLongProperty(STRING_PROP));
        assertDoesNotThrow(() -> subject.getLongProperty(LONG_PROP));
        assertThrows(ClassCastException.class, () -> subject.getStringProperty(LONG_PROP));
        assertDoesNotThrow(() -> subject.getStringProperty(STRING_PROP));
        assertThrows(ClassCastException.class, () -> subject.getBooleanProperty(STRING_PROP));
        assertDoesNotThrow(() -> subject.getBooleanProperty(BOOLEAN_PROP));
        assertThrows(ClassCastException.class, () -> subject.getProfileProperty(STRING_PROP));
        assertDoesNotThrow(() -> subject.getProfileProperty(PROFILE_PROP));
        assertThrows(ClassCastException.class, () -> subject.getStringsProperty(STRING_PROP));
        assertDoesNotThrow(() -> subject.getStringsProperty(CS_STRINGS_PROP));
    }
}
