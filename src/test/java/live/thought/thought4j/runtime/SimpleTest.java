package live.thought.thought4j.runtime;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import live.thought.thought4j.ThoughtClientInterface;
import live.thought.thought4j.ThoughtClientInterface.AddressBalanceInfo;
import live.thought.thought4j.ThoughtClientInterface.BlockTemplate;
import live.thought.thought4j.ThoughtClientInterface.MasternodeInfo;
import live.thought.thought4j.ThoughtRPCClient;

public class SimpleTest
{
  private static final Logger logger = Logger.getLogger(SimpleTest.class.getCanonicalName());
  static
  {
    logger.setLevel(Level.ALL);
    for (Handler handler : logger.getParent().getHandlers())
      handler.setLevel(Level.ALL);
  }

  public static void main(String[] args) throws Exception
  {
    ThoughtClientInterface b = new ThoughtRPCClient(true);

    Map<String, MasternodeInfo> masternodes = b.masternodeList();
    System.out.println("Masternode count: " + masternodes.size());
    
    //System.out.println(b.getBlockChainInfo());
    System.out.println(b.getMiningInfo());
    //BlockTemplate bl = b.getBlockTemplate();
    //System.out.println(bl);
    //b.submitBlock("0009c8d");
    //System.out.println(b.getBlockTemplate(bl.longpollid()));

    //String aa = "kzSJ2PorYyS5zY6VuMygsSiae7wTBRBm5W";
    //String ab = "mpN3WTJYsrnnWeoMzwTxkp8325nzArxnxN";
    //String ac = b.getNewAddress("TEST");

    //System.out.println(b.getBalance("", 0));
    // System.out.println(b.sendFrom("", ab, 0.1));
    // System.out.println(b.sendToAddress(ab, 0.1, "comment", "tocomment"));
    // System.out.println(b.getReceivedByAddress(ab));
    // System.out.println(b.sendToAddress(ac, 0.01));

    //System.out.println(b.validateAddress(ac));

    // b.importPrivKey(b.dumpPrivKey(aa));

    //System.out.println(b.getAddressesByAccount("TEST"));
    Set<String> addresses = new HashSet<String>();
    addresses.add("kvdPDVw6T6ws8N2fAZiaFMHsJLXWDXtHiq");
    AddressBalanceInfo abi = b.getAddressBalance(addresses);
    System.out.println("Balance:   " + abi.balance());
    System.out.println("Spendable: " + abi.balance_spendable());
    System.out.println("Immature:  " + abi.balance_immature());
    System.out.println("Received:  " + abi.received());
    
  }
}
