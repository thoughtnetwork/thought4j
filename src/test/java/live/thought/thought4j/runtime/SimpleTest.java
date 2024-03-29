package live.thought.thought4j.runtime;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import live.thought.thought4j.ThoughtClientInterface;
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

    //Map<String, MasternodeInfo> masternodes = b.masternodeList();
    //System.out.println("Masternode count: " + masternodes.size());
    
    //System.out.println(b.getBlockChainInfo());
    //System.out.println(b.getMiningInfo());
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
    ThoughtClientInterface.BasicTxInput basicTxInput = new ThoughtClientInterface.BasicTxInput("d511fbbfa09e5ddbbd4cdd0d79aa2135b52415c8986d84729cb3874c62776423", 1);
    List<ThoughtClientInterface.BasicTxInput> inputSet = Collections.singletonList(basicTxInput);
    System.out.println(b.lockunspent(false, inputSet));
    
  }
}
