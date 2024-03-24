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

package com.hedera.node.app.service.mono.statedumpers.scheduledtransactions;

import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.NavigableMap;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;

@SuppressWarnings("java:S6218") // "Equals/hashcode methods should be overridden in records containing array fields"
record BBMScheduledSecondValue(long number, NavigableMap<RichInstant, ImmutableLongList> ids) {

    static BBMScheduledSecondValue fromMono(@NonNull final ScheduleSecondVirtualValue scheduleVirtualValue) {
        return new BBMScheduledSecondValue(scheduleVirtualValue.getKey().getKeyAsLong(), scheduleVirtualValue.getIds());
    }
}
