package vn.iotstar.transcodingworker.transcode.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.iotstar.transcodingworker.transcode.ProbeResult;
import vn.iotstar.transcodingworker.transcode.RenditionOutput;
import vn.iotstar.transcodingworker.transcode.ResolutionLadder;
import vn.iotstar.transcodingworker.transcode.Rung;
import vn.iotstar.transcodingworker.transcode.TranscodeFailedException;
import vn.iotstar.transcodingworker.transcode.TranscodeResult;
import vn.iotstar.transcodingworker.transcode.TranscodeService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Gọi {@code ffprobe}/{@code ffmpeg} qua CLI ({@link ProcessBuilder}) thay vì một binding Java —
 * đây là cách tiếp cận chuẩn ngành, giữ JVM app mỏng và tận dụng trực tiếp binary đã kiểm chứng
 * rộng rãi thay vì một lớp bọc JNI/JNA thêm bug.
 */
@Service
@Slf4j
public class FfmpegTranscodeService implements TranscodeService {

    private static final int SEGMENT_DURATION_SECONDS = 6;
    private static final Duration PROCESS_TIMEOUT = Duration.ofHours(2);

    private final String ffprobeBinary;
    private final String ffmpegBinary;
    private final ObjectMapper objectMapper;

    public FfmpegTranscodeService(
            @Value("${transcode.ffprobe-binary:ffprobe}") String ffprobeBinary,
            @Value("${transcode.ffmpeg-binary:ffmpeg}") String ffmpegBinary,
            ObjectMapper objectMapper) {
        this.ffprobeBinary = ffprobeBinary;
        this.ffmpegBinary = ffmpegBinary;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProbeResult probe(Path sourceFile) {
        List<String> command = List.of(
                ffprobeBinary, "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height:format=duration",
                "-of", "json",
                sourceFile.toAbsolutePath().toString());

        String output = runAndCaptureStdout(command, sourceFile.getParent());
        try {
            JsonNode root = objectMapper.readTree(output);
            JsonNode stream = root.path("streams").path(0);
            int width = stream.path("width").asInt(0);
            int height = stream.path("height").asInt(0);
            double durationRaw = root.path("format").path("duration").asDouble(0);
            if (width <= 0 || height <= 0) {
                throw new TranscodeFailedException("ffprobe did not report a video stream with width/height for " + sourceFile);
            }
            return new ProbeResult(width, height, (int) Math.round(durationRaw));
        } catch (IOException e) {
            throw new TranscodeFailedException("Failed to parse ffprobe output for " + sourceFile, e);
        }
    }

    @Override
    public TranscodeResult transcode(Path sourceFile, Path outputDir) {
        ProbeResult probe = probe(sourceFile);
        List<Rung> ladder = ResolutionLadder.buildFor(probe.width(), probe.height());

        try {
            Files.createDirectories(outputDir);
            for (Rung rung : ladder) {
                Files.createDirectories(outputDir.resolve(rung.label()));
            }
        } catch (IOException e) {
            throw new TranscodeFailedException("Failed to create output directories under " + outputDir, e);
        }

        byte[] rawKey = generateAes128Key();
        Path keyFile = outputDir.resolve("segment.key");
        Path keyInfoFile = outputDir.resolve("keyinfo.txt");
        writeKeyFiles(rawKey, keyFile, keyInfoFile);

        List<String> command = buildFfmpegCommand(sourceFile, outputDir, ladder, keyInfoFile);
        runProcess(command, outputDir, "ffmpeg");

        List<RenditionOutput> renditions = ladder.stream()
                .map(rung -> new RenditionOutput(rung, outputDir.resolve(rung.label()).resolve("playlist.m3u8")))
                .toList();

        for (RenditionOutput r : renditions) {
            if (!Files.exists(r.playlistFile())) {
                throw new TranscodeFailedException("ffmpeg finished but expected playlist is missing: " + r.playlistFile());
            }
        }

        Path masterPlaylist = outputDir.resolve("master.m3u8");
        if (!Files.exists(masterPlaylist)) {
            throw new TranscodeFailedException("ffmpeg finished but master playlist is missing: " + masterPlaylist);
        }

        return new TranscodeResult(masterPlaylist, renditions, probe.durationSeconds(), rawKey);
    }

    private byte[] generateAes128Key() {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        return key;
    }

    /**
     * {@code keyinfo.txt} theo định dạng ffmpeg mong đợi (3 dòng: key URI, đường dẫn file khoá cục
     * bộ, IV hex tuỳ chọn). Dòng đầu là placeholder — streaming-service viết lại URI thật (trỏ về
     * endpoint phát khoá của chính nó, kèm playback token) lúc phục vụ playlist, ffmpeg chỉ cần
     * một giá trị hợp lệ về cú pháp để mã hoá segment, không ai thực sự tải theo URI này.
     */
    private void writeKeyFiles(byte[] rawKey, Path keyFile, Path keyInfoFile) {
        try {
            Files.write(keyFile, rawKey);
            String keyInfo = "encrypted.key" + "\n" + keyFile.toAbsolutePath() + "\n";
            Files.writeString(keyInfoFile, keyInfo, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new TranscodeFailedException("Failed to write HLS key info files under " + keyInfoFile.getParent(), e);
        }
    }

    private List<String> buildFfmpegCommand(Path sourceFile, Path outputDir, List<Rung> ladder, Path keyInfoFile) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBinary);
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(sourceFile.toAbsolutePath().toString());

        // Một lần split + scale cho mỗi rendition, tránh decode nguồn N lần.
        StringBuilder filter = new StringBuilder("[0:v]split=").append(ladder.size());
        for (int i = 0; i < ladder.size(); i++) {
            filter.append("[v").append(i).append("]");
        }
        filter.append(";");
        for (int i = 0; i < ladder.size(); i++) {
            Rung r = ladder.get(i);
            filter.append("[v").append(i).append("]scale=w=").append(r.width()).append(":h=").append(r.height())
                    .append("[v").append(i).append("out];");
        }
        cmd.add("-filter_complex");
        cmd.add(filter.substring(0, filter.length() - 1));

        StringBuilder varStreamMap = new StringBuilder();
        for (int i = 0; i < ladder.size(); i++) {
            Rung r = ladder.get(i);
            cmd.add("-map"); cmd.add("[v" + i + "out]");
            cmd.add("-c:v:" + i); cmd.add("libx264");
            cmd.add("-b:v:" + i); cmd.add(r.bitrateKbps() + "k");
            cmd.add("-map"); cmd.add("a:0");
            cmd.add("-c:a:" + i); cmd.add("aac");
            cmd.add("-b:a:" + i); cmd.add("128k");
            if (i > 0) {
                varStreamMap.append(" ");
            }
            varStreamMap.append("v:").append(i).append(",a:").append(i).append(",name:").append(r.label());
        }

        cmd.add("-f"); cmd.add("hls");
        cmd.add("-hls_time"); cmd.add(String.valueOf(SEGMENT_DURATION_SECONDS));
        cmd.add("-hls_playlist_type"); cmd.add("vod");
        cmd.add("-hls_key_info_file"); cmd.add(keyInfoFile.toAbsolutePath().toString());
        cmd.add("-master_pl_name"); cmd.add("master.m3u8");
        cmd.add("-var_stream_map"); cmd.add(varStreamMap.toString());
        cmd.add("-hls_segment_filename"); cmd.add(outputDir.resolve("%v").resolve("segment_%03d.ts").toString());
        cmd.add(outputDir.resolve("%v").resolve("playlist.m3u8").toString());

        return cmd;
    }

