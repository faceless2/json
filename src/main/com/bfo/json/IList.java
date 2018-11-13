package com.bfo.json;

import java.io.*;
import java.util.*;

class IList extends Core {

    private final List<Json> list;

    IList() {
        list = new ArrayList<Json>();
    }

    @Override Object value() {
        return list;
    }

    @Override List<Json> listValue() {
        return list;
    }

    @Override String type() {
        return "list";
    }

    int size() {
        return list.size();
    }

    void add(Json value) {
        if (value.parent() != null) {
            value.notifyRemove();
        }
        list.add(value);
    }

    Json set(int ix, Json value) {
        if (value.parent() != null) {
            value.notifyRemove();
        }
        if (ix == list.size()) {
            add(value);
            return null;
        } else {
            while (list.size() <= ix) {
                list.add(new Json());
            }
            Json oldvalue = list.set(ix, value);
            if (oldvalue != null) {
                oldvalue.notifyRemove();
            }
            return oldvalue;
        }
    }

    Json remove(int ix) {
        if (ix >= 0 && ix < list.size()) {
            Json oldvalue = list.remove(ix);
            if (oldvalue != null) {
                oldvalue.notifyRemove();
                return oldvalue;
            }
        }
        return null;
    }

    Iterator<Json> iterator() {
        return list.listIterator();
    }

    Json get(int ix) {
        return ix < 0 || ix >= list.size() ? null : list.get(ix);
    }

    @Override void write(Appendable sb, SerializerState state) throws IOException {
        sb.append("[");
        if (state.prefix != null) {
            state.prefix.append("  ");
//            sb.append(state.prefix);
        }
        boolean first = true;
        for (int i=0;i<list.size();i++) {
            if (i > 0) {
                sb.append(',');
            }
            if (state.prefix != null) {
                sb.append(state.prefix);
            }
            String key = Integer.toString(i);
            Json ochild = list.get(i);
            Json child = state.filter.enter(key, ochild);
            if (child != null) {
                child.getCore().write(sb, state);
            } else {
                Json.NULL.write(sb, state);
            }
            state.filter.exit(key, ochild);
        }
        if (state.prefix != null) {
            state.prefix.setLength(state.prefix.length() - 2);
            sb.append(state.prefix);
        }
        sb.append("]");
    }

}
