package vn.iotstar.userservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import vn.iotstar.userservice.util.Plan;

@Schema(name = "UpdatePlanRequest", description = "[ADMIN] Đổi gói cước của một người dùng.")
public record UpdatePlanRequest(
        @NotNull
        @Schema(example = "PLUS", requiredMode = Schema.RequiredMode.REQUIRED)
        Plan plan
) {}
