# Java JSON, CBOR, Msgpack, JWT, COSE toolkit 

The BFO JSON package is yet another Java JSON parser.
It supports JSR353 / JSR374 (both the `javax.json` and the `jakarta.json` packages),
and also has a custom API
which adds support for Msgpack, CBOR and CBOR-diag (the `com.bfo.json` package).

### simple
* the `com.bfo.json` API is essentially a single class, with a few helper classes that are all optional.
Items are added with `put`, retrieved with `get`, read with `read` and written with `write`.
Collections are used for maps and lists, and you can use the whole API with no more than
about 5 or 6 methods.
Which means although the API is [fully documented](https://faceless2.github.io/json/docs/), you can probably get away without reading any of it.

### fast
* A 2021 Macbook M1 will read Json at about 120MB/s from text (80MB/s from binary, as it has to convert to UTF-8),
and write at about 400MB/s. It can read CBOR/Msgpack at about 300MB/s and write at 600MB/s. That's ridiculously fast;
as part of version 1.4 I had to change the large-file benchmarks to use microseconds. A great deal of effort has been
spent on removing buffer copying and avoiding slow codepaths in the JVM - I don't think you'll find a faster Java API.

### streaming (new in v2)
* As of version 2 reading is block-based. A Reader can be created, and blocks fed into it
as they arrive. This is new in version 2.

### correct
* the API has been tested against the Json sample data made available by Nicolas Seriot at
http://seriot.ch/parsing_json.php,
and has been authored with reference to [RFC8259](https://tools.ietf.org/html/rfc8259).
CBOR support is newer, but has again been tested against [RFC7049](https://tools.ietf.org/html/rfc7049)
and fuzzed input, to make sure errors are handled properly. It's also been run against the
JSR374 test suite.

### self-contained
* the API has no external requirements unless you're using the `javax.json` package, in which case you'll need the Jar
containing those classes.

## Features
* JSON, CBOR, Msgpack and CBOR-diag serialization are all available from the same object; read as one format, write as another.
* Map keys can be numbers or booleans as well as strings - required for COSE/Msgpack, these will be converted to strings when serializing to JSON
* Listeners and Events to monitor changes to the structure
* Easy interchange between the `com.bfo.json` and `javax.json` APIs without needing to serialize.
* Flexible typing; if you request the int value of a string it will try to convert it to an int. If you put a value with a String key on a list, it will convert to a map.
* Numbers read as ints, longs, doubles, BigIntegers, or BigDecimals, with the smallest type chosen first.
* CBOR/Msgpack binary strings are stored as ByteBuffers, but will be converted to Base64 strings when serialized as Json.
* Option of mapping Json to more complex Java objects is possible, but not included with the code. By default data is retrieved as  Maps, Lists and primitive types only
* Java Web Token (JWT) support class for reading/writing/signing/verifying.
* COSE signed object support class for reading/writing/signing/verifying.
* Java Web Keys (JWK) support EC, RSA and EdDSA public/private keys, and Hmac, AES-KW and AES-GCM-KW symmetric keys
* Java Web Keys (JWK) support for ML-DSA (provisional) and SLH-DSA (extremely provisional) - both new in 2.1
* Experimental Yaml parser (derived from https://github.com/EsotericSoftware/yamlbeans)

## Building and Documentation
* Prebuilt binary available at [https://faceless2.github.io/json/dist/bfojson-2.1.jar](https://faceless2.github.io/json/dist/bfojson-2.1.jar)
* The API docs will always be available at [https://faceless2.github.io/json/docs/](https://faceless2.github.io/json/docs/)
* Compiles under Java 11 or later - the API supports EdDSA keys (new in Java 15) via reflection.
* Or download with `git clone http://github.com/faceless2/json.git`. Type `ant`. Jar is in `dist`, docs are in `docs`
 
## Design Decisions
* Json objects have a _parent_ pointer, which means they can only exist in the tree at one location.
  They are directly mutable, no need for builder classes.

* Mapping JavaScript objects to a Java object can be done by use of a JsonFactory, however this is done after the object is read.
  Most of the complexity of other Java Json APIs comes from the mapping process between Json and Java objects;
  if you want to go down this route you have a trivially simple interface to implement, but you're on your own.
   
* JavaScript is loosely typed, and this API acknowleges this: if you request an int value from a Json string,
  it will try to parse the String as an integer. If you don't want this, see the JsonReadOptions to turn it off.

* Json in the wild has many variations - comments are embedded, maps have unquoted keys, and so on.
  By default the API will adhere closely to RFC8259 when reading or writing, although this can be changed.
  Again see JsonReadOptions.

* When reading Json numbers, they will be mapped to ints, longs, BigIntegers and double as necessary.
  If BigDecimal support is required, this can be turned on in JsonReadOptions

* Json is read from `java.io.Reader` and written to `java.io.Appendable`.
  You can read from an `InputStream` too, in which case it will look for a UTF-8 or UTF-16 BOM at the start of the stream.
  CBOR/Msgpack are read from an `InputStream` and written to an `OutputStream`.
  Errors encountered during reading are reported with context, line and column numbers (for JSON) or byte offset (for CBOR/Msgpack).

* CBOR serialization offers two complexities that are not supported in this API:
  duplicate keys in maps and "special" types that are not defined.
  Duplicate keys encountered during reading throw an `IOException`,
  and special types (which should really only be encountered while testing)
  are converted to a tagged null object. Tags are limited
  to 63 bits, and tags applied to Map keys are ignored.

* CBOR serialization will convert tag types 2 and 3 on a "buffer" to BigInteger, as described in RFC7049.
  But other tags used to distinguish Dates, non-UTF8 strings, URLs etc. are not applied.
A <code>JsonFactory</code> can easily be written to cover as many of these are needed.

* Msgpack serialization is similar to CBOR, but simpler. "extension types" are stored as
  Buffers, with the extension type stored as a tag from 0..255. Like CBOR, duplicate keys encountered
  during read will throw an IOException.

* It's possible (since 1.4) to read and write indefinitely large strings and buffers - the [JsonReadOptions.Filter](https://faceless2.github.io/json/docs/api/com/bfo/json/JsonReadOptions.Filter.html) class can be used to divert content away to a File, for example. The use of intermediate buffers has been kept to an absolute minimum.

* Reading Json/CBOR/Msgpack (since 2.0) is block based, to allow reading from packet-based APIs like `java.nio.channels.Selector`
  or Netty.
  

## Examples
```java
// The basics
Json json = Json.read("{}"}; // Create a new map
json.put("a", "apples"); // Add a string
json.putPath("b.c", new Json("oranges")); // Add an intermediate map and another string
json.putPath("b.c[1]", 3}; // Replace previous string with an array, add a null then a number.
json.putPath("\"d.e\"", true); // Add a key containing a quote character
System.out.println(json); // {"a":"apples","b":{"c":[null,3]},"d.e":true}
json.write(System.out); // The same as above, but doesn't serialize to a String first.
json.write(new JsonWriter().setOutput(System.out)); // Same as above, for setting writer options
System.out.println(json.get("b").get("c").get(1).stringValue()); // "3"
System.out.println(json.get("b").get("c").get(1).intValue()); // 3
System.out.println(json.getPath("b.c[1]").stringValue()); // "3" - same as above but using path
System.out.println(json.getPath("b.c[1]").intValue()); // 3 - same as above but using path

// Types
json.put("d", "2");
json.put("e", 0);
System.out.println(json.type()); // "map"
System.out.println(json.isMap()); // true
System.out.println(json.get("d")); // "2"
System.out.println(json.get("d").type()); // "string"
System.out.println(json.get("d").intValue()); // 2 (automatically converted from string)
System.out.println(json.get("d").numberValue().getClass()); // java.lang.Integer
json.put("d", "9999999999");
System.out.println(json.get("d").numberValue().getClass()); // java.lang.Long
json.put("d", "99999999999999999999999999");
System.out.println(json.get("d").numberValue().getClass()); // java.math.BigInteger
System.out.println(new Json(2).equals(new Json(2.0)));  // true - numbers compare by value not type
System.out.println(json.get("d").booleanValue()); // true
System.out.println(json.get("e").booleanValue()); // false
json.put("e", Json.read("[]")); // Add a new list
System.out.println(json.get("e").type()); // "list"
json.putPath("e[0]", false);
System.out.println(json.get("e")); // [false] - e is a list
json.putPath("e[\"a\"]", true); // this will convert e to a map
System.out.println(json.get("e")); // {"0":false,"a":true}
json.setValue(new Json("string")); // copy value from specified object
System.out.println(json.value()); // "string"

// Serialization
String input = "{\"b\":1, /*comment*/ \"a\": 2}";
json = Json.read(input);  // Fails, comments not allowed
json = Json.read(new JsonReader().setInput(new StringReader(input)).setComments(true)); // OK
json.write(new JsonWriter().setOutput(...).setIndent(2).setSorted(true)); // pretty print and sort keys
// {
//   "a": 2,
//   "b": 1,
// }

// CBOR / Msgpack
json.put("buffer", ByteBuffer.wrap(new byte[] { ... }));   // add a ByteBuffer
System.out.println(json.get("buffer").type());      // "buffer"
System.out.println(json.get("buffer").stringValue());  // Base64 encoded value of buffer
json.setTag(20);        // Set a CBOR tag on a value
json.write(new CborWriter().setOutput(outputstream));    // serialize the same JSON object to CBOR
json = Json.read(new CborReader().setInput(inputstream));   // read CBOR from an Inputream
json = Json.readCbor(inputstream);   // shortcut for the line above
System.out.println(json.get("buffer").getTag());        // "20"
System.out.println(json.get("b").getTag());        // "-1", which means no tag
json.put("nan", Double.NaN);
json.write(new CborWriter().setOutput(outputstream));    // infinity is fine in CBOR
json.write(new JsonWriter().setOutput(writer));    // throws IOException - infinity not allowed in Json
json.write(new JsonWriter().setOutput(writer).setAllowNaN(true));  // infinity serializes as null
json.write(new MsgpackWriter().setOutput(outputstream));    // Msgpack instead of CBOR


// Events
json.addListener(new JsonListener() {
    public void jsonEvent(Json root, JsonEvent event) {
        if (event.after == null) {
            System.out.println("Removed " + root.find(event.before));
        } else if (event.before == null) {
            System.out.println("Added " + root.find(event.after));
        } else {
            System.out.println("Changed " + root.find(event.after) + " from " + event.before+" to " + event.after);
        }
    }
});

// Paths as keys
json.putPath("a.b", true);  // "Added a.b"
json.getPath("a.b").put("c", true);  // "Added a.b.c"
json.getPath("a.b").put("c", false);  // "Changed a.b.c" from true to false
json.removePath("a.b"); // "Removed a.b"

// Conversion to/from JSR374 - the representations are independent, not shared
javax.json.JsonValue jsrvalue = javax.json.Json.createReader(...).readValue(); // object read via JSR374
bfovalue = new Json(jsrvalue);   // convert from JSR374 to BFO
jsrvalue = javax.json.Json.createObjectBuilder(bfovalue.mapValue()).build(); // convert from BFO to JSR374

// Factories for type conversion are simple
JsonFactory factory = new JsonFactory() {
    public Json toJson(Object o) {
        if (o instanceof URL) {
            return "[url] " + o;
        }
        return null;
    }
    public Object fromJson(Json o) {
        if (o.isString() && o.startsWith("[url] ")) {
            return new URL(o.stringValue().substring(6));
        }
        return null;
    }
};
Json j = new Json(new URL(...), factory).toString(); // "[url] ..."
Json j = Json.read("[url] ...");    // read as normal
j.setFactory(factory);              // ... then set factory on the tree.
j.object();                         // ... factory ensures object is a URL.
new Json(Collections.<String,URL>singletonMap(key, url), factory); // collections work with factories too

```

### JWK and COSE

In addition the JWT class adds basic support for [Java Web Tokens](https://jwt.io).
With only a handful of methods it is trivial to use, but supports all JWT signing methods.
Encryption with JWE is not supported

```java
JWT jwt = new JWT(Json.parse("{\"iss\":\"foo\"}"));
byte[] secretkeydata = ...
SecretKey key = new JWK(secretkeydata, "HS256").getSecretKey();
jwt.sign(key);                       // Sign using a symmetric key
jwt = new JWT(jwt.toString());       // Encode then decode
assert jwt.verify(key);              // Verify using the same symmetric key

PublicKey pubkey = ...
PrivateKey prikey = ...
jwt.getHeader().put("x5u", ...);     // Add custom content to header
jwt.sign(prikey);                    // Sign using a assymmetric key
assert jwt.verify(pubkey);           // Verify using corresponding key

jwt.getPayload().clear();            // Modify the payload
assert !jwt.verify(pubkey);          // Signature is no longer valid
```

COSE was added in version 5. Signing with single or multiple signatures
are supported, but counter-signing (for timestamps) is pending and encryption
support is not currently planned.

```java
// Signing
COSE cose = new COSE();
cose.setPayload(ByteBuffer.wrap("Hello, World".getBytes("UTF-8")));
cose.sign(privateKey);               // Sign using a private key, eg ES256
cose.writeCBOR(..., null);           // Write COSE to stream, or...
ByteBuffer buf = cose.toCbor();      // Write COSE to ByteBuffer

// Verifying
Json json = Json.readCBOR(..., null);     // Reload COSE
cose = new COSE(json);
String s = new String(cose.getPayload().array(), "UTF-8"); // Hello, World
assert jwt.verify(publicKey) == 0;   // Verify with the public key
```

For both JWT and COSE, the [JWK](https://faceless2.github.io/json/docs/com/bfo/json/JWK.html) utility class can convert
between the Java `PublicKey`, `PrivateKey` and `SecretKey` implementations and their JWK or COSE-key representations.

EdDSA requires Java 15+, ML-DSA requires Java 24+ or BouncyCastle 1.79+. However these
are handled with reflection so the library can be compiled and run on Java 11 or later without any problems; the
new key types will throw an error if they're unsupported when encountered.

# Related projects

* https://github.com/faceless2/c2pa - a C2PA implementation built on this package
* https://github.com/faceless2/zpath - an XPath-like language for JSON/CBOR.
