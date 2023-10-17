//file:noinspection UnnecessaryQualifiedReference
//file:noinspection GroovyAssignabilityCheck

// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: GPL-3.0-only


import org.mycore.common.MCRConstants
import org.mycore.common.MCRTransactionHelper
import org.mycore.user2.MCRUserManager
import org.mycore.datamodel.metadata.MCRMetadataManager
import org.mycore.datamodel.common.MCRXMLMetadataManager
import org.mycore.datamodel.metadata.MCRObjectID
import org.mycore.datamodel.metadata.MCRObject
import org.mycore.datamodel.metadata.MCRDerivate
import org.mycore.solr.MCRSolrClientFactory
import org.mycore.solr.search.MCRSolrSearchUtils
import org.jdom2.xpath.XPathFactory
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.Attribute
import org.jdom2.Text
import org.jdom2.output.XMLOutputter


Document.metaClass.getAt << { String q ->
    // doc["query"] -> first match
    def xpath = XPathFactory.instance().compile(q, org.jdom2.filter.Filters.fpassthrough(), null, MCRConstants.getStandardNamespaces())
    return _CAUREPL_isRo(delegate) ? _CAUREPL_setRo(xpath.evaluateFirst(delegate)) : xpath.evaluateFirst(delegate)
}

Document.metaClass.getAt << { List q ->
    // doc[] -> ls
    if (q.isEmpty()) return delegate.rootElement != null ? (delegate.rootElement.toString() + ":\n" + delegate.rootElement.content).replaceAll("\\n", "\\\n       ") : null
    // doc[["query"]] -> all matches
    if (q.size() != 1) throw new RuntimeException("Please supply exactly one query")
    def x = q.get(0)
    def xpath = XPathFactory.instance().compile(x, org.jdom2.filter.Filters.fpassthrough(), null, MCRConstants.getStandardNamespaces())
    return _CAUREPL_isRo(delegate) ? _CAUREPL_setRo(xpath.evaluate(delegate)) : xpath.evaluate(delegate)
}

Document.metaClass.call << { ->
    // doc() -> full xml
    return "\n" + (new XMLOutputter(org.jdom2.output.Format.getPrettyFormat()).outputString(delegate.rootElement))
}

Document.metaClass.leftShift << { Object x ->
    // doc << x -> set root element
    if (_CAUREPL_isRo(delegate)) throw new RuntimeException("this is a read-only-view. use mcrxml() or mcrderxml() for write access.")
    de.uni_kiel.rz.fdr.repl.mycore.XMLHelpers.setStringOrElement(delegate, x, false)
    return delegate
}

Document.metaClass.plus << { Object x ->
    // doc + x -> add root element
    if (_CAUREPL_isRo(delegate)) throw new RuntimeException("this is a read-only-view. use mcrxml() or mcrderxml() for write access.")
    de.uni_kiel.rz.fdr.repl.mycore.XMLHelpers.addStringOrElement(delegate, x, false)
    return delegate
}

Document.metaClass.reload << { ->
    def x = mcrxml(delegate["/*/@ID"].getValue())
    if (!x) throw new RuntimeException("reload failed")
    delegate.rootElement = x.detachRootElement()
    return delegate
}

Document.metaClass.getId << { -> return delegate["/*/@ID"].getValue() }

Element.metaClass.getAt << { String q ->
    // foo["query"] -> first match
    def xpath = XPathFactory.instance().compile(q, org.jdom2.filter.Filters.fpassthrough(), null, MCRConstants.getStandardNamespaces())
    return _CAUREPL_isRo(delegate) ? _CAUREPL_setRo(xpath.evaluateFirst(delegate)) : xpath.evaluateFirst(delegate)
}

Element.metaClass.getAt << { int q ->
    // foo[1] -> nth element
    return _CAUREPL_isRo(delegate) ? _CAUREPL_setRo(delegate.getContent(q)) : delegate.getContent(q)
}

Element.metaClass.getAt << { List q ->
    // foo[] -> ls
    if (q.isEmpty()) return (delegate.toString() + ":\n" + delegate.content).replaceAll("\\n", "\\\n       ")
    // foo[["query"]] -> all matches
    if (q.size() != 1) throw new RuntimeException("Please supply exactly one query")
    def x = q.get(0)
    def xpath = XPathFactory.instance().compile(x, org.jdom2.filter.Filters.fpassthrough(), null, MCRConstants.getStandardNamespaces())
    return _CAUREPL_isRo(delegate) ? _CAUREPL_setRo(xpath.evaluate(delegate)) : xpath.evaluate(delegate)
}

