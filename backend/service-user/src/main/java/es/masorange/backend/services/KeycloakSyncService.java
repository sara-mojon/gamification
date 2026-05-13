package es.masorange.backend.services;

import es.masorange.backend.common.exception.ServiceCommunicationException;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.core.Response;
import java.util.Collections;

@Service
public class KeycloakSyncService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakSyncService.class);

    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    public KeycloakSyncService(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    /**
     * Elimina un usuario físicamente de Keycloak usando su ID.
     */
    public void deleteUserInKeycloak(String keycloakId) {
        try (Response response = keycloak.realm(realm).users().delete(keycloakId)) {
            if (response.getStatus() == 404) {
                log.warn("El usuario ya no existía en Keycloak: {}", keycloakId);
                return;
            }
            if (response.getStatus() >= 400) {
                throw new ServiceCommunicationException("Keycloak error: " + response.getStatus());
            }
        } catch (Exception e) {
            throw new ServiceCommunicationException("Error de conexión con Keycloak: " + e.getMessage());
        }
    }

    /**
     * Cambia el rol de un usuario en Keycloak (Ej: de 'USER' a 'ADMIN')
     */
    public void updateUserRoleInKeycloak(String keycloakId, String oldRoleName, String newRoleName) {
        try {
            UserResource userResource = keycloak.realm(realm).users().get(keycloakId);

            String keycloakNewRole = "ROLE_" + newRoleName.toUpperCase();
            String keycloakOldRole = "ROLE_" + oldRoleName.toUpperCase();

            RoleRepresentation newRole = keycloak.realm(realm).roles().get(keycloakNewRole).toRepresentation();
            userResource.roles().realmLevel().add(Collections.singletonList(newRole));

            RoleRepresentation oldRole = keycloak.realm(realm).roles().get(keycloakOldRole).toRepresentation();
            userResource.roles().realmLevel().remove(Collections.singletonList(oldRole));

            log.info("Roles actualizados en Keycloak para: {}", keycloakId);

        } catch (Exception e) {
            log.error("Error actualizando roles en Keycloak para el usuario {}: {}", keycloakId, e.getMessage(), e);
        }
    }
}