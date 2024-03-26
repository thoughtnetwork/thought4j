/*
 * thought4j Java RPC Client library for the Thought Network
 * 
 * Copyright (c) 2018, Thought Network LLC
 * 
 * Based on code from the Bitcoin-JSON-RPC-Client
 * Repackaged with simple additions for easier maven usage by Alessandro Polverini
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import live.thought.thought4j.ThoughtClientInterface.AddressUtxo;
import live.thought.thought4j.ThoughtClientInterface.FundRawTransactionOptions;
import live.thought.thought4j.ThoughtClientInterface.FundedRawTransaction;
import live.thought.thought4j.ThoughtClientInterface.Transaction.Details;
import live.thought.thought4j.util.Base64Coder;
import live.thought.thought4j.util.CoinUtil;
import live.thought.thought4j.util.JSON;
import live.thought.thought4j.util.ListMapWrapper;
import live.thought.thought4j.util.MapWrapper;

/**
 *
 * @author Mikhail Yevchenko m.ṥῥẚɱ.ѓѐḿởύḙ at azazar.com Small modifications by
 *         Alessandro Polverini polverini at gmail.com
 */
public class ThoughtRPCClient implements ThoughtClientInterface
{

  private static final Logger logger = Logger.getLogger(ThoughtRPCClient.class.getCanonicalName());

  public final URL            rpcURL;

  private URL                 noAuthURL;
  private String              authStr;

  public ThoughtRPCClient(String rpcUrl) throws MalformedURLException
  {
    this(new URL(rpcUrl));
  }

  public ThoughtRPCClient(URL rpc)
  {
    this.rpcURL = rpc;
    try
    {
      noAuthURL = new URI(rpc.getProtocol(), null, rpc.getHost(), rpc.getPort(), rpc.getPath(), rpc.getQuery(), null).toURL();
    }
    catch (MalformedURLException | URISyntaxException ex)
    {
      throw new IllegalArgumentException(rpc.toString(), ex);
    }
    authStr = rpc.getUserInfo() == null ? null
        : String.valueOf(Base64Coder.encode(rpc.getUserInfo().getBytes(Charset.forName("ISO8859-1"))));
  }

  public static final URL    DEFAULT_JSONRPC_URL;
  public static final URL    DEFAULT_JSONRPC_TESTNET_URL;
  public static final URL    DEFAULT_JSONRPC_REGTEST_URL;

  public static final String DEFAULT_HOST         = "localhost";
  public static final String DEFAULT_USER         = "user";
  public static final String DEFAULT_PASSWORD     = "pass";
  public static final int    DEFAULT_PORT         = 10617;
  public static final int    DEFAULT_TEST_PORT    = 11617;
  public static final int    DEFAULT_REGTEST_PORT = 12617;
  static
  {
    String user = DEFAULT_USER;
    String password = DEFAULT_PASSWORD;
    String host = DEFAULT_HOST;
    int    port = DEFAULT_PORT;

    try
    {
      File f;
      File home = new File(System.getProperty("user.home"));

      if ((f = new File(home, ".thoughtcore" + File.separatorChar + "thought.conf")).exists())
      {
      }
      else if ((f = new File(home,
          "AppData" + File.separatorChar + "Roaming" + File.separatorChar + "ThoughtCore" + File.separatorChar + "thought.conf"))
              .exists())
      {
      }
      else
      {
        f = null;
      }

      if (f != null)
      {
        logger.fine("Thought configuration file found");

        Properties p = new Properties();
        try (FileInputStream i = new FileInputStream(f))
        {
          p.load(i);
        }

        user = p.getProperty("rpcuser", user);
        password = p.getProperty("rpcpassword", password);
        host = p.getProperty("rpcconnect", host);
        String prt = p.getProperty("rpcport", Integer.toString(port));
        port = Integer.parseInt(prt);
      }
    }
    catch (Exception ex)
    {
      logger.log(Level.SEVERE, null, ex);
    }

    try
    {
      DEFAULT_JSONRPC_URL = new URL("http://" + user + ':' + password + "@" + host + ":" + Integer.toString(DEFAULT_PORT) + "/");
      DEFAULT_JSONRPC_TESTNET_URL = new URL(
          "http://" + user + ':' + password + "@" + host + ":" + Integer.toString(DEFAULT_TEST_PORT) + "/");
      DEFAULT_JSONRPC_REGTEST_URL = new URL(
          "http://" + user + ':' + password + "@" + host + ":" + Integer.toString(DEFAULT_REGTEST_PORT) + "/");
    }
    catch (MalformedURLException ex)
    {
      throw new RuntimeException(ex);
    }
  }

  public ThoughtRPCClient(boolean testNet)
  {
    this(testNet ? DEFAULT_JSONRPC_TESTNET_URL : DEFAULT_JSONRPC_URL);
  }

  public ThoughtRPCClient()
  {
    this(DEFAULT_JSONRPC_TESTNET_URL);
  }

  private HostnameVerifier hostnameVerifier = null;
  private SSLSocketFactory sslSocketFactory = null;

  public HostnameVerifier getHostnameVerifier()
  {
    return hostnameVerifier;
  }

  public void setHostnameVerifier(HostnameVerifier hostnameVerifier)
  {
    this.hostnameVerifier = hostnameVerifier;
  }

  public SSLSocketFactory getSslSocketFactory()
  {
    return sslSocketFactory;
  }

  public void setSslSocketFactory(SSLSocketFactory sslSocketFactory)
  {
    this.sslSocketFactory = sslSocketFactory;
  }

  public static final Charset QUERY_CHARSET = Charset.forName("ISO8859-1");

  public byte[] prepareRequest(final String method, final Object... params)
  {
    return JSON.stringify(new LinkedHashMap<String, Object>()
    {
      private static final long serialVersionUID = 1L;
      {
        put("method", method);
        put("params", params);
        put("id", "1");
      }
    }).getBytes(QUERY_CHARSET);
  }

  private static byte[] loadStream(InputStream in, boolean close) throws IOException
  {
    ByteArrayOutputStream o = new ByteArrayOutputStream();
    try
    { 
      if (null == in)
      {
        o.write("Null".getBytes());
      }
      else
      {
        byte[] buffer = new byte[1024];
        for (;;)
        {
          int nr = in.read(buffer);

          if (nr == -1)
            break;
          if (nr == 0)
            throw new IOException("Read timed out");

          o.write(buffer, 0, nr);
        }
      }
    }
    finally
    {
      if (close && null != in)
      {
        in.close();
      }
    }
    return o.toByteArray();
  }

  public Object loadResponse(InputStream in, Object expectedID, boolean close) throws IOException, GenericRpcException
  {
      String r = new String(loadStream(in, close), QUERY_CHARSET);
      logger.log(Level.FINE, "Thought JSON-RPC response:\n{0}", r);
      try
      {
        Map<?, ?> response = (Map<?, ?>) JSON.parse(r);

        if (!expectedID.equals(response.get("id")))
          throw new ThoughtRPCException(
              "Wrong response ID (expected: " + String.valueOf(expectedID) + ", response: " + response.get("id") + ")");

        if (response.get("error") != null)
          throw new GenericRpcException(JSON.stringify(response.get("error")));

        return response.get("result");
      }
      catch (ClassCastException ex)
      {
        throw new ThoughtRPCException("Invalid server response format (data: \"" + r + "\")");
      }
  }

  public Object query(String method, Object... o) throws GenericRpcException
  {
    HttpURLConnection conn;
    try
    {
      conn = (HttpURLConnection) noAuthURL.openConnection();

      conn.setDoOutput(true);
      conn.setDoInput(true);

      if (conn instanceof HttpsURLConnection)
      {
        if (hostnameVerifier != null)
          ((HttpsURLConnection) conn).setHostnameVerifier(hostnameVerifier);
        if (sslSocketFactory != null)
          ((HttpsURLConnection) conn).setSSLSocketFactory(sslSocketFactory);
      }

      // conn.connect();
      ((HttpURLConnection) conn).setRequestProperty("Authorization", "Basic " + authStr);
      byte[] r = prepareRequest(method, o);
      logger.log(Level.FINE, "Thought JSON-RPC request:\n{0}", new String(r, QUERY_CHARSET));
      conn.getOutputStream().write(r);
      conn.getOutputStream().close();
      int responseCode = conn.getResponseCode();
      if (responseCode != 200)
        throw new ThoughtRPCException(method, Arrays.deepToString(o), responseCode, conn.getResponseMessage(),
            new String(loadStream(conn.getErrorStream(), true)));
      return loadResponse(conn.getInputStream(), "1", true);
    }
    catch (IOException ex)
    {
      throw new ThoughtRPCException(method, Arrays.deepToString(o), ex);
    }
  }