    private String runAndCaptureStdout(List<String> command, Path workingDir) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(false);
            if (workingDir != null) {
                builder.directory(workingDir.toFile());
            }
            Process process = builder.start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TranscodeFailedException("ffprobe timed out after " + PROCESS_TIMEOUT);
            }
            if (process.exitValue() != 0) {
                throw new TranscodeFailedException("ffprobe exited with code " + process.exitValue() + ": " + stderr);
            }
            return stdout;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start ffprobe process", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranscodeFailedException("Interrupted while waiting for ffprobe", e);
        }
    }

    private void runProcess(List<String> command, Path workingDir, String label) {
        log.info("Running {}: {}", label, String.join(" ", command));
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            if (workingDir != null) {
                builder.directory(workingDir.toFile());
            }
            Process process = builder.start();
            // Đọc + log stdout/stderr trong lúc process chạy — không đọc SAU waitFor() vì buffer
            // OS có thể đầy và làm process treo (deadlock kinh điển khi dùng ProcessBuilder).
            try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
                reader.lines().forEach(line -> log.debug("[{}] {}", label, line));
            }
            boolean finished = process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TranscodeFailedException(label + " timed out after " + PROCESS_TIMEOUT);
            }
            if (process.exitValue() != 0) {
                throw new TranscodeFailedException(label + " exited with non-zero code " + process.exitValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start " + label + " process", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranscodeFailedException("Interrupted while waiting for " + label, e);
        }
    }
}
