package com.bfo.json;

import java.io.*;
import java.util.*;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.*;
import com.jayway.jsonpath.spi.json.*;
import com.jayway.jsonpath.spi.mapper.*;

/**
 * A JsonProvider for <a href="https://github.com/json-path/JsonPath">JsonPath</a>
 * This will be used whenever the {@link Json#eval} or {@link Json#evalAll} methods
 * are called.
 */
public class JsonPathProviderBFO implements JsonProvider {

    static Object read(String path, Json ctx) {
        return JsonPath.compile(path).read(ctx, getConfiguration());
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Object createArray() {
        return new ArrayList();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Object createMap() {
        return new HashMap();
    }

    @Override
    public String toJson(Object o) {
        return o.toString();
    }

    @Override
    public boolean isArray(Object o) {
        return o instanceof Json && ((Json)o).isList();
    }

    @Override
    public boolean isMap(Object o) {
        return o instanceof Json && ((Json)o).isMap();
    }

    @Override
    public int length(Object o) {
        if (o instanceof List) {
            return ((List)o).size();
        } else if (o instanceof Json) {
            Json json = (Json)o;
            if (json.isList() || json.isMap()) {
                return json.size();
            } else if (json.isString()) {
                return json.stringValue().length();
            }
        }
        return 0;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Iterable toIterable(Object o) {
        return ((Json)o).listValue();
    }

    @Override
    public Collection<String> getPropertyKeys(Object o) {
        List<String> l = new ArrayList<String>();
        for (Object key : ((Json)o).mapValue().keySet()) {
            if (key instanceof String) {
                l.add((String)key);
            }
        }
        return l;
    }

    @Override
    public Object getArrayIndex(Object o, int idx) {
        if (o instanceof List) {
            return ((List)o).get(idx);
        } else {
            return ((Json)o).listValue().get(idx);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public Object getArrayIndex(Object o, int idx, boolean unwrap) {
        return getArrayIndex(o, idx);
    }

    @Override
    public void setArrayIndex(Object o, int idx, Object v) {
        ((Json)o).put(idx, v);
    }

    @Override
    public Object getMapValue(Object o, String key) {
        Object json = ((Json)o)._mapValue().get(key);
        if (json == null) {
            json = UNDEFINED;
        }
        return json;
    }

    private static String quoteKey(String s) {
        for (int i=0;i<s.length();i++) {
            char c = s.charAt(i);
            if (c == '.' || c == '[' || c == ']') {
                StringBuilder sb = new StringBuilder(s.length());
                sb.append('"');
                for (i=0;i<s.length();i++) {
                    c = s.charAt(i);
                    if (c == '"') {
                        sb.append("\\\"");
                    } else {
                        sb.append(c);
                    }
                }
                sb.append('"');
                return sb.toString();
            }
        }
        return s;
    }

    @Override
    public void setProperty(Object o, Object key, Object v) {
        if (key instanceof Integer) {
            ((Json)o).put(((Integer)key).intValue(), v);
        } else {
            ((Json)o).put(quoteKey(key.toString()), v);
        }
    }

    @Override
    public void removeProperty(Object o, Object key) {
        if (key instanceof Integer) {
            ((Json)o).remove(((Integer)key).intValue());
        } else {
            ((Json)o).remove(quoteKey(key.toString()));
        }
    }

    @Override
    public Object parse(InputStream in, String charset) throws InvalidJsonException {
        try {
            return Json.read(new InputStreamReader(in, charset), null);
        } catch (Exception e) {
            throw new InvalidJsonException(e);
        }
    }

    @Override
    public Object parse(String s) throws InvalidJsonException {
        try {
            return Json.read(s);
        } catch (Exception e) {
            throw new InvalidJsonException(e);
        }
    }

    @Override
    public Object unwrap(Object o) {
        return ((Json)o).value();
    }

    //---------------------------------------

    private static Configuration configuration;

    public synchronized static Configuration getConfiguration() {
        if (configuration == null) {
            MappingProvider prov = new MappingProvider() {
                public <T> T map(Object source, Class<T> targetType, Configuration configuration) {
                    return null;
                }
                public <T> T map(Object source, TypeRef<T> targetType, Configuration configuration)  {
                    return null;
                }
            };
    //        jsonpathconf = new Configuration.ConfigurationBuilder().jsonProvider(getJsonProvider()).mappingProvider(prov).options(Option.SUPPRESS_EXCEPTIONS).build();
            configuration = new Configuration.ConfigurationBuilder().jsonProvider(new JsonPathProviderBFO()).mappingProvider(prov).build();
        }
        return configuration;
    }

}
