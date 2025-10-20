package vn.iotstar.mediaservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.iotstar.mediaservice.service.MediaService;
import vn.iotstar.utils.dto.UploadRequestDto;
import vn.iotstar.utils.dto.UploadResponseDto;

@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media API", description = "Endpoints for media upload and management")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @Operation(summary = "Request upload URL from S3")
    @PostMapping("/upload/request")
    public UploadResponseDto requestUploadUrl(@RequestBody UploadRequestDto request) {
        return mediaService.requestUploadUrl(request);
    }
}