Element.metaClass.call << { ->
    // foo() -> full xml
    return "\n" + (new XMLOutputter(org.jdom2.output.Format.getPrettyFormat()).outputString(delegate))
}

Element.metaClass.leftShift << { Object x ->
    // foo << x -> set contents
    if (_CAUREPL_isRo(delegate)) throw new RuntimeException("this is a read-only-view. use mcrxml() or mcrderxml() for write access.")
    if (x instanceof org.jdom2.Content || x instanceof String || x instanceof Collection) de.uni_kiel.rz.fdr.repl.mycore.XMLHelpers.setStringOrElement(delegate, x, true)
    else if (x instanceof Attribute) delegate.setAttribute(de.uni_kiel.rz.fdr.repl.mycore.XMLHelpers.inheritNamespace(delegate, x))
    else throw new RuntimeException("don't know how to handle class " + x.getClass())
    return delegate
}

Element.metaClass.plus << { Object x ->
    // foo + x -> append contents
    if (_CAUREPL_isRo(delegate)) throw new RuntimeException("this is a read-only-view. use mcrxml() or mcrderxml() for write access.")
    if (x instanceof org.jdom2.Content || x instanceof String || x instanceof Collection) de.uni_kiel.rz.fdr.repl.mycore.XMLHelpers.addStringOrElement(delegate, x, true)
    else if (x instanceof Attribute) delegate.setAttribute(de.uni_kiel.rz.fdr.repl.mycore.XMLHelpers.inheritNamespace(delegate, x))
    else throw new RuntimeException("don't know how to handle class " + x.getClass())
    return delegate
}

Attribute.metaClass.leftShift << { Object x ->
    // attr << x -> set attribute value
    if (_CAUREPL_isRo(delegate)) throw new RuntimeException("this is a read-only-view. use mcrxml() or mcrderxml() for write access.")
    delegate.setValue(de.uni_kiel.rz.fdr.repl.mycore.XMLHelpers.inheritNamespace(delegate, x).toString())
    return delegate
}

Attribute.metaClass.call << { ->
    // attr() -> value
    return delegate.value
}

Text.metaClass.call << { ->
    // text() -> value
    return delegate.value
}

Text.metaClass.leftShift << { Object x ->
    // foo << x -> set contents
    if (_CAUREPL_isRo(delegate)) throw new RuntimeException("this is a read-only-view. use mcrxml() or mcrderxml() for write access.")
    delegate.setText(x.toString())
    return delegate
}

Text.metaClass.plus << { Object x ->
    // foo + x -> append contents
    if (_CAUREPL_isRo(delegate)) throw new RuntimeException("this is a read-only-view. use mcrxml() or mcrderxml() for write access.")
    delegate.append(x.toString())
    return delegate
}

org.mycore.datamodel.metadata.MCRBase.metaClass.reload << { ->
    def x = mcrxml(delegate.id.toString())
    if (!x) throw new RuntimeException("reload failed")
    delegate.setFromJDOM(x)
    return delegate
}

MCRObject.metaClass.getAt << { String q -> return _CAUREPL_setRo(delegate.createXML().getAt(q)) }

MCRObject.metaClass.getAt << { List q -> return _CAUREPL_setRo(delegate.createXML().getAt(q)) }

MCRObject.metaClass.call << { -> return delegate.createXML().call() }

MCRDerivate.metaClass.getAt << { String q -> return _CAUREPL_setRo(delegate.createXML().getAt(q)) }

MCRDerivate.metaClass.getAt << { List q -> return _CAUREPL_setRo(delegate.createXML().getAt(q)) }

MCRDerivate.metaClass.call << { -> return delegate.createXML().call() }

org.mycore.datamodel.metadata.MCRObjectStructure.metaClass.getAt << { String q -> return _CAUREPL_setRo(delegate.createXML().getAt(q)) }

org.mycore.datamodel.metadata.MCRObjectStructure.metaClass.getAt << { List q -> return _CAUREPL_setRo(delegate.createXML().getAt(q)) }

org.mycore.datamodel.metadata.MCRObjectStructure.metaClass.call << { -> return delegate.createXML().call() }

