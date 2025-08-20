package vn.iotstar.authservice.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
@Tag(name = "Admin API", description = "Endpoints for admin operations such as user management, role management, and system monitoring.")
public class AdminController {

    @Operation(summary = "Admin operations",
               description = "This endpoint is reserved for future admin functionalities.")
    public String adminOperations() {
        return "Admin operations are not yet implemented.";
    }
}