  public String queryJson(String method, Object... o) throws GenericRpcException
  {
    HttpURLConnection conn;
    try
    {
      conn = (HttpURLConnection) noAuthURL.openConnection();

      conn.setDoOutput(true);
      conn.setDoInput(true);

      if (conn instanceof HttpsURLConnection)
      {
        if (hostnameVerifier != null)
          ((HttpsURLConnection) conn).setHostnameVerifier(hostnameVerifier);
        if (sslSocketFactory != null)
          ((HttpsURLConnection) conn).setSSLSocketFactory(sslSocketFactory);
      }

      // conn.connect();
      ((HttpURLConnection) conn).setRequestProperty("Authorization", "Basic " + authStr);
      byte[] r = prepareRequest(method, o);
      logger.log(Level.FINE, "Thought JSON-RPC request:\n{0}", new String(r, QUERY_CHARSET));
      conn.getOutputStream().write(r);
      conn.getOutputStream().close();
      int responseCode = conn.getResponseCode();
      if (responseCode != 200)
        throw new ThoughtRPCException(method, Arrays.deepToString(o), responseCode, conn.getResponseMessage(),
            new String(loadStream(conn.getErrorStream(), true)));
      return new String(loadStream(conn.getInputStream(), true));
    }
    catch (IOException ex)
    {
      throw new ThoughtRPCException(method, Arrays.deepToString(o), ex);
    }
  }

  
  @Override
  public String createRawTransaction(List<TxInput> inputs, List<TxOutput> outputs) throws GenericRpcException
  {
    List<Map<?, ?>> pInputs = new ArrayList<>();

    for (final TxInput txInput : inputs)
    {
      pInputs.add(new LinkedHashMap<String, Object>()
      {
        private static final long serialVersionUID = 1L;
        {
          put("txid", txInput.txid());
          put("vout", txInput.vout());
        }
      });
    }

    Map<String, Double> pOutputs = new LinkedHashMap<String, Double>();

    Double oldValue;
    for (TxOutput txOutput : outputs)
    {
      if ((oldValue = pOutputs.put(txOutput.address(), txOutput.amount())) != null)
        pOutputs.put(txOutput.address(), CoinUtil.normalizeAmount(oldValue + txOutput.amount()));
      // throw new ThoughtRpcException("Duplicate output");
    }

    return (String) query("createrawtransaction", pInputs, pOutputs);
  }
  
  class FundedRawTransactionImpl extends MapWrapper implements FundedRawTransaction
  {
    private static final long serialVersionUID = 1L;

    public FundedRawTransactionImpl(Map<?, ?> result)
    {
      super(result);
    }
    
    @Override
    public String hex()
    {
      return mapStr("hex");
    }

    @Override
    public double fee()
    {
      return mapDouble("fee");
    }

    @Override
    public int changepos()
    {
      return mapInt("changepos");
    }
    
  }
  
  public FundedRawTransaction fundRawTransaction(String hexstring, FundRawTransactionOptions options) throws GenericRpcException
  { 
    Map<?, ?> result = null;
    if (null != options)
    {
      Map<String, Object> optionMap = new LinkedHashMap<String, Object>();
      if (null != options.getChangeAddress()) optionMap.put("changeAddress", options.getChangeAddress());
      if (null != options.getChangePosition()) optionMap.put("changePosition", options.getChangePosition());
      if (null != options.getIncludeWatching()) optionMap.put("includeWatching", options.getIncludeWatching());
      if (null != options.getLockUnspents()) optionMap.put("lockUnspents", options.getLockUnspents());
      if (null != options.getReserveChangeKey()) optionMap.put("reserveChangeKey", options.getReserveChangeKey());
      if (null != options.getFeeRate()) optionMap.put("feeRate", options.getFeeRate());
      if (null != options.getSubtractFeeFromOutputs()) optionMap.put("subtractFeeFromOutputs", options.getSubtractFeeFromOutputs());
      result = (Map<?, ?>) query("fundrawtransaction", hexstring, optionMap);
    }
    else
    {
      result = (Map<?, ?>) query("fundrawtransaction", hexstring);
    }
    
    return new FundedRawTransactionImpl(result);
  }

  @Override
  public boolean lockunspent(boolean lock, List<BasicTxInput> inputs) throws GenericRpcException
  {
    List<Map<?, ?>> pInputs = new ArrayList<>();

    for (final TxInput txInput : inputs)
    {
      pInputs.add(new LinkedHashMap<String, Object>()
      {
        private static final long serialVersionUID = 1L;
        {
          put("txid", txInput.txid());
          put("vout", txInput.vout());
        }
      });
    }
    return (boolean) query("lockunspent", lock, pInputs);
  }

  private class LockedUnspentImpl implements LockedUnspent
  {
    String txid;
    int    vout;

    @Override
    public String txid()
    {
      return txid;
    }

    @Override
    public int vout()
    {
      return vout;
    }

  }

  @Override
  public List<LockedUnspent> listlockunspent() throws GenericRpcException
  {
    List<Map<?,?>> maps = (List<Map<?,?>>) query("listlockunspent");
    List<LockedUnspent> retval = new ArrayList<>();
    for (Map<?,?> m : maps)
    {
      LockedUnspentImpl lui = new LockedUnspentImpl();
      lui.txid = m.get("txid").toString();
      lui.vout = Integer.parseInt(m.get("vout").toString());
      retval.add(lui);
    }
    return retval;
  }

  @Override
  public String dumpPrivKey(String address) throws GenericRpcException
  {
    return (String) query("dumpprivkey", address);
  }

  @Override
  public String getAccount(String address) throws GenericRpcException
  {
    return (String) query("getaccount", address);
  }

  @Override
  public String getAccountAddress(String account) throws GenericRpcException
  {
    return (String) query("getaccountaddress", account);
  }

  @Override
  public List<String> getAddressesByAccount(String account) throws GenericRpcException
  {
    return (List<String>) query("getaddressesbyaccount", account);
  }

  @Override
  public double getBalance() throws GenericRpcException
  {
    return ((Number) query("getbalance")).doubleValue();
  }

  @Override
  public double getBalance(String account) throws GenericRpcException
  {
    return ((Number) query("getbalance", account)).doubleValue();
  }

  @Override
  public double getBalance(String account, int minConf) throws GenericRpcException
  {
    return ((Number) query("getbalance", account, minConf)).doubleValue();
  }
  
  @Override
  public Map<String,Double> listAddressBalances(double minBalance) throws GenericRpcException
  {
    return (Map<String,Double>) query("listaddressbalances", minBalance);
  }
  
  static Map<String,Collection<String>> addrParam = new HashMap<String, Collection<String>>();
  
  private class AddressBalanceInfoWrapper extends MapWrapper implements AddressBalanceInfo, Serializable
  {
    private static final long serialVersionUID = 1L;

    public AddressBalanceInfoWrapper(Map<?, ?> m)
    {
      super(m);
    }

    @Override
    public long balance()
    {
      return mapLong("balance");
    }

    @Override
    public long balance_immature()
    {
      return mapLong("balance_immature");
    }

    @Override
    public long balance_spendable()
    {
      return mapLong("balance_spendable");
    }

    @Override
    public long received()
    {
      return mapLong("received");
    }
  }

  
  @Override 
  public AddressBalanceInfo getAddressBalance(Set<String> addresses) throws GenericRpcException
  {
    addrParam.put("addresses", addresses);
    return new AddressBalanceInfoWrapper((Map<?,?>) query("getaddressbalance", addrParam));
  }
  
  private class AddressMempoolInfoWrapper extends MapWrapper implements AddressMempoolInfo
  {
    
    public AddressMempoolInfoWrapper(Map<?, ?> m)
    {
      super(m);
    }

    @Override
    public String address()
    {
      return mapStr("address");
    }

    @Override
    public String txid()
    {
      return mapStr("txid");
    }

    @Override
    public int index()
    {
      return mapInt("index");
    }

    @Override
    public long notions()
    {
      return mapLong("notions");
    }

    @Override
    public long timestamp()
    {
      return mapLong("timestamp");
    }

    @Override
    public String prevtxid()
    {
      return mapStr("prevtxid");
    }

    @Override
    public String prevout()
    {
      return mapStr("prevout");
    }
    
  }
  
  @Override 
  public List<AddressMempoolInfo> getAddressMempool(Set<String> addresses) throws GenericRpcException
  {
    addrParam.put("addresses", addresses);
    List<AddressMempoolInfo> retval = new LinkedList<AddressMempoolInfo>();
      
    List<Map<?,?>> maps = (List<Map<?,?>>)query("getaddressmempool", addrParam);
    
    for (Map<?,?> m : maps)
    {
      AddressMempoolInfo ampi = new AddressMempoolInfoWrapper(m);
      retval.add(ampi);
    }
    return retval;
  }
  
  @Override 
  public List<String> getAddressTxids(Set<String> addresses) throws GenericRpcException
  {
    addrParam.put("addresses", addresses);
    List<String> retval = (List<String>)query("getaddresstxids", addrParam);
    return retval;
  }
  
  @Override 
  public List<String> getAddressTxids(Set<String> addresses, int start, int end) throws GenericRpcException
  {
    Map<String, Object> params = new LinkedHashMap<String, Object>();
    params.put("addresses", addresses);
    params.put("start", start);
    params.put("end", end);
    List<String> retval = (List<String>)query("getaddresstxids", params);
    return retval;
  }
  
  
  private class AddressUtxoWrapper extends MapWrapper implements AddressUtxo
  {
    
    public AddressUtxoWrapper(Map<?, ?> m)
    {
      super(m);
    }

