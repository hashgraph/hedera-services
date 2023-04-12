package com.hedera.node.app.workflows.dispatcher;

import com.hedera.node.app.service.consensus.impl.WritableTopicStore;

public interface WritableStoreFactory {
    WritableTopicStore createTopicStore();
}
