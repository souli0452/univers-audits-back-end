package gov.bf.ascelc.univers_audits.security.permission;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Réserve un point d'entrée aux utilisateurs qui détiennent la permission donnée.
 *
 * <pre>{@code @HasPermission(AppPermission.DOSSIER_VOIR)}</pre>
 *
 * Équivaut à {@code @PreAuthorize("hasAuthority('PERM_DOSSIER_VOIR')")}. Le nom de la permission est vérifié à la
 * compilation, ce qui rend impossible une faute de frappe dans une expression.
 * Nécessite le bean défini dans {@link MethodSecurityConfig}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAuthority('PERM_{value}')")
public @interface HasPermission {

    AppPermission value();
}
