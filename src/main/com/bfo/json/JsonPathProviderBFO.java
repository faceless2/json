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
    public Object createArray() {
        return new ArrayList();
    }

    @Override
    public Object createMap() {
        return new HashMap();
    }

    @Override
    public String toJson(Object o) {
        return o.toString();
    }

    @Override
    public boolean isArray(Object o) {
        return o != null && ((Json)o).isList();
    }

    @Override
    public boolean isMap(Object o) {
        return o != null && ((Json)o).isMap();
    }

    @Override
    public int length(Object o) {
        if (o instanceof List) {
            return ((List)o).size();
        } else if (((Json)o).isList()) {
            return ((IList)((Json)o).core).size();
        } else if (((Json)o).isMap()) {
            return ((IMap)((Json)o).core).size();
        } else if (((Json)o).core instanceof IString) {
            return ((IString)((Json)o).core).stringValue().length();
        } else {
            return 0;
        }
    }

    @Override
    public Iterable toIterable(Object o) {
        return ((Json)o).core.listValue();
    }

    @Override
    public Collection<String> getPropertyKeys(Object o) {
        return ((Json)o).core.mapValue().keySet();
    }

    @Override
    public Object getArrayIndex(Object o, int idx) {
        if (o instanceof List) {
            return ((List)o).get(idx);
        } else {
            return ((Json)o).core.listValue().get(idx);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public Object getArrayIndex(Object o, int idx, boolean unwrap) {
        return getArrayIndex(o, idx, unwrap);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setArrayIndex(Object o, int idx, Object v) {
        List l = (List)o;
        if (l.size() > idx) {
            l.set(idx, v);
        } else {
            while (l.size() < idx) {
                l.add(null);
            }
            l.add(v);
        }
    }

    @Override
    public Object getMapValue(Object o, String key) {
        return ((IMap)((Json)o).core).get(key.toString());
    }

    @Override
    public void setProperty(Object o, Object key, Object v) {
        if (!(v instanceof Json)) {
            v = new Json(v);
        }
        ((IMap)((Json)o).core).put(key.toString(), (Json)v);
    }

    @Override
    public void removeProperty(Object o, Object key) {
        ((IMap)((Json)o).core).remove(key.toString());
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
