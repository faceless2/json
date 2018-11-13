package com.bfo.json;

import java.util.*;
import java.text.*;
import java.io.*;

class IMap extends Core {

    private final Map<String,Json> map;

    IMap() {
        map = new LinkedHashMap<String,Json>();
    }

    int size() {
        return map.size();
    }

    @Override Object value() {
        return map;
    }

    @Override Map<String,Json> mapValue() {
        return map;
    }

    @Override String type() {
        return "map";
    }

    Json put(String key, Json value) {
        return map.put(key, value);
    }

    Json remove(String key) {
        return map.remove(key);
    }

    Json get(String key) {
        return map.get(key);
    }

    @Override void write(Appendable sb, SerializerState state) throws IOException {
        sb.append("{");
        if (state.prefix != null) {
            state.prefix.append("  ");
            sb.append(state.prefix);
        }
        boolean first = true;
        Set<Map.Entry<String,Json>> set = map.entrySet();
        if (state.options.isSorted()) {
            set = new TreeMap<String,Json>(map).entrySet();
        }
        for (Map.Entry<String,Json> e : set) {
            String key = e.getKey();
            if (state.options.isNFC()) {
                key = Normalizer.normalize(key, Normalizer.Form.NFC);
            }
            Json ovalue = e.getValue();
            Json value = state.filter.enter(key, ovalue);
            if (value != null) {
                if (first) {
                    first = false;
                } else {
                    sb.append(",");
                    if (state.prefix != null) {
                        sb.append(state.prefix);
                    }
                }
                IString.write(key, sb);
                sb.append(':');
                value.getCore().write(sb, state);
            }
            state.filter.exit(key, ovalue);
        }
        if (state.prefix != null) {
           state.prefix.setLength(state.prefix.length() - 2);
           sb.append(state.prefix);
        }
        sb.append("}");
    }


}
