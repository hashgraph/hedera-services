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
package com.hedera.services.stats;

import com.hedera.services.utils.NonAtomicReference;
import com.swirlds.common.metrics.DoubleGauge;
import java.util.function.DoubleSupplier;

public record UtilGauge(
        DoubleSupplier valueSource,
        DoubleGauge.Config config,
        NonAtomicReference<DoubleGauge> gauge) {}
