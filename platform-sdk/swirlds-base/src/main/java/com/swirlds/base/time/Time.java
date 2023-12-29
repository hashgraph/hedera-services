/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.base.time;

import com.swirlds.base.time.internal.OSTime;
import java.time.InstantSource;

/**
 * @deprecated users of this class should migrate to {@link TimeSource} for measuring elapsed time or {@link InstantSource} for wall-clock.
 */
@Deprecated
public interface Time extends TimeSource, InstantSource {

    @Deprecated
    static Time system() {
        return OSTime.getInstance();
    }
}
