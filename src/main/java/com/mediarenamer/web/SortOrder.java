package com.mediarenamer.web;

/** Episode order. */
public enum SortOrder {
    Airdate, DVD, Absolute, Digital, Story, Production, Official, Date;

    public static SortOrder forName(String name) {
        if (name != null) {
            for (SortOrder o : values()) {
                if (o.name().equalsIgnoreCase(name)) {
                    return o;
                }
            }
        }
        return Airdate;
    }
}
