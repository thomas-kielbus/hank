package com.rapleaf.hank.zookeeper;

import org.apache.log4j.Logger;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;

public abstract class WatchedNode<T> {
  private static final Logger LOG = Logger.getLogger(WatchedNode.class);
  private T value;
  private final String nodePath;
  private final ZooKeeperPlus zk;

  private final Watcher watcher = new Watcher() {
    @Override
    public void process(WatchedEvent event) {
      // this lock is important so that when changes start happening, we
      // won't run into any concurrency issues
      synchronized (WatchedNode.this) {
        try {
          update();
        } catch (KeeperException e) {
          LOG.error("Exception while trying to update our cached value for " + nodePath + "!", e);
        } catch (InterruptedException e) {
          LOG.trace("Interrupted while trying to update our cached value for " + nodePath, e);
        }
      }
    }
  };

  protected WatchedNode(ZooKeeperPlus zk, String nodePath)
      throws KeeperException, InterruptedException {
    this(zk, nodePath, false, null);
  }

  public WatchedNode(ZooKeeperPlus zk, String nodePath, boolean create, T initValue)
      throws KeeperException, InterruptedException {
    this.zk = zk;
    this.nodePath = nodePath;
    if (create) {
      if (zk.exists(nodePath, false) == null) {
        zk.create(nodePath, encode(initValue), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
      }
    }
    update();
  }

  private void update() throws KeeperException, InterruptedException {
    value = decode(zk.getData(nodePath, watcher, new Stat()));
  }

  protected abstract T decode(byte[] data);

  public T get() {
    return value;
  }

  public void set(T v) throws KeeperException, InterruptedException {
    zk.setData(nodePath, encode(v), -1);
    synchronized (this) {
      value = v;
    }
  }

  protected abstract byte[] encode(T v);
}