package vn.iotstar.transcodingworker.transcode;

/** Một nấc trong thang độ phân giải HLS. */
public record Rung(String label, int width, int height, int bitrateKbps) {
}
