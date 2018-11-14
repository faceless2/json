# BFO JSON Parser

The BFO Json Parser is yet another Java Json parser, with the follow emphasis:

### simple
* the API is essentially a single class, with a few helper classes that are all optional. Items are added with `put`, retrieved with `get`, read with `read` and written with `write`. Collections are used for maps and lists, and you can use the whole API with no more than about 5 or 6 methods. Which means although the API is [fully documented](https://faceless2.github.io/json/docs/index.html), you can probably get away without reading any of it.

### fast
* A typical laptop in 2018 would be able to read Json at about 7MB/s and write at about 9MB/s. There are plenty of Java Json APIs claiming to be the fastest; benchmarking is not something I care to spend much time on, but informally it is testing faster than anything else I could find.

### correct
* the API has been tested against the Json sample data made available by Nicolas Seriot at http://seriot.ch/parsing_json.php, and has been authored with reference to [RFC8259](https://tools.ietf.org/html/rfc8259)

### self-contained
* the API has no external requirements. Although it compiles against the JsonPath implementation https://github.com/json-path/JsonPath, provided the "eval" methods are not used there is no need for those classes to be available at runtime. To build it, type "ant" (and if you'd prefer the Maven experience, type "ant" then go and do something else for two hours).

## Features
* JsonPath integration (optional)
* Listeners and Events to monitor changes to the structure
* Flexible typing; if you request the int value of a string it will try to convert it to an int. If you put a value with a String key on a list, it will convert to a map.
* Numbers read as ints, longs, doubles, BigIntegers, or BigDecimals, with the smallest type chosen first.
* Option of mapping Json to more complex Java objects is possible, but not included with the code. By default data is retrieved as  Maps, Lists and primitive types only

## Building and Documentation
Download with `git clone http://github.com/faceless2/json`. Type `ant`. Tests are run automatically and javadoc constructed in "docs". If you just want the Jar, type `ant build`.
The API docs will always be available at https://faceless2.github.io/json/docs/index.html
 
## Design Decisions
* Mapping JavaScript objects to a Java object can be done by use of a JsonFactory, however this is done after the object is read. Most of the complexity of other Java Json APIs comes from the mapping process between Json and Java objects; if you want to go down this route you have a trivially simple interface to implement, but you're on your own.
   
* JavaScript is loosely typed, and this API acknowleges this: if you request an int value from a Json string, it will try to parse the String as an integer. If you don't want this, see the JsonReadOptions to turn it off.

* Json in the wild has many variations - comments are embedded, maps have unquoted keys, and so on. By default the API will adhere closely to RFC8259 when reading or writing, although this can be changed. Again see JsonReadOptions.

* When reading Json numbers, they will be mapping to ints, longs, BigIntegers and double as appropriate. If BigDecimal support is required, this can be turned on in JsonReadOptions

* Things are read from Readers and written to Appendable. You can read from an InputStream too, in which case it will look for a BOM at the start of the stream

## Examples
```java
// The basics
Json json = Json.read("{}"}; // Create a new map
json.put("a", "apples"); // Add a string
json.put("b.c", new Json("oranges")); // Add an intermediate map and another string
json.put("b.c[1]", 3}; // Replace previous string with an array, add a null then a number.
System.out.println(json); // {"a":"apples","b":{"c":[null,3]}}
json.write(System.out, null); // The same as above, but doesn't serialize to a String first.
System.out.println(json.get("b.c[1]").stringValue()); // "3"
System.out.println(json.get("b.c[1]").intValue()); // 3


// Types
System.out.println(json.get("a").type()); // "string"
System.out.println(json.type()); // "map"
System.out.println(json.isMap()); // true
System.out.println(json.get("b.c[1]")); // "pears"
System.out.println(json.get("b").get("c").get(1)); // "pears"

// Type conversion
json.put("d", "2");
json.put("e", "0");
System.out.println(json.get("d")); // "2"
System.out.println(json.get("d").type()); // "string"
System.out.println(json.get("d").intValue()); // 2
System.out.println(json.get("d").numberValue().getClass()); // java.lang.Integer
json.put("d", "9999999999");
System.out.println(json.get("d").numberValue().getClass()); // java.lang.Long
json.put("d", "99999999999999999999999999");
System.out.println(json.get("d").numberValue().getClass()); // java.math.BigInteger
System.out.println(json.get("d").booleanValue()); // true
System.out.println(json.get("e").booleanValue()); // false
json.put("e", Json.read("[]")); // Add a new list
System.out.println(json.get("e").type()); // "list"
json.put("e[0]", false);
System.out.println(json.get("e")); // [false] - e is a list
json.put("e[\"a\"]", true); // this will convert e to a map
System.out.println(json.get("e")); // {"0":false,"a":true}

// Serialization
json = Json.read("{b:1, a: 2}");  // Fails, keys is not quoted
json = Json.read(new StringReader("{b: 1, a: 2}"), new JsonReadOptions().setAllowUnquotedKey(true)); // OK
json.write(System.out, new JsonWriteOptions().setPretty(true).setSorted(true)); // pretty print and sort keys
// {
//   "a": 2,
//   "b": 1,
// }

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
json.put("a.b", true);  // "Added a.b"
json.get("a.b").put("c", true);  // "Added a.b.c"
json.get("a.b").put("c", false);  // "Changed a.b.c" from true to false
json.remove("a.b"); // "Removed a.b"

// JsonPath
json = Json.parse("{\"a\":{\"b\":{\"c\":[10,20,30,40]}}}");
json.eval("$..c[2]").intValue(); // 30
```

This code is written by the team at http://bfo.com. If you like it, come and see what else we do.
