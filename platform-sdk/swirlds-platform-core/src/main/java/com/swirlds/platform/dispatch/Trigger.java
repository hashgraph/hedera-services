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

package com.swirlds.platform.dispatch;

import com.swirlds.platform.dispatch.types.TriggerEight;
import com.swirlds.platform.dispatch.types.TriggerFive;
import com.swirlds.platform.dispatch.types.TriggerFour;
import com.swirlds.platform.dispatch.types.TriggerNine;
import com.swirlds.platform.dispatch.types.TriggerOne;
import com.swirlds.platform.dispatch.types.TriggerSeven;
import com.swirlds.platform.dispatch.types.TriggerSix;
import com.swirlds.platform.dispatch.types.TriggerTen;
import com.swirlds.platform.dispatch.types.TriggerThree;
import com.swirlds.platform.dispatch.types.TriggerTwo;
import com.swirlds.platform.dispatch.types.TriggerZero;

/**
 * The base interface for all dispatcher types.
 */
public sealed interface Trigger<T extends Trigger<T>>
        permits TriggerZero,
                TriggerOne,
                TriggerTwo,
                TriggerThree,
                TriggerFour,
                TriggerFive,
                TriggerSix,
                TriggerSeven,
                TriggerEight,
                TriggerNine,
                TriggerTen {}
