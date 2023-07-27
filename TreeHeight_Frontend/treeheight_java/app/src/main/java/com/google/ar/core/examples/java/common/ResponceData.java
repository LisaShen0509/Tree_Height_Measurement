package com.google.ar.core.examples.java.common;

import com.google.gson.JsonArray;

import java.util.Map;

public class ResponceData {
//    Float height;
    Map<Integer,Float>  heights;
    String rel_depth;
    String mask;

    public ResponceData(Map<Integer, Float> heights, String rel_depth, String mask) {
        this.heights = heights;
        this.rel_depth = rel_depth;
        this.mask = mask;
    }

    public Map<Integer, Float> getHeights() {
        return heights;
    }

    public String getRel_depth() {
        return rel_depth;
    }

    public String getMask() {
        return mask;
    }
}
