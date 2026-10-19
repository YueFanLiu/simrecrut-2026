package fr.isep.simrecrut.controller.identity;

import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.dto.identity.RoleDtos.RoleSummary;
import fr.isep.simrecrut.service.identity.RoleQueryService;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!resume-workspace")
@RequestMapping("/api/v1/admin/roles")
public class AdminRoleController {

    private final RoleQueryService roles;

    public AdminRoleController(RoleQueryService roles) {
        this.roles = roles;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleSummary>>> list() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Roles retrieved.", roles.listRoles()));
    }
}
