/*
 * thought4j Java RPC Client library for the Thought Network
 * 
 * Copyright (c) 2018, Thought Network LLC
 * 
 * Based on code from the Bitcoin-JSON-RPC-Client
 * Created by fpeters on 11-01-17.
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

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

import live.thought.thought4j.util.JSON;


public class ThoughtRPCClientTest {

    class MyClientTest extends ThoughtRPCClient {

        String expectedMethod;
        Object[] expectedObject;
        String result;

        MyClientTest(boolean testNet, String expectedMethod, Object[] expectedObject, String result) {
            super(testNet);
            this.expectedMethod = expectedMethod;
            this.expectedObject = expectedObject;
            this.result = result;
        }

        @Override
        public Object query(String method, Object... o) throws GenericRpcException {
            if(method!=expectedMethod) {
                throw new GenericRpcException("wrong method");
            }
            if(o.equals(expectedObject)){
                throw new GenericRpcException("wrong object");
            }
            return JSON.parse(result);
        }
    }

    MyClientTest client;

    @Test
    public void signRawTransactionTest() throws Exception {
        client = new MyClientTest(false, "signrawtransaction", null,
                                    "{\n" +
                                            "  \"hex\": \"0100000001b8b2244faca910c1ffff24ecd2b559b4699338398bf77e4cb1fdeb19ad419ea0010000006b483045022100b68b7fe9cfabb32949af6747b6769dffcf2aa4170e4df2f0e9d0a4571989e94e02204cf506c210cdb6b6b4413bf251a0b57ebcf1b1b2d303ba6183239b557ef0a310012102ab46e1d7b997d8094e97bc06a21a054c2ef485fac512e2dc91eb9831af55af4effffffff012e2600000000000017a9140b2d7ed4e5076383ba8e98b9b3bce426b7a2ea1e8700000000\",\n" +
                                            "  \"complete\": true\n" +
                                            "}\n");
        LinkedList<ThoughtClientInterface.ExtendedTxInput> inputList = new LinkedList<ThoughtClientInterface.ExtendedTxInput>();
        LinkedList<String> privateKeys = new LinkedList<String>();
        privateKeys.add("cSjzx3VAM1r9iLXLvL6N61oS3zKns9Z9DcocrbkEzesPTDHWm5r4");
        String hex = client.signRawTransaction("0100000001B8B2244FACA910C1FFFF24ECD2B559B4699338398BF77E4CB1FDEB19AD419EA0010000001976A9144CB4C3B90994FEF58FABB6D8368302E917C6EFB188ACFFFFFFFF012E2600000000000017A9140B2D7ED4E5076383BA8E98B9B3BCE426B7A2EA1E8700000000",
                                                inputList, privateKeys, "ALL");
        assertEquals("0100000001b8b2244faca910c1ffff24ecd2b559b4699338398bf77e4cb1fdeb19ad419ea0010000006b483045022100b68b7fe9cfabb32949af6747b6769dffcf2aa4170e4df2f0e9d0a4571989e94e02204cf506c210cdb6b6b4413bf251a0b57ebcf1b1b2d303ba6183239b557ef0a310012102ab46e1d7b997d8094e97bc06a21a054c2ef485fac512e2dc91eb9831af55af4effffffff012e2600000000000017a9140b2d7ed4e5076383ba8e98b9b3bce426b7a2ea1e8700000000",
                    hex);
    }

    @Test
    public void signRawTransactionTestException() throws Exception {
        client = new MyClientTest(false, "signrawtransaction", null,
                "{\n" +
                        "  \"hex\": \"0100000001b8b2244faca910c1ffff24ecd2b559b4699338398bf77e4cb1fdeb19ad419ea00100000000ffffffff012e2600000000000017a9140b2d7ed4e5076383ba8e98b9b3bce426b7a2ea1e8700000000\",\n" +
                        "  \"complete\": false,\n" +
                        "  \"errors\": [\n" +
                        "    {\n" +
                        "      \"txid\": \"a09e41ad19ebfdb14c7ef78b39389369b459b5d2ec24ffffc110a9ac4f24b2b8\",\n" +
                        "      \"vout\": 1,\n" +
                        "      \"scriptSig\": \"\",\n" +
                        "      \"sequence\": 4294967295,\n" +
                        "      \"error\": \"Operation not valid with the current stack size\"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}");
        LinkedList<ThoughtClientInterface.ExtendedTxInput> inputList = new LinkedList<ThoughtClientInterface.ExtendedTxInput>();
        LinkedList<String> privateKeys = new LinkedList<String>();
        try {
            client.signRawTransaction("0100000001B8B2244FACA910C1FFFF24ECD2B559B4699338398BF77E4CB1FDEB19AD419EA0010000001976A9144CB4C3B90994FEF58FABB6D8368302E917C6EFB188ACFFFFFFFF012E2600000000000017A9140B2D7ED4E5076383BA8E98B9B3BCE426B7A2EA1E8700000000",
                    inputList, privateKeys, "ALL");
        }
        catch(Exception e) {
            assertThat(e.getMessage(), is("Incomplete"));
        }
    }

    @Test
    public void signRawTransactionTest2() throws Exception {
        client = new MyClientTest(false, "signrawtransaction", null,
                "{\n" +
                        "  \"hex\": \"0100000001b8b2244faca910c1ffff24ecd2b559b4699338398bf77e4cb1fdeb19ad419ea0010000006b483045022100b68b7fe9cfabb32949af6747b6769dffcf2aa4170e4df2f0e9d0a4571989e94e02204cf506c210cdb6b6b4413bf251a0b57ebcf1b1b2d303ba6183239b557ef0a310012102ab46e1d7b997d8094e97bc06a21a054c2ef485fac512e2dc91eb9831af55af4effffffff012e2600000000000017a9140b2d7ed4e5076383ba8e98b9b3bce426b7a2ea1e8700000000\",\n" +
                        "  \"complete\": true\n" +
                        "}\n");
        String hex = client.signRawTransaction("0100000001B8B2244FACA910C1FFFF24ECD2B559B4699338398BF77E4CB1FDEB19AD419EA0010000001976A9144CB4C3B90994FEF58FABB6D8368302E917C6EFB188ACFFFFFFFF012E2600000000000017A9140B2D7ED4E5076383BA8E98B9B3BCE426B7A2EA1E8700000000");
        assertEquals("0100000001b8b2244faca910c1ffff24ecd2b559b4699338398bf77e4cb1fdeb19ad419ea0010000006b483045022100b68b7fe9cfabb32949af6747b6769dffcf2aa4170e4df2f0e9d0a4571989e94e02204cf506c210cdb6b6b4413bf251a0b57ebcf1b1b2d303ba6183239b557ef0a310012102ab46e1d7b997d8094e97bc06a21a054c2ef485fac512e2dc91eb9831af55af4effffffff012e2600000000000017a9140b2d7ed4e5076383ba8e98b9b3bce426b7a2ea1e8700000000",
                    hex);
    }

    @Test
    public void lockunspentTest() throws Exception {
        client = new MyClientTest(true, "lockunspent", null,
                "true");
        ThoughtClientInterface.BasicTxInput basicTxInput = new ThoughtClientInterface.BasicTxInput("46dfc2f86fd72b8470456dcb1c582c7bd81b1d82fd8f5e25bba143e11ca123d9", 0);
        List<ThoughtClientInterface.BasicTxInput> inputSet = Collections.singletonList(basicTxInput);
        boolean bool = client.lockunspent(false, inputSet);
        assertEquals("true", String.valueOf(bool));
    }

    @Test
    public void listlockunspentTest() throws Exception {
        client = new MyClientTest(true, "listlockunspent", null,
                "[\n" +
                        "  {\n" +
                        "    \"txid\": \"210789515218c170ab441965dd92a8a9afc64959da1974c576108cef086a9e2c\",\n" +
                        "    \"vout\": 0\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"txid\": \"cd08e9d1b8b3b3aac59d41f97939691e9076e441ef2bb58dfe172fb0454b432e\",\n" +
                        "    \"vout\": 0\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"txid\": \"725d9f4b31bd53fca87c5528ced1ebc1df2b893e3521917587fe15793ddb445c\",\n" +
                        "    \"vout\": 0\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"txid\": \"554269ff92caca96768c4e962463e6d42692fcf2f3bfc4ef8ed3af4d8bc811a3\",\n" +
                        "    \"vout\": 0\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"txid\": \"bdeb93b5a0bd8d5c31ac16723ce9519e46b182d974153f8b1130a6b2192fb4b0\",\n" +
                        "    \"vout\": 0\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"txid\": \"dd84c039f29012e0a438c882fc7789b39872b6bb4d96b05cc90b0bff3bcd9bc5\",\n" +
                        "    \"vout\": 0\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"txid\": \"52bedf25cdf864275925f61289c0b2a28f03ce5af7de78bca94059b32f0641cb\",\n" +
                        "    \"vout\": 0\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"txid\": \"431fd51f9b9c090b50ff9204a40342da5de0fad8ff2d4713a6bde9f00ec40ecd\",\n" +
                        "    \"vout\": 0\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"txid\": \"e28dc9af5edc28712b197a3deaf12313df661321c1018fa5fd2bda0e0ea116ce\",\n" +
                        "    \"vout\": 0\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"txid\": \"b938131ff0ed9b940a5aa94c434dd34403436b92dc23da5d94002d7a287769d3\",\n" +
                        "    \"vout\": 0\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"txid\": \"2d5bde2a8067c117fdabcac48b5799fcbd0b7e4b38fdce484c2c4abef490dfe1\",\n" +
                        "    \"vout\": 0\n" +
                        "  }\n" +
                        "]\n");
        List<ThoughtClientInterface.LockedUnspent> listLockedUnspents = client.listlockunspent();
        Iterator<ThoughtClientInterface.LockedUnspent> iterator = listLockedUnspents.iterator();

        StringBuilder lockedUnspent = new StringBuilder();
        lockedUnspent.append("[\n");
        while (iterator.hasNext()) {
            ThoughtClientInterface.LockedUnspent lunspent = iterator.next();
            lockedUnspent.append("  " + "{\n");
            lockedUnspent.append("    " + "\"txid\": " + "\"" + lunspent.txid() +"\"" + ",\n");
            lockedUnspent.append("    " + "\"vout\": " + lunspent.vout() + "\n");
            if (iterator.hasNext()) {
                lockedUnspent.append("  " + "},\n");
            } else{
                lockedUnspent.append("  " + "}\n");
                lockedUnspent.append("]\n");
            }

        }

        String output = new String(lockedUnspent);

        assertEquals("[\n" +
                "  {\n" +
                "    \"txid\": \"210789515218c170ab441965dd92a8a9afc64959da1974c576108cef086a9e2c\",\n" +
                "    \"vout\": 0\n" +
                "  },\n" +
                "  {\n" +
                "    \"txid\": \"cd08e9d1b8b3b3aac59d41f97939691e9076e441ef2bb58dfe172fb0454b432e\",\n" +
                "    \"vout\": 0\n" +
                "  },\n" +
                "  {\n" +
                "    \"txid\": \"725d9f4b31bd53fca87c5528ced1ebc1df2b893e3521917587fe15793ddb445c\",\n" +
                "    \"vout\": 0\n" +
                "  },\n" +
                "  {\n" +
                "    \"txid\": \"554269ff92caca96768c4e962463e6d42692fcf2f3bfc4ef8ed3af4d8bc811a3\",\n" +
                "    \"vout\": 0\n" +
                "  },\n" +
                "  {\n" +
                "    \"txid\": \"bdeb93b5a0bd8d5c31ac16723ce9519e46b182d974153f8b1130a6b2192fb4b0\",\n" +
                "    \"vout\": 0\n" +
                "  },\n" +
                "  {\n" +
                "    \"txid\": \"dd84c039f29012e0a438c882fc7789b39872b6bb4d96b05cc90b0bff3bcd9bc5\",\n" +
                "    \"vout\": 0\n" +
                "  },\n" +
                "  {\n" +
                "    \"txid\": \"52bedf25cdf864275925f61289c0b2a28f03ce5af7de78bca94059b32f0641cb\",\n" +
                "    \"vout\": 0\n" +
                "  },\n" +
                "  {\n" +
                "    \"txid\": \"431fd51f9b9c090b50ff9204a40342da5de0fad8ff2d4713a6bde9f00ec40ecd\",\n" +
                "    \"vout\": 0\n" +
                "  },\n" +
                "  {\n" +
                "    \"txid\": \"e28dc9af5edc28712b197a3deaf12313df661321c1018fa5fd2bda0e0ea116ce\",\n" +
                "    \"vout\": 0\n" +
                "  },\n" +
                "  {\n" +
                "    \"txid\": \"b938131ff0ed9b940a5aa94c434dd34403436b92dc23da5d94002d7a287769d3\",\n" +
                "    \"vout\": 0\n" +
                "  },\n" +
                "  {\n" +
                "    \"txid\": \"2d5bde2a8067c117fdabcac48b5799fcbd0b7e4b38fdce484c2c4abef490dfe1\",\n" +
                "    \"vout\": 0\n" +
                "  }\n" +
                "]\n", output);
    }
}