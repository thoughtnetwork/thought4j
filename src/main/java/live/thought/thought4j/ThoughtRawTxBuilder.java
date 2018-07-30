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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import live.thought.thought4j.util.CoinUtil;

/**
 *
 * @author azazar
 */
public class ThoughtRawTxBuilder {

  public final ThoughtClientInterface thought;

  public ThoughtRawTxBuilder(ThoughtClientInterface thought) {
    this.thought = thought;
  }
  public Set<ThoughtClientInterface.TxInput> inputs = new LinkedHashSet<>();
  public List<ThoughtClientInterface.TxOutput> outputs = new ArrayList<>();

  private class Input extends ThoughtClientInterface.BasicTxInput {

    private static final long serialVersionUID = 1L;

    public Input(String txid, int vout) {
      super(txid, vout);
    }

    public Input(ThoughtClientInterface.TxInput copy) {
      this(copy.txid(), copy.vout());
    }

    @Override
    public int hashCode() {
      return txid.hashCode() + vout;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null)
        return false;
      if (!(obj instanceof ThoughtClientInterface.TxInput))
        return false;
      ThoughtClientInterface.TxInput other = (ThoughtClientInterface.TxInput) obj;
      return vout == other.vout() && txid.equals(other.txid());
    }

  }

  public ThoughtRawTxBuilder in(ThoughtClientInterface.TxInput in) {
    inputs.add(new Input(in.txid(), in.vout()));
    return this;
  }

  public ThoughtRawTxBuilder in(String txid, int vout) {
    in(new ThoughtClientInterface.BasicTxInput(txid, vout));
    return this;
  }

  public ThoughtRawTxBuilder out(String address, double amount) {
    if (amount <= 0d)
      return this;
    outputs.add(new ThoughtClientInterface.BasicTxOutput(address, amount));
    return this;
  }

  public ThoughtRawTxBuilder in(double value) throws GenericRpcException {
    return in(value, 6);
  }

  public ThoughtRawTxBuilder in(double value, int minConf) throws GenericRpcException {
    List<ThoughtClientInterface.Unspent> unspent = thought.listUnspent(minConf);
    double v = value;
    for (ThoughtClientInterface.Unspent o : unspent) {
      if (!inputs.contains(new Input(o))) {
        in(o);
        v = CoinUtil.normalizeAmount(v - o.amount());
      }
      if (v < 0)
        break;
    }
    if (v > 0)
      throw new GenericRpcException("Not enough bitcoins (" + v + "/" + value + ")");
    return this;
  }

  private HashMap<String, ThoughtClientInterface.RawTransaction> txCache = new HashMap<>();

  private ThoughtClientInterface.RawTransaction tx(String txId) throws GenericRpcException {
    ThoughtClientInterface.RawTransaction tx = txCache.get(txId);
    if (tx != null)
      return tx;
    tx = thought.getRawTransaction(txId);
    txCache.put(txId, tx);
    return tx;
  }

  public ThoughtRawTxBuilder outChange(String address) throws GenericRpcException {
    return outChange(address, 0d);
  }

  public ThoughtRawTxBuilder outChange(String address, double fee) throws GenericRpcException {
    double is = 0d;
    for (ThoughtClientInterface.TxInput i : inputs)
      is = CoinUtil.normalizeAmount(is + tx(i.txid()).vOut().get(i.vout()).value());
    double os = fee;
    for (ThoughtClientInterface.TxOutput o : outputs)
      os = CoinUtil.normalizeAmount(os + o.amount());
    if (os < is)
      out(address, CoinUtil.normalizeAmount(is - os));
    return this;
  }

  public String create() throws GenericRpcException {
    return thought.createRawTransaction(new ArrayList<>(inputs), outputs);
  }

  public String sign() throws GenericRpcException {
    return thought.signRawTransaction(create(), null, null);
  }

  public String send() throws GenericRpcException {
    return thought.sendRawTransaction(sign());
  }

}
