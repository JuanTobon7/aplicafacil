package com.aplicafacil.linkedin.infrastructure.scraper;

import org.springframework.stereotype.Component;

import com.aplicafacil.linkedin.application.port.out.BrowserAutomation.LoginResult;
import com.aplicafacil.linkedin.contract.IdentitySource;
import com.aplicafacil.linkedin.contract.LinkedInIdentity;
import com.aplicafacil.linkedin.contract.LinkedInIdentityProvider;
import com.aplicafacil.linkedin.contract.ProfileSnapshot;

/**
 * Fase local (proxy): la identidad y el perfil los lee el scraper con la misma sesión en la que la persona
 * inició sesión ({@code LinkedInProfileReader} vive allí, con los selectores centralizados). Aquí solo se
 * exige que la identidad sea de tipo SESSION y traiga el URN del miembro.
 */
@Component
public class SessionIdentityProvider implements LinkedInIdentityProvider<LoginResult> {

    @Override
    public IdentitySource source() {
        return IdentitySource.SESSION;
    }

    @Override
    public LinkedInIdentity readIdentity(LoginResult result) {
        LinkedInIdentity identity = result.identity();
        if (identity == null || identity.source() != IdentitySource.SESSION) {
            throw new IllegalStateException("El scraper no devolvió una identidad de sesión");
        }
        return identity;
    }

    @Override
    public ProfileSnapshot readProfile(LoginResult result) {
        return result.profile();
    }
}
