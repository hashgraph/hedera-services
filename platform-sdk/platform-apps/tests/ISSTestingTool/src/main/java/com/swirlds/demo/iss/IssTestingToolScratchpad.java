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

package com.swirlds.demo.iss;

import com.swirlds.platform.scratchpad.ScratchpadType;

/**
 * Types of data stored in the scratch pad by the ISS Testing Tool.
 */
public enum IssTestingToolScratchpad implements ScratchpadType {

    /** Data about ISSs that were provoked */
    PROVOKED_ISS;

    @Override
    public int getFieldId() {
        return 1;
    }
}
