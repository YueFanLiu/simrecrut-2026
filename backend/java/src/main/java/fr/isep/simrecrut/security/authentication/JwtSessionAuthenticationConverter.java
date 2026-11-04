package fr.isep.simrecrut.security.authentication;

import fr.isep.simrecrut.entity.identity.AccountStatus;
import fr.isep.simrecrut.entity.identity.UserAccount;
import fr.isep.simrecrut.mapper.identity.RoleMapper;
import fr.isep.simrecrut.mapper.identity.UserAccountMapper;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.identity.AuthenticationFlowException;
import java.time.Instant;
import java.util.LinkedHashSet;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@Profile("!resume-workspace")
public class JwtSessionAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final RedisSessionStore sessions;
    private final UserAccountMapper accounts;
    private final RoleMapper roles;

    public JwtSessionAuthenticationConverter(
            RedisSessionStore sessions, UserAccountMapper accounts, RoleMapper roles) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.roles = roles;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            Object versionClaim = jwt.getClaim("ver");
            long version = Long.parseLong(String.valueOf(versionClaim));
            RedisSessionStore.Session session = sessions.load(jwt.getId());
            if (session == null || jwt.getExpiresAt() == null
                    || !session.publicId().equals(jwt.getSubject())
                    || session.securityVersion() != version
                    || session.expiresAtEpochSecond() != jwt.getExpiresAt().getEpochSecond()
                    || !jwt.getExpiresAt().isAfter(Instant.now())) {
                throw invalid();
            }

            UserAccount account = accounts.findById(session.userId());
            if (account == null || account.getStatus() != AccountStatus.ACTIVE
                    || account.getEmailVerifiedAt() == null || account.getSecurityVersion() == null
                    || account.getSecurityVersion() != version
                    || !session.publicId().equals(account.getPublicId())) {
                throw invalid();
            }

            var authorities = new LinkedHashSet<SimpleGrantedAuthority>();
            for (var role : roles.findByUserId(account.getId())) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleCode().name()));
            }
            for (String permission : roles.findPermissionCodesByUserId(account.getId())) {
                authorities.add(new SimpleGrantedAuthority(permission));
            }
            AccountPrincipal principal = new AccountPrincipal(account.getId(), account.getPublicId(),
                    jwt.getId(), version, jwt.getExpiresAt());
            return new UsernamePasswordAuthenticationToken(principal, null, authorities);
        } catch (DataAccessException exception) {
            throw AuthenticationFlowException.unavailable();
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private BadCredentialsException invalid() {
        return new BadCredentialsException("Invalid or expired access token.");
    }
}
