package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Une valeur refusée par Keycloak est une saisie à corriger (400 ou 409), pas une erreur interne. */
class KeycloakAdminServiceSaisiesRefuseesTest {

    private final Keycloak keycloak = Mockito.mock(Keycloak.class, Answers.RETURNS_DEEP_STUBS);
    private KeycloakAdminService service;
    private UserResource utilisateur;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(KeycloakAdminService.class, Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "keycloakClient", keycloak);
        ReflectionTestUtils.setField(service, "realm", "asce-lc");
        utilisateur = keycloak.realm("asce-lc").users().get("id-1");
        when(utilisateur.toRepresentation()).thenReturn(new UserRepresentation());
    }

    @Test
    void unProfilRefuseParKeycloakEstUneSaisieInvalide() {
        Mockito.doThrow(new BadRequestException()).when(utilisateur).update(any());

        assertThatThrownBy(() -> service.updateUserProfile("id-1", "\"", "Nom", "a@jetable.test"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unCourrielDejaPrisEstUnConflit() {
        Mockito.doThrow(new ClientErrorException(409)).when(utilisateur).update(any());

        assertThatThrownBy(() -> service.updateUserProfile("id-1", "A", "B", "pris@jetable.test"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unAgentRefuseParKeycloakEstUneSaisieInvalide() {
        when(keycloak.realm("asce-lc").users().create(any())).thenReturn(Response.status(400).build());

        assertThatThrownBy(() -> service.createUser("a'", "A", "B", "a"))
                .isInstanceOf(BusinessException.class);
    }
}