org.mycore.datamodel.metadata.MCRObjectService.metaClass.getAt << { String q -> return _CAUREPL_setRo(delegate.createXML().getAt(q)) }

org.mycore.datamodel.metadata.MCRObjectService.metaClass.getAt << { List q -> return _CAUREPL_setRo(delegate.createXML().getAt(q)) }

org.mycore.datamodel.metadata.MCRObjectService.metaClass.call << { -> return delegate.createXML().call() }

org.mycore.datamodel.metadata.MCRObjectMetadata.metaClass.getAt << { String q -> return _CAUREPL_setRo(delegate.createXML().getAt(q)) }

org.mycore.datamodel.metadata.MCRObjectMetadata.metaClass.getAt << { List q -> return _CAUREPL_setRo(delegate.createXML().getAt(q)) }

org.mycore.datamodel.metadata.MCRObjectMetadata.metaClass.call << { -> return delegate.createXML().call() }

org.mycore.datamodel.metadata.MCRObjectDerivate.metaClass.getAt << { String q -> return _CAUREPL_setRo(delegate.createXML().getAt(q)) }

org.mycore.datamodel.metadata.MCRObjectDerivate.metaClass.getAt << { List q -> return _CAUREPL_setRo(delegate.createXML().getAt(q)) }

org.mycore.datamodel.metadata.MCRObjectDerivate.metaClass.call << { -> return delegate.createXML().call() }


def mcrcompile(Map params=[:], path) {
    if (!params["classloader"]) params["classloader"] = de.uni_kiel.rz.fdr.repl.mycore.GroovySourceDirsStartupHandler.classLoader
    if (!params["classpath"]) params["classpath"] = de.uni_kiel.rz.fdr.repl.mycore.GroovySourceDirsStartupHandler.classPath
    return compile(params, path)
}

// creates new mycore sessions
def mcrsession(Map params=[:]) {
    def o
    try {
        if (MCRSessionMgr.hasCurrentSession()) {
            o = MCRSessionMgr.getCurrentSession()
            MCRSessionMgr.releaseCurrentSession()
        }

        if (params.keySet().intersect(["session", "sessionid", "user", "userid"]).size() > 1) throw new RuntimeException("supply at most one parameter of: session, sessionid, user, userid")
        def s = null
        def u = null
        if (params["userid"] || params["user"]) {
            u = params["user"] ? params["user"] : MCRUserManager.getUser(params["userid"])
            if (!u) throw new RuntimeException("unknown user")
            MCRSessionMgr.unlock()
            try {
                s = MCRSessionMgr.getCurrentSession()
                s.currentIP = "127.0.0.1"
                s.userInformation = u
                if (params["autoclose"]) s.put(de.uni_kiel.rz.fdr.repl.mycore.REPLStartupHandler.SESSION_AUTOCLOSE, true)
                MCRSessionMgr.currentSession = s
            } catch (Exception e) {
                s.close()
                throw e
            } finally {
                MCRSessionMgr.releaseCurrentSession()
            }
        } else if (params["session"]) {
            s = params["session"]
        } else if (params["sessionid"]) {
            s = MCRSessionMgr.getSession(params["sessionid"])
            if (!s) throw new RuntimeException("unknown session " + params["sessionid"])
        } else if (!params["newsession"]) {
            s = MCRSessionMgr.getSession(mcrsessionid)
            if (!s) throw new RuntimeException("mcr shell session is not valid")
        } else {
            throw new RuntimeException("please supply exactly one parameter of : session, sessionid, user, userid, or omit newsession")
        }
        return s
    } finally {
        if (o) MCRSessionMgr.setCurrentSession(o)
    }
}


