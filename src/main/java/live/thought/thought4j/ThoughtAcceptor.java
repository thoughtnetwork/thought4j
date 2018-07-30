/*
 * thought4j Java RPC Client library for the Thought Network
 * 
 * Copyright (c) 2018, Thought Network LLC
 * 
 * Based on code from the Bitcoin-JSON-RPC-Client
 * Copyright (c) 2013, Mikhail Yevchenko.
 * 
 * License
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the 
 * Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject
 * to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
 * THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package live.thought.thought4j;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ThoughtAcceptor implements Runnable
{

  private static final Logger                         logger    = Logger.getLogger(ThoughtAcceptor.class.getCanonicalName());

  public final ThoughtClientInterface                 thought;
  private String                                      lastBlock, monitorBlock = null;
  int                                                 monitorDepth;
  private final LinkedHashSet<ThoughtPaymentListener> listeners = new LinkedHashSet<ThoughtPaymentListener>();

  public ThoughtAcceptor(ThoughtClientInterface thought, String lastBlock, int monitorDepth)
  {
    this.thought = thought;
    this.lastBlock = lastBlock;
    this.monitorDepth = monitorDepth;
  }

  public ThoughtAcceptor(ThoughtClientInterface thought)
  {
    this(thought, null, 6);
  }

  public ThoughtAcceptor(ThoughtClientInterface thought, String lastBlock, int monitorDepth, ThoughtPaymentListener listener)
  {
    this(thought, lastBlock, monitorDepth);
    listeners.add(listener);
  }

  public ThoughtAcceptor(ThoughtClientInterface thought, ThoughtPaymentListener listener)
  {
    this(thought, null, 12);
    listeners.add(listener);
  }

  public String getAccountAddress(String account) throws GenericRpcException
  {
    List<String> a = thought.getAddressesByAccount(account);
    if (a.isEmpty())
      return thought.getNewAddress(account);
    return a.get(0);
  }

  public synchronized String getLastBlock()
  {
    return lastBlock;
  }

  public synchronized void setLastBlock(String lastBlock) throws GenericRpcException
  {
    if (this.lastBlock != null)
      throw new IllegalStateException("lastBlock already set");
    this.lastBlock = lastBlock;
    updateMonitorBlock();
  }

  public synchronized ThoughtPaymentListener[] getListeners()
  {
    return listeners.toArray(new ThoughtPaymentListener[0]);
  }

  public synchronized void addListener(ThoughtPaymentListener listener)
  {
    listeners.add(listener);
  }

  public synchronized void removeListener(ThoughtPaymentListener listener)
  {
    listeners.remove(listener);
  }

  private HashSet<String> seen = new HashSet<String>();

  private void updateMonitorBlock() throws GenericRpcException
  {
    monitorBlock = lastBlock;
    for (int i = 0; i < monitorDepth && monitorBlock != null; i++)
    {
      ThoughtClientInterface.Block b = thought.getBlock(monitorBlock);
      monitorBlock = b == null ? null : b.previousHash();
    }
  }

  public synchronized void checkPayments() throws GenericRpcException
  {
    ThoughtClientInterface.TransactionsSinceBlock t = monitorBlock == null ? thought.listSinceBlock()
        : thought.listSinceBlock(monitorBlock);
    for (ThoughtClientInterface.Transaction transaction : t.transactions())
    {
      if ("receive".equals(transaction.category()))
      {
        if (!seen.add(transaction.txId()))
          continue;
        for (ThoughtPaymentListener listener : listeners)
        {
          try
          {
            listener.transaction(transaction);
          }
          catch (Exception ex)
          {
            logger.log(Level.SEVERE, null, ex);
          }
        }
      }
    }
    if (!t.lastBlock().equals(lastBlock))
    {
      seen.clear();
      lastBlock = t.lastBlock();
      updateMonitorBlock();
      for (ThoughtPaymentListener listener : listeners)
      {
        try
        {
          listener.block(lastBlock);
        }
        catch (Exception ex)
        {
          logger.log(Level.SEVERE, null, ex);
        }
      }
    }
  }

  private boolean stop = false;

  public void stopAccepting()
  {
    stop = true;
  }

  private long checkInterval = 5000;

  /**
   * Get the value of checkInterval
   *
   * @return the value of checkInterval
   */
  public long getCheckInterval()
  {
    return checkInterval;
  }

  /**
   * Set the value of checkInterval
   *
   * @param checkInterval
   *          new value of checkInterval
   */
  public void setCheckInterval(long checkInterval)
  {
    this.checkInterval = checkInterval;
  }

  @Override
  public void run()
  {
    stop = false;
    long nextCheck = 0;
    while (!(Thread.interrupted() || stop))
    {
      if (nextCheck <= System.currentTimeMillis())
        try
        {
          nextCheck = System.currentTimeMillis() + checkInterval;
          checkPayments();
        }
        catch (GenericRpcException ex)
        {
          Logger.getLogger(ThoughtAcceptor.class.getName()).log(Level.SEVERE, null, ex);
        }
      else
        try
        {
          Thread.sleep(Math.max(nextCheck - System.currentTimeMillis(), 100));
        }
        catch (InterruptedException ex)
        {
          Logger.getLogger(ThoughtAcceptor.class.getName()).log(Level.WARNING, null, ex);
        }
    }
  }

  // public static void main(String[] args) {
  // //System.out.println(System.getProperties().toString().replace(", ", ",\n"));
  // final BitcoindRpcClient thought = new BitcoinJSONRPCClient(true);
  // new BitcoinAcceptor(thought, null, 6, new BitcoinPaymentListener() {
  //
  // public void block(String blockHash) {
  // try {
  // System.out.println("new block: " + blockHash + "; date: " +
  // thought.getBlock(blockHash).time());
  // } catch (BitcoinRpcException ex) {
  // logger.log(Level.SEVERE, null, ex);
  // }
  // }
  //
  // public void transaction(Transaction transaction) {
  // System.out.println("tx: " + transaction.confirmations() + "\t" +
  // transaction.amount() + "\t=> " + transaction.account());
  // }
  // }).run();
  // }

}
