package com.bfo.json;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.nio.file.*;

/**
 * Tests from http://seriot.ch/parsing_json.php
 */
class Seriot {
    public static void main(String[] args) throws Exception {
        URI uri = Seriot.class.getResource("resources").toURI();

        // Step 1: test test_parsing 
        System.out.println("----- BEGIN SERIOT PARSE TESTS -----");
        FileSystem fs = FileSystems.newFileSystem(uri, Collections.<String,Object>emptyMap());
        for (Iterator<Path> i = Files.walk(fs.getPath("com/bfo/json/resources/test_parsing"), 1).iterator();i.hasNext();) {
            Path path = i.next();
            String name = path.getFileName().toString();
            if (name.endsWith(".json")) {
                InputStream in = null;
                boolean shouldFail = name.startsWith("n_");
                try {
                    in = Files.newInputStream(path);
                    Json json = Json.read(in, null);
                    if (shouldFail) {
                        System.out.println("* "+path+": FAIL (parsed when it should have failed)");
                    } else {
                        System.out.println("* "+path+": OK");
                    }
                } catch (StackOverflowError e) {
                    if (shouldFail) {
                        System.out.println("* "+path+": OK ("+e.getMessage()+")");
                    } else {
                        System.out.println("* "+path+": FAIL "+e);
                    }
                } catch (Exception e) {
                    if (shouldFail) {
                        System.out.println("* "+path+": OK ("+e.getMessage()+")");
                    } else {
                        System.out.println("* "+path+": FAIL "+e);
                    }
                } finally {
                    if (in != null) try { in.close(); } catch (Exception e) {}
                }
            }
        }
        System.out.println("----- END SERIOT PARSE TESTS -----");

        // Step 2: transform tests from test_parsing 
        System.out.println("----- BEGIN SERIOT TRANSFORM TESTS -----");
        for (Iterator<Path> i = Files.walk(fs.getPath("com/bfo/json/resources/test_transform"), 1).iterator();i.hasNext();) {
            Path path = i.next();
            String name = path.getFileName().toString();
            int c;
            if (name.endsWith(".json")) {
                Reader in = null;
                StringBuilder sb = new StringBuilder();
                try {
                    in = new InputStreamReader(Files.newInputStream(path), "UTF-8");
                    while ((c=in.read())>=0) {
                        sb.append(c);
                    }
                    String input = sb.toString();
//                    System.out.println("test: "+path);
                    String output = Json.read(sb).toString();
                    if (input.equals(output)) {
                        System.out.println("* "+path+": OK");
                    } else {
                        System.out.println("* "+path+": Changed from \""+input+"\" to \""+output+"\"");
                    }
                } catch (Exception e) {
                    System.out.println("* "+path+": FAIL "+e);
                } finally {
                    if (in != null) try { in.close(); } catch (Exception e) {}
                    in.close();
                }
            }
        }
        System.out.println("----- END SERIOT TRANSFORM TESTS -----");
    }
}
