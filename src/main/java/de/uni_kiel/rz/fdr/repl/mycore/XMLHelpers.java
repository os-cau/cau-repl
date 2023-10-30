// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.mycore;

import org.jdom2.*;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Some helper functions related to JDOM XML.
 */
public class XMLHelpers {

    // utility class only
    private XMLHelpers() {}

    /**
     * Sets the namespace of a child node and all of its descendants to the namespace of reference, but only if
     * <b>none</b> of the child or its descendants has a defined namespace.
     * @param reference The reference node for the namespace.
     * @param child The child that will recursively inherit the namspace.
     * @param <T> Class of the child.
     * @return The child, whether modified or not.
     */
    public static <T> T inheritNamespace(NamespaceAware reference, T child) {
        return inheritNamespace(reference, List.of(child)).stream().findFirst().orElseThrow();
    }

    /**
     * For each child in children: sets the namespace of a child node and all of its descendants to the namespace of
     * reference, but only if <b>none</b> of the children or their descendants have a defined namespace.
     * @param reference The reference node for the namespace.
     * @param children The child that will recursively inherit the namspace.
     * @param <T> Class of the children.
     * @return The childrem, whether modified or not.
     */
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

    /**
     * Returns all elements and attributes below the root, including the root itself.
     * @param root The root node to recurse from.
     * @return The list of the sub-elements and attributes, including the root itself.
     */
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

    private static List<Content> stringToElement(String s, NamespaceAware nsReference) throws JDOMException {
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
            throw new UncheckedIOException(e);
        }
        return List.copyOf(doc.getRootElement().getContent()).stream().map(Content::detach).toList();
    }

    private static Collection<Content> collectionToContent(Collection<?> content, NamespaceAware nsReference) throws JDOMException {
        List<Content> list = new ArrayList<>();
        for (Object x : content) {
            if (x instanceof String s) {
                String st = s.trim();
                if (st.startsWith("<") && st.endsWith(">")) list.addAll(inheritNamespace(nsReference, stringToElement(s, nsReference)));
                else list.add(inheritNamespace(nsReference, new Text(s)));
            } else if (x instanceof Content c) {
                list.add(inheritNamespace(nsReference, c));
            } else {
                throw new IllegalArgumentException("Can't cast " + x.getClass() + " to JDOM Content.");
            }
        }
        return list;
    }

    /**
     * Adds content to a JDOM node, automatically coverting XML-like strings to JDOM elements.
     * @param parent The parent node to which the content will be added.
     * @param content The content that will be added, either JDOM {@code Content} or {@code String}. The latter
     *                will be automatically converted to a JDOM {@code Element} if it starts with "{@code <}" and
     *                ends with "{@code >}".
     * @param nsInherit Controls whether the content shall inherit the parent's XML namespace according to the rules of
     *                  {@link XMLHelpers#inheritNamespace(NamespaceAware, Object) inheritNamespace()}.
     * @return The parent node, with the added content.
     * @throws JDOMException If your XML-like string is malformed.
     */
    public static Parent addStringOrElement(Parent parent, Object content, boolean nsInherit) throws JDOMException {
        return addStringOrElement(parent, List.of(content), nsInherit);
    }

    /**
     * Adds content to a JDOM node, automatically coverting XML-like strings to JDOM elements.
     * @param parent The parent node to which the content will be added.
     * @param content The content that will be added, either JDOM {@code Content} or {@code String}. The latter
     *                will be automatically converted to a JDOM {@code Element} if it starts with "{@code <}" and
     *                ends with "{@code >}".
     * @param nsInherit Controls whether the content shall inherit the parent's XML namespace according to the rules of
     *                  {@link XMLHelpers#inheritNamespace(NamespaceAware, Object) inheritNamespace()}.
     * @return The parent node, with the added content.
     * @throws JDOMException If any of your XML-like strings are malformed.
     */
    public static Parent addStringOrElement(Parent parent, Collection<?> content, boolean nsInherit) throws JDOMException {
        if (parent instanceof Element e) {
            e.addContent(collectionToContent(content, nsInherit ? parent : null));
        } else if (parent instanceof Document d) {
            d.addContent(collectionToContent(content, nsInherit ? parent : null));
        } else throw new RuntimeException("internal error: not a document or element");
        return parent;
    }

    /**
     * Sets the content of a JDOM node, automatically coverting XML-like strings to JDOM elements.
     * @param parent The parent node whose content will be set.
     * @param content The content that will be added, either JDOM {@code Content} or {@code String}. The latter
     *                will be automatically converted to a JDOM {@code Element} if it starts with "{@code <}" and
     *                ends with "{@code >}".
     * @param nsInherit Controls whether the content shall inherit the parent's XML namespace according to the rules of
     *                  {@link XMLHelpers#inheritNamespace(NamespaceAware, Collection) inheritNamespace()}.
     * @return The parent node, with the altered content.
     * @throws JDOMException If your XML-like string is malformed.
     */
    public static Parent setStringOrElement(Parent parent, Object content, boolean nsInherit) throws JDOMException {
        return setStringOrElement(parent, List.of(content), nsInherit);
    }

    /**
     * Sets the content of a JDOM node, automatically coverting XML-like strings to JDOM elements.
     * @param parent The parent node whose content will be set.
     * @param content The content that will be added, either JDOM {@code Content} or {@code String}. The latter
     *                will be automatically converted to a JDOM {@code Element} if it starts with "{@code <}" and
     *                ends with "{@code >}".
     * @param nsInherit Controls whether the content shall inherit the parent's XML namespace according to the rules of
     *                  {@link XMLHelpers#inheritNamespace(NamespaceAware, Object) inheritNamespace()}.
     * @return The parent node, with the altered content.
     * @throws JDOMException If your XML-like string is malformed.
     */
    public static Parent setStringOrElement(Parent parent, Collection<?> content, boolean nsInherit) throws JDOMException {
        if (parent instanceof Element e) {
            e.setContent(collectionToContent(content, nsInherit ? parent : null));
        } else if (parent instanceof Document d) {
            d.setContent(collectionToContent(content, nsInherit ? parent : null));
        } else throw new RuntimeException("internal error: not a document or element");
        return parent;
    }

}
