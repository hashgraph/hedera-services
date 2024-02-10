/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.testreader;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Uniquely identifies a test variant.
 *
 * @param panel the panel that the test belongs to
 * @param name  the name of the test
 */
public record JrsTestIdentifier(@NonNull String panel, @NonNull String name) implements Comparable<JrsTestIdentifier> {
    @Override
    public int compareTo(@NonNull final JrsTestIdentifier that) {
        if (this.panel.equals(that.panel)) {
            return this.name.compareTo(that.name);
        } else {
            return this.panel.compareTo(that.panel);
        }
    }
}
