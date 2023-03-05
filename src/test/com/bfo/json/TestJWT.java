package com.bfo.json;

import java.util.*;
import java.text.*;
import java.security.*;
import java.io.*;

public class TestJWT {
    public static void main(String[] args) throws Exception {
        System.out.println("----- BEGIN JWT TESTS -----");
        for (int i=0;i<tests.length;) {
            String msg = tests[i++];
            JWT jwt = new JWT(msg);
            String alg = jwt.getAlgorithm();
            JWK pub = new JWK(tests[i++].getBytes("UTF-8"), alg);
            JWK pri = new JWK(tests[i++].getBytes("UTF-8"), alg);
            assert jwt.verify(pub.getSecretKey() != null ? pub.getSecretKey() : pub.getPublicKey()) : "Verify " + jwt.getAlgorithm();
            jwt.sign(pri.getSecretKey() != null ? pri.getSecretKey() : pri.getPrivateKey());
            assert jwt.verify(pub.getSecretKey() != null ? pub.getSecretKey() : pub.getPublicKey()) : "Sign+Verify " + jwt.getAlgorithm();
            System.out.println("* sign+verify " + jwt.getAlgorithm());
        }
        System.out.println("----- END JWT TESTS -----");
        System.out.println("----- BEGIN JWK+JWT TESTS -----");
        for (int i=0;i<jwktests.length;i++) {
            String s = jwktests[i];
            Json j = Json.read(s);
            String error = j.has("error") ? j.remove("error").stringValue() : null;
            try {
                JWK jwk = new JWK(j);
                assert !jwk.getKeys().isEmpty();
                if (jwk.getPrivateKey() != null && jwk.getPublicKey() != null) {
                    Json msg = new Json("Hello, World");
                    JWT jwt = new JWT(msg);
                    jwt.sign(jwk.getPrivateKey());
                    assert jwt.verify(jwk.getPublicKey()) : "Sign+Verify " + jwk.get("kid");
                    System.out.println("* sign+verify " + jwk.get("kid") +" "+jwt.getAlgorithm());
                    JWK jwk2 = new JWK(new KeyPair(jwk.getPublicKey(), jwk.getPrivateKey()));
                    jwk2.put("kid", jwk.get("kid"));
                    assert jwt.verify(jwk2.getPublicKey()) : "Sign+Verify with cloned key " + jwk2.get("kid");
                    System.out.println("* sign+verify on cloned key " + jwk2.get("kid") +" " + jwk2.getAlgorithm());
                }
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().equals(error)) {
                } else {
                    System.out.println("JWK TEST " + i);
                    throw e;
                }
            }
        }
        System.out.println("----- END JWT TESTS -----");
    }

    private static String[] tests = {

// HS256
"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.XbPfbIHMI6arZ3Y922BhjWgQzWXcXNrz0ogtVhfEd2o",
"secret",
"secret",

// HS384
"eyJhbGciOiJIUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.hO2sthNQUSfvI9ylUdMKDxcrm8jB3KL6Rtkd3FOskL-jVqYh2CK1es8FKCQO8_tW",
"secret",
"secret",

// HS512
"eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.wUVS6tazE2N98_J4SH_djkEe1igXPu0qILAvVXCiO6O20gdf5vZ2sYFWX3c-Hy6L4TD47b3DSAAO9XjSqpJfag",
"secret",
"secret",

// RS256
"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.NHVaYe26MbtOYhSKkoKYdFVomg4i8ZJd8_-RU8VNbftc4TSMb4bXP3l3YlNWACwyXPGffz5aXHc6lty1Y2t4SWRqGteragsVdZufDn5BlnJl9pdR_kdVFUsra2rWKEofkZeIC4yWytE58sMIihvo9H1ScmmVwBcQP6XETqYd0aSHp1gOa9RdUPDvoXQ5oqygTqVtxaDr6wUFKrKItgBMzWIdNZ6y7O9E0DhEPTbE9rfBo6KTFsHAZnMg4k68CDp2woYIaXbmYTWcvbzIuHO7_37GT79XdIwkm95QJ7hYC9RiwrV7mesbY4PAahERJawntho0my942XheVLmGwLMBkQ",
"-----BEGIN PUBLIC KEY-----\n MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu1SU1LfVLPHCozMxH2Mo\n 4lgOEePzNm0tRgeLezV6ffAt0gunVTLw7onLRnrq0/IzW7yWR7QkrmBL7jTKEn5u\n +qKhbwKfBstIs+bMY2Zkp18gnTxKLxoS2tFczGkPLPgizskuemMghRniWaoLcyeh\n kd3qqGElvW/VDL5AaWTg0nLVkjRo9z+40RQzuVaE8AkAFmxZzow3x+VJYKdjykkJ\n 0iT9wCS0DRTXu269V264Vf/3jvredZiKRkgwlL9xNAwxXFg0x/XFw005UWVRIkdg\n cKWTjpBP2dPwVZ4WWC+9aGVd+Gyn1o0CLelf4rEjGoXbAAEgAqeGUxrcIlbjXfbc\n mwIDAQAB\n -----END PUBLIC KEY-----",
"-----BEGIN PRIVATE KEY-----\n MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQC7VJTUt9Us8cKj\n MzEfYyjiWA4R4/M2bS1GB4t7NXp98C3SC6dVMvDuictGeurT8jNbvJZHtCSuYEvu\n NMoSfm76oqFvAp8Gy0iz5sxjZmSnXyCdPEovGhLa0VzMaQ8s+CLOyS56YyCFGeJZ\n qgtzJ6GR3eqoYSW9b9UMvkBpZODSctWSNGj3P7jRFDO5VoTwCQAWbFnOjDfH5Ulg\n p2PKSQnSJP3AJLQNFNe7br1XbrhV//eO+t51mIpGSDCUv3E0DDFcWDTH9cXDTTlR\n ZVEiR2BwpZOOkE/Z0/BVnhZYL71oZV34bKfWjQIt6V/isSMahdsAASACp4ZTGtwi\n VuNd9tybAgMBAAECggEBAKTmjaS6tkK8BlPXClTQ2vpz/N6uxDeS35mXpqasqskV\n laAidgg/sWqpjXDbXr93otIMLlWsM+X0CqMDgSXKejLS2jx4GDjI1ZTXg++0AMJ8\n sJ74pWzVDOfmCEQ/7wXs3+cbnXhKriO8Z036q92Qc1+N87SI38nkGa0ABH9CN83H\n mQqt4fB7UdHzuIRe/me2PGhIq5ZBzj6h3BpoPGzEP+x3l9YmK8t/1cN0pqI+dQwY\n dgfGjackLu/2qH80MCF7IyQaseZUOJyKrCLtSD/Iixv/hzDEUPfOCjFDgTpzf3cw\n ta8+oE4wHCo1iI1/4TlPkwmXx4qSXtmw4aQPz7IDQvECgYEA8KNThCO2gsC2I9PQ\n DM/8Cw0O983WCDY+oi+7JPiNAJwv5DYBqEZB1QYdj06YD16XlC/HAZMsMku1na2T\n N0driwenQQWzoev3g2S7gRDoS/FCJSI3jJ+kjgtaA7Qmzlgk1TxODN+G1H91HW7t\n 0l7VnL27IWyYo2qRRK3jzxqUiPUCgYEAx0oQs2reBQGMVZnApD1jeq7n4MvNLcPv\n t8b/eU9iUv6Y4Mj0Suo/AU8lYZXm8ubbqAlwz2VSVunD2tOplHyMUrtCtObAfVDU\n AhCndKaA9gApgfb3xw1IKbuQ1u4IF1FJl3VtumfQn//LiH1B3rXhcdyo3/vIttEk\n 48RakUKClU8CgYEAzV7W3COOlDDcQd935DdtKBFRAPRPAlspQUnzMi5eSHMD/ISL\n DY5IiQHbIH83D4bvXq0X7qQoSBSNP7Dvv3HYuqMhf0DaegrlBuJllFVVq9qPVRnK\n xt1Il2HgxOBvbhOT+9in1BzA+YJ99UzC85O0Qz06A+CmtHEy4aZ2kj5hHjECgYEA\n mNS4+A8Fkss8Js1RieK2LniBxMgmYml3pfVLKGnzmng7H2+cwPLhPIzIuwytXywh\n 2bzbsYEfYx3EoEVgMEpPhoarQnYPukrJO4gwE2o5Te6T5mJSZGlQJQj9q4ZB2Dfz\n et6INsK0oG8XVGXSpQvQh3RUYekCZQkBBFcpqWpbIEsCgYAnM3DQf3FJoSnXaMhr\n VBIovic5l0xFkEHskAjFTevO86Fsz1C2aSeRKSqGFoOQ0tmJzBEs1R6KqnHInicD\n TQrKhArgLXX4v3CddjfTRJkFWDbE/CkvKZNOrcf1nhaGCPspRJj2KUkj1Fhl9Cnc\n dn/RsYEONbwQSjIfMPkvxF+8HQ==\n -----END PRIVATE KEY-----",

// RS384
"eyJhbGciOiJSUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.o1hC1xYbJolSyh0-bOY230w22zEQSk5TiBfc-OCvtpI2JtYlW-23-8B48NpATozzMHn0j3rE0xVUldxShzy0xeJ7vYAccVXu2Gs9rnTVqouc-UZu_wJHkZiKBL67j8_61L6SXswzPAQu4kVDwAefGf5hyYBUM-80vYZwWPEpLI8K4yCBsF6I9N1yQaZAJmkMp_Iw371Menae4Mp4JusvBJS-s6LrmG2QbiZaFaxVJiW8KlUkWyUCns8-qFl5OMeYlgGFsyvvSHvXCzQrsEXqyCdS4tQJd73ayYA4SPtCb9clz76N1zE5WsV4Z0BYrxeb77oA7jJhh994RAPzCG0hmQ",
"-----BEGIN PUBLIC KEY-----\n MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu1SU1LfVLPHCozMxH2Mo\n 4lgOEePzNm0tRgeLezV6ffAt0gunVTLw7onLRnrq0/IzW7yWR7QkrmBL7jTKEn5u\n +qKhbwKfBstIs+bMY2Zkp18gnTxKLxoS2tFczGkPLPgizskuemMghRniWaoLcyeh\n kd3qqGElvW/VDL5AaWTg0nLVkjRo9z+40RQzuVaE8AkAFmxZzow3x+VJYKdjykkJ\n 0iT9wCS0DRTXu269V264Vf/3jvredZiKRkgwlL9xNAwxXFg0x/XFw005UWVRIkdg\n cKWTjpBP2dPwVZ4WWC+9aGVd+Gyn1o0CLelf4rEjGoXbAAEgAqeGUxrcIlbjXfbc\n mwIDAQAB\n -----END PUBLIC KEY-----",
"-----BEGIN PRIVATE KEY-----\n MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQC7VJTUt9Us8cKj\n MzEfYyjiWA4R4/M2bS1GB4t7NXp98C3SC6dVMvDuictGeurT8jNbvJZHtCSuYEvu\n NMoSfm76oqFvAp8Gy0iz5sxjZmSnXyCdPEovGhLa0VzMaQ8s+CLOyS56YyCFGeJZ\n qgtzJ6GR3eqoYSW9b9UMvkBpZODSctWSNGj3P7jRFDO5VoTwCQAWbFnOjDfH5Ulg\n p2PKSQnSJP3AJLQNFNe7br1XbrhV//eO+t51mIpGSDCUv3E0DDFcWDTH9cXDTTlR\n ZVEiR2BwpZOOkE/Z0/BVnhZYL71oZV34bKfWjQIt6V/isSMahdsAASACp4ZTGtwi\n VuNd9tybAgMBAAECggEBAKTmjaS6tkK8BlPXClTQ2vpz/N6uxDeS35mXpqasqskV\n laAidgg/sWqpjXDbXr93otIMLlWsM+X0CqMDgSXKejLS2jx4GDjI1ZTXg++0AMJ8\n sJ74pWzVDOfmCEQ/7wXs3+cbnXhKriO8Z036q92Qc1+N87SI38nkGa0ABH9CN83H\n mQqt4fB7UdHzuIRe/me2PGhIq5ZBzj6h3BpoPGzEP+x3l9YmK8t/1cN0pqI+dQwY\n dgfGjackLu/2qH80MCF7IyQaseZUOJyKrCLtSD/Iixv/hzDEUPfOCjFDgTpzf3cw\n ta8+oE4wHCo1iI1/4TlPkwmXx4qSXtmw4aQPz7IDQvECgYEA8KNThCO2gsC2I9PQ\n DM/8Cw0O983WCDY+oi+7JPiNAJwv5DYBqEZB1QYdj06YD16XlC/HAZMsMku1na2T\n N0driwenQQWzoev3g2S7gRDoS/FCJSI3jJ+kjgtaA7Qmzlgk1TxODN+G1H91HW7t\n 0l7VnL27IWyYo2qRRK3jzxqUiPUCgYEAx0oQs2reBQGMVZnApD1jeq7n4MvNLcPv\n t8b/eU9iUv6Y4Mj0Suo/AU8lYZXm8ubbqAlwz2VSVunD2tOplHyMUrtCtObAfVDU\n AhCndKaA9gApgfb3xw1IKbuQ1u4IF1FJl3VtumfQn//LiH1B3rXhcdyo3/vIttEk\n 48RakUKClU8CgYEAzV7W3COOlDDcQd935DdtKBFRAPRPAlspQUnzMi5eSHMD/ISL\n DY5IiQHbIH83D4bvXq0X7qQoSBSNP7Dvv3HYuqMhf0DaegrlBuJllFVVq9qPVRnK\n xt1Il2HgxOBvbhOT+9in1BzA+YJ99UzC85O0Qz06A+CmtHEy4aZ2kj5hHjECgYEA\n mNS4+A8Fkss8Js1RieK2LniBxMgmYml3pfVLKGnzmng7H2+cwPLhPIzIuwytXywh\n 2bzbsYEfYx3EoEVgMEpPhoarQnYPukrJO4gwE2o5Te6T5mJSZGlQJQj9q4ZB2Dfz\n et6INsK0oG8XVGXSpQvQh3RUYekCZQkBBFcpqWpbIEsCgYAnM3DQf3FJoSnXaMhr\n VBIovic5l0xFkEHskAjFTevO86Fsz1C2aSeRKSqGFoOQ0tmJzBEs1R6KqnHInicD\n TQrKhArgLXX4v3CddjfTRJkFWDbE/CkvKZNOrcf1nhaGCPspRJj2KUkj1Fhl9Cnc\n dn/RsYEONbwQSjIfMPkvxF+8HQ==\n -----END PRIVATE KEY-----",

// RS512\n
"eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.jYW04zLDHfR1v7xdrW3lCGZrMIsVe0vWCfVkN2DRns2c3MN-mcp_-RE6TN9umSBYoNV-mnb31wFf8iun3fB6aDS6m_OXAiURVEKrPFNGlR38JSHUtsFzqTOj-wFrJZN4RwvZnNGSMvK3wzzUriZqmiNLsG8lktlEn6KA4kYVaM61_NpmPHWAjGExWv7cjHYupcjMSmR8uMTwN5UuAwgW6FRstCJEfoxwb0WKiyoaSlDuIiHZJ0cyGhhEmmAPiCwtPAwGeaL1yZMcp0p82cpTQ5Qb-7CtRov3N4DcOHgWYk6LomPR5j5cCkePAz87duqyzSMpCB0mCOuE3CU2VMtGeQ",
"-----BEGIN PUBLIC KEY-----\n MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu1SU1LfVLPHCozMxH2Mo\n 4lgOEePzNm0tRgeLezV6ffAt0gunVTLw7onLRnrq0/IzW7yWR7QkrmBL7jTKEn5u\n +qKhbwKfBstIs+bMY2Zkp18gnTxKLxoS2tFczGkPLPgizskuemMghRniWaoLcyeh\n kd3qqGElvW/VDL5AaWTg0nLVkjRo9z+40RQzuVaE8AkAFmxZzow3x+VJYKdjykkJ\n 0iT9wCS0DRTXu269V264Vf/3jvredZiKRkgwlL9xNAwxXFg0x/XFw005UWVRIkdg\n cKWTjpBP2dPwVZ4WWC+9aGVd+Gyn1o0CLelf4rEjGoXbAAEgAqeGUxrcIlbjXfbc\n mwIDAQAB\n -----END PUBLIC KEY-----",
"-----BEGIN PRIVATE KEY-----\n MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQC7VJTUt9Us8cKj\n MzEfYyjiWA4R4/M2bS1GB4t7NXp98C3SC6dVMvDuictGeurT8jNbvJZHtCSuYEvu\n NMoSfm76oqFvAp8Gy0iz5sxjZmSnXyCdPEovGhLa0VzMaQ8s+CLOyS56YyCFGeJZ\n qgtzJ6GR3eqoYSW9b9UMvkBpZODSctWSNGj3P7jRFDO5VoTwCQAWbFnOjDfH5Ulg\n p2PKSQnSJP3AJLQNFNe7br1XbrhV//eO+t51mIpGSDCUv3E0DDFcWDTH9cXDTTlR\n ZVEiR2BwpZOOkE/Z0/BVnhZYL71oZV34bKfWjQIt6V/isSMahdsAASACp4ZTGtwi\n VuNd9tybAgMBAAECggEBAKTmjaS6tkK8BlPXClTQ2vpz/N6uxDeS35mXpqasqskV\n laAidgg/sWqpjXDbXr93otIMLlWsM+X0CqMDgSXKejLS2jx4GDjI1ZTXg++0AMJ8\n sJ74pWzVDOfmCEQ/7wXs3+cbnXhKriO8Z036q92Qc1+N87SI38nkGa0ABH9CN83H\n mQqt4fB7UdHzuIRe/me2PGhIq5ZBzj6h3BpoPGzEP+x3l9YmK8t/1cN0pqI+dQwY\n dgfGjackLu/2qH80MCF7IyQaseZUOJyKrCLtSD/Iixv/hzDEUPfOCjFDgTpzf3cw\n ta8+oE4wHCo1iI1/4TlPkwmXx4qSXtmw4aQPz7IDQvECgYEA8KNThCO2gsC2I9PQ\n DM/8Cw0O983WCDY+oi+7JPiNAJwv5DYBqEZB1QYdj06YD16XlC/HAZMsMku1na2T\n N0driwenQQWzoev3g2S7gRDoS/FCJSI3jJ+kjgtaA7Qmzlgk1TxODN+G1H91HW7t\n 0l7VnL27IWyYo2qRRK3jzxqUiPUCgYEAx0oQs2reBQGMVZnApD1jeq7n4MvNLcPv\n t8b/eU9iUv6Y4Mj0Suo/AU8lYZXm8ubbqAlwz2VSVunD2tOplHyMUrtCtObAfVDU\n AhCndKaA9gApgfb3xw1IKbuQ1u4IF1FJl3VtumfQn//LiH1B3rXhcdyo3/vIttEk\n 48RakUKClU8CgYEAzV7W3COOlDDcQd935DdtKBFRAPRPAlspQUnzMi5eSHMD/ISL\n DY5IiQHbIH83D4bvXq0X7qQoSBSNP7Dvv3HYuqMhf0DaegrlBuJllFVVq9qPVRnK\n xt1Il2HgxOBvbhOT+9in1BzA+YJ99UzC85O0Qz06A+CmtHEy4aZ2kj5hHjECgYEA\n mNS4+A8Fkss8Js1RieK2LniBxMgmYml3pfVLKGnzmng7H2+cwPLhPIzIuwytXywh\n 2bzbsYEfYx3EoEVgMEpPhoarQnYPukrJO4gwE2o5Te6T5mJSZGlQJQj9q4ZB2Dfz\n et6INsK0oG8XVGXSpQvQh3RUYekCZQkBBFcpqWpbIEsCgYAnM3DQf3FJoSnXaMhr\n VBIovic5l0xFkEHskAjFTevO86Fsz1C2aSeRKSqGFoOQ0tmJzBEs1R6KqnHInicD\n TQrKhArgLXX4v3CddjfTRJkFWDbE/CkvKZNOrcf1nhaGCPspRJj2KUkj1Fhl9Cnc\n dn/RsYEONbwQSjIfMPkvxF+8HQ==\n -----END PRIVATE KEY-----",

// ES256\n
"eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.tyh-VfuzIxCyGYDlkBA7DfyjrqmSHu6pQ2hoZuFqUSLPNY2N0mpHb3nk5K17HWP_3cYHBw7AhHale5wky6-sVA",
"-----BEGIN PUBLIC KEY-----\n MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEVs/o5+uQbTjL3chynL4wXgUg2R9\n q9UU8I5mEovUf86QZ7kOBIjJwqnzD1omageEHWwHdBO6B+dFabmdT9POxg==\n -----END PUBLIC KEY-----",
"-----BEGIN PRIVATE KEY-----\n MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgevZzL1gdAFr88hb2\n OF/2NxApJCzGCEDdfSp6VQO30hyhRANCAAQRWz+jn65BtOMvdyHKcvjBeBSDZH2r\n 1RTwjmYSi9R/zpBnuQ4EiMnCqfMPWiZqB4QdbAd0E7oH50VpuZ1P087G\n -----END PRIVATE KEY-----",

// ES384\n
"eyJhbGciOiJFUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.VUPWQZuClnkFbaEKCsPy7CZVMh5wxbCSpaAWFLpnTe9J0--PzHNeTFNXCrVHysAa3eFbuzD8_bLSsgTKC8SzHxRVSj5eN86vBPo_1fNfE7SHTYhWowjY4E_wuiC13yoj",
"-----BEGIN PUBLIC KEY-----\n MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEC1uWSXj2czCDwMTLWV5BFmwxdM6PX9p+\n Pk9Yf9rIf374m5XP1U8q79dBhLSIuaojsvOT39UUcPJROSD1FqYLued0rXiooIii\n 1D3jaW6pmGVJFhodzC31cy5sfOYotrzF\n -----END PUBLIC KEY-----",
"-----BEGIN PRIVATE KEY-----\n MIG2AgEAMBAGByqGSM49AgEGBSuBBAAiBIGeMIGbAgEBBDCAHpFQ62QnGCEvYh/p\n E9QmR1C9aLcDItRbslbmhen/h1tt8AyMhskeenT+rAyyPhGhZANiAAQLW5ZJePZz\n MIPAxMtZXkEWbDF0zo9f2n4+T1h/2sh/fviblc/VTyrv10GEtIi5qiOy85Pf1RRw\n 8lE5IPUWpgu553SteKigiKLUPeNpbqmYZUkWGh3MLfVzLmx85ii2vMU=\n -----END PRIVATE KEY-----",

// ES512\n
"eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.AbVUinMiT3J_03je8WTOIl-VdggzvoFgnOsdouAs-DLOtQzau9valrq-S6pETyi9Q18HH-EuwX49Q7m3KC0GuNBJAc9Tksulgsdq8GqwIqZqDKmG7hNmDzaQG1Dpdezn2qzv-otf3ZZe-qNOXUMRImGekfQFIuH_MjD2e8RZyww6lbZk",
"-----BEGIN PUBLIC KEY-----\n MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQBgc4HZz+/fBbC7lmEww0AO3NK9wVZ\n PDZ0VEnsaUFLEYpTzb90nITtJUcPUbvOsdZIZ1Q8fnbquAYgxXL5UgHMoywAib47\n 6MkyyYgPk0BXZq3mq4zImTRNuaU9slj9TVJ3ScT3L1bXwVuPJDzpr5GOFpaj+WwM\n Al8G7CqwoJOsW7Kddns=\n -----END PUBLIC KEY-----",
"-----BEGIN PRIVATE KEY-----\n MIHuAgEAMBAGByqGSM49AgEGBSuBBAAjBIHWMIHTAgEBBEIBiyAa7aRHFDCh2qga\n 9sTUGINE5jHAFnmM8xWeT/uni5I4tNqhV5Xx0pDrmCV9mbroFtfEa0XVfKuMAxxf\n Z6LM/yKhgYkDgYYABAGBzgdnP798FsLuWYTDDQA7c0r3BVk8NnRUSexpQUsRilPN\n v3SchO0lRw9Ru86x1khnVDx+duq4BiDFcvlSAcyjLACJvjvoyTLJiA+TQFdmrear\n jMiZNE25pT2yWP1NUndJxPcvVtfBW48kPOmvkY4WlqP5bAwCXwbsKrCgk6xbsp12\n ew==\n -----END PRIVATE KEY-----",

// PS256\n
"eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.iOeNU4dAFFeBwNj6qdhdvm-IvDQrTa6R22lQVJVuWJxorJfeQww5Nwsra0PjaOYhAMj9jNMO5YLmud8U7iQ5gJK2zYyepeSuXhfSi8yjFZfRiSkelqSkU19I-Ja8aQBDbqXf2SAWA8mHF8VS3F08rgEaLCyv98fLLH4vSvsJGf6ueZSLKDVXz24rZRXGWtYYk_OYYTVgR1cg0BLCsuCvqZvHleImJKiWmtS0-CymMO4MMjCy_FIl6I56NqLE9C87tUVpo1mT-kbg5cHDD8I7MjCW5Iii5dethB4Vid3mZ6emKjVYgXrtkOQ-JyGMh6fnQxEFN1ft33GX2eRHluK9eg",
"-----BEGIN PUBLIC KEY-----\n MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu1SU1LfVLPHCozMxH2Mo\n 4lgOEePzNm0tRgeLezV6ffAt0gunVTLw7onLRnrq0/IzW7yWR7QkrmBL7jTKEn5u\n +qKhbwKfBstIs+bMY2Zkp18gnTxKLxoS2tFczGkPLPgizskuemMghRniWaoLcyeh\n kd3qqGElvW/VDL5AaWTg0nLVkjRo9z+40RQzuVaE8AkAFmxZzow3x+VJYKdjykkJ\n 0iT9wCS0DRTXu269V264Vf/3jvredZiKRkgwlL9xNAwxXFg0x/XFw005UWVRIkdg\n cKWTjpBP2dPwVZ4WWC+9aGVd+Gyn1o0CLelf4rEjGoXbAAEgAqeGUxrcIlbjXfbc\n mwIDAQAB\n -----END PUBLIC KEY-----",
"-----BEGIN PRIVATE KEY-----\n MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQC7VJTUt9Us8cKj\n MzEfYyjiWA4R4/M2bS1GB4t7NXp98C3SC6dVMvDuictGeurT8jNbvJZHtCSuYEvu\n NMoSfm76oqFvAp8Gy0iz5sxjZmSnXyCdPEovGhLa0VzMaQ8s+CLOyS56YyCFGeJZ\n qgtzJ6GR3eqoYSW9b9UMvkBpZODSctWSNGj3P7jRFDO5VoTwCQAWbFnOjDfH5Ulg\n p2PKSQnSJP3AJLQNFNe7br1XbrhV//eO+t51mIpGSDCUv3E0DDFcWDTH9cXDTTlR\n ZVEiR2BwpZOOkE/Z0/BVnhZYL71oZV34bKfWjQIt6V/isSMahdsAASACp4ZTGtwi\n VuNd9tybAgMBAAECggEBAKTmjaS6tkK8BlPXClTQ2vpz/N6uxDeS35mXpqasqskV\n laAidgg/sWqpjXDbXr93otIMLlWsM+X0CqMDgSXKejLS2jx4GDjI1ZTXg++0AMJ8\n sJ74pWzVDOfmCEQ/7wXs3+cbnXhKriO8Z036q92Qc1+N87SI38nkGa0ABH9CN83H\n mQqt4fB7UdHzuIRe/me2PGhIq5ZBzj6h3BpoPGzEP+x3l9YmK8t/1cN0pqI+dQwY\n dgfGjackLu/2qH80MCF7IyQaseZUOJyKrCLtSD/Iixv/hzDEUPfOCjFDgTpzf3cw\n ta8+oE4wHCo1iI1/4TlPkwmXx4qSXtmw4aQPz7IDQvECgYEA8KNThCO2gsC2I9PQ\n DM/8Cw0O983WCDY+oi+7JPiNAJwv5DYBqEZB1QYdj06YD16XlC/HAZMsMku1na2T\n N0driwenQQWzoev3g2S7gRDoS/FCJSI3jJ+kjgtaA7Qmzlgk1TxODN+G1H91HW7t\n 0l7VnL27IWyYo2qRRK3jzxqUiPUCgYEAx0oQs2reBQGMVZnApD1jeq7n4MvNLcPv\n t8b/eU9iUv6Y4Mj0Suo/AU8lYZXm8ubbqAlwz2VSVunD2tOplHyMUrtCtObAfVDU\n AhCndKaA9gApgfb3xw1IKbuQ1u4IF1FJl3VtumfQn//LiH1B3rXhcdyo3/vIttEk\n 48RakUKClU8CgYEAzV7W3COOlDDcQd935DdtKBFRAPRPAlspQUnzMi5eSHMD/ISL\n DY5IiQHbIH83D4bvXq0X7qQoSBSNP7Dvv3HYuqMhf0DaegrlBuJllFVVq9qPVRnK\n xt1Il2HgxOBvbhOT+9in1BzA+YJ99UzC85O0Qz06A+CmtHEy4aZ2kj5hHjECgYEA\n mNS4+A8Fkss8Js1RieK2LniBxMgmYml3pfVLKGnzmng7H2+cwPLhPIzIuwytXywh\n 2bzbsYEfYx3EoEVgMEpPhoarQnYPukrJO4gwE2o5Te6T5mJSZGlQJQj9q4ZB2Dfz\n et6INsK0oG8XVGXSpQvQh3RUYekCZQkBBFcpqWpbIEsCgYAnM3DQf3FJoSnXaMhr\n VBIovic5l0xFkEHskAjFTevO86Fsz1C2aSeRKSqGFoOQ0tmJzBEs1R6KqnHInicD\n TQrKhArgLXX4v3CddjfTRJkFWDbE/CkvKZNOrcf1nhaGCPspRJj2KUkj1Fhl9Cnc\n dn/RsYEONbwQSjIfMPkvxF+8HQ==\n -----END PRIVATE KEY-----",

// PS384\n
"eyJhbGciOiJQUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.Lfe_aCQme_gQpUk9-6l9qesu0QYZtfdzfy08w8uqqPH_gnw-IVyQwyGLBHPFBJHMbifdSMxPjJjkCD0laIclhnBhowILu6k66_5Y2z78GHg8YjKocAvB-wSUiBhuV6hXVxE5emSjhfVz2OwiCk2bfk2hziRpkdMvfcITkCx9dmxHU6qcEIsTTHuH020UcGayB1-IoimnjTdCsV1y4CMr_ECDjBrqMdnontkqKRIM1dtmgYFsJM6xm7ewi_ksG_qZHhaoBkxQ9wq9OVQRGiSZYowCp73d2BF3jYMhdmv2JiaUz5jRvv6lVU7Quq6ylVAlSPxeov9voYHO1mgZFCY1kQ",
"-----BEGIN PUBLIC KEY-----\n MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu1SU1LfVLPHCozMxH2Mo\n 4lgOEePzNm0tRgeLezV6ffAt0gunVTLw7onLRnrq0/IzW7yWR7QkrmBL7jTKEn5u\n +qKhbwKfBstIs+bMY2Zkp18gnTxKLxoS2tFczGkPLPgizskuemMghRniWaoLcyeh\n kd3qqGElvW/VDL5AaWTg0nLVkjRo9z+40RQzuVaE8AkAFmxZzow3x+VJYKdjykkJ\n 0iT9wCS0DRTXu269V264Vf/3jvredZiKRkgwlL9xNAwxXFg0x/XFw005UWVRIkdg\n cKWTjpBP2dPwVZ4WWC+9aGVd+Gyn1o0CLelf4rEjGoXbAAEgAqeGUxrcIlbjXfbc\n mwIDAQAB\n -----END PUBLIC KEY-----",
"-----BEGIN PRIVATE KEY-----\n MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQC7VJTUt9Us8cKj\n MzEfYyjiWA4R4/M2bS1GB4t7NXp98C3SC6dVMvDuictGeurT8jNbvJZHtCSuYEvu\n NMoSfm76oqFvAp8Gy0iz5sxjZmSnXyCdPEovGhLa0VzMaQ8s+CLOyS56YyCFGeJZ\n qgtzJ6GR3eqoYSW9b9UMvkBpZODSctWSNGj3P7jRFDO5VoTwCQAWbFnOjDfH5Ulg\n p2PKSQnSJP3AJLQNFNe7br1XbrhV//eO+t51mIpGSDCUv3E0DDFcWDTH9cXDTTlR\n ZVEiR2BwpZOOkE/Z0/BVnhZYL71oZV34bKfWjQIt6V/isSMahdsAASACp4ZTGtwi\n VuNd9tybAgMBAAECggEBAKTmjaS6tkK8BlPXClTQ2vpz/N6uxDeS35mXpqasqskV\n laAidgg/sWqpjXDbXr93otIMLlWsM+X0CqMDgSXKejLS2jx4GDjI1ZTXg++0AMJ8\n sJ74pWzVDOfmCEQ/7wXs3+cbnXhKriO8Z036q92Qc1+N87SI38nkGa0ABH9CN83H\n mQqt4fB7UdHzuIRe/me2PGhIq5ZBzj6h3BpoPGzEP+x3l9YmK8t/1cN0pqI+dQwY\n dgfGjackLu/2qH80MCF7IyQaseZUOJyKrCLtSD/Iixv/hzDEUPfOCjFDgTpzf3cw\n ta8+oE4wHCo1iI1/4TlPkwmXx4qSXtmw4aQPz7IDQvECgYEA8KNThCO2gsC2I9PQ\n DM/8Cw0O983WCDY+oi+7JPiNAJwv5DYBqEZB1QYdj06YD16XlC/HAZMsMku1na2T\n N0driwenQQWzoev3g2S7gRDoS/FCJSI3jJ+kjgtaA7Qmzlgk1TxODN+G1H91HW7t\n 0l7VnL27IWyYo2qRRK3jzxqUiPUCgYEAx0oQs2reBQGMVZnApD1jeq7n4MvNLcPv\n t8b/eU9iUv6Y4Mj0Suo/AU8lYZXm8ubbqAlwz2VSVunD2tOplHyMUrtCtObAfVDU\n AhCndKaA9gApgfb3xw1IKbuQ1u4IF1FJl3VtumfQn//LiH1B3rXhcdyo3/vIttEk\n 48RakUKClU8CgYEAzV7W3COOlDDcQd935DdtKBFRAPRPAlspQUnzMi5eSHMD/ISL\n DY5IiQHbIH83D4bvXq0X7qQoSBSNP7Dvv3HYuqMhf0DaegrlBuJllFVVq9qPVRnK\n xt1Il2HgxOBvbhOT+9in1BzA+YJ99UzC85O0Qz06A+CmtHEy4aZ2kj5hHjECgYEA\n mNS4+A8Fkss8Js1RieK2LniBxMgmYml3pfVLKGnzmng7H2+cwPLhPIzIuwytXywh\n 2bzbsYEfYx3EoEVgMEpPhoarQnYPukrJO4gwE2o5Te6T5mJSZGlQJQj9q4ZB2Dfz\n et6INsK0oG8XVGXSpQvQh3RUYekCZQkBBFcpqWpbIEsCgYAnM3DQf3FJoSnXaMhr\n VBIovic5l0xFkEHskAjFTevO86Fsz1C2aSeRKSqGFoOQ0tmJzBEs1R6KqnHInicD\n TQrKhArgLXX4v3CddjfTRJkFWDbE/CkvKZNOrcf1nhaGCPspRJj2KUkj1Fhl9Cnc\n dn/RsYEONbwQSjIfMPkvxF+8HQ==\n -----END PRIVATE KEY-----"

    };

    static String[] jwktests = {
    // Samples generated from https://mkjwk.org
"{ \"kty\": \"EC\", \"d\": \"ENPEV1zcqHGAetO_kiZ00MsoMjTYX073Yw4q-Cdzlso\", \"crv\": \"P-256\", \"kid\": \"key1\", \"x\": \"wDUiranalL5MJro9XEBLkf4Dppfl9UMdiktIKqIxgQY\", \"y\": \"sYQc4t9CGz-zB-b98XgLRPrvIvUXVadl3SAIT-5f_EI\" }", // ec pair
" { \"kty\": \"EC\", \"crv\": \"P-256\", \"kid\": \"key2\", \"x\": \"wDUiranalL5MJro9XEBLkf4Dppfl9UMdiktIKqIxgQY\", \"y\": \"sYQc4t9CGz-zB-b98XgLRPrvIvUXVadl3SAIT-5f_EI\" }",        // ex public
"{ \"p\": \"9aRvGHhJ-pIfZBDNW6R868-hoTeYZGfbbguTF5JjMt5jPbJvbgA-ESzAM6ph0xIR1F9KavxTkMIMlH_xwOgGcDjmH2wANmCxv_om9u72ntHJSbN4EaT2pGJ8QMdUsRaIi21nM_7R2xwYE5MOF2HJRl5jnD7kZ9syXY1URLbrof0\", \"kty\": \"RSA\", \"q\": \"wpvOMgAB5MFX3avVXFV0itQ_ucrS4WceHPFtTfvXUiF4ZTtiCKqxvWz6-t8Eg3O_It5k1kZMyEgnyd-aB7A7Vy4EPm5vui5LdKOGeNB3eNmV2M76WDyU7UDcrxC8f_M6lnYBdhL3QsFrGrjU0ja1WfnSh5JlKUiFv5ZM0hasnoU\", \"d\": \"UotY8V4Io90NaTkmd0DFeolzfxhfSgdhVA9p_vwpkKmG8vy2XyND35U7tp7j4pBAP0m7rJ3Q1iCW01xzXvmNsEwpJNE5IOHi19OCRspBr9fXFZDu2ghtMAPlNOBzq2HBtikNeHAZxPC1Bvz0YKRHwjgUoii_IxriPZDOSm0I_GoYBUGbg4JBgYaLRiQdKrigXSx0bMDMrrb2nsCDOoIQie9Ohwfp38PudFXYkIPWEUJyQF5mOgWBnF4ctiqnc2jH57204O5jdQCiETewmAnVZF5pzVJ78FtYCrxnEGfQ6Pmt2ZbSvwiXqTrTeGTiQfP_IX8OGTHn0asfH83sfsI5YQ\", \"e\": \"AQAB\", \"kid\": \"key3\", \"qi\": \"FgG0sEkmWvuZBnNSZfJdQ_5zpvb_JvGX0VymKlle0ZputVGsoR8S0AWmOp16Ze8RgkoAHnaI5ldAJcijK_RggGVK7INZHt29u2pNOrERq1NVaeXFHVZXg-M2k74Dard8Apaa-p-Vd6xxGE00Cfugyx3Aroxu5iiNfS05CoMphaE\", \"dp\": \"BXMme9Z_Xa3MQH4W8EeP9fNfVKJVITUkqyZalxVKWiCQ9xd0XW5n8h2aDgtjrDkkaB_NGN6MRBPqD4lfJbaAHhFFGcFdp9cxWl8EakoW8qMY4ie3LD0Ks87zjsRdXqhvUxiUR5UJtuDCr_x9PYuP2Z3Se-7xRlSYo3a1PekJBUk\", \"dq\": \"XI-ElC2CuQ3ov8tR8NfIVZu8L58zi_fiz7tY1MWa1TqMLAIBj8O4RDOIcDgQyoEMAQasCWpobL49Bnxuo4eDweRmKleWloQcaX65n87vdDBxYCyZz8wIvYujKzUXovfJ_vRjn4hHyJ18VrpgpxdUTFZRdx__M5KF4ukW4rncIo0\", \"n\": \"urwcmhp-GWTfqPaTyfuxs1K2SfHPxc6maIcCDbvSF3ZbDuVOstWgw-cA5moHDABg2LGuBBP73SkZZXT4KmOdC4WSL5AAub1DOi8i0cDW22lppYwi8u7VCiv7e3_EJ_1t6RWTdnqjT9pWa6YbcXywlHSieWS7g5jDeO5Xy4D-TGsp1R4IikSrqiMMwgVHA9apksMPRY27Ht-TIhuvMTT6tGQUYLwjwjqIR7qfypnF24T3MfSFv6VhqdQp9Mrx84HNxOG_WH7cCMcUyOMOXVidOsEwSuJVQ3esD4NMY610RmMsPQ33lYAB-ZmPxrzZgKfHnEqRHMVOyiQI8lRfCmFOcQ\" }",// rsa pair
"{ \"kty\": \"RSA\", \"e\": \"AQAB\", \"kid\": \"key4\", \"n\": \"urwcmhp-GWTfqPaTyfuxs1K2SfHPxc6maIcCDbvSF3ZbDuVOstWgw-cA5moHDABg2LGuBBP73SkZZXT4KmOdC4WSL5AAub1DOi8i0cDW22lppYwi8u7VCiv7e3_EJ_1t6RWTdnqjT9pWa6YbcXywlHSieWS7g5jDeO5Xy4D-TGsp1R4IikSrqiMMwgVHA9apksMPRY27Ht-TIhuvMTT6tGQUYLwjwjqIR7qfypnF24T3MfSFv6VhqdQp9Mrx84HNxOG_WH7cCMcUyOMOXVidOsEwSuJVQ3esD4NMY610RmMsPQ33lYAB-ZmPxrzZgKfHnEqRHMVOyiQI8lRfCmFOcQ\" }",// rsa public
"{ \"kty\": \"oct\", \"kid\": \"key5\", \"k\": \"2umbJilxdwwLILTQ4n9ioJS_TxPvjEJd8eXiJSk0QCiuzSvi-9zuu-laBd2kqI1rrtpm00XkD6s0Q39lmkpMHAXrqiH37CkqLVV5iihEeOZYHAPtVoTbRcgo7UPbtFV9wZ_QJ8qNNYbX8gi05kG4lVR7wsU01he0ZPR4uaoonCN0iXUANz1kCY04Z2Hx4bLd3P1iZgWmwbI5j07ifq8JXQdp2RINZ-x3bPRz2ozWsKFGluoiD9xVHVxqG9rbjfa5F-qmhipayQAsV8B8qQjXk9ZiQIWcoC5tYjyFGJ4jfsqmRwD1nGdJBmkw-zZ0LkOKbH1De1FOCnbHzjQWJ4gpnw\", \"alg\": \"HS256\" }", // symmetric
"{ \"kty\": \"oct\", \"kid\": \"key6\", \"k\": \"Y21QPWoFn4SbuEQNi0sIN9AhAPP8yZwUnN_Cycmx2usFAmB03QGNja50kHuMgGH_Vdy6jJ0sv3xu8Hu6Dm0iIsDG1fYeF2eFlx4IGs1XjlEIGMh4IIxLBSGCUOtcNfNC3wu-Tef82DzXsrIwkDyHSVeBUAvex6_gwPw2B4ZzgfYbBspl9-q_02yyNeE_Jgy1AP7ZEvK3PZCWsKgb0rx7WCQoBtp3RD5MvQJE3fIooVToyG__hJqRnbZf2zAu2fEj5mNY2SoHNjOe7RKSqKIcs-SNk_SVr9dAK4AnONCwzxJeiz0FqQacz_wCLih8h_R2VjaOUSyHQXkGZKCz5IFn8A\", \"alg\": \"A128KW\" }", // symmetric
" { \"kty\": \"oct\", \"kid\": \"key7\", \"k\": \"trrRo5pGgYEXpM2ZW2YfeFHsZMpJVsOCgQdgktFNUc7LX4FDwnsRqXJP_r6FEZLjFd5pco4B73KJ0H0DUEVCvjtCfyG2Sfx66kGsbuSCz8dSui2UzqW1y10tjTqe1BgLAsKmIWHeylrLR21gZkM8NrR3p6XTbZaEf50R5WrCwsawSDkGnBIbjtO0u89nuxtMTWlhdCHrMVLvR3UMiMxGHU72OBYnnMjbsxWhLdiupzEZ4WSvsu0ZBjiiX1EUxlMEK7YExgX0cvwOc6x80U05sv3_Dzj5jf_UCJ_F1t0JRhn1aJ3NkvgOq1_RRWUrvfQSVPF2c7VuONdO883ky9_QAQ\", \"alg\": \"A256GCMKW\", \"error\":\"Missing symmetric param iv\" }", // symmetric, will fail - needs "iv" and "tag"
"{ \"kty\": \"oct\", \"kid\": \"key8\", \"k\": \"ARLFtI8uX4AFgdyJfu0nD75RhMgAZvEjfBXrbyGl8YZAW9xbpj9NpO263cQx7MM_nZut2WgnYfuhTUknZ8nhgW0Hp6dGzJD6LMxBz052eW2w9OkE8ClI5rMKxoPVWGMSxTcCu2nMuoBt8c59ZmluUnVp2uPrPDckwSJyKYmdOyRyjsHaI4ODO1fK6lb-pNOEvmgLXcEJDWWkkgjBML7XOzFR5kYjbWp7JrEFuOh3RM1UBQIGwENKGldULUOeNnOAT5TpajhSxvEqad3K2UYjU0gxdueo5vyUDzaxwaNYFufiMXsvfk7YQCwNXtmvY1gkc1Rrssi3KpzT_LVUWdVHGA\", \"alg\": \"dir\", \"error\":\"Unsupported symmetric algorithm \\\"dir\\\"\" }", // symmetric direct
//"{ \"kty\": \"OKP\", \"d\": \"Wy2kgGMQa2fs6-uVVS8a2tcV0XqTgTjvDDHIlLbN86s\", \"crv\": \"Ed25519\", \"kid\": \"key9\", \"x\": \"Z2TXyhGHLWyLDSyzkvgo4WTBCXHdACM99P3aCUKnHAM\", \"alg\": \"ECDH-ES+A256KW\",\"error\":\"Key type \\\"OKP\\\" not yet supported\" }", // ECDH-ES-A256KW key pair - requires "epk", "apu", "apv"
//"{ \"kty\": \"OKP\", \"crv\": \"Ed25519\", \"kid\": \"key10\", \"x\": \"Z2TXyhGHLWyLDSyzkvgo4WTBCXHdACM99P3aCUKnHAM\", \"alg\": \"ECDH-ES+A256KW\",\"error\":\"Missing EC param d\" }",  // ECDH-ES-A256KW public only
"{\"kty\":\"OKP\",\"crv\":\"Ed25519\", \"kid\":\"key11\", \"d\":\"nWGxne_9WmC6hEr0kuwsxERJxWl7MmkZcDusAxyuf2A\", \"x\":\"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo\"}",  // From https://www.rfc-editor.org/rfc/rfc8037
"{ \"kty\": \"OKP\", \"d\": \"p6fvoLBgG5_J0vyeBeIEh9SCFaPVQzLQTqMWIl2sKyM\", \"use\": \"sig\", \"crv\": \"Ed25519\", \"kid\": \"key12\", \"x\": \"GuxO6U7VRen6E3DDjjIbqg0rSWYGvTu3uEyw6MiG7tg\", \"alg\": \"EdDSA\" }",
"{ \"kty\": \"OKP\", \"d\": \"ugglqeU0-dNmTHNc2eXoWfFXzkh_EEc9G2KPXeK5T9wuyz1SoeC7Tunov3-qYgZhBHLlK_Mc2NaM\", \"kid\": \"key13\", \"crv\": \"Ed448\", \"x\": \"xf0Dek77XwjrmJpUaugl9AskH6m3_tRSKnAV_ufxJApZa-r1nQ7qrDVdhSXVtxzoZMZxry0O8PkA\", \"alg\": \"EdDSA\" }",
"{ \"p\": \"8obvGqSKpiYSNHgvZGoFvKvNXbQY_H7YEiWrcoBR6tqu-oiJtbLUsdalbHot0chd98423IJkOFXPzRMsTk3bOpVAW0clztw1FyBG-JzWlD9L4_aKo3nHyQeF5NxBRVAdefjJaUoIPTxc-n66VtW_qcXvYZBKtmZ7aTX2ih2OdiM\", \"kty\": \"RSA\", \"q\": \"1xaHNfRbbtFCCoYF8JengYaIJlTxvR1IPbNDKbOCxM3vHEH5cz4NOWdTcAkPNtM0ukuBcDaEj6EwPCadF5KsrExZVKR7sw8TcnzHRfMZ94YmGQUCkZYTbp2tQnYF8bOwWnHf53rmpouJ-t1uQWkoedeePxBpW9920sMUSqHhqs0\", \"d\": \"Tm07UmvsSBjgEut5ludbSVqT-wJnYh8YN8-kTEemnN2LB41R7t_c-o53E7uCtWtnEYa6X890q2BHpnHwzxlUatszQh0h-h6sJn_5NDMWrpdbdwJ8yT4-qOYuNmnJh6E9izCR3ujSyMfESZC3p5n4oHb45mVcvtiaokcKU_G_dNxt0ueQ1n1S_C8BcRd8XYoTOtM-S5sol7mDz_OtShwFZiTaupg-Bef_NXWtq53jXtr5c9T1ADM2jsSqSHEEIGFBCZ7dHHKP57zh3rk-GX1R4YxkM_TEjDVnMbck74VPZcKaJdkMYh-Wv272491OA3oKaeCi2h0FSXnh8V9AIYd0wQ\", \"e\": \"AQAB\", \"kid\": \"key14\", \"qi\": \"UTHC8GGZr78n6t_wN0gDnmBqQ8ggAobDSFC_W5a3ig3kQs1IXr44qUZsAuHezsxCYWd5xdNCxzL6_ndiuYuZ0GlQcKUpvg2IL9i_Kie2GZKWXiQutluzKqtbBC_PRFueLznXArWoYSx3FmZJ-ON0Spg4wpdikgEUCxqYG6vN77s\", \"dp\": \"qKTzt08bqpVmq123dtfSzXOaBsKpbUq27UeMhL5-OBWA_23adKK6DD_6IAiFvA7caOjYdVWmYxYova3LbVfuTjpi2hYLUGEH-eDT-ST2gXfSSd6yYSLZrgyxKoeOS6h-FsbNJ433VL2Y2gcthBx2fpPI9crkaRmyT2La4QfUfIk\", \"alg\": \"PS256\", \"dq\": \"t--XD4f5jQo87NzyRW6bm8PduNIZrL1W4xTjEc1mvkDSA5tT5L6i9n0rOHs23I6_37Tka37j3CRfKIFpBBi1u97K4fyLoHP7EIQusd4UTb4V5H2JVdE-qvECkvx1Vt5wqNgLP1y11KUuCS_FswBn0dKXjJzPTPcMGW8kcjJ_1t0\", \"n\": \"y8SqgDIAXZYLpifpSkz-I8jDFMTVvp_kTWAC4ayQPmXlQhghFoIw0j4MM4CX1uW9OoR27qS0MecF_sxanxPth9TJVIZ_Dwb3CkWpAQSTdnCih-U-t-6K6WV7S_8GbbgdriOIUSN_kdj_cfbBpbr9Gg8I0sIU_YmOHbvDirfat_KM9R-jozEJnU6J2n43lazDe7dx_7gx4p9qAZq11jqosrVRpmltnGwUTLeyD-ZkFCCKyoqD_E0fWR0ITaGUK8UN4gFbbsDkWQXlEqdsI5UTg3_ZbIAPJ6pfvtb8hPcRIQ26Mi8P7btsLKONN_1ByJwfG8Hh2m4TzPYA-pjaHUrYBw\" }",
"{ \"p\": \"7yooZEH3a-FjPX-UViK6ikc6rCLxWG09I8Y51nknvARyKSnnSgjcznnrx4cEFciPtqY3kQyUqSKZKMwMRJNifFP9Fvr8q0v9babo3RqagSu9AZi3pzukZx8dIRWyBiXOFRPONPwZfwGpbdnpv9cT1cpfr3i8M8CBZP2u-VzSBV0\", \"kty\": \"RSA\", \"q\": \"oTAwDv0rWNmH0vHIun-NHKA1rkWQAARFwQVbp890OjSulwTudYCKTLpbTtok5KB5-RruLjY4zrUPMEj7iCjrI6eP7Yno6DBT-f-MRiBATXR-U-_ybQ-FVwSDovsjxz0FGNMdeixVYsFNWU_nEvm1EPfgvtRpr85GUBHj0p09IZE\", \"d\": \"lke1uA-sEW68yQibR4N0l8SJ-XsU0GqfYaWN8FNfCJt_wuEaR-HjWIXIjSEc_wlSiYtPgt51jkPGNdqJctEjA3z8wUHel-Kd9DBDjBddfi6c6wZodNC4TAIR7nB5jeS-pgRIjmFNoxoRLI5kpSWHi2Q8EwjywxzswVJPhw0MkhKNPlOnBypMTDk0V2XFKj6DxzRywR1rMy6f1ONe738i9jnFuBBsIPwBiJOo5Kx01iZVi4maQ0OUSaGsleqKEFLhbLJqnFlQevouvapsjzrissetC30wGggJK3tgZEnU4fCbFhSRMibDR3FbqUAsJ4uteTyHjIqSjqM2z1XKU8_BQQ\", \"e\": \"AQAB\", \"kid\": \"key15\", \"qi\": \"PxjLppYOvcpqMPc3YfyBJLMIhNtB7HjZI7LvvV0BqFwdyltXIMjyxkvsOLnKJ0YOgqZvIyk6CD-uBWtMBQIGY06ezRjJGbBY-6DdItbhgyjjNPihqFh9Dd59hjO9RlC70tAkrt2xooe2e4C5IK-yWuwPkHg-wCZkM-bXbuB5wZM\", \"dp\": \"sijBdg399gssFj_XjGLKev-coWZWSvz1MpUTuMT_6HuXXzqr5Oa9NcJ09WmKjX-eLv2bHx40D9qKJW37JYp1LxCR1HCbkC9HVkuj5DFRLzAZ1_qftKAlU_xFgsPaneHDpsfeMHIrvATM5dwS2KmrSPM8XEagTBz3RvgDRBb1DLE\", \"alg\": \"RS512\", \"dq\": \"UEpVlhHL9s0cmBnyF9wTaW_wbWefLDL0oApQNo3i4l99nCJLueIWgdPOSb_l4rCBMXGVtRUzRNvxveaMzK09O5xq2DL6_jWcjwoZUJEeFrbxelQqmOLDU64e-B9LGiKuGEiJBWNyAOgy5Esl2lDiPZqLq-LY5kbExXuz1SSX39E\", \"n\": \"lpaINIivJBiO-g1NOT15CxvnPSfT_KGvzJo7tudVi2v3dfEjcGWP_jg0RhIRgz-BkS5GGJi_yFxsff40Xo-2fm-wRNJhJjoSAilXNqpQESUDVF4oZjZHT8bQOrDl5c6w7K3k6pe82YS2OUtVVuBPOhT0Wjfk1fLXB_YzwkB_ISzrMWsVSCLFMa5Ea_hf4net4Mge9y-lUAxVNoA-ER0z036u7bUNvnDLwZWxsC5hulIWECvzSLJfUSBqrcNGHTnsfuEWgQF6z8pvP0CvNDrPblDGf4SPZFX1T89EGQRGs5-lJ4_rwqL0PsW8ygjlWiIXHUm66wYIGvpQzQjT9c8GrQ\" }",
"{ \"kty\": \"EC\", \"d\": \"ACOMiApySPNLkh4PU8vur5SQI_ylWXmCVV1paoC_nReCqJP2m2yesUcZgfPET7RyXwvxUaBYep2zOXauPHobS7ei\", \"kid\": \"key16\", \"crv\": \"P-521\", \"x\": \"ADX7tylfZslKhAWCEVBpImc83tnKbeC4sZa39nIJ1bnLArpSmUZQvkJgGNgSbHuKUOq5cr1XqhUF-FZMHJwpoeOl\", \"y\": \"AEecCK0Nb2rzsEZrTJnyUbCL9iHKDuzsE7k4QDU3JOxa7DevW3H58dWwQfEfK3wg9eIfZrroGGVXtscLj5MP444L\", \"alg\": \"ES512\" }",
"{ \"p\": \"2IZ3NBBj5K1jhIwEZfZQHTiPITtWMUlKFne9jDbKco4Jagde3b7b1dyQBxvqc5YYWshHXAMzse_H6HGJ_dSDJHG4Lcc4RxH69_HYDf6lqFwzteEzsheSulV_AQ0LTHYDWaSNNa_VL7U_PSxXIz1yWdZ50GCC62l6fPhZwPXvr1Z7J593E8UlTT8z8WxTO6APgXgTZ_tBl3S8LTc1s-UShzslbyMB4dXWzDB9WVfHf6Ct74MilM_Rw0zGS3jq5xp3KPHCHOpQmD77cQKruxQtCxXJNBWtk1Ag2Y8U9PKLuqbMVyiS2blMSOWclRCC501z0GZYZrltASWvRMYVBC2fPQ\", \"kty\": \"RSA\", \"q\": \"yQpwn_jshMUeAxScZLruJUHvorExdHijWY4D8xmM-QTBd0KMlDEWNFjvGl50-5VchRqeVIWxsiDPuRQ112n-cMwIMfYwkb2b2zJiMurYWL2tCVf-W0jN52M0DDe9239AHK3HLkAJsKATlOCa98xypi2Wn_lxIgEGVVrXlhCq9eYB31LluBrz0PPHzsCNINWFyzFn0saWpyYVT8e1hqpQlBfRqfZR6e8yPc6S_Kx8sd9EU_v5baVCW-IvgMl46houS60xTkGl65qWBOr0FHj0ksZ3ePOmNW-Pjq_KifNFTy-zanK0miBHt2s7RviPpldhTgkkOqc9hocD5NzJhcgslQ\", \"d\": \"Fh28ZJuU01MVbwmUowJjjgfMEiPuUbkiplLhrgJt9il1AtHUM_Z74w4TUHGTbBLO7cSrn8nOIQnTZ2trrL2PJ4Rj_ELzWTgJGEltkP9dLHXNF1rEIhNbIskAxoCaSToY4SS72oR7njnAY06C7nRQ7BuSksFXKsmH4xwykSoBoxx0siTfewux-rmLlCAzSCFKLc_aFj77yJ__ork_PmcbN31APlK9hc1tihjCsBKiQZxqPVsXAJ2rECbVwQ6b__6wgzm6SmJF4sODbS8volz0opNSXHU2Ax2pBdHLA8Br46Zj6sATqfRMWqXAGbbRTevm8NIe1ipjsRGCOSClpw5QUf7fQvAK1kR4lLQdstOeZVC7olw7DUBptHoukpJCe2wugXj90IqZU90wAvE-Cz9GQ-lvOvaSeJSQELGCkBJDf1i5aMKXzCCoJChQkHq9KlBaS-0I9f7I1xdw_v4UUivCV3ah2Tx62pxTTqSk7y7kjP5x7cG3vmntl454shrzfeNqlk-Nvns7rEFb9dZ_gr2G7WEPeJrIdVfzA6aXKfiBFvCcOjA8E5k17PHl904SKiFJEpOePp3gfcfR1pnyP-rAviWZ825V97hz6ie6bBvNGhJluvxftNfvsEMzZ_pNeGUR7ocJtRx-i6GTN16UKFoSWNgwUOOQeohQpUV4z7z3zEE\", \"e\": \"AQAB\", \"kid\": \"key17\", \"qi\": \"OePBzBsKWYcOkuUk4fxUjjYtvuvwM2voamXuDEbiHxBD7_Ew6iV_hnyg6gRFwur-7wRoInOt9QPqo4aS8k3-TIcEYtxKa6HlxVL-vptgQbNOgmt_Un2siElD9CKmXiIYCRQDfmrqWBDbOrfxXp1LVNSTODVttvlgndy5XEpX5i1o8n7ZlJsg7RcOEyNGeS73A9gac0ytQ4QFQ4aZtpBvGFTMQUdAdWATnnrgzAlomc_snZUZwao406LB1wU1PX24ZRBNyQnCDwDxHF5yxdgG1xwCle54L_m66T9uqD57O-LgpnZOd0jzSXfLLo-9kVMfgO3h0BvfRUtPQBLiiv_JIg\", \"dp\": \"SmgC5KBRQ48V6MGY7VT0eOYrwVa7qtotnITvXRSjAzSuJYJ85iSlNNvDnr3IFopYujGi3Aq5pAbrIoSJz_FMU6TEju6r9zhFjxjLjxuX38A3gOPvuN6C7IdeyoSqAk93nUF_yRgwYWGeZq4rD5AZEKg0WhggRTeNAQ7zAO1CvXUxgw84g1G3HNGATxFTlOTPD0WtHYlpI9vYoo0bZkNs143KsmNMTUgHtUlgeehBRSl5PBpLMuorgjqK1fqqdlpQ4oAZnYgjw_24uEAbtYEcbXfCGaTtbfcWIs0ZfYcv3ydchwMJW8piXZaR9WOkKEokZ1QUQtCrlb5WeYK8TCBFXQ\", \"alg\": \"PS512\", \"dq\": \"CLGM4T4QbEVh4Nw5TxnORootZKmo69JzreG4RgDq2bHc891K2vWsciMOY-OFx4NT9uWJxznozz0f_m7jTSdmO1Z3XEG7I_JBFR9-o5kINf4tx2OouiXR0Zm2nHikmyNLkHHrVRKr2Jlzf1KfIOwUYR9DjjNpMwE8VuCQxgp-eheyKQgaqvVvaa_BCi3FbJAPMxQ12yV_SkcZUNPgA6zFw0GsbV8bd7RCRe3y6T5EPiTbfY494QHudq63QyhzNBxhuCALK8km9aM_rYLDf0cjvF0cuXw_ybvDR3ZOmdyNOLllVssTCD8xGrtmWtFQ1O6eZJN8n5Sv0mtwPatWg5R-aQ\", \"n\": \"qgpoGqscMrFWMqp059MHGbBLvbABnIPLm0F4wkF4gnH617ic00WNM1rj9wRSGWoqizhiKT98qWJH4EAAucKcFT_lHDLsiza6saFFoQEGuNn3BlzYn0dk-YRfIRPYaV3iyQc84lZwSLh67Rs3h5sSJw68jlplg84bDH8HfTGyygD0i6PJsrsTSzAfKgoSwBt1lOdeuf7cEaccUAi1Y0yShKOcZfYGQ1y4v3A57ajaxEE6E58Qkb1RQ3NiJOQpSIUe7mhwkQP48YGRQoLZAPqQYkQkg2kwbDqCFjBRYBaS7D8tFWIZh2MOvpdytZsuJxQ1PWtEXQS9q9Lt8HgYQfN81QnB-Tn2CtXFbpHpJ9voaVxV0-82sgms7mUun4Zu58cr47ttC0rUoSWYXl9gFCbGpFFDvC2idWY_HvI6eXX3jpBWqcoSqheHDeOARaUmq7Mndgza3P3YNG6MUutAOnFA7EarORIClXONRhXKv4ZtmaXDzMtV_jGTZHj0bSefMjfuVbA2qIIt7YqQa-g4NLl9uCMfQphfGbp4kaBmIOrigFmG8i_AbNJsM-uwxjdOoj1ebPBFJy961amu-QjzhQDq6aP0orLuozdvc0wFC4PnpYGA38sHrpfAS7Qon8HaubBo7Vhg5lUWAguS0t4vAHr8A-0YqglVE4x0hqhRN16UKoE\" }"
    };
}
