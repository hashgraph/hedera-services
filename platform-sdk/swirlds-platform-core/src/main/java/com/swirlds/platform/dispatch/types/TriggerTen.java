/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.dispatch.types;

import com.swirlds.platform.dispatch.Trigger;

/**
 * A trigger that accepts ten arguments.
 *
 * @param <A>
 * 		the type of the first argument
 * @param <B>
 * 		the type of the second argument
 * @param <C>
 * 		the type of the third argument
 * @param <D>
 * 		the type of the fourth argument
 * @param <E>
 * 		the type of the fifth argument
 * @param <F>
 * 		the type of the sixth argument
 * @param <G>
 * 		the type of the seventh argument
 * @param <H>
 * 		the type of the eighth argument
 * @param <I>
 * 		the type of the ninth argument
 * @param <J>
 * 		the type of the tenth argument
 */
@FunctionalInterface
public non-sealed interface TriggerTen<A, B, C, D, E, F, G, H, I, J>
        extends Trigger<TriggerTen<A, B, C, D, E, F, G, H, I, J>> {

    /**
     * Dispatch a trigger event.
     *
     * @param a
     * 		the first argument
     * @param b
     * 		the second argument
     * @param c
     * 		the third argument
     * @param d
     * 		the fourth argument
     * @param e
     * 		the fifth argument
     * @param f
     * 		the sixth argument
     * @param g
     * 		the seventh argument
     * @param h
     * 		the eighth argument
     * @param i
     * 		the ninth argument
     * @param j
     * 		the tenth argument
     */
    void dispatch(A a, B b, C c, D d, E e, F f, G g, H h, I i, J j);
}
