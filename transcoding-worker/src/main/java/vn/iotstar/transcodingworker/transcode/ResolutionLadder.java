package vn.iotstar.transcodingworker.transcode;

import java.util.List;

/**
 * Thang độ phân giải cố định cho v1. Không upscale: chỉ giữ những nấc có chiều cao ≤ nguồn. Nếu
 * nguồn thấp hơn cả nấc thấp nhất (360p), dùng một nấc duy nhất đúng bằng độ phân giải nguồn thay
 * vì bỏ qua transcode hoàn toàn — vẫn cần đóng gói HLS + mã hoá dù chỉ một chất lượng.
 */
public final class ResolutionLadder {

    private static final List<Rung> FULL_LADDER = List.of(
            new Rung("1080p", 1920, 1080, 5000),
            new Rung("720p", 1280, 720, 2800),
            new Rung("480p", 854, 480, 1400),
            new Rung("360p", 640, 360, 800)
    );

    private ResolutionLadder() {
    }

    public static List<Rung> buildFor(int sourceWidth, int sourceHeight) {
        List<Rung> applicable = FULL_LADDER.stream().filter(r -> r.height() <= sourceHeight).toList();
        if (!applicable.isEmpty()) {
            return applicable;
        }
        // Nguồn thấp hơn 360p: một nấc duy nhất bằng đúng kích thước nguồn (làm tròn chẵn, ffmpeg
        // yêu cầu width/height chẵn cho hầu hết codec H.264).
        int w = sourceWidth % 2 == 0 ? sourceWidth : sourceWidth - 1;
        int h = sourceHeight % 2 == 0 ? sourceHeight : sourceHeight - 1;
        return List.of(new Rung("source", w, h, 800));
    }
}
