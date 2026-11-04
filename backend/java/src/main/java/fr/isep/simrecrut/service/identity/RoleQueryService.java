package fr.isep.simrecrut.service.identity;

import fr.isep.simrecrut.dto.identity.RoleDtos.RoleSummary;
import fr.isep.simrecrut.mapper.identity.RoleMapper;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@Profile("!resume-workspace")
public class RoleQueryService {

    private final RoleMapper roles;

    public RoleQueryService(RoleMapper roles) {
        this.roles = roles;
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('simrecrut:role:list')")
    public List<RoleSummary> listRoles() {
        return roles.findAll().stream()
                .map(role -> new RoleSummary(
                        role.getRoleCode().name(), role.getName(), role.getDescription()))
                .toList();
    }
}
