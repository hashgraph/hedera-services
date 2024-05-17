/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.tss.groth21;

import com.swirlds.platform.tss.TssShareId;
import com.swirlds.platform.tss.pairings.Field;
import com.swirlds.platform.tss.pairings.FieldElement;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A share ID for the Groth21 TSS scheme.
 *
 * @param id the share ID
 */
public record Groth21ShareId<FE extends FieldElement<FE, F>, F extends Field<FE, F>>(@NonNull FieldElement<FE, F> id)
        implements TssShareId {}
