package vn.iotstar.transcodingworker.transcode;

import java.nio.file.Path;

public interface TranscodeService {

    /** {@code ffprobe} chiều rộng/cao/thời lượng của video nguồn. */
    ProbeResult probe(Path sourceFile);

    /**
     * Một lần chạy {@code ffmpeg} duy nhất, sinh mọi rendition trong thang độ phân giải phù hợp
     * với nguồn + master playlist + mã hoá AES-128 từng segment. Không decode lại nguồn nhiều lần.
     */
    TranscodeResult transcode(Path sourceFile, Path outputDir);
}
