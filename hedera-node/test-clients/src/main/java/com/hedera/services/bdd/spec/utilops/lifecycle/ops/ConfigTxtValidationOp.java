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

package com.hedera.services.bdd.spec.utilops.lifecycle.ops;

import static com.hedera.services.bdd.junit.hedera.ExternalPath.UPGRADE_ARTIFACTS_DIR;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.CONFIG_TXT;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.loadAddressBook;

import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeSelector;
import com.hedera.services.bdd.spec.utilops.lifecycle.AbstractLifecycleOp;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;

public class ConfigTxtValidationOp extends AbstractLifecycleOp {
    private final Consumer<AddressBook> bookValidator;

    public ConfigTxtValidationOp(
            @NonNull final NodeSelector selector, @NonNull final Consumer<AddressBook> bookValidator) {
        super(selector);
        this.bookValidator = Objects.requireNonNull(bookValidator);
    }

    @Override
    protected void run(@NonNull final HederaNode node) {
        final var configTxtPath = node.getExternalPath(UPGRADE_ARTIFACTS_DIR).resolve(CONFIG_TXT);
        final var addressBook = loadAddressBook(configTxtPath);
        bookValidator.accept(addressBook);
    }
}
