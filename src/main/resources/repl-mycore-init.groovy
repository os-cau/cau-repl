//file:noinspection UnnecessaryQualifiedReference

// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: GPL-3.0-only

:register de.uni_kiel.rz.fdr.repl.groovy.GroovyCmdMCRCli :mcrcli :M

import org.mycore.common.MCRSessionMgr

// initialize MyCoRe session
MCRSessionMgr.unlock()
mcrsessionid = {
    def session = MCRSessionMgr.getCurrentSession()
    session.currentIP = "127.0.0.1"
    session.userInformation = org.mycore.common.MCRSystemUserInformation.superUserInstance
    MCRSessionMgr.currentSession = session
    // workaround for a mycore bug - MCRLanguageFactory needs an active session for class init, so trigger it here to make sure it succeeds
    org.mycore.datamodel.language.MCRLanguageFactory.ENGLISH
    MCRSessionMgr.releaseCurrentSession()
    return session.ID
}()