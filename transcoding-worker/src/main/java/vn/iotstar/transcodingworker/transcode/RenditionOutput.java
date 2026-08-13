package vn.iotstar.transcodingworker.transcode;

import java.nio.file.Path;

public record RenditionOutput(Rung rung, Path playlistFile) {
}
