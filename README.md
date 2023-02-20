# BFO JSON/CBOR Parser

The BFO JSON/CBOR/Msgpack Parser is yet another Java JSON parser, with the follow emphasis:

### simple
* the API is essentially a single class, with a few helper classes that are all optional. Items are added with `put`, retrieved with `get`, read with `read` and written with `write`. Collections are used for maps and lists, and you can use the whole API with no more than about 5 or 6 methods. Which means although the API is [fully documented](https://faceless2.github.io/json/docs/), you can probably get away without reading any of it.

### fast
* A 2021 Macbook M1 will read Json at about 120MB/s from text (80MB/s from binary, as it has to convert to UTF-8),
and write at about 400MB/s. It can read CBOR/Msgpack at about 300MB/s and write at 600MB/s. That's ridiculously fast;
as part of version 4 I had to change the large-file benchmarks to use microseconds. A great deal of effort has been
spent on removing buffer copying and avoiding slow codepaths in the JVM - I don't think you'll find a faster Java API.

### correct
* the API has been tested against the Json sample data made available by Nicolas Seriot at
http://seriot.ch/parsing_json.php,
and has been authored with reference to [RFC8259](https://tools.ietf.org/html/rfc8259).
CBOR support is newer, but has again been tested against [RFC7049](https://tools.ietf.org/html/rfc7049)
and fuzzed input, to make sure errors are handled properly.

### self-contained
* the API has no external requirements. Although it compiles against the JsonPath implementation https://github.com/json-path/JsonPath, provided the "eval" methods are not used there is no need for those classes to be available at runtime. To build it, type "ant" (and if you'd prefer the Maven experience, type "ant" then go and do something else for two hours).

## Features
* JSON, CBOR and Msgpack serialization are both available from the same object
* non-string map keys since version 5, to support COSE. Non-string keys are convert to strings when serializing to JSON
* Java Web Token (JWT) support class for reading/writing/signing/verifying.
* COSE signed object support class for reading/writing/signing/verifying.
* Java Web Keys (JWK) support EC, RSA and EdDSA public/private keys, and Hmac, AES-KW and AES-GCM-KW symmetric keys
* JsonPath integration (optional)
* Listeners and Events to monitor changes to the structure
* Flexible typing; if you request the int value of a string it will try to convert it to an int. If you put a value with a String key on a list, it will convert to a map.
* Numbers read as ints, longs, doubles, BigIntegers, or BigDecimals, with the smallest type chosen first.
* CBOR/Msgpack binary strings are stored as ByteBuffers, but will be converted to Base64 strings when serialized as Json.
* Option of mapping Json to more complex Java objects is possible, but not included with the code. By default data is retrieved as  Maps, Lists and primitive types only

## Building and Documentation
* Prebuilt binary available at [https://faceless2.github.io/json/dist/bfojson-5.jar](https://faceless2.github.io/json/dist/bfojson-5.jar)
* The API docs will always be available at [https://faceless2.github.io/json/docs/](https://faceless2.github.io/json/docs/)
* Or download with `git clone http://github.com/faceless2/json.git`. Type `ant`. Jar is in `dist`, docs are in `docs`
 
## Design Decisions
* Mapping JavaScript objects to a Java object can be done by use of a JsonFactory, however this is done after the object is read. Most of the complexity of other Java Json APIs comes from the mapping process between Json and Java objects; if you want to go down this route you have a trivially simple interface to implement, but you're on your own.
   
* JavaScript is loosely typed, and this API acknowleges this: if you request an int value from a Json string, it will try to parse the String as an integer. If you don't want this, see the JsonReadOptions to turn it off.

* Json in the wild has many variations - comments are embedded, maps have unquoted keys, and so on. By default the API will adhere closely to RFC8259 when reading or writing, although this can be changed. Again see JsonReadOptions.

* When reading Json numbers, they will be mapping to ints, longs, BigIntegers and double as appropriate. If BigDecimal support is required, this can be turned on in JsonReadOptions

* Json is read from Readers and written to Appendable.
  You can read from an InputStream too, in which case it will look for a BOM at the start of the stream.
  CBOR/Msgpack are read from an InputStream and written to an OutputStream.
  Errors encountered during reading are reported with context, line and column numbers (for JSON) or byte offset (for CBOR/Msgpack).

* CBOR serialization offers two complexities that are not supported in this API:
duplicate keys in maps and "special" types that are not defined.
Duplicate keys encountered during reading throw an IOException,
and special types (which should really only
be encountered while testing) are converted to a tagged null object. Tags are limited
to 63 bits, and tags applied to Map keys are ignored.

* CBOR serialization will convert tag types 2 and 3 on a "buffer" to BigInteger, as described in RFC7049
But other tags used to distinguish Dates, non-UTF8 strings, URLs etc. are not applied.
A <code>JsonFactory</code> can easily be written to cover as many of these are needed.

* Msgpack serialization is similar to CBOR, but a bit simpler. "extension types" are stored as
Buffers, with the extension type stored as a tag from 0..255. Like CBOR, duplicate keys encountered
during read will throw an IOException.

* It's possible (since v4) to read and write indefinitely large strings and buffers - the [JsonReadOptions.Filter](https://faceless2.github.io/json/docs/api/com/bfo/json/JsonReadOptions.Filter.html) class can be used to divert content away to a File, for example. There use of intermediate buffers has been kept to an absolute minimum.


## Examples
```java
// The basics
Json json = Json.read("{}"}; // Create a new map
json.put("a", "apples"); // Add a string
json.putPath("b.c", new Json("oranges")); // Add an intermediate map and another string
json.putPath("b.c[1]", 3}; // Replace previous string with an array, add a null then a number.
json.putPath("\"d.e\"", true); // Add a key containing a quote character
System.out.println(json); // {"a":"apples","b":{"c":[null,3]},"d.e":true}
json.write(System.out, null); // The same as above, but doesn't serialize to a String first.
System.out.println(json.getPath("b.c[1]").stringValue()); // "3"
System.out.println(json.getPath("b.c[1]").intValue()); // 3
System.out.println(json.get("b").get("c").get(1).intValue()); // 3

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
json = Json.read("{b:1, a: 2}");  // Fails, keys is not quoted
json = Json.read(new StringReader("{b: 1, a: 2}"), new JsonReadOptions().setAllowUnquotedKey(true)); // OK
json.write(System.out, new JsonWriteOptions().setPretty(true).setSorted(true)); // pretty print and sort keys
// {
//   "a": 2,
//   "b": 1,
// }

// CBOR / Msgpack
json.put("buffer", ByteBuffer.wrap(new byte[] { ... }));   // add a ByteBuffer
System.out.println(json.get("buffer").type());      // "buffer"
System.out.println(json.get("buffer").stringValue());  // Base64 encoded value of buffer
json.setTag(20);        // Set a CBOR tag on a value
json.writeCbor(new OutputStream(...), null);    // serialize the same JSON object to CBOR
json = Json.readCbor(new InputStream(...), null);   // read CBOR from an Inputream
System.out.println(json.get("buffer").getTag());        // "20"
System.out.println(json.get("b").getTag());        // "-1", which means no tag
json.put("nan", Double.NaN);
json.writeCbor(new OutputStream(...), null);    // infinity is fine in CBOR
json.write(new StringWriter(), null);    // throws IOException - infinity not allowed in Json
json.write(new StringWriter(), new JsonWriteOptions().setAllowNaN(true));  // infinity serializes as null
json.writeMsgpack(new OutputStream(...), null);    // Msgpack instead of CBOR


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
json.putPath("a.b", true);  // "Added a.b"
json.getPath("a.b").put("c", true);  // "Added a.b.c"
json.getPath("a.b").put("c", false);  // "Changed a.b.c" from true to false
json.removePath("a.b"); // "Removed a.b"

// JsonPath
json = Json.parse("{\"a\":{\"b\":{\"c\":[10,20,30,40]}}}");
json.eval("$..c[2]").intValue(); // 30
```

In addition the JWT class adds basic support for [Java Web Tokens](https://jwt.io).
With only a handful of methods it is trivial to use, but supports all JWT signing methods.
Encryption with JWE is not supported

```java
JWT jwt = new JWT(Json.parse("{\"iss\":\"foo\"}"));
byte[] key = ...
jwt.sign(key, "HS256");           // Sign using a symmetric key
jwt = new JWT(jwt.toString());    // Encode then decode
assert jwt.verify(key, null);     // Verify using the same symmetric key

byte[] pubkey = ...
byte[] prikey = ...
jwt.getHeader().put("x5u", ...);     // Add custom content to header
jwt.sign(prikey, "ES256");           // Sign using a asymmetric key
assert jwt.verify(pubkey, "ES256");  // Verify using corresponding key
PublicKey pubkeyobj = ...
assert jwt.verify(pubkeyobj, "ES256", null); // or use a java.security.Key

jwt.getPayload().clear();            // Modify the payload
assert !jwt.verify(pubkey, "ES256"); // Signature is no longer valid
```

Compiles under Java 11 or later - the API supports EdDSA keys (new in Java 15) via reflection.

This code is written by the team at [bfo.com](https://bfo.com). If you like it, come and see what else we do.
