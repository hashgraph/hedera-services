package com.swirlds.common.merkle.proto;

import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.FieldType;

public final class MerkleNodeProtoFields {

    // All merkle nodes

    // message MerkleNode {
    //     Hash hash = 1;
    //     repeated MerkleNode child = 2;
    //     // custom node fields
    // }

    public static final int NUM_NODE_HASH = 1;
    public static final int NUM_NODE_CHILD = 2;
    // Min field number for node's own data fields
    public static final int MIN_NUM_NODE_OWNDATA = 10;

    public static final FieldDefinition FIELD_NODE_HASH = new FieldDefinition(
            "hash", FieldType.BYTES, false, false, false, NUM_NODE_HASH);

    public static final FieldDefinition FIELD_NODE_CHILD = new FieldDefinition(
            "child", FieldType.MESSAGE, true, true, false, NUM_NODE_CHILD);

    // Hash

    // message Hash {
    //     uint32 digestType = 1;
    //     bytes data = 2;
    // }

    public static final int NUM_HASH_DIGESTTYPE = 1;
    public static final int NUM_HASH_DATA = 2;

    public static final FieldDefinition FIELD_HASH_DIGESTTYPE = new FieldDefinition(
            "digestType", FieldType.UINT32, false, true, false, NUM_HASH_DIGESTTYPE);

    public static final FieldDefinition FIELD_HASH_DATA = new FieldDefinition(
            "data", FieldType.BYTES, false, false, false, NUM_HASH_DATA);

    // Virtual map state

    // message VirtualMapState {
    //     bytes label = 11;
    //     uint32 firstLeafPath = 12;
    //     unit32 lastLeafPath = 13;
    // }

    public static final int NUM_VMSTATE_LABEL = MIN_NUM_NODE_OWNDATA + 1;
    public static final int NUM_VMSTATE_FIRSTLEAFPATH = MIN_NUM_NODE_OWNDATA + 2;
    public static final int NUM_VMSTATE_LASTLEAFPATH = MIN_NUM_NODE_OWNDATA + 3;

    public static final FieldDefinition FIELD_VMSTATE_LABEL = new FieldDefinition(
            "label", FieldType.STRING, false, true, false, NUM_VMSTATE_LABEL);

    public static final FieldDefinition FIELD_VMSTATE_FIRSTLEAFPATH = new FieldDefinition(
            "firstLeafPath", FieldType.UINT32, false, true, false, NUM_VMSTATE_FIRSTLEAFPATH);

    public static final FieldDefinition FIELD_VMSTATE_LASTLEAFPATH = new FieldDefinition(
            "lastLeafPath", FieldType.UINT32, false, true, false, NUM_VMSTATE_LASTLEAFPATH);

    // Virtual root node

    // message VirtualRootNode {
    //     // No virtual root children fields
    //     VirtualCache cache = 11;
    // }

    public static final int NUM_VRNODE_CACHE = MIN_NUM_NODE_OWNDATA + 1;

    public static final FieldDefinition FIELD_VRNODE_CACHE = new FieldDefinition(
            "cache", FieldType.MESSAGE, false, true, false, NUM_VRNODE_CACHE);

    // Virtual node cache

    // message VirtualCache {
    //     uint64 fastCopyVersion = 1;
    //     repeated CacheKeyToLeafEntry keyToLeaf = 2;
    //     repeated CachePathToKeyEntry pathToKey = 3;
    //     repeated CachePathToHashEntry pathToHash = 4;
    // }

    public static final int NUM_VNODECACHE_COPYVERSION = 1;
    public static final int NUM_VNODECACHE_KEYTOLEAF = 2;
    public static final int NUM_VNODECACHE_PATHTOKEY = 3;
    public static final int NUM_VNODECACHE_PATHTOHASH = 4;

    public static final FieldDefinition FIELD_VNODECACHE_COPYVERSION = new FieldDefinition(
            "fastCopyVersion", FieldType.UINT64, false, true, false, NUM_VNODECACHE_COPYVERSION);

    public static final FieldDefinition FIELD_VNODECACHE_KEYTOLEAF = new FieldDefinition(
            "keyToLeaf", FieldType.MESSAGE, true, true, false, NUM_VNODECACHE_KEYTOLEAF);

