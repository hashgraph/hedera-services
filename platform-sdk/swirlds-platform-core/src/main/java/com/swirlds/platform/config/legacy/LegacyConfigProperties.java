/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.config.legacy;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Optional;

/**
 * Bean for all parameters that can be part of the config.txt file
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future once the
 * 		config.txt has been migrated to the regular config API. If you need to use this class please try to do as less
 * 		static access as possible.
 */
@Deprecated(forRemoval = true)
public class LegacyConfigProperties {

    private String swirldName = null;

    private JarAppConfig appConfig = null;

    private AddressBook addressBook = null;

    /**
     * Set the address book.
     *
     * @param addressBook the address book
     */
    public void setAddressBook(@NonNull final AddressBook addressBook) {
        Objects.requireNonNull(addressBook, "addressBook");
        this.addressBook = addressBook.copy();
    }

    /**
     * Get the address book. If no address book is set, an empty address book is returned.
     *
     * @return the address book
     */
    @NonNull
    public AddressBook getAddressBook() {
        if (addressBook == null) {
            return new AddressBook();
        }
        return addressBook.copy();
    }

    public void setAppConfig(final JarAppConfig appConfig) {
        this.appConfig = CommonUtils.throwArgNull(appConfig, "appConfig");
    }

    public void setSwirldName(final String swirldName) {
        this.swirldName = CommonUtils.throwArgNull(swirldName, "swirldName");
    }

    public Optional<String> swirldName() {
        return Optional.ofNullable(swirldName);
    }

    public Optional<JarAppConfig> appConfig() {
        return Optional.ofNullable(appConfig);
    }
}
