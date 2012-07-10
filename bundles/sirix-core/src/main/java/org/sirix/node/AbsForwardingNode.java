package org.sirix.node;

import com.google.common.collect.ForwardingObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.sirix.node.delegates.NodeDelegate;
import org.sirix.node.interfaces.INode;

/**
 * Skeletal implementation of {@link INode} interface.
 * 
 * @author Johannes Lichtenberger, University of Konstanz
 * 
 */
public abstract class AbsForwardingNode extends ForwardingObject implements INode {

  /** Constructor for use by subclasses. */
  protected AbsForwardingNode() {
  }

  @Override
  protected abstract NodeDelegate delegate();
  
  /**
   * Get a snapshot of the node delegate.
   * 
   * @return new {@link NodeDelegate} instance (snapshot of the current one)
   */
  @Nonnull
  public NodeDelegate getNodeDelegate() {
    return new NodeDelegate(delegate());
  }

  @Override
  public int getTypeKey() {
    return delegate().getTypeKey();
  }

  @Override
  public void setTypeKey(final int pTypeKey) {
    delegate().setTypeKey(pTypeKey);
  }

  @Override
  public boolean hasParent() {
    return delegate().hasParent();
  }

  @Override
  public long getNodeKey() {
    return delegate().getNodeKey();
  }

  @Override
  public long getParentKey() {
    return delegate().getParentKey();
  }

  @Override
  public void setParentKey(final long pParentKey) {
    delegate().setParentKey(pParentKey);
  }

  @Override
  public long getHash() {
    return delegate().getHash();
  }

  @Override
  public void setHash(final long pHash) {
    delegate().setHash(pHash);
  }
  
  @Override
  public void setPCR(final long pPCR) {
    delegate().setPCR(pPCR);
  }
  
  @Override
  public long getPCR() {
    return delegate().getPCR();
  }

  @Override
  public String toString() {
    return delegate().toString();
  }
  
  @Override
  public boolean isSameItem(@Nullable final INode pOther) {
    return delegate().isSameItem(pOther);
  }
}