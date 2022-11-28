/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.state.impl;

abstract class MutableStateBaseTest<V> extends StateBaseTest<V> {
    protected abstract MutableStateBase<Long, V> state();

    //   - If the key is unknown in the tree, do X
    //   - If the key is known in the tree, do X
    //   - If the key has already been "getForModify", do X
    //   - If the key has already been "put", do X
    //   - If the key has already been "remove"d, do X

    // getForModify
    // Should:
    //   - Return something that I can modify directly. The modified value must
    //     NOT impact the underlying merkle tree, until I do a commit.
    //   - List the key as part of the "modifiedKeys"
    //   - If the key has already been removed, return null
    //   - If the key doesn't exist, return null
    //   - If the key has been "put", then no-op

    // put
    // Should:
    //   - If the key matches something already in the tree, replace it
    //   - If the key is new, insert it
    //   - Not modify the tree at all until commit
    //   - List the key as part of "modifiedKeys"
    //   - If the key has already been "getForModify", then override for put

    // remove
    // Should:
    //   - If the key matches something

    // modifiedKeys
    // readKeys -- should it be impacted by getForModify, put, and remove??

    // reset

    // commit
    // Should:
    //   - Push changes (remove, put, getForModify) to the underlying tree
    //   - Clear the set of "modifiedKeys" (but not necessarily the cache...?)
}
