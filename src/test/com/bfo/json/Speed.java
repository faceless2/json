package com.bfo.json;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.nio.file.*;

/**
 * Tests from http://seriot.ch/parsing_json.php
 */
class Speed {
    public static void main(String[] args) throws Exception {
        System.out.println("----- BEGIN SPEED TESTS -----");
        BufferedReader r = null;
        final int PASS = 20;
        try {
            r = new BufferedReader(new InputStreamReader(Speed.class.getResourceAsStream("resources/speedlist.txt"), "UTF-8"));
            String s;
            while ((s=r.readLine()) != null) {
                s = s.replaceAll("#.*", "").trim();
                if (s.length() > 0) {
                    InputStream in = null;
                    try {
                        URL url = new URL(s);
                        in = url.openConnection().getInputStream();
                        int c;
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        while ((c=in.read())>=0) {
                            out.write(c);
                        }
                        out.close();
                        byte[] buf = out.toByteArray();
                        String string = new String(buf, "UTF-8");

                        long l1 = System.currentTimeMillis();
                        Json json = null;
                        for (int i=0;i<PASS;i++) {
                            json = Json.read(new ByteArrayInputStream(buf), null);
                        }
                        long time = (System.currentTimeMillis() - l1) / PASS;
                        System.out.println("* "+s+": read from binary: "+time+"ms, "+(buf.length * 1000 / 1024 / time)+"Kb/s");

                        l1 = System.currentTimeMillis();
                        for (int i=0;i<PASS;i++) {
                            json = Json.read(string);
                        }
                        time = (System.currentTimeMillis() - l1) / PASS;
                        System.out.println("* "+s+": read from text: "+time+"ms, "+(string.length() * 1000 / 1024 / time)+"Kc/s");

                        l1 = System.currentTimeMillis();
                        StringBuilder sb = new StringBuilder(string.length());
                        for (int i=0;i<PASS;i++) {
                            sb.setLength(0);
                            json.write(sb, null);
                        }
                        time = (System.currentTimeMillis() - l1) / PASS;
                        System.out.println("* "+s+": write: "+time+"ms, "+(sb.length() * 1000 / 1024 / time)+"Kc/s");

                        l1 = System.currentTimeMillis();
                        for (int i=0;i<PASS;i++) {
                            out.reset();
                            json.writeCbor(out, null);
                        }
                        time = (System.currentTimeMillis() - l1) / PASS;
                        System.out.println("* "+s+": write CBOR: "+time+"ms, "+(out.size() * 1000 / 1024 / time)+"Kb/s");

                        buf = out.toByteArray();
                        l1 = System.currentTimeMillis();
                        for (int i=0;i<PASS;i++) {
                            ByteArrayInputStream bin = new ByteArrayInputStream(buf);
                            json.readCbor(bin, null);
                        }
                        time = (System.currentTimeMillis() - l1) / PASS;
                        System.out.println("* "+s+": read CBOR: "+time+"ms, "+(buf.length * 1000 / 1024 / time)+"Kb/s");



                    } catch (Exception e) {
                        System.out.println("* "+s+": FAIL "+e);
                        e.printStackTrace();
                    } finally {
                        if (in != null) try { in.close(); } catch (Exception e) {}
                    }
                }
            }
        } finally {
            if (r != null) try { r.close(); } catch (Exception e) {}
        }
        System.out.println("----- END SPEED TESTS -----");
    }
}
