package com.bfo.json;

import java.util.*;
import java.text.*;
import java.math.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;

public class TestCbor {
    public static void main(String[] args) throws Exception {
        System.out.println("----- BEGIN APPENDIX-A CBOR TESTS -----");
        System.out.println("  Note tests 18, 19, 20, 22, 23, 25, 27, 28, 29, 31, 32 and 33 are tests of 16-bit precision floats,");
        System.out.println("  which we convert to 32 bit in Java. So they are expected to fail round-trip testing.");
        System.out.println("  Also notes tests 44, 45, 46 involve Cbor \"simple\" types which we have no object for, so we");
        System.out.println("  convert to tagged-undefined objects. Finally test 71 is an indefinite length buffer which we convert");
        System.out.println("  to definite, so the diagnostic will fail there too");
        InputStream in = TestCbor.class.getResourceAsStream("resources/appendix_a.json");         // https://github.com/cbor/test-vectors
        Json j = Json.read(new JsonReader().setInput(in));
        in.close();
        for (int i=0;i<j.size();i++) {
            Json test = j.get(i);
            byte[] hex1 = readHex(test.stringValue("hex"));
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            new CborReader().setDraining().setFinal().setInput(ByteBuffer.wrap(hex1)).write(new CborWriter().setOutput(bout));
            byte[] hex2 = bout.toByteArray();
            if (!Arrays.equals(hex1, hex2)) {
                System.out.println("Test " + i + ": read direct to writer: FAIL (input="+hex(hex1)+" output="+hex(hex2)+" test="+test+")"); // Note tests 44,45,46 will fail
            }
            Json cbor = read(test.stringValue("hex"));
            if (test.booleanValue("roundtrip")) {
                String hex = write(cbor);
                if (!hex.equalsIgnoreCase(test.stringValue("hex"))) {
                    System.out.println("Test " + i + ": roundtrip via Json object: FAIL (input="+test.stringValue("hex")+" output="+hex+")");
                }
            }
            if (test.get("decoded") != null) {
                String s1 = cbor.toString();
                String s2 = test.get("decoded").toString();
                if (!s1.equals(s2)) {
                    System.out.println("Test " + i + ": decode FAIL: (expected " + s2 + " got " + s1 + ")");
                }
            } else {
                String s1 = cbor.toString(new JsonWriter().setCborDiag("HEX"));
                String s2 = test.isNull("diagnostic") ? "null" : test.stringValue("diagnostic");
                if (!s1.equals(s2)) {
                    System.out.println("Test " + i + ": diagnostic FAIL (expected " + s2 + " got " + s1 + ")");
                }
            }
        }
        System.out.println("----- END APPENDIX-A CBOR TESTS -----");

        System.out.println("----- BEGIN CBOR TESTS -----");
        j = null;
        String tostring = null;
        assert (j=read("00")).toString().equals("0") && j.getTag() < 0;
        assert (j=read("01")).toString().equals("1");
        assert (j=read("0a")).toString().equals("10");
        assert (j=read("17")).toString().equals("23");
        assert (j=read("1818")).toString().equals("24");
        assert (j=read("1819")).toString().equals("25");
        assert (j=read("1864")).toString().equals("100");
        assert (j=read("1903e8")).toString().equals("1000");
        assert (j=read("1a000f4240")).toString().equals("1000000");
        assert (j=read("1b000000e8d4a51000")).toString().equals("1000000000000");
        assert (j=read("1bffffffffffffffff")).toString().equals("18446744073709551615");
        assert (j=read("c249010000000000000000")).toString().equals("18446744073709551616");
        assert (j=read("3bffffffffffffffff")).toString().equals("-18446744073709551616");
        assert (j=read("c349010000000000000000")).toString().equals("-18446744073709551617");
        assert (j=read("20")).toString().equals("-1");
        assert (j=read("29")).toString().equals("-10");
        assert (j=read("3863")).toString().equals("-100");
        assert (j=read("3903e7")).toString().equals("-1000");
        assert (j=read("f90000")).toString().equals("0");
        assert (j=read("f98000")).toString().equals("-0");
        assert (j=read("f93c00")).toString().equals("1");
        assert (j=read("fb3ff199999999999a")).toString().equals("1.1");
        assert (j=read("f93e00")).toString().equals("1.5");
        assert (j=read("f97bff")).toString().equals("65504");
        assert (j=read("fa47c35000")).toString().equals("100000");
        assert (j=read("fa7f7fffff")).toString().equals("3.4028235e+38");
        assert (j=read("fb7e37e43c8800759c")).toString().equals("1e+300");
        assert (j=read("f90001")).toString().equals("5.9604645e-08");
        assert (j=read("f90400")).toString().equals("6.1035156e-05");
        assert (j=read("f9c400")).toString().equals("-4");
        assert (j=read("fbc010666666666666")).toString().equals("-4.1");
        assert (j=read("f97c00")).numberValue().equals(Float.POSITIVE_INFINITY);        // 16-bit +inf
        assert Float.isNaN((j=read("f97e00")).numberValue().floatValue());              // 16-bit NaN
        assert (j=read("f9fc00")).numberValue().equals(Float.NEGATIVE_INFINITY);        // 16-bit -inf
        assert (j=read("fa7f800000")).numberValue().equals(Float.POSITIVE_INFINITY);    // 32-bit +inf
        assert Float.isNaN((j=read("fa7fc00000")).numberValue().floatValue());          // 32-bit NaN
        assert (j=read("faff800000")).numberValue().equals(Float.NEGATIVE_INFINITY);    // 32-bit -inf
        assert (j=read("fb7ff0000000000000")).numberValue().equals(Double.POSITIVE_INFINITY);    // 64-bit +inf
        assert Double.isNaN((j=read("fb7ff8000000000000")).numberValue().doubleValue());          // 64-bit NaN
        assert (j=read("fbfff0000000000000")).numberValue().equals(Double.NEGATIVE_INFINITY);    // 64-bit -inf
        assert (j=read("f4")).toString().equals("false");
        assert (j=read("f5")).toString().equals("true");
        assert (j=read("f6")).toString().equals("null");
        assert (j=read("f7")).isUndefined() && j.getTag() < 0;     // undefined
        assert (j=read("f0")).isUndefined() && j.getTag() == 16;     // simple(16)
        assert (j=read("f818")).isUndefined() && j.getTag() == 24;   // simple(24)
        assert (j=read("f8ff")).isUndefined() && j.getTag() == 255 : j.getTag();   // simple(255)
        assert (j=read("c074323031332d30332d32315432303a30343a30305a")).toString().equals("\"2013-03-21T20:04:00Z\"") && j.getTag() == 0 : j;
        assert (j=read("c11a514b67b0")).toString().equals("1363896240") && j.getTag() == 1;
        assert (j=read("c1fb41d452d9ec200000")).toString().equals("1363896240.5") && j.getTag() == 1;
        assert (j=read("d74401020304")).toString().equals("\"AQIDBA\"") && j.getTag() == 23;
        assert (j=read("d818456449455446")).toString().equals("\"ZElFVEY\"") && j.getTag() == 24;
        assert (j=read("d82076687474703a2f2f7777772e6578616d706c652e636f6d")).toString().equals("\"http://www.example.com\"") && j.getTag() == 32;
        assert (j=read("40")).toString().equals("\"\"");
        assert (j=read("4161")).toString().equals("\"YQ\"") && j.bufferValue().limit() == 1 : j.toString();
        assert (j=read("426162")).toString().equals("\"YWI\"") && j.bufferValue().limit() == 2 : j.toString();
        assert (j=read("43616263")).toString().equals("\"YWJj\"") && j.bufferValue().limit() == 3 : j.toString();
        assert (j=read("4401020304")).toString().equals("\"AQIDBA\"") && j.bufferValue().limit() == 4 : j.toString();
        assert (j=read("60")).toString().equals("\"\"");
        assert (j=read("6161")).toString().equals("\"a\"");
        assert (j=read("6449455446")).toString().equals("\"IETF\"");
        assert (j=read("62225c")).toString().equals("\"\\\"\\\\\"") : j.toString();
        assert (j=read("62c3bc")).toString().equals("\"ü\"");
        assert (j=read("63e6b0b4")).toString().equals("\"水\"");
        assert (j=read("64f0908591")).toString().equals("\"𐅑\"") : (int)j.toString().charAt(1);
        assert (j=read("80")).toString().equals("[]");
        assert (j=read("83010203")).toString().equals("[1,2,3]");
        assert (j=read("8301820203820405")).toString().equals("[1,[2,3],[4,5]]");
        assert (j=read("98190102030405060708090a0b0c0d0e0f101112131415161718181819")).toString().equals("[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25]");
        assert (j=read("a0")).toString().equals("{}");
        assert (j=read("a201020304")).toString().equals("{\"1\":2,\"3\":4}");
        assert (j=read("a26161016162820203")).toString().equals("{\"a\":1,\"b\":[2,3]}");
        assert (j=read("826161a161626163")).toString().equals("[\"a\",{\"b\":\"c\"}]");
        assert (j=read("a56161614161626142616361436164614461656145")).toString().equals("{\"a\":\"A\",\"b\":\"B\",\"c\":\"C\",\"d\":\"D\",\"e\":\"E\"}");
        assert (j=read("5f42010243030405ff")).toString().equals("\"AQIDBAU\"");
        assert (j=read("7f657374726561646d696e67ff")).toString().equals("\"streaming\"");
        assert (j=read("9fff")).toString().equals("[]");
        assert (j=read("9f018202039f0405ffff")).toString().equals("[1,[2,3],[4,5]]");
        assert (j=read("9f01820203820405ff")).toString().equals("[1,[2,3],[4,5]]");
        assert (j=read("83018202039f0405ff")).toString().equals("[1,[2,3],[4,5]]");
        assert (j=read("83019f0203ff820405")).toString().equals("[1,[2,3],[4,5]]");
        assert (j=read("9f0102030405060708090a0b0c0d0e0f101112131415161718181819ff")).toString().equals("[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25]");
        assert (j=read("bf61610161629f0203ffff")).toString().equals("{\"a\":1,\"b\":[2,3]}");
        assert (j=read("826161bf61626163ff")).toString().equals("[\"a\",{\"b\":\"c\"}]");
        assert (j=read("bf6346756ef563416d7421ff")).toString().equals("{\"Fun\":true,\"Amt\":-2}");
        // Next ones feature integer keys, from example at 
        assert (j=read("a10126")).get(1).intValue() == -7;
        assert (j=read("a10126")).get("1") == null;
        assert (tostring=(j=read("D8628440A054546869732069732074686520636F6E74656E742E818343A10126A1044231315840E2AEAFD40D69D19DFE6E52077C5D7FF4E408282CBEFB5D06CBF414AF2E19D982AC45AC98B8544C908B4507DE1E90B717C3D34816FE926A2B98F53AFD2FA0F30A")).toString(new JsonWriter().setCborDiag("HEX"))).equals("98([h'',{},h'546869732069732074686520636F6E74656E742E',[[h'A10126',{4:h'3131'},h'E2AEAFD40D69D19DFE6E52077C5D7FF4E408282CBEFB5D06CBF414AF2E19D982AC45AC98B8544C908B4507DE1E90B717C3D34816FE926A2B98F53AFD2FA0F30A']]])") : tostring;

        System.out.println("----- END CBOR TESTS -----");
        System.out.println("----- BEGIN CBOR BIGNUMBER TESTS -----");
        assert (j=read("f97e00")).isNumber() && Double.isNaN(j.doubleValue());
        assert (j=read("f97c00")).isNumber() && j.doubleValue() == Double.POSITIVE_INFINITY;
        assert (j=read("f9fc00")).isNumber() && j.doubleValue() == Double.NEGATIVE_INFINITY;
        assert (j=read("c24100")).isNumber() && j.doubleValue() == 0;
        assert (j=read("c24101")).isNumber() && j.doubleValue() == 1;
        assert (j=read("c34100")).isNumber() && j.doubleValue() == -1;
        assert (j=read("c24e03ffffffffffffc0000000000001")).isNumber() && j.numberValue() instanceof BigInteger && ((BigInteger)j.numberValue()).equals(BigInteger.valueOf(9007199254740991l).pow(2));
        assert (j=read("c482201865")).isNumber() && j.numberValue() instanceof BigDecimal && j.numberValue().toString().equals("10.1") : j.numberValue().toString()+"="+j.numberValue().doubleValue();
        assert (j=read("c482201903e9")).isNumber() && j.numberValue() instanceof BigDecimal && j.numberValue().toString().equals("100.1") : j.numberValue().toString();
        assert (j=read("c4822001")).isNumber() && j.numberValue() instanceof BigDecimal && j.numberValue().toString().equals("0.1") : j.numberValue().toString();
        assert (j=read("c4822020")).isNumber() && j.numberValue() instanceof BigDecimal && j.numberValue().toString().equals("-0.1") : j.numberValue().toString();
        assert (j=read("c482382cc254056e5e99b1be81b6eefa3964490ac18c69399361")).isNumber() && j.numberValue() instanceof BigDecimal && j.numberValue().equals(BigDecimal.valueOf(Math.PI).pow(3)) : j.numberValue().toString();
        assert (j=read("c5822003")).isNumber() && j.numberValue() instanceof BigDecimal && j.numberValue().toString().equals("1.5") : j.numberValue().toString();
        assert (j=read("c48220c24909fffffffffffffff7")).isNumber() && j.numberValue() instanceof BigDecimal && j.numberValue().toString().equals("18446744073709551615.1");
        assert (tostring=write(new Json(new BigDecimal(BigInteger.valueOf(1001), 1)))).equals("C482201903E9") : tostring;       // 1001 / (10^1) == 100.1
        System.out.println("----- END CBOR BIGNUMBER TESTS -----");
        System.out.println("----- BEGIN CBOR ROUNDTRIP TESTS -----");
        for (String s : INPUT) {
            j = read(s);
            String s1 = j.toString(new JsonWriter().setCborDiag("HEX"));
            ByteBuffer buf = j.toCbor();
            j = Json.readCbor(buf);
            String s2 = j.toString(new JsonWriter().setCborDiag("HEX"));
            assert s1.equals(s1) : s1 + " != " + s2;
        }
        System.out.println("----- END CBOR ROUNDTRIP TESTS -----");

        System.out.println("----- BEGIN PROXY WRITE TESTS -----");
        j = Json.read("{}");
        // Test our "proxyWrite" approach works
        for (int i=0;i<1000;i++) {
            final int c = i;
            final byte[] targetbytes = new byte[i];
            for (int k=0;k<targetbytes.length;k++) {
                targetbytes[k] = (byte)((k % 26) + 'A');
            }
            final String targetstring = new String(targetbytes, "ISO-8859-1");
            final Json magicBuffer = new Json(ByteBuffer.wrap("bad".getBytes("UTF-8"))) {
                @Override public ReadableByteChannel getBufferStream() throws IOException {
                    return Channels.newChannel(new ByteArrayInputStream(targetbytes));
                }
                @Override public long getBufferStreamLength() throws IOException {
                    return targetbytes.length;
                }
            };
            final Json magicString = new Json("bad") {
                @Override public Readable getStringStream() throws IOException {
                    return new StringReader(targetstring);
                }
                @Override public long getStringStreamByteLength() throws IOException {
                    return -1;
                }
            };
            j.put("buffer", magicBuffer);
            j.put("string", magicString);

            ByteBuffer testbuf;
            String teststring;
            Json j2;

            ByteBuffer buf = j.toCbor();
            j2 = Json.readCbor(buf);
            testbuf = j2.bufferValue("buffer");
            assert Arrays.equals(targetbytes, testbuf.array()) : "cbor proxy buffer: i="+i+" j="+j+" buf="+hex(testbuf.array());
            teststring = j2.stringValue("string");
            assert teststring.equals(targetstring) : "cbor proxy string: i="+i+" j="+j+" s="+teststring;

            String w = j.toString();
            j2 = Json.read(w);
            testbuf = j2.bufferValue("buffer");
            assert Arrays.equals(targetbytes, testbuf.array()) : "json proxy buffer: i="+i+" j="+j+" buf="+hex(testbuf.array());
            teststring = j2.stringValue("string");
            assert teststring.equals(targetstring) : "json proxy string: i="+i+" j="+j+" s="+teststring;

            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            j.write(new MsgpackWriter().setOutput(bout));
            buf = ByteBuffer.wrap(bout.toByteArray());
            j2 = Json.read(new MsgpackReader().setInput(buf));
            testbuf = j2.bufferValue("buffer");
            assert Arrays.equals(targetbytes, testbuf.array()) : "msgpack proxy buffer: i="+i+" j="+j+" buf="+hex(testbuf.array());
            teststring = j2.stringValue("string");
            assert teststring.equals(targetstring) : "msgpack proxy string: i="+i+" j="+j+" s="+teststring;
        }
        System.out.println("----- END PROXY WRITE TESTS -----");
    }

