package vn.iotstar.transcodingworker.transcode;

/** ffprobe/ffmpeg thoát với mã khác 0, hoặc output không đúng dạng mong đợi. */
public class TranscodeFailedException extends RuntimeException {
    public TranscodeFailedException(String message) {
        super(message);
    }

    public TranscodeFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