    @Override
    public String address()
    {
      return mapStr("address");
    }

    @Override
    public String txid()
    {
      return mapStr("txid");
    }

    @Override
    public int outputIndex()
    {
      return mapInt("outputIndex");
    }

    @Override
    public long notions()
    {
      return mapLong("notions");
    }

    @Override
    public String script()
    {
      return mapStr("script");
    }

    @Override
    public int height()
    {
      return mapInt("height");
    }

    
   
  }

  @Override
  public List<AddressUtxo> getAddressUtxos(Set<String> addresses) throws GenericRpcException
  {
    addrParam.put("addresses", addresses);
    List<AddressUtxo> retval = new LinkedList<AddressUtxo>();
      
    List<Map<?,?>> maps = (List<Map<?,?>>)query("getaddressutxos", addrParam);
    
    for (Map<?,?> m : maps)
    {
      AddressUtxo utxo = new AddressUtxoWrapper(m);
      retval.add(utxo);
    }
    return retval;
  }

  @Override
  public SmartFeeResult getEstimateSmartFee(int blocks)
  {
    return new SmartFeeResultMapWrapper((Map<?, ?>) query("estimatesmartfee", blocks));
  }

  private class InfoWrapper extends MapWrapper implements Info, Serializable
  {
    private static final long serialVersionUID = 1L;

    public InfoWrapper(Map<?, ?> m)
    {
      super(m);
    }

    @Override
    public double balance()
    {
      return mapDouble("balance");
    }

    @Override
    public int blocks()
    {
      return mapInt("blocks");
    }

    @Override
    public int connections()
    {
      return mapInt("connections");
    }

    @Override
    public double difficulty()
    {
      return mapDouble("difficulty");
    }

    @Override
    public String errors()
    {
      return mapStr("errors");
    }

    @Override
    public long keyPoolOldest()
    {
      return mapLong("keypoololdest");
    }

    @Override
    public long keyPoolSize()
    {
      return mapLong("keypoolsize");
    }

    @Override
    public double payTxFee()
    {
      return mapDouble("paytxfee");
    }

    @Override
    public long protocolVersion()
    {
      return mapLong("protocolversion");
    }

    @Override
    public String proxy()
    {
      return mapStr("proxy");
    }

    @Override
    public double relayFee()
    {
      return mapDouble("relayfee");
    }

    @Override
    public boolean testnet()
    {
      return mapBool("testnet");
    }

    @Override
    public int timeOffset()
    {
      return mapInt("timeoffset");
    }

    @Override
    public long version()
    {
      return mapLong("version");
    }

    @Override
    public long walletVersion()
    {
      return mapLong("walletversion");
    }

  }

  private class TxOutSetInfoWrapper extends MapWrapper implements TxOutSetInfo, Serializable
  {
    private static final long serialVersionUID = 1L;

    public TxOutSetInfoWrapper(Map<?, ?> m)
    {
      super(m);
    }

    @Override
    public long height()
    {
      return mapInt("height");
    }

    @Override
    public String bestBlock()
    {
      return mapStr("bestBlock");
    }

    @Override
    public long transactions()
    {
      return mapInt("transactions");
    }

    @Override
    public long txouts()
    {
      return mapInt("txouts");
    }

    @Override
    public long bytesSerialized()
    {
      return mapInt("bytes_serialized");
    }

    @Override
    public String hashSerialized()
    {
      return mapStr("hash_serialized");
    }

    @Override
    public BigDecimal totalAmount()
    {
      return mapBigDecimal("total_amount");
    }
  }

  private class WalletInfoWrapper extends MapWrapper implements WalletInfo, Serializable
  {
    private static final long serialVersionUID = 1L;

    public WalletInfoWrapper(Map m)
    {
      super(m);
    }

    @Override
    public long walletVersion()
    {
      return mapLong("walletversion");
    }

    @Override
    public BigDecimal balance()
    {
      return mapBigDecimal("balance");
    }

    @Override
    public BigDecimal unconfirmedBalance()
    {
      return mapBigDecimal("unconfirmed_balance");
    }

    @Override
    public BigDecimal immatureBalance()
    {
      return mapBigDecimal("immature_balance");
    }

    @Override
    public long txCount()
    {
      return mapLong("txcount");
    }

    @Override
    public long keyPoolOldest()
    {
      return mapLong("keypoololdest");
    }

    @Override
    public long keyPoolSize()
    {
      return mapLong("keypoolsize");
    }

    @Override
    public long unlockedUntil()
    {
      return mapLong("unlocked_until");
    }

    @Override
    public BigDecimal payTxFee()
    {
      return mapBigDecimal("paytxfee");
    }

    @Override
    public String hdMasterKeyId()
    {
      return mapStr("hdmasterkeyid");
    }
  }

  private class NetworkInfoWrapper extends MapWrapper implements NetworkInfo, Serializable
  {
    private static final long serialVersionUID = 1L;

    public NetworkInfoWrapper(Map<?, ?> m)
    {
      super(m);
    }

    @Override
    public long version()
    {
      return mapLong("version");
    }

    @Override
    public String subversion()
    {
      return mapStr("subversion");
    }

    @Override
    public long protocolVersion()
    {
      return mapLong("protocolversion");
    }

    @Override
    public String localServices()
    {
      return mapStr("localservices");
    }

    @Override
    public boolean localRelay()
    {
      return mapBool("localrelay");
    }

    @Override
    public long timeOffset()
    {
      return mapLong("timeoffset");
    }

    @Override
    public long connections()
    {
      return mapLong("connections");
    }

    @Override
    public List<Network> networks()
    {
      List<Map<?,?>> maps = (List<Map<?,?>>) m.get("networks");
      List<Network> networks = new LinkedList<Network>();
      for (Map<?,?> m : maps)
      {
        Network net = new NetworkWrapper(m);
        networks.add(net);
      }
      return networks;
    }

    @Override
    public BigDecimal relayFee()
    {
      return mapBigDecimal("relayfee");
    }

    @Override
    public List<String> localAddresses()
    {
      return (List<String>) m.get("localaddresses");
    }

    @Override
    public String warnings()
    {
      return mapStr("warnings");
    }
  }

  private class NetworkWrapper extends MapWrapper implements Network, Serializable
  {
    private static final long serialVersionUID = 1L;

    public NetworkWrapper(Map<?,?> m)
    {
      super(m);
    }

    @Override
    public String name()
    {
      return mapStr("name");
    }

    @Override
    public boolean limited()
    {
      return mapBool("limited");
    }

    @Override
    public boolean reachable()
    {
      return mapBool("reachable");
    }

    @Override
    public String proxy()
    {
      return mapStr("proxy");
    }

    @Override
    public boolean proxyRandomizeCredentials()
    {
      return mapBool("proxy_randomize_credentials");
    }
  }

  private class MultiSigWrapper extends MapWrapper implements MultiSig, Serializable
  {
    private static final long serialVersionUID = 1L;

    public MultiSigWrapper(Map<?,?> m)
    {
      super(m);
    }

    @Override
    public String address()
    {
      return mapStr("address");
    }

    @Override
    public String redeemScript()
    {
      return mapStr("redeemScript");
    }
  }

  private class NodeInfoWrapper extends MapWrapper implements NodeInfo, Serializable
  {
    private static final long serialVersionUID = 1L;

    public NodeInfoWrapper(Map<?,?> m)
    {
      super(m);
    }

    @Override
    public String addedNode()
    {
      return mapStr("addednode");
    }

    @Override
    public boolean connected()
    {
      return mapBool("connected");
    }

    @Override
    public List<Address> addresses()
    {
      List<Map<?,?>> maps = (List<Map<?,?>>) m.get("addresses");
      List<Address> addresses = new LinkedList<Address>();
      for (Map<?,?> m : maps)
      {
        Address add = new AddressWrapper(m);
        addresses.add(add);
      }
      return addresses;
    }
  }

  private class AddressWrapper extends MapWrapper implements Address, Serializable
  {
    private static final long serialVersionUID = 1L;

    public AddressWrapper(Map<?,?> m)
    {
      super(m);
    }

    @Override
    public String address()
    {
      return mapStr("address");
    }

    @Override
    public String connected()
    {
      return mapStr("connected");
    }
  }

  private class DetailsWrapper extends MapWrapper implements Details, Serializable
  {
    private static final long serialVersionUID = 1L;
    public DetailsWrapper(Map<?,?> m)
    {
      super(m);
    }
    @Override
    public String account()
    {
      return mapStr(m, "account");
    }
    @Override
    public String address()
    {
      return mapStr(m, "address");
    }
    @Override
    public String category()
    {
      return mapStr(m, "category");
    }
    @Override
    public double amount()
    {
      return mapDouble(m, "amount");
    }
    @Override
    public String label()
    {
      return mapStr(m, "label");
    }
    @Override
    public int vout()
    {
      return mapInt(m, "vout");
    }
    
    
  }
  
  private class TransactionWrapper extends MapWrapper implements Transaction, Serializable
  {
    private static final long serialVersionUID = 1L;

    public TransactionWrapper(Map<?,?> m)
    {
      super(m);
    }

    @Override
    public String account()
    {
      return mapStr(m, "account");
    }

