// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: GPL-3.0-only

package de.uni_kiel.rz.fdr.repl.mycore;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Log4jListAppender extends AbstractAppender {
    public List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());

    public Log4jListAppender(final String name, final Filter filter) {
        super(name, filter, null, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(org.apache.logging.log4j.core.LogEvent event) { events.add(event); }

    // not thread-safe
    public List<LogEvent> resetEvents() {
        ArrayList<LogEvent> l = new ArrayList<>(events);
        events.clear();
        return l;
    }

    public LogEvent getError() {
        return events.stream().filter(e -> e.getLevel().isMoreSpecificThan(Level.WARN)).findFirst().orElse(null);
    }
}
