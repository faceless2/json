package com.bfo.json;

public class Test {
    public static void main(String[] args) throws Exception {
        System.setErr(System.out);
        Seriot.main(args);
        System.out.flush();
        Test2.main(args);
        System.out.flush();
        TestCbor.main(args);
        System.out.flush();
        TestMsgpack.main(args);
        System.out.flush();
        TestJWT.main(args);
        System.out.flush();
        TestCOSE.main(args);
        System.out.flush();
        TestJSR.main(args);
        System.out.flush();
        TestPartial.main(args);
        System.out.flush();
        TestYaml.main(args);
        System.out.flush();
        Speed.main(args);
        System.out.flush();
    }
}