// run a closure inside a MyCoRe session
def mcrdo(Map params=[:], Closure closure) {
    if (params.keySet().intersect(["session", "sessionid", "user", "userid", "join"]).size() > 1) throw new RuntimeException("supply at most one parameter of: session, sessionid, user, userid, join")
    def sessionparams = new HashMap(params)
    def deferautoclose = params["deferautoclose"] as boolean
    def transaction = params["transaction"] as boolean
    def session = params["session"] ?: (params["sessionid"] ? MCRSessionMgr.getSession(params["sessionid"]) : null)
    if (params["join"]) {
        if (!MCRSessionMgr.hasCurrentSession()) throw new RuntimeException("'join' requested, but there is no active session")
        session = MCRSessionMgr.currentSession
    }
    def keep = session && MCRSessionMgr.hasCurrentSession() && session == MCRSessionMgr.getCurrentSession()
    def old
    def active
    try {
        if (MCRSessionMgr.hasCurrentSession() && !keep) {
            if (!params["quiet"]) println("WARNING: there is a preexisting MyCoRe session - mcrdo will use its own session logic instead...")
            old = MCRSessionMgr.getCurrentSession()
            MCRSessionMgr.releaseCurrentSession()
        }
        if (session) active = session
        else {
            sessionparams.put("autoclose", true)
            active = mcrsession(sessionparams)
        }
        if (!MCRSessionMgr.hasCurrentSession()) MCRSessionMgr.setCurrentSession(active)
        try {
            if (transaction) MCRTransactionHelper.beginTransaction()
            def res = closure.call()
            if (transaction) MCRTransactionHelper.commitTransaction()
            return res
        } finally {
            if (transaction && MCRTransactionHelper.isTransactionActive()) {
                println(warn("MyCoRe Transaction Rollback...").message)
                MCRTransactionHelper.rollbackTransaction()
            }
            if (!keep) {
                MCRSessionMgr.releaseCurrentSession()
                if (!deferautoclose && active.get(de.uni_kiel.rz.fdr.repl.mycore.REPLStartupHandler.SESSION_AUTOCLOSE)) active.close()
            }
        }
    } finally {
        if (old) MCRSessionMgr.setCurrentSession(old)
    }
}


static def mcrinvalidate(def... ids) {
    for (def id : ids) {
        // don't reference MCRMODSWrapper by class -> not every MyCoRe installation might have it
        if (id instanceof org.mycore.datamodel.metadata.MCRBase || id.class.name.equals("org.mycore.mods.MCRMODSWrapper")) id = id.id
        if (id instanceof String) id = MCRObjectID.getInstance(id)
        org.mycore.datamodel.common.MCRCreatorCache.invalidate(id)
        org.mycore.access.MCRAccessCacheHelper.clearAllPermissionCaches(id.toString())
    }
}


def mcrsave(Map params=[:], def... object) {
    def jointransaction = params.containsKey("jointransaction") ? params["jointransaction"] as boolean : true
    def update = params.containsKey("update") ? params["update"] as boolean : true
    def reload = params.containsKey("reload") ? params["update"] as boolean : true
    def outerta = MCRTransactionHelper.isTransactionActive()
    if (!jointransaction && outerta) throw new RuntimeException("there is an outer transaction and you set jointransaction=false")
    def session = MCRSessionMgr.hasCurrentSession() ? null : mcrsession(params)
    if (session) MCRSessionMgr.setCurrentSession(session)
    try {
        if (!outerta) MCRTransactionHelper.beginTransaction()
        object = object.flatten()
        for (def o : object) {
            if (o instanceof MCRObject) update ? MCRMetadataManager.update(o) : MCRMetadataManager.create(o)  // making sure to reinit the object from the xml in the update case
            else if (o instanceof MCRDerivate) update ? MCRMetadataManager.update(o) : MCRMetadataManager.create(o)  // making sure to reinit the object from the xml in the update case
            else if (o instanceof Document && o["/mycoreobject"]) update ? MCRMetadataManager.update(new MCRObject(o)) : MCRMetadataManager.create(new MCRObject(o))
            else if (o instanceof Document && o["/mycorederivate"]) update ? MCRMetadataManager.update(new MCRDerivate(o)) : MCRMetadataManager.create(new MCRDerivate(o))
            // don't reference MCRMODSWrapper by class -> not every MyCoRe installation might have it
            else if (o.class.name.equals("org.mycore.mods.MCRMODSWrapper")) update ? MCRMetadataManager.update(o.getMCRObject()) : MCRMetadataManager.create(o.getMCRObject())
            else throw new RuntimeException("don't know how to handle class " + o.class)
            mcrinvalidate(o.id)
            if (reload) o.reload()
        }
        if (!outerta) MCRTransactionHelper.commitTransaction()
    } finally {
        if (session) {
            if (MCRTransactionHelper.isTransactionActive()) {
                println("MyCoRe Transaction Rollback...")
                MCRTransactionHelper.rollbackTransaction()
            }
            MCRSessionMgr.releaseCurrentSession()
            if (session.get(de.uni_kiel.rz.fdr.repl.mycore.REPLStartupHandler.SESSION_AUTOCLOSE)) session.close()
        }
    }
}


