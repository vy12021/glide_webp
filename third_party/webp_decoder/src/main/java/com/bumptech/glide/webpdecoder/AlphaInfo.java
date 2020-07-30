package com.bumptech.glide.webpdecoder;

/**
 * ALPH trunk
 */
public class AlphaInfo {

  @WebpDecoder.WebpDecodeStatus
  int status = WebpDecoder.STATUS_OK;

  Format format;

  Filter filter;

  int preProcessingMethod;

  Vp8Info.LosslessInfo.LosslessTransform transform;

  @Override
  public String toString() {
    return "AlphaHead{" +
            "status=" + status +
            ", format=" + format +
            ", filter=" + filter +
            ", preProcessingMethod=" + preProcessingMethod +
            ", transform=" + transform +
            '}';
  }

  /**
   * Alpha subchunk filter method
   */
  public enum Filter {

    None,
    Horizontal,
    Vertical,
    Gradient

  }

  /**
   * ALPH subchunk compression method
   */
  public enum Format {

    // no compression
    NoneCompression,
    // compressed but lossless
    Lossless,
    Invalid;

    public static Format getFormat(int method) {
      Format[] formats = values();
      if (method >= values().length) {
        return Invalid;
      }
      return formats[method];
    }

  }

}
