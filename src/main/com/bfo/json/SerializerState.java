package com.bfo.json;

class SerializerState {
    final StringBuilder prefix;
    final JsonWriteOptions options;
    final JsonWriteOptions.Filter filter;

    SerializerState(Json ctx, JsonWriteOptions options) {
        this.options = options;
        prefix = options.isPretty() ? new StringBuilder("\n") : null;
        JsonWriteOptions.Filter tfilter = options.getFilter();
        if (tfilter == null) {
            tfilter = new JsonWriteOptions.Filter() {
                public Json enter(String key, Json child) {
                    return child;
                }
                public void exit(String key, Json child) {
                }
                public void initialize(Json ctx) {
                }
            };
        }
        filter = tfilter;
        filter.initialize(ctx);
    }

}
