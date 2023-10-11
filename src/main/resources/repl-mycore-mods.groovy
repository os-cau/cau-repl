//file:noinspection UnnecessaryQualifiedReference

// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: GPL-3.0-only

import org.mycore.mods.MCRMODSWrapper

MCRMODSWrapper.metaClass.toString = { -> return "MCRMODSWrapper(" + getMCRObject().getId().toString() + ")" }
MCRMODSWrapper.metaClass.getAt << { int q -> return _CAUREPL_setRo(delegate.MODS.getAt(q)) }
MCRMODSWrapper.metaClass.getAt << { String q -> return _CAUREPL_setRo(delegate.MODS.getAt(q)) }
MCRMODSWrapper.metaClass.getAt << { List q -> return _CAUREPL_setRo(delegate.MODS.getAt(q)) }
MCRMODSWrapper.metaClass.call << { -> return delegate.MODS.call() }
MCRMODSWrapper.metaClass.leftShift << { Object x -> return delegate.MODS.leftShift(x) }
MCRMODSWrapper.metaClass.plus << { Object x -> return delegate.MODS.plus(x) }
MCRMODSWrapper.metaClass.reload << { Object x -> return delegate.getMCRObject().reload() }
MCRMODSWrapper.metaClass.getId << { -> return delegate.getMCRObject().getId() }
MCRMODSWrapper.metaClass.getJdomDocument << { -> return delegate.getMCRObject().jdomDocument }
MCRMODSWrapper.metaClass.createXML << { -> return delegate.getMCRObject().createXML() }

def mcrmods(selector="mods", filter=null) {
    def s = mcrstream(selector, filter instanceof String ? filter : null).map {
        if (!MCRMODSWrapper.isSupported(it)) throw new RuntimeException("mycore object ${it.id} does not seem to be a MODS container")
        return new org.mycore.mods.MCRMODSWrapper(it)
    }
    if (filter && !(filter instanceof String)) s = s.filter(filter)
    def x = s.toList()
    if (selector instanceof String && MCRObjectID.isValid(selector)) return x.isEmpty() ? null : x.get(0) as org.mycore.mods.MCRMODSWrapper
    return x as List<org.mycore.mods.MCRMODSWrapper>
}

def mcrmodsstream(selector="mods", filter=null) {
    def s = mcrstream(selector, filter instanceof String ? filter : null).map {
        if (!MCRMODSWrapper.isSupported(it)) throw new RuntimeException("mycore object ${it.id} does not seem to be a MODS container")
        return new org.mycore.mods.MCRMODSWrapper(it)
    }
    if (filter && !(filter instanceof String)) s = s.filter(filter)
    return s as java.util.stream.Stream<org.mycore.mods.MCRMODSWrapper>
}
