/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gui.model;

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extraction of several params from {@code GuiPlatformAccessor} to move it to new gui module.
 */
@Deprecated(forRemoval = true)
public class GuiModel {

    private static final GuiModel INSTANCE = new GuiModel();
    private final Map<NodeId, String> aboutStrings = new ConcurrentHashMap<>();
    private final Map<NodeId, String> platformNames = new ConcurrentHashMap<>();
    private final Map<NodeId, byte[]> swirldIds = new ConcurrentHashMap<>();
    private final Map<NodeId, Integer> instanceNumbers = new ConcurrentHashMap<>();

    private GuiModel() {}

    /**
     * The SwirldMain calls this to set the string that is shown when the user chooses "About" from the Swirlds menu in
     * the upper-right corner of the window. It is recommended that this be a short string that includes the name of the
     * app, the version number, and the year.
     *
     * @param nodeId the ID of the node
     * @param about  wha should show in the "about" window from the menu
     */
    public void setAbout(@NonNull final NodeId nodeId, @NonNull final String about) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(about, "about must not be null");
        aboutStrings.put(nodeId, about);
    }

    /**
     * Get the "about" string, or an empty string if none has been set.
     *
     * @param nodeId the ID of the node
     * @return an "about" string
     */
    public String getAbout(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        return aboutStrings.getOrDefault(nodeId, "");
    }

    /**
     * Set a platform name, given the node ID.
     *
     * @param nodeId       the ID of the node
     * @param platformName a platform name
     */
    public void setPlatformName(@NonNull final NodeId nodeId, @NonNull final String platformName) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(platformName, "platformName must not be null");
        platformNames.put(nodeId, platformName);
    }

    /**
     * Get a platform name, given the node ID, or an empty string if none has been set.
     *
     * @param nodeId the ID of the node
     * @return a platform name
     */
    @NonNull
    public String getPlatformName(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        return platformNames.getOrDefault(nodeId, "");
    }

    /**
     * Set the swirld ID for a node.
     *
     * @param nodeId   the ID of the node
     * @param swirldId the swirld ID
     */
    public void setSwirldId(@NonNull final NodeId nodeId, @NonNull final byte[] swirldId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(swirldId, "swirldId must not be null");
        swirldIds.put(nodeId, swirldId);
    }

    /**
     * Get the swirld ID for a node, or null if none is set.
     *
     * @param nodeId the ID of the node
     * @return the swirld ID
     */
    @Nullable
    public byte[] getSwirldId(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        return swirldIds.getOrDefault(nodeId, null);
    }

    /**
     * Set the instance number for a node.
     *
     * @param nodeId         the ID of the node
     * @param instanceNumber the instance number
     */
    public void setInstanceNumber(@NonNull final NodeId nodeId, final int instanceNumber) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        instanceNumbers.put(nodeId, instanceNumber);
    }

    /**
     * Get the instance number for a node, or -1 if none is set.
     *
     * @param nodeId the ID of the node
     * @return the instance number
     */
    public int getInstanceNumber(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        return instanceNumbers.getOrDefault(nodeId, -1);
    }

    public static GuiModel getInstance() {
        return INSTANCE;
    }
}
