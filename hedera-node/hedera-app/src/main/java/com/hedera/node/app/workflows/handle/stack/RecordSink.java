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

package com.hedera.node.app.workflows.handle.stack;

import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RecordSink {
    protected final List<SingleTransactionRecordBuilder> precedingBuilders = new ArrayList<>();
    protected final List<SingleTransactionRecordBuilder> followingBuilders = new ArrayList<>();

    public void addPreceding(List<SingleTransactionRecordBuilder> builders) {
        precedingBuilders.addAll(builders);
    }

    public void addFollowing(List<SingleTransactionRecordBuilder> builders) {
        followingBuilders.addAll(builders);
    }

    public List<SingleTransactionRecordBuilder> allBuilders() {
        if (precedingBuilders.isEmpty()) {
            return followingBuilders;
        } else {
            final List<SingleTransactionRecordBuilder> allBuilders = new ArrayList<>(precedingBuilders);
            allBuilders.addAll(followingBuilders);
            return allBuilders;
        }
    }

    public boolean hasChildOrPreceding(SingleTransactionRecordBuilder baseBuilder) {
        for (var recordBuilder : precedingBuilders) {
            if (recordBuilder != baseBuilder) {
                return true;
            }
        }
        for (var recordBuilder : followingBuilders) {
            if (recordBuilder != baseBuilder) {
                return true;
            }
        }
        return false;
    }

    public <T> void forEachChildAndPreceding(
            @NonNull Class<T> recordBuilderClass,
            @NonNull Consumer<T> consumer,
            SingleTransactionRecordBuilder baseBuilder) {
        for (var recordBuilder : followingBuilders) {
            if (recordBuilder != baseBuilder) {
                consumer.accept(recordBuilderClass.cast(recordBuilder));
            }
        }
        for (var recordBuilder : precedingBuilders) {
            if (recordBuilder != baseBuilder) {
                consumer.accept(recordBuilderClass.cast(recordBuilder));
            }
        }
    }

    public int numBuilders() {
        return precedingBuilders.size() + followingBuilders.size();
    }
}
