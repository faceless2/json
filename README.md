# BFO JSON Parser

The BFO Json Parser is yet another Java Json parser, with the follow emphasis:

### small
* the API is essentially a single class, with a few helper classes that are all optional. Items are added with `put`, retrieved with `get`, read with `read` and written with `write`. Normal Collections are used for maps and lists, and you can use the whole API with no more than about 5 or 6 methods. Which means although the API is fully documented, you can probably get away without reading any of it.

### fast
* A typical laptop in 2018 would be able to read Json at about 7MB/s and write at about 9MB/s. There are plenty of Java Json APIs claiming to be the fastest; benchmarking is not something I care to spend much time on, although informally it is testing faster than anything else I could find.

### correct
* the API has been tested against the Json sample data made available by Nicolas Seriot at http://seriot.ch/parsing_json.php, and has been authored with reference to RFC8259

### self-contained
* the API has no external requirements. Although it compiles against the JsonPath implementation https://github.com/json-path/JsonPath, provided the "eval" methods are not used there is no need for those classes to be available at runtime. To build it, type "ant". No maven here, not ever.

## Features
* JsonPath integration (optional)
* Listeners and Events to monitor changes to the structure
* Flexible typing; if you request the int value of a string it will try to convert it to an int. If you put a value with a String key on a list, it will convert to a map.
* Numbers read as ints, longs, doubles, BigIntegers, or BigDecimals, with the smallest type chosen first.
* Option of mapping Json to more complex Java objects is possible, but not included with the code. By default data us retrieved as  Maps, Lists and primitive types only

 
## Design Decisions

* Mapping JavaScript objects to a Java object can be done by use of a JsonFactory, however this is done after the object is read. Most of the complexity of other Java Json APIs comes from the mapping process between Json and Java objects; if you want to go down this route you have a trivially simple interface to implement, but you're on your own.
   
* JavaScript is loosely typed, and this API acknowleges this: if you request an int value from a Json string, it will try to parse the String as an integer (although this is configurable).

* Json in the wild has many variations - comments are embedded, maps have unquoted keys, and so on. By default the API will adhere closely to RFC8259 when reading or writing, although this can be changed.

* When reading Json numbers, they will be mapping to ints, longs, BigIntegers and double as appropriate. If BigDecimal support is required, this can be turned on with the appropriate JsonReadOption

* Things are read from Readers and written to Appendable. You can read from an InputStream too, in which case it will look for a BOM at the start of the stream

## Examples
```java
Json json = Json.read("{}"};
json.put("a", "apples");
json.put("b.c", "oranges");
json.put("b.c[1]", "pears"};
System.out.println(json); // {"a":"apples","b":{"c":[null,"pears"]}}

System.out.println(json.get("a").type()); // "string"
System.out.println(json.type()); // "map"
System.out.println(json.isMap()); // true
System.out.println(json.get("b.c[1]")); // "pears"
System.out.println(json.get("b").get("c").get(1)); // "pears"

json.put("d", "2");
json.put("e", "0");
System.out.println(json.get("d")); // "2"
System.out.println(json.get("d").type()); // "string"
System.out.println(json.get("d").intValue()); // 2
System.out.println(json.get("d").booleanValue()); // true
System.out.println(json.get("e").booleanValue()); // false

json = Json.read("{a: 1}");  // Fails, a is not quoted
json = Json.read(new StringReader("{a: 1}"), new JsonReadOptions().setAllowUnquotedKey(true)); // OK
json.write(System.out, new JsonWriteOptions().setPretty(true));
// {
//   "a": 1
// }
```

## Build instructions
Download. Type "ant". Tests are run automatically and javadoc constructed in "docs". If you just want the Jar, type "ant build".

This code is written by the team at bfo.com. If you like it, come and see what else we do.