    @Override
    public String address()
    {
      return mapStr(m, "address");
    }

    @Override
    public String category()
    {
      return mapStr(m, "category");
    }

    @Override
    public double amount()
    {
      return mapDouble(m, "amount");
    }

    @Override
    public double fee()
    {
      return mapDouble(m, "fee");
    }
    
    @Override
    public boolean generated()
    {
      return mapBool(m, "generated");
    }

    @Override
    public int confirmations()
    {
      return mapInt(m, "confirmations");
    }

    @Override
    public String blockHash()
    {
      return mapStr(m, "blockhash");
    }

    @Override
    public int blockIndex()
    {
      return mapInt(m, "blockindex");
    }

    @Override
    public Date blockTime()
    {
      return mapCTime(m, "blocktime");
    }

    @Override
    public String txId()
    {
      return mapStr(m, "txid");
    }

    @Override
    public Date time()
    {
      return mapCTime(m, "time");
    }

    @Override
    public Date timeReceived()
    {
      return mapCTime(m, "timereceived");
    }

    @Override
    public List<Details> details()
    {
      List<Map<?,?>> maps = (List<Map<?,?>>) m.get("details");
      List<Details> details = new LinkedList<Details>();
      for (Map<?,?> m : maps)
      {
        Details add = new DetailsWrapper(m);
        details.add(add);
      }
      return details;
    }
    
    @Override
    public String comment()
    {
      return mapStr(m, "comment");
    }

    @Override
    public String commentTo()
    {
      return mapStr(m, "to");
    }

    private RawTransaction raw = null;

    @Override
    public RawTransaction raw()
    {
      if (raw == null)
        try
        {
          raw = getRawTransaction(txId());
        }
        catch (GenericRpcException ex)
        {
          throw new RuntimeException(ex);
        }
      return raw;
    }

    @Override
    public String toString()
    {
      return m.toString();
    }
  }

  private class TxOutWrapper extends MapWrapper implements TxOut, Serializable
  {
    private static final long serialVersionUID = 1L;

    public TxOutWrapper(Map<?,?> m)
    {
      super(m);
    }

    @Override
    public String bestBlock()
    {
      return mapStr("bestblock");
    }

    @Override
    public long confirmations()
    {
      return mapLong("confirmations");
    }

    @Override
    public BigDecimal value()
    {
      return mapBigDecimal("value");
    }

    @Override
    public String asm()
    {
      return mapStr("asm");
    }

    @Override
    public String hex()
    {
      return mapStr("hex");
    }

    @Override
    public long reqSigs()
    {
      return mapLong("reqSigs");
    }

    @Override
    public String type()
    {
      return mapStr("type");
    }

    @Override
    public List<String> addresses()
    {
      return (List<String>) m.get("addresses");
    }

    @Override
    public long version()
    {
      return mapLong("version");
    }

    @Override
    public boolean coinBase()
    {
      return mapBool("coinbase");
    }
  }

  private class MiningInfoWrapper extends MapWrapper implements MiningInfo, Serializable
  {
    private static final long serialVersionUID = 1L;

    public MiningInfoWrapper(Map<?,?> m)
    {
      super(m);
    }

    @Override
    public int blocks()
    {
      return mapInt("blocks");
    }

    @Override
    public int currentBlockSize()
    {
      return mapInt("currentblocksize");
    }

    @Override
    public int currentBlockWeight()
    {
      return mapInt("currentblockweight");
    }

    @Override
    public int currentBlockTx()
    {
      return mapInt("currentblocktx");
    }

    @Override
    public double difficulty()
    {
      return mapDouble("difficulty");
    }

    @Override
    public String errors()
    {
      return mapStr("errors");
    }

    @Override
    public double networkHashps()
    {
      return Double.valueOf(mapStr("networkhashps"));
    }

    @Override
    public int pooledTx()
    {
      return mapInt("pooledtx");
    }

    @Override
    public boolean testNet()
    {
      return mapBool("testnet");
    }

    @Override
    public String chain()
    {
      return mapStr("chain");
    }
  }

  private class MasternodeWrapper extends MapWrapper implements Masternode, Serializable
  {
	  private static final long serialVersionUID = 1L;

	    public MasternodeWrapper(Map<?,?> m)
	    {
	      super(m);
	    }

		@Override
		public String payee() {
			return mapStr("payee");
		}

		@Override
		public String script() {
			return mapStr("script");
		}

		@Override
		public long amount() {
			return mapLong("amount");
		}
  }
  
  private class BlockTemplateTransactionWrapper extends MapWrapper implements BlockTemplateTransaction, Serializable
  {
    private static final long serialVersionUID = 1L;
    
    public BlockTemplateTransactionWrapper(Map<?, ?> m)
    {
      super(m);
    }

    @Override
    public String data()
    {
      return mapStr("data");
    }

    @Override
    public String hash()
    {
      return mapStr("hash");
    }

    @Override
    public List<Long> depends()
    {
      return (List<Long>) m.get("depends");
    }

    @Override
    public long fee()
    {
      return mapLong("fee");
    }

    @Override
    public long sigops()
    {
      return mapLong("sigops");
    }

    @Override
    public boolean required()
    {
      return mapBool("required");
    }
  }
  
  private class BlockTemplateWrapper extends MapWrapper implements BlockTemplate, Serializable
  {
    private static final long serialVersionUID = 1L;

    public BlockTemplateWrapper(Map<?,?> m)
    {
      super(m);
    }

    @Override
    public List<String> capabilities()
    {
      return (List<String>) m.get("capabilities");
    }

    @Override
    public long version()
    {
      return mapLong("version");
    }

    @Override
    public List<String> rules()
    {
      return (List<String>) m.get("rules");
    }

    @Override
    public int vbrequired()
    {
      return mapInt("vbrequired");
    }

    @Override
    public String previousblockhash()
    {
      return mapStr("previousblockhash");
    }

    @Override
    public List<BlockTemplateTransaction> transactions()
    {
      List<Map<?,?>> maps = (List<Map<?,?>>) m.get("transactions");
      List<BlockTemplateTransaction> transactions = new LinkedList<BlockTemplateTransaction>();
      for (Map<?,?> m : maps)
      {
        BlockTemplateTransaction add = new BlockTemplateTransactionWrapper(m);
        transactions.add(add);
      }
      return transactions;
    }

    @Override
    public long coinbasevalue()
    {
      return mapLong("coinbasevalue");
    }

    @Override
    public String longpollid()
    {
      return mapStr("longpollid");
    }

    @Override
    public String target()
    {
      return mapStr("target");
    }

    @Override
    public long mintime()
    {
      return mapLong("mintime");
    }

    @Override
    public List<String> mutable()
    {
      return (List<String>) m.get("mutable");
    }

    @Override
    public String noncerange()
    {
      return mapStr("noncerange");
    }

    @Override
    public long sigoplimit()
    {
      return mapLong("sigoplimit");
    }

    @Override
    public long sizelimit()
    {
      return mapLong("sizelimit");
    }

    @Override
    public long curtime()
    {
      return mapLong("curtime");
    }

    @Override
    public String bits()
    {
      return mapStr("bits");
    }

    @Override
    public long height()
    {
      return mapLong("height");
    }
    
    @Override
    public List<Masternode> masternode()
    {
      List<Map<?,?>> maps = (List<Map<?,?>>) m.get("masternode");
      List<Masternode> masternodes = new LinkedList<Masternode>();
      for (Map<?,?> m : maps)
      {
        Masternode add = new MasternodeWrapper(m);
        masternodes.add(add);
      }
      return masternodes;
    }
    
    @Override
    public boolean masternode_payments_started() 
    {
      return mapBool("masternode_payments_started");	
    }
    
    @Override
    public boolean masternode_payments_enforced()
    {
    	return mapBool("masternode_payments_enforced");
    }
    
    @Override
    public String coinbase_payload()
    {
      return mapStr("coinbase_payload");
    }
  }
  
  private class BlockChainInfoMapWrapper extends MapWrapper implements BlockChainInfo, Serializable
  {
    private static final long serialVersionUID = 1L;

    public BlockChainInfoMapWrapper(Map<?,?> m)
    {
      super(m);
    }

    @Override
    public String chain()
    {
      return mapStr("chain");
    }

    @Override
    public int blocks()
    {
      return mapInt("blocks");
    }

    @Override
    public String bestBlockHash()
    {
      return mapStr("bestblockhash");
    }

    @Override
    public double difficulty()
    {
      return mapDouble("difficulty");
    }

    @Override
    public double verificationProgress()
    {
      return mapDouble("verificationprogress");
    }

    @Override
    public String chainWork()
    {
      return mapStr("chainwork");
    }
  }

  private class SmartFeeResultMapWrapper extends MapWrapper implements SmartFeeResult, Serializable
  {
    private static final long serialVersionUID = 1L;

    public SmartFeeResultMapWrapper(Map<?,?> m)
    {
      super(m);
    }

    @Override
    public double feeRate()
    {
      return mapDouble("feerate");
    }

    @Override
    public int blocks()
    {
      return mapInt("blocks");
    }

  }

