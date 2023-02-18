package com.bfo.json;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.security.*;
import com.bfo.json.*;

public class TestCOSE {

    public static final String[] INPUTS = {
"{ \"title\":\"JOSE Cookbook Example 4.2 - RSA-PSS signature\", \"input\":{ \"plaintext\":\"This is the content.\", \"sign\":{ \"signers\":[ { \"key\":{ \"kty\":\"EC\", \"kid\":\"11\", \"crv\":\"P-256\", \"x\":\"usWxHK2PmfnHKwXPS54m0kTcGJ90UiglWiGahtagnv8\", \"y\":\"IBOL-C3BttVivg-lSreASjpkttcsz-1rb7btKLv8EX4\", \"d\":\"V8kgd2ZBRuh2dgyVINBUqpPDr7BOMGcF22CQMIUHtNM\" }, \"unprotected\":{ \"kid\":\"11\" }, \"protected\":{ \"alg\":\"ES256\" } } ] }, \"rng_description\":\"seed for signature\" }, \"intermediates\":{ \"signers\":[ { \"ToBeSign_hex\":\"85695369676E61747572654043A101264054546869732069732074686520636F6E74656E742E\" } ] }, \"output\":{ \"cbor_diag\":\"98([h'', {}, h'546869732069732074686520636F6E74656E742E', [[h'A10126', {4: h'3131'}, h'E2AEAFD40D69D19DFE6E52077C5D7FF4E408282CBEFB5D06CBF414AF2E19D982AC45AC98B8544C908B4507DE1E90B717C3D34816FE926A2B98F53AFD2FA0F30A']]])\", \"cbor\":\"D8628440A054546869732069732074686520636F6E74656E742E818343A10126A1044231315840E2AEAFD40D69D19DFE6E52077C5D7FF4E408282CBEFB5D06CBF414AF2E19D982AC45AC98B8544C908B4507DE1E90B717C3D34816FE926A2B98F53AFD2FA0F30A\" } }",     // Example C.1.1

"{ \"title\":\"JOSE Cookbook Example 4.8 - multiple signatures\", \"input\":{ \"plaintext\":\"This is the content.\", \"sign\":{ \"signers\":[ { \"key\":{ \"kty\":\"EC\", \"kid\":\"11\", \"crv\":\"P-256\", \"x\":\"usWxHK2PmfnHKwXPS54m0kTcGJ90UiglWiGahtagnv8\", \"y\":\"IBOL-C3BttVivg-lSreASjpkttcsz-1rb7btKLv8EX4\", \"d\":\"V8kgd2ZBRuh2dgyVINBUqpPDr7BOMGcF22CQMIUHtNM\" }, \"unprotected\":{ \"kid\":\"11\" }, \"protected\":{ \"alg\":\"ES256\" } }, { \"key\":{ \"kty\":\"EC\", \"kid\":\"bilbo.baggins@hobbiton.example\", \"use\":\"sig\", \"crv\":\"P-521\", \"x\":\"AHKZLLOsCOzz5cY97ewNUajB957y-C-U88c3v13nmGZx6sYl_oJXu9A5RkTKqjqvjyekWF-7ytDyRXYgCF5cj0Kt\", \"y\":\"AdymlHvOiLxXkEhayXQnNCvDX4h9htZaCJN34kfmC6pV5OhQHiraVySsUdaQkAgDPrwQrJmbnX9cwlGfP-HqHZR1\", \"d\":\"AAhRON2r9cqXX1hg-RoI6R1tX5p2rUAYdmpHZoC1XNM56KtscrX6zbKipQrCW9CGZH3T4ubpnoTKLDYJ_fF3_rJt\" }, \"protected\":{ \"alg\":\"ES512\" }, \"unprotected\":{ \"kid\":\"bilbo.baggins@hobbiton.example\" } } ] } }, \"intermediates\":{ \"signers\":[ { \"ToBeSign_hex\":\"85695369676E61747572654043A101264054546869732069732074686520636F6E74656E742E\" }, { \"ToBeSign_hex\":\"85695369676E61747572654044A10138234054546869732069732074686520636F6E74656E742E\" } ] }, \"output\":{ \"cbor_diag\":\"98([h'', {}, h'546869732069732074686520636F6E74656E742E', [[h'A10126', {4: h'3131'}, h'E2AEAFD40D69D19DFE6E52077C5D7FF4E408282CBEFB5D06CBF414AF2E19D982AC45AC98B8544C908B4507DE1E90B717C3D34816FE926A2B98F53AFD2FA0F30A'], [h'A1013823', {4: h'62696C626F2E62616767696E7340686F626269746F6E2E6578616D706C65'}, h'00A2D28A7C2BDB1587877420F65ADF7D0B9A06635DD1DE64BB62974C863F0B160DD2163734034E6AC003B01E8705524C5C4CA479A952F0247EE8CB0B4FB7397BA08D009E0C8BF482270CC5771AA143966E5A469A09F613488030C5B07EC6D722E3835ADB5B2D8C44E95FFB13877DD2582866883535DE3BB03D01753F83AB87BB4F7A0297']]])\", \"cbor\":\"D8628440A054546869732069732074686520636F6E74656E742E828343A10126A1044231315840E2AEAFD40D69D19DFE6E52077C5D7FF4E408282CBEFB5D06CBF414AF2E19D982AC45AC98B8544C908B4507DE1E90B717C3D34816FE926A2B98F53AFD2FA0F30A8344A1013823A104581E62696C626F2E62616767696E7340686F626269746F6E2E6578616D706C65588400A2D28A7C2BDB1587877420F65ADF7D0B9A06635DD1DE64BB62974C863F0B160DD2163734034E6AC003B01E8705524C5C4CA479A952F0247EE8CB0B4FB7397BA08D009E0C8BF482270CC5771AA143966E5A469A09F613488030C5B07EC6D722E3835ADB5B2D8C44E95FFB13877DD2582866883535DE3BB03D01753F83AB87BB4F7A0297\" } }",       // Example C.1.2
"{ \"title\":\"SIG-03:  Counter Signature example\", \"input\":{ \"plaintext\":\"This is the content.\", \"sign\":{ \"countersign\":{ \"signers\":[ { \"key\":{ \"kty\":\"EC\", \"kid\":\"11\", \"crv\":\"P-256\", \"x\":\"usWxHK2PmfnHKwXPS54m0kTcGJ90UiglWiGahtagnv8\", \"y\":\"IBOL-C3BttVivg-lSreASjpkttcsz-1rb7btKLv8EX4\", \"d\":\"V8kgd2ZBRuh2dgyVINBUqpPDr7BOMGcF22CQMIUHtNM\" }, \"unprotected\":{ \"kid\":\"11\" }, \"protected\":{ \"alg\":\"ES256\" } } ] }, \"signers\":[ { \"key\":{ \"kty\":\"EC\", \"kid\":\"11\", \"crv\":\"P-256\", \"x\":\"usWxHK2PmfnHKwXPS54m0kTcGJ90UiglWiGahtagnv8\", \"y\":\"IBOL-C3BttVivg-lSreASjpkttcsz-1rb7btKLv8EX4\", \"d\":\"V8kgd2ZBRuh2dgyVINBUqpPDr7BOMGcF22CQMIUHtNM\" }, \"unprotected\":{ \"kid\":\"11\" }, \"protected\":{ \"alg\":\"ES256\" } } ] }, \"rng_description\":\"seed for signature\" }, \"intermediates\":{ \"signers\":[ { \"ToBeSign_hex\":\"85695369676E61747572654043A101264054546869732069732074686520636F6E74656E742E\" } ], \"countersigners\":[ { \"ToBeSign_hex\":\"8570436F756E7465725369676E61747572654043A101264054546869732069732074686520636F6E74656E742E\" } ] }, \"output\":{ \"cbor_diag\":\"98([h'', {7: [h'A10126', {4: h'3131'}, h'5AC05E289D5D0E1B0A7F048A5D2B643813DED50BC9E49220F4F7278F85F19D4A77D655C9D3B51E805A74B099E1E085AACD97FC29D72F887E8802BB6650CCEB2C']}, h'546869732069732074686520636F6E74656E742E', [[h'A10126', {4: h'3131'}, h'E2AEAFD40D69D19DFE6E52077C5D7FF4E408282CBEFB5D06CBF414AF2E19D982AC45AC98B8544C908B4507DE1E90B717C3D34816FE926A2B98F53AFD2FA0F30A']]])\", \"cbor\":\"D8628440A1078343A10126A10442313158405AC05E289D5D0E1B0A7F048A5D2B643813DED50BC9E49220F4F7278F85F19D4A77D655C9D3B51E805A74B099E1E085AACD97FC29D72F887E8802BB6650CCEB2C54546869732069732074686520636F6E74656E742E818343A10126A1044231315840E2AEAFD40D69D19DFE6E52077C5D7FF4E408282CBEFB5D06CBF414AF2E19D982AC45AC98B8544C908B4507DE1E90B717C3D34816FE926A2B98F53AFD2FA0F30A\" } }",    // Example C.1.3
"{ \"title\":\"Sig-04: Add crit flag and origination time\", \"input\":{ \"plaintext\":\"This is the content.\", \"sign\":{ \"protected\":{ \"reserved\":false, \"crit\":[ \"reserved\" ] }, \"signers\":[ { \"key\":{ \"kty\":\"EC\", \"kid\":\"11\", \"crv\":\"P-256\", \"x\":\"usWxHK2PmfnHKwXPS54m0kTcGJ90UiglWiGahtagnv8\", \"y\":\"IBOL-C3BttVivg-lSreASjpkttcsz-1rb7btKLv8EX4\", \"d\":\"V8kgd2ZBRuh2dgyVINBUqpPDr7BOMGcF22CQMIUHtNM\" }, \"unprotected\":{ \"kid\":\"11\" }, \"protected\":{ \"alg\":\"ES256\" } } ] }, \"rng_description\":\"seed for signature\" }, \"intermediates\":{ \"signers\":[ { \"ToBeSign_hex\":\"85695369676E617475726556A2687265736572766564F4028168726573657276656443A101264054546869732069732074686520636F6E74656E742E\" } ] }, \"output\":{ \"cbor_diag\":\"98([h'A2687265736572766564F40281687265736572766564', {}, h'546869732069732074686520636F6E74656E742E', [[h'A10126', {4: h'3131'}, h'3FC54702AA56E1B2CB20284294C9106A63F91BAC658D69351210A031D8FC7C5FF3E4BE39445B1A3E83E1510D1ACA2F2E8A7C081C7645042B18ABA9D1FAD1BD9C']]])\", \"cbor\":\"D8628456A2687265736572766564F40281687265736572766564A054546869732069732074686520636F6E74656E742E818343A10126A10442313158403FC54702AA56E1B2CB20284294C9106A63F91BAC658D69351210A031D8FC7C5FF3E4BE39445B1A3E83E1510D1ACA2F2E8A7C081C7645042B18ABA9D1FAD1BD9C\" } }", // Example C.1.4
"{ \"title\":\" - EC signature\", \"input\":{ \"plaintext\":\"This is the content.\", \"sign0\":{ \"key\":{ \"kty\":\"EC\", \"kid\":\"11\", \"crv\":\"P-256\", \"x\":\"usWxHK2PmfnHKwXPS54m0kTcGJ90UiglWiGahtagnv8\", \"y\":\"IBOL-C3BttVivg-lSreASjpkttcsz-1rb7btKLv8EX4\", \"d\":\"V8kgd2ZBRuh2dgyVINBUqpPDr7BOMGcF22CQMIUHtNM\" }, \"unprotected\":{ \"kid\":\"11\" }, \"protected\":{ \"alg\":\"ES256\" }, \"alg\":\"ES256\" }, \"rng_description\":\"seed for signature\" }, \"intermediates\":{ \"ToBeSign_hex\":\"846A5369676E61747572653143A101264054546869732069732074686520636F6E74656E742E\" }, \"output\":{ \"cbor_diag\":\"18([h'A10126', {4: h'3131'}, h'546869732069732074686520636F6E74656E742E', h'8EB33E4CA31D1C465AB05AAC34CC6B23D58FEF5C083106C4D25A91AEF0B0117E2AF9A291AA32E14AB834DC56ED2A223444547E01F11D3B0916E5A4C345CACB36'])\", \"cbor\":\"D28443A10126A10442313154546869732069732074686520636F6E74656E742E58408EB33E4CA31D1C465AB05AAC34CC6B23D58FEF5C083106C4D25A91AEF0B0117E2AF9A291AA32E14AB834DC56ED2A223444547E01F11D3B0916E5A4C345CACB36\" } }",      // Example C.2.1
    };

