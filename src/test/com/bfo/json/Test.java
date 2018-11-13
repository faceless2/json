package com.bfo.json;

public class Test {
    public static void main(String[] args) throws Exception {
        System.setErr(System.out);
        Seriot.main(args);
        System.out.flush();
        Speed.main(args);
        System.out.flush();
        Test2.main(args);
        System.out.flush();
    }
}