// run a repl- / mycore-processing job
def mcrjob(Map params=[:], Closure closure) {
    params = new HashMap(params)
    if (params.keySet().intersect(["session", "sessionid", "user", "userid", "threadfactory"]).size() == 0) params.put("user", org.mycore.common.MCRSystemUserInformation.superUserInstance)
    if (params.keySet().intersect(["session", "sessionid", "user", "userid", "threadfactory"]).size() > 1) throw new RuntimeException("supply at most one parameter of: session, sessionid, user, userid, threadfactory")
    def convert = params["convert"] as boolean
    if (params["inputs"] && !convert) {
        if (params["inputs"][0] instanceof org.mycore.datamodel.metadata.MCRBase) throw new RuntimeException("MCRObject and MCRDerivate objects are not serializable and can't directly be used as job inputs. Use .createXML() instead or set the \"convert\" parameter of mcrjob() to transform them automatically.")
        if (params["inputs"][0].class.name.equals("org.mycore.mods.MCRMODSWrapper")) throw new RuntimeException("MCRMODSWrapper objects are not serializable and can't directly be used as job inputs. Use the .createXML() instead or set the \"convert\" parameter of mcrjob() to transform them automatically.")
    } else if (params["inputs"]) {
        params["inputs"] = params["inputs"].collect { it instanceof Document ? it : it.createXML() }
    }
    def transaction = params["transaction"] as boolean
    def transactionerrors = params.containsKey("transactionerrors") ? params["transactionerrors"] : 1
    def s = null
    if (!params.containsKey("threadfactory")) {
        params.put("newsession", true)
        params.put("autoclose", true)
        s = mcrsession(params)
        def ic = { ev ->
            switch (ev.eventType()) {
                case REPLJob.JobEventType.INPUT_ERROR -> {
                    def p = ev.job().progress
                    if (transaction && p.isActive() && p.state() != REPLJob.JobState.CANCELLING && p.errors() >= transactionerrors) {
                        ev.job().warn("There were {} error(s) in the transaction of session {} - cancelling job", p.errors(), s.ID)
                        ev.job().cancel()
                    }
                }
                case REPLJob.JobEventType.JOB_START -> {
                    ev.job().debug("Using MyCoRe user {}, {}session {}", s.userInformation.userID, s.get(de.uni_kiel.rz.fdr.repl.mycore.REPLStartupHandler.SESSION_AUTOCLOSE) ? "transient ": "", s.ID)
                    if (transaction && !MCRTransactionHelper.isTransactionActive()) {
                        ev.job().debug("Starting a new transaction for session {}", s.ID)
                        MCRTransactionHelper.beginTransaction()
                    }
                }
                case REPLJob.JobEventType.JOB_DONE_SUCCESS, REPLJob.JobEventType.JOB_DONE_CANCELLED, REPLJob.JobEventType.JOB_DONE_INTERNALERROR -> {
                    if (transaction) {
                        if (!MCRTransactionHelper.isTransactionActive()) {
                            eb.job().warn("The transaction for session {} was no longer active in the end", s.ID)
                        } else if (ev.eventType() == REPLJob.JobEventType.JOB_DONE_SUCCESS) {
                            ev.job().debug("Committing transaction for session {}", s.ID)
                            mcrdo({ MCRTransactionHelper.commitTransaction() }, session: s, deferautoclose: true)
                        } else {
                            ev.job().warn("Rolling back transaction for session {}", s.ID)
                            mcrdo( { MCRTransactionHelper.rollbackTransaction() }, session: s, deferautoclose: true)
                        }
                    }
                    if (s.get(de.uni_kiel.rz.fdr.repl.mycore.REPLStartupHandler.SESSION_AUTOCLOSE)) {
                        ev.job().debug("Closing MyCoRe Session {}", s.ID)
                        s.close()
                    }
                }
            }
        }
        params.put("internalcallback", params["internalcallback"] ? params["internalcallback"].andThen(ic) : ic)
        params.put("threadfactory", new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return java.util.concurrent.Executors.defaultThreadFactory().newThread(() -> {
                    org.mycore.common.MCRSessionMgr.setCurrentSession(s)
                    r.run()
                })
            }
        })
    } else {
        println("WARNING: will use your user-supplied thread factory - session handling and transactions disabled")
    }
    return job(params, closure)
}

