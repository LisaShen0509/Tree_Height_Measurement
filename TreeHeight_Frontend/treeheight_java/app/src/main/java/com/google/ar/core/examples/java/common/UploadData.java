package com.google.ar.core.examples.java.common;

import android.graphics.Bitmap;

import java.io.File;

public class UploadData {
    Bitmap Image;
    File Depth;
    File Rawdepth;
    File Confidence;

    public UploadData(Bitmap image, File depth, File rawdepth, File confidence) {
        Image = image;
        Depth = depth;
        Rawdepth = rawdepth;
        Confidence = confidence;
    }

    public Bitmap getImage() {
        return Image;
    }

    public File getDepth() {
        return Depth;
    }

    public File getRawdepth() {
        return Rawdepth;
    }

    public File getConfidence() {
        return Confidence;
    }
}
