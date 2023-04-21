/*
 * (c) 2016-2021 Swirlds, Inc.
 *
 * This software is the confidential and proprietary information of
 * Swirlds, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Swirlds.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

/**
 * A disk-based VirtualDataSource implementation; complete module documentation to be assembled over time as the full
 * implementation is transplanted here.
 */
open module com.swirlds.jasperdb {
    exports com.swirlds.jasperdb.collections;
    exports com.swirlds.jasperdb.utilities;
    exports com.swirlds.jasperdb.files;
    exports com.swirlds.jasperdb.files.hashmap;
    exports com.swirlds.jasperdb;
    exports com.swirlds.jasperdb.settings;
    exports com.swirlds.jasperdb.config;
    exports com.swirlds.merkledb;
    exports com.swirlds.merkledb.collections;
    exports com.swirlds.merkledb.config;
    exports com.swirlds.merkledb.files;
    exports com.swirlds.merkledb.files.hashmap;
    exports com.swirlds.merkledb.serialize;
    exports com.swirlds.merkledb.settings;
    exports com.swirlds.merkledb.utilities;

    requires com.swirlds.base;
    requires com.swirlds.common;
    requires com.swirlds.logging;
    requires com.swirlds.virtualmap;
    requires org.apache.commons.lang3;
    requires org.eclipse.collections.impl;
    requires org.eclipse.collections.api;
    requires org.apache.logging.log4j;
    requires java.management;
    requires jdk.management;
    requires jdk.unsupported;
    requires com.swirlds.config;
    requires static com.github.spotbugs.annotations;
}
