package com.bfo.json;

import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;

/**
 * See how the "partial" parsing mode works, by loading a file with Unicode etc in very small
 * chunks from ByteBuffers, InputStreams etc in each of the formats.
 */
public class TestPartial {
    public static void main(String[] args) throws Exception {
        String url = "https://raw.githubusercontent.com/lemire/Code-used-on-Daniel-Lemire-s-blog/master/2018/05/02/twitter.json";
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        InputStream in = args.length == 0 ? new URL(url).openConnection().getInputStream() : new FileInputStream(args[0]);
        byte[] buf = new byte[8192];
        int l;
        while ((l=in.read(buf)) >= 0) {
            bout.write(buf, 0, l);
        }
        in.close();
        byte[] jsonbuf = bout.toByteArray();
        Json json = Json.read(new JsonReader().setCborDiag(true).setInput(ByteBuffer.wrap(jsonbuf)));
        jsonbuf = json.toString(new JsonWriter().setCborDiag("base64")).getBytes("UTF-8");
        bout.reset();
        json.write(new CborWriter().setOutput(bout));
        byte[] cborbuf = bout.toByteArray();
        bout.reset();
        json.write(new MsgpackWriter().setOutput(bout));
        byte[] msgpackbuf = bout.toByteArray();


        System.out.println("----- BEGIN TRICKLE TESTS -----");
        for (int pass=0;pass<9;pass++) {
            JsonBuilder builder = new JsonBuilder();
            String format, source = null;
            AbstractReader reader;
            if (pass < 3) {
                format = "JSON";
                reader = new JsonReader().setCborDiag(true).setPartial();
                buf = jsonbuf;
            } else if (pass < 6) {
                format = "CBOR";
                reader = new CborReader().setPartial();
                buf = cborbuf;
            } else {
                format = "Msgpack";
                reader = new MsgpackReader().setPartial();
                buf = msgpackbuf;
            }
            for (int size=1;size<7;size++) {
                try {
                    for (int i=0;i<buf.length;i+=size) {
                        switch (pass % 3) {
                            case 0:
                                source = "buffer";
                                reader.setInput(ByteBuffer.wrap(buf, i, Math.min(buf.length - i, size)));
                                break;
                            case 1:
                                source = "InputStream";
                                reader.setInput(new ByteArrayInputStream(buf, i, Math.min(buf.length - i, size)));
                                break;
                            case 2:
                                source = "ReadableByteChannel";
                                reader.setInput(Channels.newChannel(new ByteArrayInputStream(buf, i, Math.min(buf.length - i, size))));
                                break;
                        }
                        while (reader.hasNext()) {
                            builder.event(reader.next());
                        }
                    }
                    Json j = builder.build();
                    if (j == null) {
                        System.out.println("Partial "+format+" from " + source + " size=" + size + " FAIL: incomplete");
                    } else {
                        JsonStream writer;
                        OutputStreamWriter wr = null;
                        if (pass < 3) {
                            writer = new JsonWriter().setCborDiag("base64").setOutput(wr = new OutputStreamWriter(bout, "UTF-8"));
                        } else if (pass < 6) {
                            writer = new CborWriter().setOutput(bout);
                        } else {
                            writer = new MsgpackWriter().setOutput(bout);
                        }
                        bout.reset();
                        j.write(writer);
                        if (wr != null) {
                            wr.close();
                        }
                        byte[] outbuf = bout.toByteArray();
                        if (!Arrays.equals(buf, outbuf)) {
                            System.out.println("Partial "+format+" from " + source + " size=" + size + " FAIL");
                        } else {
                            System.out.println("Partial "+format+" from " + source + " size=" + size + " OK");
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Partial "+format+" from " + source + " size=" + size + " FAIL: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        System.out.println("----- END TRICKLE TESTS -----");
    }
}