  private class BlockMapWrapper extends MapWrapper implements Block, Serializable
  {
    private static final long serialVersionUID = 1L;

    public BlockMapWrapper(Map<?,?> m)
    {
      super(m);
    }

    @Override
    public String hash()
    {
      return mapStr("hash");
    }

    @Override
    public int confirmations()
    {
      return mapInt("confirmations");
    }

    @Override
    public int size()
    {
      return mapInt("size");
    }

    @Override
    public int height()
    {
      return mapInt("height");
    }

    @Override
    public int version()
    {
      return mapInt("version");
    }

    @Override
    public String merkleRoot()
    {
      return mapStr("merkleroot");
    }

    @Override
    public String chainwork()
    {
      return mapStr("chainwork");
    }

    @Override
    public List<String> tx()
    {
      return (List<String>) m.get("tx");
    }

    @Override
    public Date time()
    {
      return mapCTime("time");
    }

    @Override
    public long nonce()
    {
      return mapLong("nonce");
    }

    @Override
    public String bits()
    {
      return mapStr("bits");
    }

    @Override
    public double difficulty()
    {
      return mapDouble("difficulty");
    }

    @Override
    public String previousHash()
    {
      return mapStr("previousblockhash");
    }

    @Override
    public String nextHash()
    {
      return mapStr("nextblockhash");
    }

    @Override
    public Block previous() throws GenericRpcException
    {
      if (!m.containsKey("previousblockhash"))
        return null;
      return getBlock(previousHash());
    }

    @Override
    public Block next() throws GenericRpcException
    {
      if (!m.containsKey("nextblockhash"))
        return null;
      return getBlock(nextHash());
    }

  }

  @Override
  public Block getBlock(int height) throws GenericRpcException
  {
    String hash = (String) query("getblockhash", height);
    return getBlock(hash);
  }

  @Override
  public Block getBlock(String blockHash) throws GenericRpcException
  {
    return new BlockMapWrapper((Map<?,?>) query("getblock", blockHash));
  }

  @Override
  public String getRawBlock(String blockHash) throws GenericRpcException
  {
    return (String) query("getblock", blockHash, false);
  }

  @Override
  public String getBlockHash(int height) throws GenericRpcException
  {
    return (String) query("getblockhash", height);
  }

  @Override
  public BlockChainInfo getBlockChainInfo() throws GenericRpcException
  {
    return new BlockChainInfoMapWrapper((Map<?,?>) query("getblockchaininfo"));
  }

  @Override
  public int getBlockCount() throws GenericRpcException
  {
    return ((Number) query("getblockcount")).intValue();
  }

  @Override
  public Info getInfo() throws GenericRpcException
  {
    return new InfoWrapper((Map<?,?>) query("getinfo"));
  }

  @Override
  public TxOutSetInfo getTxOutSetInfo() throws GenericRpcException
  {
    return new TxOutSetInfoWrapper((Map<?,?>) query("gettxoutsetinfo"));
  }

  @Override
  public NetworkInfo getNetworkInfo() throws GenericRpcException
  {
    return new NetworkInfoWrapper((Map<?,?>) query("getnetworkinfo"));
  }

  @Override
  public MiningInfo getMiningInfo() throws GenericRpcException
  {
    return new MiningInfoWrapper((Map<?,?>) query("getmininginfo"));
  }
  
  @Override
  public BlockTemplate getBlockTemplate() throws GenericRpcException
  {
    return new BlockTemplateWrapper((Map<?,?>) query("getblocktemplate"));
  }
  
  @Override
  public BlockTemplate getBlockTemplate(String longpollid) throws GenericRpcException
  {
    Map<String,String> params = new HashMap<String,String>();
    params.put("longpollid", longpollid);
    return new BlockTemplateWrapper((Map<?,?>) query("getblocktemplate", params));
  }

  @Override
  public List<NodeInfo> getAddedNodeInfo(boolean dummy, String node) throws GenericRpcException
  {
    List<Map<?,?>> list = ((List<Map<?,?>>) query("getaddednodeinfo", dummy, node));
    List<NodeInfo> nodeInfoList = new LinkedList<NodeInfo>();
    for (Map<?,?> m : list)
    {
      NodeInfoWrapper niw = new NodeInfoWrapper(m);
      nodeInfoList.add(niw);
    }
    return nodeInfoList;
  }

  @Override
  public MultiSig createMultiSig(int nRequired, List<String> keys) throws GenericRpcException
  {
    return new MultiSigWrapper((Map<?,?>) query("createmultisig", nRequired, keys));
  }

  @Override
  public WalletInfo getWalletInfo()
  {
    return new WalletInfoWrapper((Map<?,?>) query("getwalletinfo"));
  }

  @Override
  public String getNewAddress() throws GenericRpcException
  {
    return (String) query("getnewaddress");
  }

  @Override
  public String getNewAddress(String account) throws GenericRpcException
  {
    return (String) query("getnewaddress", account);
  }

  @Override
  public List<String> getRawMemPool() throws GenericRpcException
  {
    return (List<String>) query("getrawmempool");
  }

  @Override
  public String getBestBlockHash() throws GenericRpcException
  {
    return (String) query("getbestblockhash");
  }

  @Override
  public String getRawTransactionHex(String txId) throws GenericRpcException
  {
    return (String) query("getrawtransaction", txId);
  }

  private class RawTransactionImpl extends MapWrapper implements RawTransaction, Serializable
  {
    private static final long serialVersionUID = 1L;

    public RawTransactionImpl(Map<?, ?> result)
    {
      super(result);
    }

    @Override
    public String hex()
    {
      return mapStr("hex");
    }

    @Override
    public String txId()
    {
      return mapStr("txid");
    }

    @Override
    public int version()
    {
      return mapInt("version");
    }

    @Override
    public long lockTime()
    {
      return mapLong("locktime");
    }

    @Override
    public String hash()
    {
      return mapStr("hash");
    }

    @Override
    public long size()
    {
      return mapLong("size");
    }

    @Override
    public long vsize()
    {
      return mapLong("vsize");
    }

    private class InImpl extends MapWrapper implements In, Serializable
    {
      private static final long serialVersionUID = 1L;

      public InImpl(Map<?,?> m)
      {
        super(m);
      }

      @Override
      public String txid()
      {
        return mapStr("txid");
      }

      @Override
      public int vout()
      {
        return mapInt("vout");
      }

      @Override
      public Map<String, Object> scriptSig()
      {
        return (Map<String, Object>) m.get("scriptSig");
      }

      @Override
      public long sequence()
      {
        return mapLong("sequence");
      }

      @Override
      public RawTransaction getTransaction()
      {
        try
        {
          return getRawTransaction(mapStr("txid"));
        }
        catch (GenericRpcException ex)
        {
          throw new RuntimeException(ex);
        }
      }

      @Override
      public Out getTransactionOutput()
      {
        return getTransaction().vOut().get(mapInt("vout"));
      }

      @Override
      public String scriptPubKey()
      {
        return mapStr("scriptPubKey");
      }

      @Override
      public boolean isCoinbase()
      {
        boolean retval = false;
        String cb = mapStr("coinbase");
        if (null != cb && cb.length() > 0)
        {
          retval = true;
        }
        return retval;
      }
      
      @Override
      public String coinbase()
      {
        return mapStr("coinbase");
      }

    }

    @Override
    public List<In> vIn()
    {
      final List<Map<String, Object>> vIn = (List<Map<String, Object>>) m.get("vin");
      return new AbstractList<In>()
      {

        @Override
        public In get(int index)
        {
          return new InImpl(vIn.get(index));
        }

        @Override
        public int size()
        {
          return vIn.size();
        }
      };
    }

    private class OutImpl extends MapWrapper implements Out, Serializable
    {
      private static final long serialVersionUID = 1L;

      public OutImpl(Map<?,?> m)
      {
        super(m);
      }

      @Override
      public double value()
      {
        return mapDouble("value");
      }

      @Override
      public int n()
      {
        return mapInt("n");
      }

      private class ScriptPubKeyImpl extends MapWrapper implements ScriptPubKey, Serializable
      {
        private static final long serialVersionUID = 1L;

        public ScriptPubKeyImpl(Map<?,?> m)
        {
          super(m);
        }

        @Override
        public String asm()
        {
          return mapStr("asm");
        }

        @Override
        public String hex()
        {
          return mapStr("hex");
        }

        @Override
        public int reqSigs()
        {
          return mapInt("reqSigs");
        }

        @Override
        public String type()
        {
          return mapStr("type");
        }

        @Override
        public List<String> addresses()
        {
          return (List<String>) m.get("addresses");
        }

      }

      @Override
      public ScriptPubKey scriptPubKey()
      {
        return new ScriptPubKeyImpl((Map<?,?>) m.get("scriptPubKey"));
      }

      @Override
      public TxInput toInput()
      {
        return new BasicTxInput(transaction().txId(), n());
      }

      @Override
      public RawTransaction transaction()
      {
        return RawTransactionImpl.this;
      }

    }

    @Override
    public List<Out> vOut()
    {
      final List<Map<String, Object>> vOut = (List<Map<String, Object>>) m.get("vout");
      return new AbstractList<Out>()
      {

        @Override
        public Out get(int index)
        {
          return new OutImpl(vOut.get(index));
        }

        @Override
        public int size()
        {
          return vOut.size();
        }
      };
    }

