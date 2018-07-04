package com.bumptech.glide.webpdecoder;

public class Helper {

    static {
        System.loadLibrary("webpparser");
    }

    public native static boolean setStdoutFile(String file);

}
