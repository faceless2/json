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
    static String string;
    public static void main(String[] args) throws Exception {
        System.out.println("----- BEGIN SPEED TESTS -----");
        BufferedReader r = null;
        final int PASS = 500;   // Really needs to be big to remove any doubt on the numbers
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
                        if (buf.length == 0) {
                            throw new Error("No input on " + s);
                        }
                        string = new String(buf, "UTF-8");
                        long time, l1;
                        Json json = null;

                        l1 = System.nanoTime();
                        for (int i=0;i<PASS;i++) {
                            json = Json.read(new JsonReader().setInput(ByteBuffer.wrap(buf)));
                        }
                        time = (System.nanoTime() - l1) / 1000 / PASS;
                        System.out.println("* "+s+": read from binary: "+time+"us, "+(buf.length * 1000000l / 1024 / time)+"Kb/s");

                        l1 = System.nanoTime();
                        for (int i=0;i<PASS;i++) {
                            json = Json.read(string);
                        }
                        time = (System.nanoTime() - l1) / 1000 / PASS;
                        System.out.println("* "+s+": read from text: "+time+"us, "+(string.length() * 1000000l / 1024 / time)+"Kc/s over " + string.length()+" chars");

                        l1 = System.nanoTime();
                        StringBuilder sb = new StringBuilder(string.length());
                        for (int i=0;i<PASS;i++) {
                            sb.setLength(0);
                            json.write(new JsonWriter().setOutput(sb));
                        }
                        time = (System.nanoTime() - l1) / 1000 / PASS;
                        System.out.println("* "+s+": write: "+time+"us, "+(sb.length() * 1000000l / 1024 / time)+"Kc/s over " + sb.length()+" chars");

                        l1 = System.nanoTime();
                        for (int i=0;i<PASS;i++) {
                            out.reset();
                            json.write(new CborWriter().setOutput(out));
                        }
                        time = (System.nanoTime() - l1) / 1000 / PASS;
                        System.out.println("* "+s+": write CBOR: "+time+"us, "+(out.size() * 1000000l / 1024 / time)+"Kb/s over " + out.size() + " bytes");

                        buf = out.toByteArray();
                        l1 = System.nanoTime();
                        for (int i=0;i<PASS;i++) {
                            json = Json.readCbor(ByteBuffer.wrap(buf));
                        }
                        time = (System.nanoTime() - l1) / 1000 / PASS;
                        System.out.println("* "+s+": read CBOR: "+time+"us, "+(buf.length * 1000000l / 1024 / time)+"Kb/s over " + buf.length + " bytes");

                        l1 = System.nanoTime();
                        for (int i=0;i<PASS;i++) {
                            out.reset();
                            json.write(new MsgpackWriter().setOutput(out));
                        }
                        time = (System.nanoTime() - l1) / 1000 / PASS;
                        System.out.println("* "+s+": write Msgpack: "+time+"us, "+(out.size() * 1000000l / 1024 / time)+"Kb/s over " + out.size() + " bytes");

                        buf = out.toByteArray();
                        l1 = System.nanoTime();
                        for (int i=0;i<PASS;i++) {
                            json = Json.read(new MsgpackReader().setInput(ByteBuffer.wrap(buf)));
                        }
                        time = (System.nanoTime() - l1) / 1000 / PASS;
                        System.out.println("* "+s+": read Msgpack: "+time+"us, "+(buf.length * 1000000l / 1024 / time)+"Kb/s over " + buf.length + " bytes");


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
