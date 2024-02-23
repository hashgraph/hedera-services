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

package com.swirlds.platform.test.event.validation;

import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.validation.GossipEventValidator;
import edu.umd.cs.findbugs.annotations.NonNull;

public class TestGossipEventValidator implements GossipEventValidator {
    private final boolean validation;
    private final String name;

    public TestGossipEventValidator(final boolean validation, @NonNull final String name) {
        this.validation = validation;
        this.name = name;
    }

    @Override
    public boolean isEventValid(@NonNull GossipEvent event) {
        return validation;
    }

    @NonNull
    @Override
    public String validatorName() {
        return name;
    }
}
