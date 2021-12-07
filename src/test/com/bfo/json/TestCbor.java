package com.bfo.json;

import java.util.*;
import java.text.*;
import java.io.*;
import java.nio.*;

public class TestCbor {
    public static void main(String[] args) throws Exception {
        System.out.println("----- BEGIN CBOR TESTS -----");
        Json j;
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
        assert (j=read("f97c00")).toString().equals("null");
        assert (j=read("f97e00")).toString().equals("null");
        assert (j=read("f9fc00")).toString().equals("null");
        assert (j=read("fa7f800000")).toString().equals("null");
        assert (j=read("fa7fc00000")).toString().equals("null");
        assert (j=read("faff800000")).toString().equals("null");
        assert (j=read("fb7ff0000000000000")).toString().equals("null");
        assert (j=read("fb7ff8000000000000")).toString().equals("null");
        assert (j=read("fbfff0000000000000")).toString().equals("null");
        assert (j=read("f4")).toString().equals("false");
        assert (j=read("f5")).toString().equals("true");
        assert (j=read("f6")).toString().equals("null");
        assert (j=read("f7")).isNull() && j.getTag() == 23;     // undefined
        assert (j=read("f0")).isNull() && j.getTag() == 16;     // simple(16)
        assert (j=read("f818")).isNull() && j.getTag() == 24;   // simple(24)
        assert (j=read("f8ff")).isNull() && j.getTag() == 255 : j.getTag();   // simple(255)
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
        System.out.println("----- END CBOR TESTS -----");
        System.out.println("----- BEGIN CBOR ROUNDTRIP TESTS -----");
        for (String s : INPUT) {
            j = read(s);
            String s1 = j.toString();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            j.writeCbor(out, null);
            j = Json.readCbor(new ByteArrayInputStream(out.toByteArray()), null);
            String s2 = j.toString();
            assert s1.equals(s1) : s1 + " != " + s2;
        }
        System.out.println("----- END CBOR ROUNDTRIP TESTS -----");

        System.out.println("----- BEGIN PROXY WRITE TESTS -----");
        j = Json.read("{}");
        // Test our "proxyWrite" approach works
        for (int i=0;i<1000;i++) {
            final int c = i;
            final byte[] tmp = new byte[i];
            for (int k=0;k<tmp.length;k++) {
                tmp[k] = (byte)k;
            }
            final Json magic = new Json(ByteBuffer.wrap("bad".getBytes("UTF-8")), null) {
                @Override protected void writeBuffer(OutputStream out) throws IOException {
                    out.write(tmp);
                }
            };
            j.put("foo", magic);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            StringWriter w = new StringWriter();

            j.writeCbor(out, null);
            InputStream in = new ByteArrayInputStream(out.toByteArray());
            Json j2 = Json.readCbor(in, null);
            ByteBuffer b = j2.bufferValue("foo");
            assert Arrays.equals(tmp, b.array()) : "cbor proxy: i="+i+" j="+j+" buf="+hex(b.array());

            j.write(w, null);
            j2 = Json.read(w.toString());
            b = j2.bufferValue("foo");
            assert Arrays.equals(tmp, b.array()) : "json proxy: i="+i+" j="+w+" buf="+hex(b.array());

            out.reset();
            j.writeMsgpack(out, null);
            in = new ByteArrayInputStream(out.toByteArray());
            j2 = Json.readMsgpack(in, null);
            b = j2.bufferValue("foo");
            assert Arrays.equals(tmp, b.array()) : "msgpack proxy: i="+i+" j="+j+" buf="+hex(b.array());
        }
        System.out.println("----- END PROXY WRITE TESTS -----");
    }

    private static List<String> INPUT = new ArrayList<String>();
    static {
        INPUT.addAll(Arrays.asList("00 01 0a 17 1818 1819 1864 1903e8 1a000f4240 1b000000e8d4a51000 1bffffffffffffffff c249010000000000000000 3bffffffffffffffff c349010000000000000000 20 29 3863 3903e7 f90000 f98000 f93c00 fb3ff199999999999a f93e00 f97bff fa47c35000 fa7f7fffff fb7e37e43c8800759c f90001 f90400 f9c400 fbc010666666666666 f97c00 f97e00 f9fc00 fa7f800000 fa7fc00000 faff800000 fb7ff0000000000000 fb7ff8000000000000 fbfff0000000000000 f4 f5 f6 f7 f0 f818 f8ff c074323031332d30332d32315432303a30343a30305a c11a514b67b0 c1fb41d452d9ec200000 d74401020304 d818456449455446 d82076687474703a2f2f7777772e6578616d706c652e636f6d 40 4401020304 60 6161 6449455446 62225c 62c3bc 63e6b0b4 64f0908591 80 83010203 8301820203820405 98190102030405060708090a0b0c0d0e0f101112131415161718181819 a0 a201020304 a26161016162820203 826161a161626163 a56161614161626142616361436164614461656145 5f42010243030405ff 7f657374726561646d696e67ff 9fff 9f018202039f0405ffff 9f01820203820405ff 83018202039f0405ff 83019f0203ff820405 9f0102030405060708090a0b0c0d0e0f101112131415161718181819ff bf61610161629f0203ffff 826161bf61626163ff bf6346756ef563416d7421ff".split(" ")));  // from spec
    }


    private static Json read(String s) throws IOException {
        byte[] b = new byte[s.length() / 2];
        for (int i=0;i<b.length;i++) {
            b[i] = (byte)((Character.digit(s.charAt(i*2), 16) << 4) + Character.digit(s.charAt(i*2 + 1), 16));
        }
        return Json.readCbor(new ByteArrayInputStream(b), null);
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