    @Override
    public String blockHash()
    {
      return mapStr("blockhash");
    }

    @Override
    public int confirmations()
    {
      return mapInt("confirmations");
    }

    @Override
    public Date time()
    {
      return mapCTime("time");
    }

    @Override
    public Date blocktime()
    {
      return mapCTime("blocktime");
    }

  }

  private class DecodedScriptImpl extends MapWrapper implements DecodedScript, Serializable
  {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public DecodedScriptImpl(Map m)
    {
      super(m);
    }

    @Override
    public String asm()
    {
      return mapStr("asm");
    }

    @Override
    public String hex()
    {
      return mapStr("hex");
    }

    @Override
    public String type()
    {
      return mapStr("type");
    }

    @Override
    public int reqSigs()
    {
      return mapInt("reqSigs");
    }

    @Override
    public List<String> addresses()
    {
      return (List) m.get("addresses");
    }

    @Override
    public String p2sh()
    {
      return mapStr("p2sh");
    }
  }

  public class NetTotalsImpl extends MapWrapper implements NetTotals, Serializable
  {
    private static final long serialVersionUID = 1L;

    public NetTotalsImpl(Map<?,?> m)
    {
      super(m);
    }

    @Override
    public long totalBytesRecv()
    {
      return mapLong("totalbytesrecv");
    }

    @Override
    public long totalBytesSent()
    {
      return mapLong("totalbytessent");
    }

    @Override
    public long timeMillis()
    {
      return mapLong("timemillis");
    }

    public class uploadTargetImpl extends MapWrapper implements uploadTarget, Serializable
    {
      private static final long serialVersionUID = 1L;

      public uploadTargetImpl(Map<?,?> m)
      {
        super(m);
      }

      @Override
      public long timeFrame()
      {
        return mapLong("timeframe");
      }

      @Override
      public int target()
      {
        return mapInt("target");
      }

      @Override
      public boolean targetReached()
      {
        return mapBool("targetreached");
      }

      @Override
      public boolean serveHistoricalBlocks()
      {
        return mapBool("servehistoricalblocks");
      }

      @Override
      public long bytesLeftInCycle()
      {
        return mapLong("bytesleftincycle");
      }

      @Override
      public long timeLeftInCycle()
      {
        return mapLong("timeleftincycle");
      }
    }

    @Override
    public NetTotals.uploadTarget uploadTarget()
    {
      return new uploadTargetImpl((Map<?,?>) m.get("uploadtarget"));
    }
  }

  @Override
  public RawTransaction getRawTransaction(String txId) throws GenericRpcException
  {
    return new RawTransactionImpl((Map<?,?>) query("getrawtransaction", txId, 1));
  }

  @Override
  public double getReceivedByAddress(String address) throws GenericRpcException
  {
    return ((Number) query("getreceivedbyaddress", address)).doubleValue();
  }

  @Override
  public double getReceivedByAddress(String address, int minConf) throws GenericRpcException
  {
    return ((Number) query("getreceivedbyaddress", address, minConf)).doubleValue();
  }

  @Override
  public void importPrivKey(String thoughtPrivKey) throws GenericRpcException
  {
    query("importprivkey", thoughtPrivKey);
  }

  @Override
  public void importPrivKey(String thoughtPrivKey, String label) throws GenericRpcException
  {
    query("importprivkey", thoughtPrivKey, label);
  }

  @Override
  public void importPrivKey(String thoughtPrivKey, String label, boolean rescan) throws GenericRpcException
  {
    query("importprivkey", thoughtPrivKey, label, rescan);
  }

  @Override
  public Object importAddress(String address, String label, boolean rescan) throws GenericRpcException
  {
    query("importaddress", address, label, rescan);
    return null;
  }

  @Override
  public Map<String, Number> listAccounts() throws GenericRpcException
  {
    return (Map<String, Number>) query("listaccounts");
  }

  @Override
  public Map<String, Number> listAccounts(int minConf) throws GenericRpcException
  {
    return (Map<String, Number>) query("listaccounts", minConf);
  }

  private static class ReceivedAddressListWrapper extends AbstractList<ReceivedAddress>
  {

    private final List<Map<String, Object>> wrappedList;

    public ReceivedAddressListWrapper(List<Map<String, Object>> wrappedList)
    {
      this.wrappedList = wrappedList;
    }

    @Override
    public ReceivedAddress get(int index)
    {
      final Map<String, Object> e = wrappedList.get(index);
      return new ReceivedAddress()
      {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        @Override
        public String address()
        {
          return (String) e.get("address");
        }

        @Override
        public String account()
        {
          return (String) e.get("account");
        }

        @Override
        public double amount()
        {
          return ((Number) e.get("amount")).doubleValue();
        }

        @Override
        public int confirmations()
        {
          return ((Number) e.get("confirmations")).intValue();
        }

        @Override
        public String toString()
        {
          return e.toString();
        }

      };
    }

    @Override
    public int size()
    {
      return wrappedList.size();
    }
  }

  @Override
  public List<ReceivedAddress> listReceivedByAddress() throws GenericRpcException
  {
    return new ReceivedAddressListWrapper((List) query("listreceivedbyaddress"));
  }

  @Override
  public List<ReceivedAddress> listReceivedByAddress(int minConf) throws GenericRpcException
  {
    return new ReceivedAddressListWrapper((List) query("listreceivedbyaddress", minConf));
  }

  @Override
  public List<ReceivedAddress> listReceivedByAddress(int minConf, boolean includeEmpty) throws GenericRpcException
  {
    return new ReceivedAddressListWrapper((List) query("listreceivedbyaddress", minConf, includeEmpty));
  }

  private class TransactionListMapWrapper extends ListMapWrapper<Transaction>
  {

    public TransactionListMapWrapper(List<Map<?, ?>> list)
    {
      super(list);
    }

    @Override
    protected Transaction wrap(final Map<?, ?> m)
    {
      return new TransactionWrapper(m);
    }
  }

  private class TransactionsSinceBlockImpl implements TransactionsSinceBlock, Serializable
  {

    /**
     * 
     */
    private static final long      serialVersionUID = 1L;
    public final List<Transaction> transactions;
    public final String            lastBlock;

    public TransactionsSinceBlockImpl(Map<?,?> r)
    {
      this.transactions = new TransactionListMapWrapper((List) r.get("transactions"));
      this.lastBlock = (String) r.get("lastblock");
    }

    @Override
    public List<Transaction> transactions()
    {
      return transactions;
    }

    @Override
    public String lastBlock()
    {
      return lastBlock;
    }

  }

  @Override
  public TransactionsSinceBlock listSinceBlock() throws GenericRpcException
  {
    return new TransactionsSinceBlockImpl((Map<?, ?>) query("listsinceblock"));
  }

  @Override
  public TransactionsSinceBlock listSinceBlock(String blockHash) throws GenericRpcException
  {
    return new TransactionsSinceBlockImpl((Map<?, ?>) query("listsinceblock", blockHash));
  }

  @Override
  public TransactionsSinceBlock listSinceBlock(String blockHash, int targetConfirmations) throws GenericRpcException
  {
    return new TransactionsSinceBlockImpl((Map<?, ?>) query("listsinceblock", blockHash, targetConfirmations));
  }

  @Override
  public List<Transaction> listTransactions() throws GenericRpcException
  {
    return new TransactionListMapWrapper((List<Map<?, ?>>) query("listtransactions"));
  }

  @Override
  public List<Transaction> listTransactions(String account) throws GenericRpcException
  {
    return new TransactionListMapWrapper((List<Map<?, ?>>) query("listtransactions", account));
  }

  @Override
  public List<Transaction> listTransactions(String account, int count) throws GenericRpcException
  {
    return new TransactionListMapWrapper((List<Map<?, ?>>) query("listtransactions", account, count));
  }

  @Override
  public List<Transaction> listTransactions(String account, int count, int skip) throws GenericRpcException
  {
    return new TransactionListMapWrapper((List<Map<?, ?>>) query("listtransactions", account, count, skip));
  }

  private class UnspentListWrapper extends ListMapWrapper<Unspent>
  {

    public UnspentListWrapper(List<Map<?, ?>> list)
    {
      super(list);
    }

    @Override
    protected Unspent wrap(final Map<?, ?> m)
    {
      return new UnspentWrapper(m);
    }
  }

  private class UnspentWrapper extends MapWrapper implements Unspent
  {
    private static final long serialVersionUID = 1L;

    UnspentWrapper(Map<?, ?> m)
    {
      super(m);
    }

    @Override
    public String txid()
    {
      return mapStr(m, "txid");
    }

    @Override
    public int vout()
    {
      return mapInt(m, "vout");
    }

    @Override
    public String address()
    {
      return mapStr(m, "address");
    }

    @Override
    public String scriptPubKey()
    {
      return mapStr(m, "scriptPubKey");
    }

    @Override
    public String account()
    {
      return mapStr(m, "account");
    }

    @Override
    public double amount()
    {
      return MapWrapper.mapDouble(m, "amount");
    }

