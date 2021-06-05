package com.bfo.json;

import java.util.*;
import java.text.*;
import java.math.*;
import java.io.*;

public class TestMsgpack {
    public static void main(String[] args) throws Exception {
        System.out.println("----- BEGIN MSGPACK TESTS -----");
        Json all = Json.read(TestMsgpack.class.getResourceAsStream("resources/msgpack-test-suite.json"), null);
        for (Map.Entry<String,Json> e : all.mapValue().entrySet()) {
            Json j = e.getValue();
            for (int i=0;i<j.size();i++) {
                Json j2 = j.get(i);
                String type = j2.mapValue().keySet().iterator().next();
                Json target = parseType(type, j2.get(type));
                if (target != null) {
                    j2 = j2.get("msgpack");
                    for (int i2=0;i2<j2.size();i2++) {
                        byte[] b = parseBinary(j2.get(i2));
                        Json test = Json.readMsgpack(new ByteArrayInputStream(b), null);
                        assert test.equals(target) : "FAIL: "+type+" "+target+" "+test;
                    }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    target.writeMsgpack(out, null);
                    out.close();
                    byte[] p = out.toByteArray();
                    boolean match = false;
                    for (int i2=0;i2<j2.size();i2++) {
                        byte[] b = parseBinary(j2.get(i2));
                        if (Arrays.equals(b, p)) {
                            match = true;
                            break;
                        }
                    }
                    assert match : "WFAIL: "+type+" "+target+" "+dump(p)+" "+j2;
                } else {
//                    System.out.println("SKIP: "+type);
                }
            }
        }
        System.out.println("----- END MSGPACK TESTS -----");

        System.out.println("----- BEGIN MSGPACK ROUNDTRIP -----");
        Json json = Json.read(args.length == 0 ? TestMsgpack.class.getResourceAsStream("resources/twitter.json") : new FileInputStream(args[0]), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        json.writeMsgpack(out, null);
        out.close();
        Json json2 = Json.readMsgpack(new ByteArrayInputStream(out.toByteArray()), null);
        assert json.equals(json2) : "mismatch";
        System.out.println("----- END MSGPACK ROUNDTRIP -----");
    }

    static String dump(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 3);
        for (int i=0;i<b.length;i++) {
            if (i > 0) {
                sb.append('-');
            }
            int j = b[i] & 0xFF;
            if (j < 0x10) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(j));
        }
        return sb.toString();
    }

    static byte[] parseBinary(Json j) {
        String s = j.stringValue();
        byte[] out = new byte[(s.length() + 1) / 3];
        for (int i=0;i<s.length();i+=3) {
            out[i/3] = (byte)Integer.parseInt(s.substring(i, i+2), 16);
        }
        return out;
    }

    static Json parseType(String type, Json j) {
        if (type.equals("nil")) {
            return j;
        } else if (type.equals("bool")) {
            return j;
        } else if (type.equals("binary")) {
            return new Json(parseBinary(j));
        } else if (type.equals("number")) {
            return j;
        } else if (type.equals("bignum")) {
            return new Json(new BigInteger(j.stringValue()));
        } else if (type.equals("string")) {
            return j;
        } else if (type.equals("array")) {
            return j;
        } else if (type.equals("map")) {
            return j;
        } else if (type.equals("ext")) {
            Json j2 = new Json(parseBinary(j.get(1)));
            j2.setTag(j.get(0).intValue());
            return j2;
        }
        return null;
    }

}
