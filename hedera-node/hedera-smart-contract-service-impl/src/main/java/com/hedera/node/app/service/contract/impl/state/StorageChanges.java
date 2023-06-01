package com.hedera.node.app.service.contract.impl.state;

import java.util.List;

public record StorageChanges(long contractNum, List<StorageChange> changes) {
}
