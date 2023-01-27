/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi;

import com.hedera.hapi.node.base.SemanticVersion;
import java.util.Comparator;

/**
 * Provides an implementation of {@link Comparator} for {@link SemanticVersion}s.
 *
 * <p>NOTE: When PBJ automatically generates {@link SemanticVersion} as {@link Comparable} then we
 * can remove this class.
 */
public class SemanticVersionComparator {
    public static final Comparator<SemanticVersion> INSTANCE =
            Comparator.comparingInt(SemanticVersion::major)
                    .thenComparingInt(SemanticVersion::minor)
                    .thenComparingInt(SemanticVersion::patch);

    private SemanticVersionComparator() {}
}
