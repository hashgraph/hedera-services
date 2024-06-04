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

package com.hedera.node.app.workflows.handle.flow.dispatcher;

import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.workflows.handle.HandleContextImpl;
import com.hedera.node.app.workflows.handle.flow.annotations.UserTransactionScope;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;
import static com.hedera.node.app.workflows.handle.HandleContextImpl.PrecedingTransactionCategory.LIMITED_CHILD_RECORDS;
import static java.util.Objects.requireNonNull;

@Singleton
public class ChildRecordBuilderFactory {
    @Inject
    public ChildRecordBuilderFactory() {
    }

    public SingleTransactionRecordBuilderImpl recordBuilderFor(final RecordListBuilder recordListBuilder,
                                                               final Configuration configuration,
                                                                HandleContext.TransactionCategory childCategory,
                                                                SingleTransactionRecordBuilderImpl.ReversingBehavior reversingBehavior,
                                                                @Nullable final ExternalizedRecordCustomizer customizer) {
       if(childCategory == PRECEDING) {
           switch (reversingBehavior) {
               case REMOVABLE:
                   return recordListBuilder.addRemovablePreceding(configuration);
               case REVERSIBLE:
                   return recordListBuilder.addReversiblePreceding(configuration);
               default:
                   return recordListBuilder.addPreceding(configuration, LIMITED_CHILD_RECORDS);
           }
       } else if(childCategory == CHILD){
              switch (reversingBehavior) {
                case REMOVABLE:
                     return recordListBuilder.addRemovableChildWithExternalizationCustomizer(configuration, requireNonNull(customizer));
                case REVERSIBLE:
                     return recordListBuilder.addChild(configuration, childCategory);
                default:
                     throw new IllegalArgumentException("Unsupported reversing behavior: " +
                             reversingBehavior + " for child category: " + childCategory);
              }
       } else {
           return recordListBuilder.addChild(configuration, childCategory);
       }
    }



}