def mcrids(Map params=[:], Object... selector) {
    def documents = params.containsKey("with_documents") ? params["with_documents"] as boolean : true
    def derivates = params.containsKey("with_derivates") ? params["with_derivates"] as boolean : false
    List<MCRObjectID> ids = []

    if (!selector) selector = MCRXMLMetadataManager.instance().listIDs()
    else selector = selector.flatten()
    for (def x : selector) {
        def isDer = x.toString().contains("_derivate_") || x.toString().endsWith("_derivate") || x.toString().equals("derivate")
        if (!derivates && isDer) continue
        if (!documents && !isDer) continue
        if (MCRObjectID.isValid(x.toString())) ids.add(MCRObjectID.getInstance(x))
        else if (x.count("_") > 1) throw new RuntimeException("malformed mcr object id: " + x)
        else if (x.contains("_")) ids.addAll(MCRXMLMetadataManager.instance().listIDsForBase(x).collect({MCRObjectID.getInstance(it)}))
        else ids.addAll(MCRXMLMetadataManager.instance().listIDsOfType(x).collect({MCRObjectID.getInstance(it)}))
    }
    return ids
}

def mcrderids(Object... selector) {
    return mcrids(selector, with_documents: false, with_derivates: true)
}

def mcrstream(selector=null, filter=null) {
    def xpath = null
    if (filter instanceof String) {
        try {
            xpath = XPathFactory.instance().compile(filter, org.jdom2.filter.Filters.fpassthrough(), null, MCRConstants.getStandardNamespaces())
        } catch (IllegalArgumentException ex) {
            throw ex.cause ?: ex
        }
        filter = { !xpath.evaluate(it.createXML()).isEmpty() }
    }
    def stream = mcrids(selector).stream()
            .map {MCRMetadataManager.retrieveMCRObject(it) }
    if (!filter) return stream as java.util.stream.Stream<MCRObject>
    return stream.filter(filter) as java.util.stream.Stream<MCRObject>
}


def mcrderstream(selector="derivate", filter=null) {
    def xpath = null
    if (filter instanceof String) {
        xpath = XPathFactory.instance().compile(filter, org.jdom2.filter.Filters.fpassthrough(), null, MCRConstants.getStandardNamespaces())
        filter = { !xpath.evaluate(it.createXML()).isEmpty() }
    }
    def stream = mcrderids(selector).stream()
            .map { MCRMetadataManager.retrieveMCRDerivate(it) }
    if (!filter) return stream as java.util.stream.Stream<MCRDerivate>
    return stream.filter(filter) as java.util.stream.Stream<MCRDerivate>
}


def mcrstreamxml(selector=null, filter=null) {
    def xpath = null
    if (filter instanceof String) {
        xpath = XPathFactory.instance().compile(filter, org.jdom2.filter.Filters.fpassthrough(), null, MCRConstants.getStandardNamespaces())
        filter = { !xpath.evaluate(it).isEmpty() }
    }
    def stream = mcrids(selector).stream()
                            .map {MCRXMLMetadataManager.instance().retrieveXML(it) }
    if (!filter) return stream as java.util.stream.Stream<Document>
    return stream.filter(filter) as java.util.stream.Stream<Document>
}


def mcrderstreamxml(selector=null, filter=null) {
    def xpath = null
    if (filter instanceof String) {
        xpath = XPathFactory.instance().compile(filter, org.jdom2.filter.Filters.fpassthrough(), null, MCRConstants.getStandardNamespaces())
        filter = { !xpath.evaluate(it).isEmpty() }
    }
    def stream = mcrderids(selector).stream()
            .map { MCRXMLMetadataManager.instance().retrieveXML(it) }
    if (!filter) return stream as java.util.stream.Stream<Document>
    return stream.filter(filter) as java.util.stream.Stream<Document>
}


def mcrxml(selector=null, filter=null) {
    def x = mcrstreamxml(selector, filter).toList()
    if (selector instanceof MCRObjectID || (selector instanceof String && MCRObjectID.isValid(selector))) return x.isEmpty() ? null : x.get(0) as Document
    return x as List<Document>
}