    public static final FieldDefinition FIELD_VNODECACHE_PATHTOKEY = new FieldDefinition(
            "pathToKey", FieldType.MESSAGE, true, true, false, NUM_VNODECACHE_PATHTOKEY);

    public static final FieldDefinition FIELD_VNODECACHE_PATHTOHASH = new FieldDefinition(
            "pathToHash", FieldType.MESSAGE, true, true, false, NUM_VNODECACHE_PATHTOHASH);

    // message CacheKeyToLeafEntry {
    //     uint64 version = 1;
    //     VirtualLeafRecord record = 2;
    //     int32 deleted = 3;
    // }

    public static final int NUM_VNCKEYTOLEAF_VERSION = 1;
    public static final int NUM_VNCKEYTOLEAF_RECORD = 2;
    public static final int NUM_VNCKEYTOLEAF_DELETED = 3;

    public static final FieldDefinition FIELD_VNCKEYTOLEAF_VERSION = new FieldDefinition(
            "version", FieldType.UINT64, false, true, false, NUM_VNCKEYTOLEAF_VERSION);

    public static final FieldDefinition FIELD_VNCKEYTOLEAF_RECORD = new FieldDefinition(
            "record", FieldType.MESSAGE, false, false, false, NUM_VNCKEYTOLEAF_RECORD);

    public static final FieldDefinition FIELD_VNCKEYTOLEAF_DELETED = new FieldDefinition(
            "deleted", FieldType.UINT32, false, true, false, NUM_VNCKEYTOLEAF_DELETED);

    // message CachePathToKeyEntry {
    //     uint64 version = 1;
    //     uint64 path = 2;
    //     VirtualLeafRecord record = 3;
    //     int32 deleted = 4;
    // }

    public static final int NUM_VNCPATHTOKEY_VERSION = 1;
    public static final int NUM_VNCPATHTOKEY_PATH = 2;
    public static final int NUM_VNCPATHTOKEY_KEY = 3;
    public static final int NUM_VNCPATHTOKEY_DELETED = 4;

    public static final FieldDefinition FIELD_VNCPATHTOKEY_VERSION = new FieldDefinition(
            "version", FieldType.UINT64, false, true, false, NUM_VNCPATHTOKEY_VERSION);

    public static final FieldDefinition FIELD_VNCPATHTOKEY_PATH = new FieldDefinition(
            "path", FieldType.UINT64, false, true, false, NUM_VNCPATHTOKEY_PATH);

    public static final FieldDefinition FIELD_VNCPATHTOKEY_KEY = new FieldDefinition(
            "key", FieldType.MESSAGE, false, false, false, NUM_VNCPATHTOKEY_KEY);

    public static final FieldDefinition FIELD_VNCPATHTOKEY_DELETED = new FieldDefinition(
            "deleted", FieldType.UINT32, false, true, false, NUM_VNCPATHTOKEY_DELETED);

    // message CachePathToHashEntry {
    //     uint64 version = 1;
    //     uint64 path = 2;
    //     Hash hash = 3;
    //     int32 deleted = 4;
    // }

    public static final int NUM_VNCPATHTOHASH_VERSION = 1;
    public static final int NUM_VNCPATHTOHASH_PATH = 2;
    public static final int NUM_VNCPATHTOHASH_HASH = 3;
    public static final int NUM_VNCPATHTOHASH_DELETED = 4;

    public static final FieldDefinition FIELD_VNCPATHTOHASH_VERSION = new FieldDefinition(
            "version", FieldType.UINT64, false, true, false, NUM_VNCPATHTOHASH_VERSION);

    public static final FieldDefinition FIELD_VNCPATHTOHASH_PATH = new FieldDefinition(
            "path", FieldType.UINT64, false, true, false, NUM_VNCPATHTOHASH_PATH);

    public static final FieldDefinition FIELD_VNCPATHTOHASH_HASH = new FieldDefinition(
            "hash", FieldType.MESSAGE, false, false, false, NUM_VNCPATHTOHASH_HASH);

    public static final FieldDefinition FIELD_VNCPATHTOHASH_DELETED = new FieldDefinition(
            "deleted", FieldType.UINT32, false, true, false, NUM_VNCPATHTOHASH_DELETED);

    private MerkleNodeProtoFields() {}

}
