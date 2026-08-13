package vn.iotstar.transcodingworker.transcode;

import java.nio.file.Path;
import java.util.List;

public record TranscodeResult(
        Path masterPlaylistFile,
        List<RenditionOutput> renditions,
        int durationSeconds,
        byte[] rawEncryptionKey
) {
}
