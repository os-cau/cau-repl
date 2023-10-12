// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.mycore;

import org.jdom2.*;
import org.jdom2.filter.ElementFilter;

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
    // child or its descendants has a defined namespace. descendants of other children are ignored for each individual
    // child
    public static <T> Collection<T> inheritNamespace(NamespaceAware reference, Collection<T> children) {
        Namespace ns = null;
        if (reference instanceof Element e) ns = e.getNamespace();
        if (reference instanceof Attribute a) ns = a.getNamespace();
        final Namespace nsf = ns;
        if (nsf == null || nsf.getURI().isEmpty()) return children;
        for (Object child : children) {
            if (!(child instanceof NamespaceAware)) continue;
            NamespaceAware c = (NamespaceAware) child;
            List<NamespaceAware> all = recurse(c);
            List<Element> elements = all.stream().filter(e -> e instanceof Element).map(e -> (Element) e).toList();
            if (elements.stream().anyMatch(e -> !e.getNamespace().getURI().isEmpty())) continue;
            List<Attribute> attributes = all.stream().filter(a -> a instanceof Attribute).map(a -> (Attribute) a).toList();
            if (attributes.stream().anyMatch(a -> !a.getNamespace().getURI().isEmpty())) continue;
            elements.stream().forEach(e -> e.setNamespace(nsf));
            // most xml schemas do not use namespaces for attributes and derive them for their elements, so do not add them there
            // attributes.stream().forEach(a -> a.setNamespace(nsf));
        }
        return children;
    }

    // returns all elements and attributes below, including root itself
    public static List<NamespaceAware> recurse(NamespaceAware root) {
        if (root instanceof Parent p) {
            List<NamespaceAware> all = new ArrayList<>(List.of(p));
            if (p instanceof Element e) all.addAll(e.getAttributes());
            p.getDescendants(new ElementFilter()).forEachRemaining(e -> {all.add(e); all.addAll(e.getAttributes());});
            return all;
        } else {
            return List.of(root);
        }
    }
}