    @Override
    public int confirmations()
    {
      return mapInt(m, "confirmations");
    }

    public boolean spendable()
    {
      return mapBool(m, "spendable");
    }
    
    public boolean solvable()
    {
      return mapBool(m, "solvable");
    }
    
    @Override
    public int ps_rounds()
    {
      return mapInt(m, "ps_rounds");
    }

    
    @Override
    public String toString()
    {
      return m.toString();
    }
  }

  @Override
  public List<Unspent> listUnspent() throws GenericRpcException
  {
    return new UnspentListWrapper((List) query("listunspent"));
  }

  @Override
  public List<Unspent> listUnspent(int minConf) throws GenericRpcException
  {
    return new UnspentListWrapper((List) query("listunspent", minConf));
  }

  @Override
  public List<Unspent> listUnspent(int minConf, int maxConf) throws GenericRpcException
  {
    return new UnspentListWrapper((List) query("listunspent", minConf, maxConf));
  }

  @Override
  public List<Unspent> listUnspent(int minConf, int maxConf, String... addresses) throws GenericRpcException
  {
    return new UnspentListWrapper((List) query("listunspent", minConf, maxConf, addresses));
  }
  
  @Override
  public String listUnspentJson(int minConf, int maxConf, String... addresses) throws GenericRpcException
  {
    return queryJson("listunspent", minConf, maxConf, addresses);
  }

  @Override
  public boolean move(String fromAccount, String toAddress, double amount) throws GenericRpcException
  {
    return (boolean) query("move", fromAccount, toAddress, amount);
  }

  @Override
  public boolean move(String fromAccount, String toAddress, double amount, String comment) throws GenericRpcException
  {
    return (boolean) query("move", fromAccount, toAddress, amount, 0, comment);
  }

  @Override
  public boolean move(String fromAccount, String toAddress, double amount, int minConf) throws GenericRpcException
  {
    return (boolean) query("move", fromAccount, toAddress, amount, minConf);
  }

  @Override
  public boolean move(String fromAccount, String toAddress, double amount, int minConf, String comment) throws GenericRpcException
  {
    return (boolean) query("move", fromAccount, toAddress, amount, minConf, comment);
  }

  @Override
  public String sendFrom(String fromAccount, String toAddress, double amount) throws GenericRpcException
  {
    return (String) query("sendfrom", fromAccount, toAddress, amount);
  }

  @Override
  public String sendFrom(String fromAccount, String toAddress, double amount, int minConf) throws GenericRpcException
  {
    return (String) query("sendfrom", fromAccount, toAddress, amount, minConf);
  }

  @Override
  public String sendFrom(String fromAccount, String toAddress, double amount, int minConf, boolean addlocked, String comment)
      throws GenericRpcException
  {
    return (String) query("sendfrom", fromAccount, toAddress, amount, minConf, addlocked, comment);
  }

  @Override
  public String sendFrom(String fromAccount, String toAddress, double amount, int minConf, boolean addlocked, String comment, String commentTo)
      throws GenericRpcException
  {
    return (String) query("sendfrom", fromAccount, toAddress, amount, minConf, addlocked, comment, commentTo);
  }

  @Override
  public String sendRawTransaction(String hex) throws GenericRpcException
  {
    return (String) query("sendrawtransaction", hex);
  }

  @Override
  public String sendToAddress(String toAddress, double amount) throws GenericRpcException
  {
    return (String) query("sendtoaddress", toAddress, amount);
  }

  @Override
  public String sendToAddress(String toAddress, double amount, String comment) throws GenericRpcException
  {
    return (String) query("sendtoaddress", toAddress, amount, comment);
  }

  @Override
  public String sendToAddress(String toAddress, double amount, String comment, String commentTo) throws GenericRpcException
  {
    return (String) query("sendtoaddress", toAddress, amount, comment, commentTo);
  }
  
  @Override
  public String sendToAddress(String toAddress, double amount, String comment, String commentTo, boolean subtractfeefromamount, boolean use_is, boolean use_ps) throws GenericRpcException
  {
    return (String) query("sendtoaddress", toAddress, amount, comment, commentTo, subtractfeefromamount, use_is, use_ps);
  }

  public String signRawTransaction(String hex) throws GenericRpcException
  {
    return signRawTransaction(hex, null, null, "ALL");
  }

  @Override
  public String signRawTransaction(String hex, List<? extends TxInput> inputs, List<String> privateKeys) throws GenericRpcException
  {
    return signRawTransaction(hex, inputs, privateKeys, "ALL");
  }

  public String signRawTransaction(String hex, List<? extends TxInput> inputs, List<String> privateKeys, String sigHashType)
  {
    List<Map<?, ?>> pInputs = null;

    if (inputs != null)
    {
      pInputs = new ArrayList<>();
      for (final TxInput txInput : inputs)
      {
        pInputs.add(new LinkedHashMap<String, Object>()
        {
          private static final long serialVersionUID = 1L;

          {
            put("txid", txInput.txid());
            put("vout", txInput.vout());
            put("scriptPubKey", txInput.scriptPubKey());
            if (txInput instanceof ExtendedTxInput)
            {
              ExtendedTxInput extin = (ExtendedTxInput) txInput;
              put("redeemScript", extin.redeemScript());
              put("amount", extin.amount());
            }
          }
        });
      }
    }

    Map<?, ?> result = (Map<?, ?>) query("signrawtransaction", hex, pInputs, privateKeys, sigHashType); // if sigHashType is null it
                                                                                                        // will return
    // the default "ALL"
    if ((Boolean) result.get("complete"))
      return (String) result.get("hex");
    else
      throw new GenericRpcException("Incomplete");
  }

  public RawTransaction decodeRawTransaction(String hex) throws GenericRpcException
  {
    Map<?, ?> result = (Map<?, ?>) query("decoderawtransaction", hex);
    RawTransaction rawTransaction = new RawTransactionImpl(result);
    return rawTransaction.vOut().get(0).transaction();
  }

  @Override
  public AddressValidationResult validateAddress(String address) throws GenericRpcException
  {
    final Map<?, ?> validationResult = (Map<?, ?>) query("validateaddress", address);
    return new AddressValidationResult()
    {

      /**
       * 
       */
      private static final long serialVersionUID = 1L;

      @Override
      public boolean isValid()
      {
        return ((Boolean) validationResult.get("isvalid"));
      }

      @Override
      public String address()
      {
        return (String) validationResult.get("address");
      }

      @Override
      public boolean isMine()
      {
        return ((Boolean) validationResult.get("ismine"));
      }

      @Override
      public boolean isScript()
      {
        return ((Boolean) validationResult.get("isscript"));
      }

      @Override
      public String pubKey()
      {
        return (String) validationResult.get("pubkey");
      }

      @Override
      public boolean isCompressed()
      {
        return ((Boolean) validationResult.get("iscompressed"));
      }

      @Override
      public String account()
      {
        return (String) validationResult.get("account");
      }

      @Override
      public String toString()
      {
        return validationResult.toString();
      }

    };
  }

  @Override
  public void setGenerate(boolean b) throws ThoughtRPCException
  {
    query("setgenerate", b);
  }

  @Override
  public List<String> generate(int numBlocks) throws ThoughtRPCException
  {
    return (List<String>) query("generate", numBlocks);
  }

  @Override
  public List<String> generate(int numBlocks, long maxTries) throws ThoughtRPCException
  {
    return (List<String>) query("generate", numBlocks, maxTries);
  }

  @Override
  public List<String> generateToAddress(int numBlocks, String address) throws ThoughtRPCException
  {
    return (List<String>) query("generatetoaddress", numBlocks, address);
  }

  @Override
  public double getEstimateFee(int nBlocks) throws GenericRpcException
  {
    return ((Number) query("estimatefee", nBlocks)).doubleValue();
  }

  @Override
  public double getEstimatePriority(int nBlocks) throws GenericRpcException
  {
    return ((Number) query("estimatepriority", nBlocks)).doubleValue();
  }

  @Override
  public void invalidateBlock(String hash) throws GenericRpcException
  {
    query("invalidateblock", hash);
  }

  @Override
  public void reconsiderBlock(String hash) throws GenericRpcException
  {
    query("reconsiderblock", hash);

  }

  private class PeerInfoWrapper extends MapWrapper implements PeerInfoResult, Serializable
  {
    private static final long serialVersionUID = 1L;

    public PeerInfoWrapper(Map<?, ?> m)
    {
      super(m);
    }

    @Override
    public long getId()
    {
      return mapLong("id");
    }

    @Override
    public String getAddr()
    {
      return mapStr("addr");
    }

    @Override
    public String getAddrLocal()
    {
      return mapStr("addrlocal");
    }

    @Override
    public String getServices()
    {
      return mapStr("services");
    }

    @Override
    public long getLastSend()
    {
      return mapLong("lastsend");
    }

    @Override
    public long getLastRecv()
    {
      return mapLong("lastrecv");
    }

    @Override
    public long getBytesSent()
    {
      return mapLong("bytessent");
    }

    @Override
    public long getBytesRecv()
    {
      return mapLong("bytesrecv");
    }

    @Override
    public long getConnTime()
    {
      return mapLong("conntime");
    }