def mcrderxml(selector=null, filter=null) {
    def x = mcrderstreamxml(selector, filter).toList()
    if (selector instanceof MCRObjectID || (selector instanceof String && MCRObjectID.isValid(selector))) return x.isEmpty() ? null : x.get(0) as Document
    return x as List<Document>
}


def mcrobj(selector=null, filter=null) {
    def x = mcrstream(selector, filter).toList()
    if (selector instanceof MCRObjectID || (selector instanceof String && MCRObjectID.isValid(selector))) return x.isEmpty() ? null : x.get(0) as MCRObject
    return x as List<MCRObject>
}


def mcrder(selector=null, filter=null) {
    def x = mcrderstream(selector, filter).toList()
    if (selector instanceof MCRObjectID || (selector instanceof String && MCRObjectID.isValid(selector))) return x.isEmpty() ? null : x.get(0) as MCRDerivate
    return x as List<MCRDerivate>
}

def mcrdiff(old=null, updated) {
    if (!old) old = mcrxml(updated.id)
    def xmlOut = new XMLOutputter()
    if (!(old instanceof Document)) old = old.createXML()
    old = xmlOut.outputString(old).split("\\n").toList()
    if (!(updated instanceof Document)) updated = updated.createXML()
    updated = xmlOut.outputString(updated).split("\\n").toList()
    def patch = com.github.difflib.DiffUtils.diff(old, updated)
    def result = new StringBuilder()
    for (def d : patch.deltas) {
        result.append("==========  @" + d.source.position + " / @" + d.target.position + "\n")
        if (d.source?.lines) for (def s : d.source.lines) result.append("-- " + s.replaceAll("\\r+\$", "\\\n"))
        if (d.target?.lines) for (def s : d.target.lines) result.append("++ " + s.replaceAll("\\r+\$", "\\\n"))
    }
    return result.isEmpty() ? null : result.toString()
}

def mcrsolr(Map params=[:], String query) {
    def client = params["client"] ?: MCRSolrClientFactory.mainSolrClient
    def sp = new org.apache.solr.common.params.ModifiableSolrParams()
    sp.set("q", query)
    sp.set("rows", Integer.MAX_VALUE)
    if (params.containsKey("start")) sp.set("start", params["start"])
    if (params.containsKey("rows")) sp.set("rows", params["rows"])
    if (params["sort"]) sp.set("sort", params["sort"])
    if (params["fl"]) sp.set("fl", params["fl"])
    return client.query(sp)
}

def mcrsolrfirst(Map params=[:], String query) {
    params = new HashMap(params)
    params["rows"] = 1
    def res = mcrsolr(params, query)
    return res.results.isEmpty() ? null : res.results[0]
}

def mcrsolrstream(Map params=[:], String query) {
    if (params.containsKey("start")) throw new RuntimeException("'start' parameter is not supported")
    if (params.containsKey("rows")) throw new RuntimeException("'rows' parameter is not supported")
    def client = params["client"] ?: MCRSolrClientFactory.mainSolrClient
    def parallel = params["parallel"] as boolean
    def chunksize = params["chunksize"] ?: 1000
    def sp = new org.apache.solr.common.params.ModifiableSolrParams()
    sp.set("q", query)
    if (params["sort"]) sp.set("sort", params["sort"])
    if (params["fl"]) sp.set("fl", params["fl"])
    return MCRSolrSearchUtils.stream(client, sp, parallel, chunksize)
}

def mcrsolrids(Map params=[:], String query) {
    params = new HashMap(params)
    params["fl"] = "id"
    return mcrsolr(params, query).results.collect { it["id"] }
}


