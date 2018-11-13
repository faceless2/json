# BFO JSON Parser

The BFO Json Parser is yet another Java Json parser, with the follow emphasis:

#### small
* the API is essentially a single class, with a few helper classes that are all optional. Items are added with `put`, retrieved with `get`, read with `read` and written with `write`. Normal Collections are used for maps and lists, and you can use the whole API with no more than about 5 or 6 methods. Which means although the API is fully documented, you can proabbly get away without reading any of it.

#### fast
* A typical laptop in 2018 would be able to read Json at about 7MB/s and write at about 9MB/s. There are plenty of Java Json APIs claiming to be the fastest; benchmarking is not something I care to spend much time on, although informally it is testing faster than anything else I could find.

#### correct
* the API has been tested against the Json sample data made available by Nicolas Seriot at http://seriot.ch/parsing_json.php, and has been authored with reference to RFC8259

#### self-contained
* the API has no external requirements. Although it compiles against the JsonPath implementation https://github.com/json-path/JsonPath, provided the "eval" methods are not used there is no need for those classes to be available at runtime. To build it, type "ant". No maven here, not ever.


 
## Design Decisions

* Mapping JavaScript objects to a Java object can be done by use of a JsonFactory, however this is done after the object is read. Most of the complexity of other Java Json APIs comes from the mapping process between Json and Java objects; if you want to go down this route you have a trivially simple interface to implement, but you're on your own.
   
* JavaScript is loosely typed, and this API acknowleges this: if you request an int value from a Json string, it will try to parse the String as an integer (although this is configurable).

* Json in the wild has many variations - comments are embedded, maps have unquoted keys, and so on. By default the API will adhere closely to RFC8259 when reading or writing, although this can be changed.

* When reading Json numbers, they will be mapping to ints, longs, BigIntegers and double as appropriate. If BigDecimal support is required, this can be turned on with the appropriate JsonReadOption