    @Override
    public int getTimeOffset()
    {
      return mapInt("timeoffset");
    }

    @Override
    public double getPingTime()
    {
      return mapDouble("pingtime");
    }

    @Override
    public long getVersion()
    {
      return mapLong("version");
    }

    @Override
    public String getSubVer()
    {
      return mapStr("subver");
    }

    @Override
    public boolean isInbound()
    {
      return mapBool("inbound");
    }

    @Override
    public int getStartingHeight()
    {
      return mapInt("startingheight");
    }

    @Override
    public long getBanScore()
    {
      return mapLong("banscore");
    }

    @Override
    public int getSyncedHeaders()
    {
      return mapInt("synced_headers");
    }

    @Override
    public int getSyncedBlocks()
    {
      return mapInt("synced_blocks");
    }

    @Override
    public boolean isWhiteListed()
    {
      return mapBool("whitelisted");
    }

  }

  @Override
  public List<PeerInfoResult> getPeerInfo() throws GenericRpcException
  {
    final List<Map<?, ?>> l = (List<Map<?, ?>>) query("getpeerinfo");
    // final List<PeerInfoResult> res = new ArrayList<>(l.size());
    // for (Map m : l)
    // res.add(new PeerInfoWrapper(m));
    // return res;
    return new AbstractList<PeerInfoResult>()
    {

      @Override
      public PeerInfoResult get(int index)
      {
        return new PeerInfoWrapper(l.get(index));
      }

      @Override
      public int size()
      {
        return l.size();
      }
    };
  }

  @Override
  public void stop()
  {
    query("stop");
  }

  @Override
  public String getRawChangeAddress() throws GenericRpcException
  {
    return (String) query("getrawchangeaddress");
  }

  @Override
  public long getConnectionCount() throws GenericRpcException
  {
    return (long) query("getconnectioncount");
  }

  @Override
  public double getUnconfirmedBalance() throws GenericRpcException
  {
    return (double) query("getunconfirmedbalance");
  }

  @Override
  public double getDifficulty() throws GenericRpcException
  {
    if (query("getdifficulty") instanceof Long)
    {
      return ((Long) query("getdifficulty")).doubleValue();
    }
    else
    {
      return (double) query("getdifficulty");
    }
  }

  @Override
  public NetTotals getNetTotals() throws GenericRpcException
  {
    return new NetTotalsImpl((Map<?, ?>) query("getnettotals"));
  }

  @Override
  public DecodedScript decodeScript(String hex) throws GenericRpcException
  {
    return new DecodedScriptImpl((Map<?, ?>) query("decodescript", hex));
  }

  @Override
  public void ping() throws GenericRpcException
  {
    query("ping");
  }

  // It doesn't work!
  @Override
  public boolean getGenerate() throws ThoughtRPCException
  {
    return (boolean) query("getgenerate");
  }

  @Override
  public double getNetworkHashPs() throws GenericRpcException
  {
    return (Double) query("getnetworkhashps");
  }

  @Override
  public boolean setTxFee(BigDecimal amount) throws GenericRpcException
  {
    return (boolean) query("settxfee", amount);
  }

  /**
   *
   * @param node
   *          example: "192.168.0.6:8333"
   * @param command
   *          must be either "add", "remove" or "onetry"
   * @throws GenericRpcException
   */
  @Override
  public void addNode(String node, String command) throws GenericRpcException
  {
    query("addnode", node, command);
  }

  @Override
  public void backupWallet(String destination) throws GenericRpcException
  {
    query("backupwallet", destination);
  }

  @Override
  public String signMessage(String thoughtAdress, String message) throws GenericRpcException
  {
    return (String) query("signmessage", thoughtAdress, message);
  }

  @Override
  public void dumpWallet(String filename) throws GenericRpcException
  {
    query("dumpwallet", filename);
  }

  @Override
  public void importWallet(String filename) throws GenericRpcException
  {
    query("dumpwallet", filename);
  }

  @Override
  public void keyPoolRefill() throws GenericRpcException
  {
    keyPoolRefill(100); // default is 100 if you don't send anything
  }

  public void keyPoolRefill(long size) throws GenericRpcException
  {
    query("keypoolrefill", size);
  }

  @Override
  public BigDecimal getReceivedByAccount(String account) throws GenericRpcException
  {
    return getReceivedByAccount(account, 1);
  }

  public BigDecimal getReceivedByAccount(String account, int minConf) throws GenericRpcException
  {
    return new BigDecimal((String) query("getreceivedbyaccount", account, minConf));
  }

  @Override
  public void encryptWallet(String passPhrase) throws GenericRpcException
  {
    query("encryptwallet", passPhrase);
  }

  @Override
  public void walletPassPhrase(String passPhrase, long timeOut) throws GenericRpcException
  {
    query("walletpassphrase", passPhrase, timeOut);
  }

  @Override
  public boolean verifyMessage(String thoughtAddress, String signature, String message) throws GenericRpcException
  {
    return (boolean) query("verifymessage", thoughtAddress, signature, message);
  }

  @Override
  public String addMultiSigAddress(int nRequired, List<String> keyObject) throws GenericRpcException
  {
    return (String) query("addmultisigaddress", nRequired, keyObject);
  }

  @Override
  public String addMultiSigAddress(int nRequired, List<String> keyObject, String account) throws GenericRpcException
  {
    return (String) query("addmultisigaddress", nRequired, keyObject, account);
  }

  @Override
  public boolean verifyChain()
  {
    return verifyChain(3, 6); // 3 and 6 are the default values
  }

  public boolean verifyChain(int checklevel, int numblocks)
  {
    return (boolean) query("verifychain", checklevel, numblocks);
  }

  /**
   * Attempts to submit new block to network. The 'jsonparametersobject' parameter
   * is currently ignored, therefore left out.
   *
   * @param hexData
   */
  @Override
  public String submitBlock(String hexData)
  {
    return (String) query("submitblock", hexData);
  }

  @Override
  public Transaction getTransaction(String txId)
  {
    return new TransactionWrapper((Map) query("gettransaction", txId));
  }

  @Override
  public TxOut getTxOut(String txId, long vout) throws GenericRpcException
  {
    return new TxOutWrapper((Map) query("gettxout", txId, vout, true));
  }

  public TxOut getTxOut(String txId, long vout, boolean includemempool) throws GenericRpcException
  {
    return new TxOutWrapper((Map) query("gettxout", txId, vout, includemempool));
  }
  
  /**
   * Adding Masternode functions
   */
  private class MasternodeOutputImpl implements MasternodeOutput
  {
    String txid;
    int    vout;
    
    @Override
    public String txid()
    {
      return txid;
    }

    @Override
    public int vout()
    {
      return vout;
    }
    
  }
  
  public List<MasternodeOutput> masternodeOutputs()
  {
    List<MasternodeOutput> retval = new ArrayList<MasternodeOutput>();
    Map results = (Map) query("masternode", "status");
    Set<String> keys = results.keySet();
    for (String key : keys)
    {
      MasternodeOutputImpl moi = new MasternodeOutputImpl();
      moi.txid = key;
      moi.vout = Integer.parseInt(results.get(key).toString());
      retval.add(moi);
    }
    
    return retval;
  }

  private class MasternodeInfoImpl implements MasternodeInfo
  {
    Map map;
    
    public MasternodeInfoImpl(Map map)
    {
      this.map = map;
    }
    
    @Override
    public String address()
    {
      return (String)map.get("address");
    }

    @Override
    public String payee()
    {
      return (String)map.get("payee");
    }

    @Override
    public String status()
    {
      return (String)map.get("status");
    }

    @Override
    public String protocol()
    {
      return (String)map.get("protocol");
    }

    @Override
    public String daemonversion()
    {
      return (String)map.get("daemonversion");
    }

    @Override
    public String sentinelversion()
    {
      return (String)map.get("sentinelversion");
    }

    @Override
    public String sentinelstate()
    {
      return (String)map.get("sentinelstate");
    }

    @Override
    public long lastseen()
    {
      Long l = (Long)map.get("lastseen");
      return null == l ? 0 : l.longValue();
    }

    @Override
    public long activeseconds()
    {
      Long l = (Long)map.get("activeseconds");
      return null == l ? 0 : l.longValue();
    }

    @Override
    public long lastpaidtime()
    {
      Long l = (Long)map.get("lastpaidtime");
      return null == l ? 0 : l.longValue();
    }

    @Override
    public long lastpaidblock()
    {
      Long l = (Long)map.get("lastpaidblock");
      return null == l ? 0 : l.longValue();
    }

    @Override
    public String owneraddress()
    {
      return (String)map.get("owneraddress");
    }

    @Override
    public String votingaddress()
    {
      return (String)map.get("votingaddress");
    }
    
  }
  
  @Override
  public Map<String, MasternodeInfo> masternodeList()
  {
    Map<String, MasternodeInfo> retval = new LinkedHashMap<String, MasternodeInfo>();
    Map results = (Map) query("masternode", "list");
    Set keys = results.keySet();
    for (Object key : keys)
    {
      retval.put((String)key, new MasternodeInfoImpl((Map)results.get(key)));
    }
    return retval;
  }
}
