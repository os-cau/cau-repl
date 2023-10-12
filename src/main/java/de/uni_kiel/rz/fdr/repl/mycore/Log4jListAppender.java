// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.mycore;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Log4jListAppender extends AbstractAppender {
    public List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());

    public Log4jListAppender(final String name, final Filter filter) {
        super(name, filter, null, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
        // we always convert messages to a SimpleMessage to get rid of any large objects associated with them, saving memory
        Log4jLogEvent.Builder builder = new Log4jLogEvent.Builder(event);
        builder.setMessage(new SimpleMessage(event.getMessage().getFormattedMessage()));
        events.add(builder.build());
    }

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