    public static void main(String[] args) throws Exception {
        System.out.println("----- BEGIN COSE-DIAG TESTS -----");
        int test = 0;
        for (String s : INPUTS) {
            Json j = Json.read(s);
            if (j.hasPath("output.cbor_diag")) {
                Json raw = read(j.getPath("output.cbor").stringValue());
                Json raw2 = Json.read(new StringReader(j.getPath("output.cbor_diag").stringValue()), new JsonReadOptions().setCborDiag(true));
                String s1 = raw.toString(new JsonWriteOptions().setCborDiag("hex"));
                String s2 = raw2 == null ? null : raw2.toString(new JsonWriteOptions().setCborDiag("hex"));
                if (!s1.equals(s2)) {
                    assert false : "cbor_diag mismatch " + test + " " + s1 + " " + s2;
                } else {
                    System.out.println("* cbor_diag match " + test + " \"" + j.get("title").stringValue() + "\"");
                }
            }
            test++;
        }
        System.out.println("----- END COSE-DIAG TESTS -----");

        System.out.println("----- BEGIN COSE TESTS -----");
        test = 0;
        for (String s : INPUTS) {
            Json j = Json.read(s);
            COSE cose = new COSE().set(read(j.getPath("output.cbor").stringValue()));
            List<List<Key>> keylist;
            if (j.hasPath("input.sign0.key")) {
                JWK jwk = new JWK(j.getPath("input.sign0.key"));
                keylist = Collections.<List<Key>>singletonList(jwk.getKeys());
            } else {
                int size = j.getPath("input.sign.signers").size();
                keylist = new ArrayList<List<Key>>();
                for (int i=0;i<size;i++) {
                    JWK jwk = new JWK(j.getPath("input.sign.signers[" + i + "].key"));
                    keylist.add(jwk.getKeys());
                }
            }
            Map<Key,String> allkeys = new LinkedHashMap<Key,String>();
            for (List<Key> keys : keylist) {
                for (Key key : keys) {
                    allkeys.put(key, null);
                }
            }
            int testkey = 0;
            for (List<Key> keys : keylist) {
                byte[] payload = j.getPath("input.plaintext").stringValue().getBytes("UTF-8");
                for (Key key : keys) {
                    if (key instanceof PublicKey) {
                        int verified = cose.verify((PublicKey)key);
                        if (verified >= 0) {
                            System.out.println("* verify test " + test + " \"" + j.get("title").stringValue() + "\" key="+testkey+" keyindex="+verified+" alg="+cose.getAlgorithm(verified));
                        } else {
                            assert false : "Verify test " + test + " \"" + j.get("title").stringValue() + "\" failed to verify";
                        }
                    }
                }
                testkey++;
            }
            COSE cose2 = new COSE();
            ByteBuffer pl = cose.getPayload();
            cose2.setPayload(cose.getPayload());
            cose2.setUnprotectedAttributes(cose.getUnprotectedAttributes());
            Json j2 = cose2.sign(allkeys).get();

            COSE cose3 = new COSE().set(j2);
            testkey = 0;
            for (List<Key> keys : keylist) {
                for (Key key : keys) {
                    if (key instanceof PublicKey) {
                        int verified = cose3.verify((PublicKey)key);
                        if (verified >= 0) {
                            System.out.println("* resign+verify test " + test + " \"" + j.get("title").stringValue() + "\" key="+testkey+" keyindex="+verified + " alg="+cose3.getAlgorithm(verified));
                        } else {
                            assert false : "Resign+Verify test " + test + " \"" + j.get("title").stringValue() + "\" failed to verify";
                        }
                    }
                }
                testkey++;
            }
            test++;
        }
        System.out.println("----- END COSE TESTS -----");

        System.out.println("----- BEGIN JWK+COSE TESTS -----");
        for (int i=0;i<TestJWT.jwktests.length;i++) {
            String s = TestJWT.jwktests[i];
            Json j = Json.read(s);
            String error = j.has("error") ? j.remove("error").stringValue() : null;
            try {
                JWK jwk = new JWK(j);
                assert !jwk.getKeys().isEmpty();
                if (jwk.getPrivateKey() != null && jwk.getPublicKey() != null) {
                    ByteBuffer payload = new Json("Hello, World".getBytes("UTF-8")).bufferValue();
                    COSE cose = new COSE().setPayload(payload);
                    if (jwk.getAlgorithm() != null && jwk.getAlgorithm().startsWith("RS")) {
                        continue;
                    }
                    cose.sign(jwk.getPrivateKey(), jwk.getAlgorithm());
                    int key = cose.verify(jwk.getPublicKey());
                    if (key >= 0) {
                        System.out.println("* JWK key " + jwk.get("kid") + " used with COSE test " + i + " " + cose.getAlgorithm(key));
                    } else {
                        assert false : "Sign+Verify JWK test " + i + " "+ jwk.get("kid")+" alg="+cose.getAlgorithm(0);
                    }
                }
                Json cose = jwk.toCOSEKey();
                JWK jwk2 = JWK.fromCOSEKey(cose);
                if (jwk.size() != jwk2.size()) {
                    assert false : "JWK->COSE->JWK for " + jwk.get("kid") + ": was " + jwk+" now " + jwk2;
                }
                for (Map.Entry<Object,Json> e : jwk.mapValue().entrySet()) {
                    Object key = e.getKey();
                    if (!jwk.get(key).equals(jwk2.get(key))) {
                        assert false : "JWK->COSE->JWK for " + jwk.get("kid") + ": key="+key+" was " + jwk+" now " + jwk2+" (was " + jwk.get(key).type()+" now " + jwk2.get(key).type() + ")";
                    }
                }
                System.out.println("* JWK to COSE to JWK for " + jwk.get("kid"));
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().equals(error)) {
                } else {
                    System.out.println("COSE+JWK TEST " + i);
                    throw e;
                }
            }
        }
        System.out.println("----- END JWK+COSE TESTS -----");

    }

    private static Json read(String s) throws IOException {
        byte[] b = new byte[s.length() / 2];
        for (int i=0;i<b.length;i++) {
            b[i] = (byte)((Character.digit(s.charAt(i*2), 16) << 4) + Character.digit(s.charAt(i*2 + 1), 16));
        }
        return Json.readCbor(new ByteArrayInputStream(b), null);
    }

}

