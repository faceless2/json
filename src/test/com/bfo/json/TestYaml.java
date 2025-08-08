package com.bfo.json;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.nio.file.*;

/**
 * Run tests, mostly from https://github.com/yaml/yaml-test-suite
 * but also others.
 */
class TestYaml {
    public static void main(String[] args) throws Exception {
        URI uri = TestYaml.class.getResource("resources").toURI();

        // Step 1: test test_parsing
        System.out.println("----- BEGIN YAML PARSE TESTS -----");
        FileSystem fs = FileSystems.getFileSystem(uri);
        if (fs == null) {
            fs = FileSystems.newFileSystem(uri, Collections.<String,Object>emptyMap());
        }
        int okcount = 0, failcount = 0, skipcount = 0;
        List<Path> paths = new ArrayList<Path>();
        for (Iterator<Path> i = Files.walk(fs.getPath("com/bfo/json/resources/yaml/"), 3).iterator();i.hasNext();) {
            paths.add(i.next());
        }
        Collections.sort(paths);
        for (Path path : paths) {
            String name = path.getFileName().toString();
            if (name.equals("in.yaml")) {
                boolean shouldfail = Files.isReadable(path.resolveSibling("error"));
                Path jsonpath = path.resolveSibling("in.json");
                InputStream in = null;
                List<Json> fromJson = null;
                if (Files.isReadable(jsonpath)) {
                    fromJson = new ArrayList<Json>();
                    in = Files.newInputStream(jsonpath);
                    JsonReader reader = new JsonReader().setInput(in).setNonDraining();
                    JsonBuilder builder = new JsonBuilder();
                    while (reader.hasNext()) {
                        JsonStream.Event e = reader.next();
                        if (builder.event(e)) {
                            fromJson.add(builder.build());
                            builder = new JsonBuilder();
                        }
                    }
                }
                try {
                    List<Json> fromYaml = new ArrayList<Json>();
                    in = Files.newInputStream(path);
                    YamlReader reader = new YamlReader().setInput(in);
                    JsonBuilder builder = new JsonBuilder();
                    while (reader.hasNext()) {
                        JsonStream.Event e = reader.next();
                        if (builder.event(e)) {
                            fromYaml.add(builder.build());
                            builder = new JsonBuilder();
                        }
                    }
                    if (shouldfail) {
                        System.out.println("* "+path+": FAIL: expected fail, but got " + fromYaml);
                        failcount++;
                    } else if (fromJson == null || fromYaml.toString().equals(fromJson.toString())) {
                        System.out.println("* "+path+": OK");
                        okcount++;
                    } else {
                        System.out.println("* "+path+": FAIL: expected " + fromJson + " got " + fromYaml);
                        failcount++;
                    }
                } catch (UnsupportedOperationException e) {
                    skipcount++;
                } catch (Exception e) {
                    if (shouldfail) {
                        System.out.println("* "+path+": OK (fail)");
                        okcount++;
                    } else {
                        failcount++;
                        System.out.println("* "+path+": FAIL: failed, expected " + fromJson);
                        e.printStackTrace();
                    }
                }
            }
        }
        System.out.println("Test " + (okcount + failcount)+" OK="+okcount+" FAIL="+failcount+" SKIP="+skipcount);
    }
}

