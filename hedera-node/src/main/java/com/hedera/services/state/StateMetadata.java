package com.hedera.services.state;

import com.hedera.services.context.ServicesContext;
import com.swirlds.common.Archivable;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.NodeId;
import com.swirlds.fchashmap.FCOneToManyRelation;

public class StateMetadata implements FastCopyable, Archivable {
	private final ServicesContext ctx;
	private final NodeId nodeId;
	private final FCOneToManyRelation<Integer, Long> uniqueTokenAssociations;
	private final FCOneToManyRelation<Integer, Long> uniqueOwnershipAssociations;
	private final FCOneToManyRelation<Integer, Long> uniqueTreasuryOwnershipAssociations;

	public StateMetadata(ServicesContext ctx, NodeId nodeId) {
		this.ctx = ctx;
		this.nodeId = nodeId;
		this.uniqueTokenAssociations = new FCOneToManyRelation<>();
		this.uniqueOwnershipAssociations = new FCOneToManyRelation<>();
		this.uniqueTreasuryOwnershipAssociations = new FCOneToManyRelation<>();
	}

	private StateMetadata(StateMetadata that) {
		this.uniqueTokenAssociations = that.uniqueTokenAssociations.copy();
		this.uniqueOwnershipAssociations = that.uniqueOwnershipAssociations.copy();
		this.uniqueTreasuryOwnershipAssociations = that.uniqueTreasuryOwnershipAssociations.copy();
		this.ctx = that.ctx;
		this.nodeId = that.nodeId;
	}

	@Override
	public void archive() {
		release();
	}

	@Override
	public StateMetadata copy() {
		return new StateMetadata(this);
	}

	@Override
	public void release() {
		uniqueTokenAssociations.release();
		uniqueOwnershipAssociations.release();
		uniqueTreasuryOwnershipAssociations.release();
	}

	public ServicesContext getCtx() {
		return ctx;
	}

	public NodeId getNodeId() {
		return nodeId;
	}

	public FCOneToManyRelation<Integer, Long> getUniqueTokenAssociations() {
		return uniqueTokenAssociations;
	}

	public FCOneToManyRelation<Integer, Long> getUniqueOwnershipAssociations() {
		return uniqueOwnershipAssociations;
	}

	public FCOneToManyRelation<Integer, Long> getUniqueTreasuryOwnershipAssociations() {
		return uniqueTreasuryOwnershipAssociations;
	}
}
