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
package com.hedera.node.app.spi.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.common.system.BasicSoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SchemaTest {
    @Test
    @DisplayName("Schemas are sorted based on version number")
    void sorting() {
        final var schemaList = new ArrayList<Schema>();
        for (int i = 1; i < 10; i++) {
            final var schema = new TestSchema(i);
            schemaList.add(schema);
        }

        // First shuffle to put them in some random order, then sort them back again
        Collections.shuffle(schemaList);
        Collections.sort(schemaList);

        final var itr = schemaList.iterator();
        Schema prev = itr.next();
        while (itr.hasNext()) {
            final var schema = itr.next();
            assertThat(schema).isGreaterThan(prev);
            assertThat(schema.getVersion()).isGreaterThan(prev.getVersion());
            prev = schema;
        }
    }

    @Test
    @DisplayName("A schema is equal to itself")
    void selfEquals() {
        final var schema = new TestSchema(1);
        assertThat(schema).isEqualTo(schema);
    }

    @Test
    @DisplayName("Two schemas are equal if their versions are equal")
    void equals() {
        final var schema1 = new TestSchema(1);
        final var schema2 = new TestSchema(1);
        assertThat(schema1).isEqualTo(schema2);
    }

    @Test
    @DisplayName("A schema is not equal to null")
    void notEqualToNull() {
        final var schema = new TestSchema(1);
        assertThat(schema).isNotEqualTo(null);
    }

    @Test
    @DisplayName("Two schemas of different versions are not equal")
    void unequal() {
        final var schema1 = new TestSchema(1);
        final var schema2 = new TestSchema(2);
        assertThat(schema1).isNotEqualTo(schema2);
    }

    @Test
    @DisplayName("Two equal schemas produce the same hashcode")
    @Disabled("Disabled until BasicSoftwareVersion implements hashCode")
    void hashCodeConsistentWithEquals() {
        final var schema1 = new TestSchema(1);
        final var schema2 = new TestSchema(1);
        assertThat(schema1).hasSameHashCodeAs(schema2);
    }

    @Test
    @DisplayName("Two different schemas probably produce different hash codes")
    void differentHashCodesConsistentWithNotEquals() {
        final var schema1 = new TestSchema(1);
        final var schema2 = new TestSchema(2);
        assertThat(schema1.hashCode()).isNotEqualTo(schema2.hashCode());
    }

    private static final class TestSchema extends Schema {
        public TestSchema(int version) {
            super(new BasicSoftwareVersion(version));
        }

        @NonNull
        @Override
        @SuppressWarnings("rawtypes")
        public Set<StateDefinition> statesToCreate() {
            return Collections.emptySet();
        }

        @Override
        public void migrate(
                @NonNull ReadableStates previousStates, @NonNull WritableStates newStates) {}

        @NonNull
        @Override
        public Set<String> statesToRemove() {
            return Collections.emptySet();
        }

        @Override
        public String toString() {
            return "TestSchema{ " + super.getVersion() + "} ";
        }
    }
}