    private static List<String> INPUT = new ArrayList<String>();
    static {
        INPUT.addAll(Arrays.asList("00 01 0a 17 1818 1819 1864 1903e8 1a000f4240 1b000000e8d4a51000 1bffffffffffffffff c249010000000000000000 3bffffffffffffffff c349010000000000000000 20 29 3863 3903e7 f90000 f98000 f93c00 fb3ff199999999999a f93e00 f97bff fa47c35000 fa7f7fffff fb7e37e43c8800759c f90001 f90400 f9c400 fbc010666666666666 f97c00 f97e00 f9fc00 fa7f800000 fa7fc00000 faff800000 fb7ff0000000000000 fb7ff8000000000000 fbfff0000000000000 f4 f5 f6 f7 f0 f818 f8ff c074323031332d30332d32315432303a30343a30305a c11a514b67b0 c1fb41d452d9ec200000 d74401020304 d818456449455446 d82076687474703a2f2f7777772e6578616d706c652e636f6d 40 4401020304 60 6161 6449455446 62225c 62c3bc 63e6b0b4 64f0908591 80 83010203 8301820203820405 98190102030405060708090a0b0c0d0e0f101112131415161718181819 a0 a201020304 a26161016162820203 826161a161626163 a56161614161626142616361436164614461656145 5f42010243030405ff 7f657374726561646d696e67ff 9fff 9f018202039f0405ffff 9f01820203820405ff 83018202039f0405ff 83019f0203ff820405 9f0102030405060708090a0b0c0d0e0f101112131415161718181819ff bf61610161629f0203ffff 826161bf61626163ff bf6346756ef563416d7421ff".split(" ")));  // from spec
        // From COSE spec (C1_1)
        INPUT.add("a10126");
        INPUT.add("D8628440A054546869732069732074686520636F6E74656E742E818343A10126A1044231315840E2AEAFD40D69D19DFE6E52077C5D7FF4E408282CBEFB5D06CBF414AF2E19D982AC45AC98B8544C908B4507DE1E90B717C3D34816FE926A2B98F53AFD2FA0F30A");
        INPUT.add("c482201903e9");      // bignumber
        INPUT.add("c5822003");
    }


    private static String write(Json j) throws IOException {
        ByteBuffer buf = j.toCbor();
        return hex(buf.array());
    }

    private static byte[] readHex(String s) throws IOException {
        byte[] b = new byte[s.length() / 2];
        for (int i=0;i<b.length;i++) {
            b[i] = (byte)((Character.digit(s.charAt(i*2), 16) << 4) + Character.digit(s.charAt(i*2 + 1), 16));
        }
        return b;
    }

    private static Json read(String s) throws IOException {
        return Json.readCbor(ByteBuffer.wrap(readHex(s)));
    }

    private static String hex(byte[] in) {
        char[] c = new char[in.length * 2];
        for (int i=0;i<in.length;i++) {
            int v = in[i] & 0xFF;
            int q = v >> 4;
            c[i*2] = (char)(q < 10 ? q + '0' : q + 'A' - 10);
            q = v & 0xF;
            c[i*2+1] = (char)(q < 10 ? q + '0' : q + 'A' - 10);
        }
        return new String(c);
    }

}
