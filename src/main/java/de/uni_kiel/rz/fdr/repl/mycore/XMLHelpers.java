// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.mycore;

import org.jdom2.*;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class XMLHelpers {

    // set the namespace of child and all of its descendants to the namespace of reference, but only if *none* of
    // child or its descendants has a defined namespace
    public static <T> T inheritNamespace(NamespaceAware reference, T child) {
        return inheritNamespace(reference, List.of(child)).stream().findFirst().orElseThrow();
    }

    // for each child in children:
    // set the namespace of child and all of its descendants to the namespace of reference, but only if *none* of
    // the children or its descendants has a defined namespace.
    public static <T> Collection<T> inheritNamespace(NamespaceAware reference, Collection<T> children) {
        Namespace ns = null;
        if (reference instanceof Element e) ns = e.getNamespace();
        if (reference instanceof Attribute a) ns = a.getNamespace();
        final Namespace nsf = ns;
        if (nsf == null || nsf.getURI().isEmpty()) return children;
        List<NamespaceAware> all = new ArrayList<>();
        for (Object child : children) {
            if (!(child instanceof NamespaceAware)) continue;
            NamespaceAware c = (NamespaceAware) child;
            List<NamespaceAware> here = recurse(c);
            List<Element> elements = here.stream().filter(e -> e instanceof Element).map(e -> (Element) e).toList();
            if (elements.stream().anyMatch(e -> !e.getNamespace().getURI().isEmpty())) return children;
            List<Attribute> attributes = here.stream().filter(a -> a instanceof Attribute).map(a -> (Attribute) a).toList();
            if (attributes.stream().anyMatch(a -> !a.getNamespace().getURI().isEmpty())) return children;
            all.addAll(here);
        }
        // no namespaces found, now carry out inheritance
        // most xml schemas do not use namespaces for attributes and derive them for their elements, so do not add them there
        all.stream().filter(x -> x instanceof Element).forEach(e -> ((Element) e).setNamespace(nsf));
        return children;
    }

    // returns all elements and attributes below, including root itself
    public static List<NamespaceAware> recurse(NamespaceAware root) {
        if (root instanceof Parent p) {
            List<NamespaceAware> all = new ArrayList<>(List.of(p));
            if (p instanceof Element e) all.addAll(e.getAttributes());
            p.getDescendants(new ElementFilter()).forEachRemaining(e -> {
                all.add(e);
                all.addAll(e.getAttributes());
            });
            return all;
        } else {
            return List.of(root);
        }
    }

    public static List<Content> stringToElement(String s, NamespaceAware nsReference) throws JDOMException {
        Element nsRoot = new Element("root");
        if (nsReference != null) for (Namespace ns : nsReference.getNamespacesInScope()) nsRoot.addNamespaceDeclaration(ns);
        String nsRootStr = new XMLOutputter(org.jdom2.output.Format.getRawFormat()).outputString(nsRoot).trim();
        if (!nsRootStr.endsWith("/>")) throw new RuntimeException("Internal error: namespace dummy root element has invalid format");
        nsRootStr = nsRootStr.substring(0, nsRootStr.length() - 2) + ">";
        SAXBuilder sax = new SAXBuilder();
        Document doc;
        try {
            doc = sax.build(new StringReader(nsRootStr + s + "</root>"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return List.copyOf(doc.getRootElement().getContent()).stream().map(Content::detach).toList();
    }

    public static Collection<Content> collectionToContent(Collection<?> content, NamespaceAware nsReference) throws JDOMException {
        List<Content> list = new ArrayList<>();
        for (Object x : content) {
            if (x instanceof String s) {
                String st = s.trim();
                if (st.startsWith("<") && st.endsWith(">")) list.addAll(inheritNamespace(nsReference, stringToElement(s, nsReference)));
                else list.add(inheritNamespace(nsReference, new Text(s)));
            } else if (x instanceof Content c) {
                list.add(inheritNamespace(nsReference, c));
            } else {
                throw new RuntimeException("Can't cast " + x.getClass() + " to JDOM Content.");
            }
        }
        return list;
    }

    public static Parent addStringOrElement(Parent parent, Object content, boolean nsInherit) throws JDOMException {
        return addStringOrElement(parent, List.of(content), nsInherit);
    }

    public static Parent addStringOrElement(Parent parent, Collection<?> content, boolean nsInherit) throws JDOMException {
        if (parent instanceof Element e) {
            e.addContent(collectionToContent(content, nsInherit ? parent : null));
        } else if (parent instanceof Document d) {
            d.addContent(collectionToContent(content, nsInherit ? parent : null));
        } else throw new RuntimeException("internal error: not a document or element");
        return parent;
    }

    public static Parent setStringOrElement(Parent parent, Object content, boolean nsInherit) throws JDOMException {
        return setStringOrElement(parent, List.of(content), nsInherit);
    }

    public static Parent setStringOrElement(Parent parent, Collection<?> content, boolean nsInherit) throws JDOMException {
        if (parent instanceof Element e) {
            e.setContent(collectionToContent(content, nsInherit ? parent : null));
        } else if (parent instanceof Document d) {
            d.setContent(collectionToContent(content, nsInherit ? parent : null));
        } else throw new RuntimeException("internal error: not a document or element");
        return parent;
    }

}
