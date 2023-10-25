// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.PasswordChangeRequiredException;
import org.apache.sshd.server.session.ServerSession;

import java.security.InvalidParameterException;

class REPLPasswordAuthenticator implements PasswordAuthenticator {
    private final String password;

    public REPLPasswordAuthenticator(String password) {
        if (password == null) throw new InvalidParameterException("password may not be empty");
        this.password = password;
    }
    @Override
    public boolean authenticate(String userInput, String passInput, ServerSession serverSession) throws PasswordChangeRequiredException, AsyncAuthException {
        if (passInput == null) return false;
        if (passInput.equals(password)) {
            serverSession.setAttribute(REPL.USER_KEY, userInput);
            return true;
        }
        try { Thread.sleep(3000); } catch (InterruptedException ignore) {}
        return false;
    }
}
