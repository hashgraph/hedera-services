/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.validation;

import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link GossipEventValidator} which combines multiple validators to provide a single output
 */
public class GossipEventValidators implements GossipEventValidator {
    private final List<GossipEventValidator> validators;

    public GossipEventValidators(@NonNull final List<GossipEventValidator> validators) {
        this.validators = new ArrayList<>(Objects.requireNonNull(validators));
    }

    /**
     * Replace an existing validator with a new one
     *
     * @param validatorName the name of the validator to replace
     * @param replacement   the new validator
     */
    public void replaceValidator(@NonNull String validatorName, @NonNull GossipEventValidator replacement) {
        Objects.requireNonNull(validatorName);
        Objects.requireNonNull(replacement);
        for (int i = 0; i < validators.size(); i++) {
            if (validators.get(i).validatorName().equals(validatorName)) {
                validators.set(i, replacement);
                return;
            }
        }
        throw new IllegalArgumentException("No validator with name " + validatorName + " found");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventValid(final GossipEvent event) {
        for (final GossipEventValidator validator : validators) {
            if (!validator.isEventValid(event)) {
                // if a single validation fails, the event is invalid
                return false;
            }
        }
        // if all checks pass, the event is valid
        return true;
    }
}
