package com.google.ar.core.examples.java.common;

public class ExportData {
//    "(id INTEGER PRIMARY KEY AUTOINCREMENT,"+
//            "tree_id INTEGER," +
//            "contour_id INTEGER,"+
//            "height real," +
    Long Id;
    Long TreeId;
    Integer ContourId;
    Float Height;

    public ExportData(Long id, Long treeId, Integer contourId, Float height) {
        Id = id;
        TreeId = treeId;
        ContourId = contourId;
        Height = height;
    }

    public Long getId() {
        return Id;
    }

    public Long getTreeId() {
        return TreeId;
    }

    public Integer getContourId() {
        return ContourId;
    }

    public Float getHeight() {
        return Height;
    }
}
