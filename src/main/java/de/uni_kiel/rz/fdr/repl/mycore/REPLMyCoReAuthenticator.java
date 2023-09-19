// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: GPL-3.0-only

package de.uni_kiel.rz.fdr.repl.mycore;

import de.uni_kiel.rz.fdr.repl.REPL;
import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.PasswordChangeRequiredException;
import org.apache.sshd.server.session.ServerSession;
import org.mycore.common.MCRSystemUserInformation;
import org.mycore.user2.MCRUserManager;

public class REPLMyCoReAuthenticator implements PasswordAuthenticator {
    @Override
    public boolean authenticate(String userInput, String passInput, ServerSession serverSession) throws PasswordChangeRequiredException, AsyncAuthException {
        if (userInput == null || passInput == null) return false;
        String adminUID = MCRSystemUserInformation.getSuperUserInstance().getUserID();
        if (!userInput.equals(adminUID)) return false;
        if (MCRUserManager.checkPassword(adminUID, passInput) != null) {
            serverSession.setAttribute(REPL.USER_KEY, adminUID);
            return true;
        }
        return false;
    }
}
