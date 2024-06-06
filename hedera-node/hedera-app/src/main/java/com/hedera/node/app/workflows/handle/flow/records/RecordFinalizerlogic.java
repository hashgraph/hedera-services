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

package com.hedera.node.app.workflows.handle.flow.records;

import com.hedera.node.app.service.token.records.ChildRecordFinalizer;
import com.hedera.node.app.service.token.records.ParentRecordFinalizer;
import com.hedera.node.app.workflows.handle.flow.dispatcher.Dispatch;
import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Finalizes the record based on the transaction category. The record finalization is delegated to the
 * parent or child record finalizer.
 */
@Singleton
public class RecordFinalizerlogic {
    private final ParentRecordFinalizer parentRecordFinalizer;
    private final ChildRecordFinalizer childRecordFinalizer;

    @Inject
    public RecordFinalizerlogic(
            final ParentRecordFinalizer parentRecordFinalizer, final ChildRecordFinalizer childRecordFinalizer) {
        this.parentRecordFinalizer = parentRecordFinalizer;
        this.childRecordFinalizer = childRecordFinalizer;
    }

    /**
     * Finalizes the record based on the transaction category. The record finalization is delegated to the
     * parent or child record finalizer. The parent record finalizer is used for user and scheduled transactions
     * and the child record finalizer is used for child and preceding transactions.
     * @param dispatch the dispatch
     */
    public void finalizeRecord(final Dispatch dispatch) {
        switch (dispatch.txnCategory()) {
            case USER, SCHEDULED -> parentRecordFinalizer.finalizeParentRecord(
                    dispatch.finalizeContext(),
                    dispatch.txnInfo().functionality(),
                    Collections.emptySet(),
                    Collections.emptyMap());
            case CHILD, PRECEDING -> childRecordFinalizer.finalizeChildRecord(
                    dispatch.finalizeContext(), dispatch.txnInfo().functionality());
        }
    }
}
