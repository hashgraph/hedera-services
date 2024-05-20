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

package com.swirlds.platform.tss.pairings;

/**
 * A group interface for group 1 of a curve.
 *
 * @param <C>   the curve type
 * @param <FE>  the field element type
 * @param <GE1> the group 1 element type
 * @param <GE2> the group 2 element type
 */
public interface Group1<
                C extends Curve<C, FE, GE1, GE2>,
                FE extends FieldElement<C, FE, GE1, GE2>,
                GE1 extends Group1Element<C, FE, GE1, GE2>,
                GE2 extends Group2Element<C, FE, GE1, GE2>>
        extends Group<C, FE, GE1, GE1, GE2> {}