def mcrcli(Map params=[:], String... commands) {
    def manager = new org.mycore.frontend.cli.MCRCommandManager()  // must be at the beginning to force inititialization of static vars
    def loggers = org.mycore.frontend.cli.MCRCommandManager.getKnownCommands().values().stream()
            .flatMap {it.stream()}
            .map {org.apache.logging.log4j.LogManager.getLogger(it.method.declaringClass)}
            .collect(java.util.stream.Collectors.toSet())
    [org.mycore.frontend.cli.MCRCommand.class, org.mycore.frontend.cli.MCRCLIExceptionHandler.class,
     org.mycore.frontend.cli.MCRCommandLineInterface.class, org.mycore.frontend.cli.MCRCommandManager.class,
     org.mycore.frontend.cli.MCRCommandUtils.class, org.mycore.frontend.cli.MCRExternalProcess.class].each{
        loggers.add(org.apache.logging.log4j.LogManager.getLogger(it))
    }
    def logFilter = org.apache.logging.log4j.core.filter.RegexFilter.createFilter("^No match for syntax: .*", [] as String[], false, org.apache.logging.log4j.core.Filter.Result.DENY, org.apache.logging.log4j.core.Filter.Result.ACCEPT)
    def outerAppender = org.apache.logging.log4j.core.appender.OutputStreamAppender.createAppender(null, logFilter, _cauOut, "CAUREPL-O-" + Thread.currentThread().id, false, true)
    for (def l : loggers) l.addAppender(outerAppender)
    def innerAppender = new de.uni_kiel.rz.fdr.repl.mycore.Log4jListAppender("CAUREPL-I-" + Thread.currentThread().id, logFilter)
    for (def l : loggers) l.addAppender(innerAppender)
    def session = params["session"] ?: mcrsession()
    def watchLog = params.containsKey("errors_from_log") ? params["errors_from_log"] as boolean : true
    def errors = 0
    def result = []
    session.put("cauCLIResult", result)
    try {
        def queue = new LinkedList(commands.toList())
        while (queue) {
            def cmd = queue.pop()
            def ts = java.time.Instant.now()
            if (cmd.trim().startsWith("#")) continue
            try {
                def newCmds
                debug("Executing MyCoRe CLI Command: {}", cmd)
                mcrdo({
                    newCmds = manager.invokeCommand(org.mycore.frontend.cli.MCRCommandLineInterface.expandCommand(cmd))
                }, transaction: true, session: session, quiet: true)
                if (watchLog) {
                    def newErr = innerAppender.getError()
                    if (newErr) throw new RuntimeException(newErr.message.formattedMessage.trim())
                }
                if (newCmds == null || !(innerAppender.events.find{it.message.formattedMessage =~ /^Syntax matched \(executed\): /})) throw new RuntimeException("Command not understood: " + cmd)
                else queue.addAll(0, newCmds)
                result.add([cmd: cmd, log: innerAppender.resetEvents(), error: null, timestamp: ts])
            } catch (Exception e) {
                errors++
                result.add([cmd: cmd, log: innerAppender.resetEvents(), error: e, timestamp: ts])
                error("ERROR: Command '{}' failed", cmd, e)
                if (!org.mycore.frontend.cli.MCRCommandLineInterface.SKIP_FAILED_COMMAND) throw e
            }
        }
    } finally {
        if (errors) error("CLI finished with {} errors", errors)
        else info("CLI finished successfully: {} commands", result.size())
        println("===> Hint: retrieve detailed results from the \"cauCLIResult\" session variable, e.g.: mcrsession().get(\"cauCLIResult\")")
        for (def l : loggers) {
            l.removeAppender(outerAppender)
            l.removeAppender(innerAppender)
        }
    }
    return !errors
}


def mcrxslt(Map params=[:], source, String... stylesheet) {
    params = new HashMap(params)
    def xsltParams = params.containsKey("params") ? params["params"] as Map<String, String> : new HashMap<String, String>()
    if (!params.keySet().intersect(["session", "sessionid", "user", "userid", "join"]) && MCRSessionMgr.hasCurrentSession()) params["join"] = true
    def content
    if (source instanceof org.mycore.datamodel.metadata.MCRBase) content = source.createXML()
    else if (source.class.name.equals("org.mycore.mods.MCRMODSWrapper")) content = source.MCRObject.createXML()
    else if (source instanceof MCRObjectID || (source instanceof String && MCRObjectID.isValid(source))) {
        try {
            content = mcrxml(source)
        } catch (Exception ignore) {
            content = mcrderxml(source)
        }
    }
    else if (source instanceof String) content = org.mycore.common.xml.MCRXMLParserFactory.nonValidatingParser.parseXML(new org.mycore.common.content.MCRStringContent(source))
    else content = source

    if (!stylesheet) return content
    def xml = new org.mycore.common.content.MCRJDOMContent(content)
    def transformer = org.mycore.common.content.transformer.MCRXSLTransformer.getInstance(stylesheet)
    return mcrdo(params, {
        def pc = new org.mycore.common.xsl.MCRParameterCollector()
        pc.setParameters(xsltParams)
        return transformer.transform(xml, pc).asXML()
    })
}

