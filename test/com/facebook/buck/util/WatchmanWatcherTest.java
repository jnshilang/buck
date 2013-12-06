/*
 * Copyright 2013-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.util;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;

import org.easymock.Capture;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;

public class WatchmanWatcherTest {

  @Test
  public void whenFilesListIsEmptyThenNoEventsAreGenerated() throws IOException {
    String watchmanOutput = Joiner.on('\n').join(
        "{",
        "\"version\": \"2.9.2\",",
        "\"clock\": \"c:1386170113:26390:5:50273\",",
        "\"is_fresh_instance\": false,",
        "\"files\": []",
        "}");
    EventBus eventBus = createStrictMock(EventBus.class);
    Process process = createMockProcess(watchmanOutput);
    replay(eventBus, process);
    WatchmanWatcher watcher = createWatcher(eventBus, process);
    watcher.postEvents();
    verify(eventBus, process);
  }

  @Test
  public void whenNameThenModifyEventIsGenerated() throws IOException {
    String watchmanOutput = Joiner.on('\n').join(
        "{\"files\": [",
            "{",
                "\"name\": \"/foo/bar/baz\"",
            "}",
        "]}");
    Capture<WatchEvent<Path>> eventCapture = new Capture<>();
    EventBus eventBus = createStrictMock(EventBus.class);
    eventBus.post(capture(eventCapture));
    Process process = createMockProcess(watchmanOutput);
    replay(eventBus, process);
    WatchmanWatcher watcher = createWatcher(eventBus, process);
    watcher.postEvents();
    verify(eventBus, process);
    assertEquals("Should be modify event.",
        StandardWatchEventKinds.ENTRY_MODIFY,
        eventCapture.getValue().kind());
  }

  @Test
  public void whenNewIsTrueThenCreateEventIsGenerated() throws IOException {
    String watchmanOutput = Joiner.on('\n').join(
        "{\"files\": [",
            "{",
                "\"name\": \"/foo/bar/baz\",",
                "\"new\": true",
            "}",
        "]}");
    Capture<WatchEvent<Path>> eventCapture = new Capture<>();
    EventBus eventBus = createStrictMock(EventBus.class);
    eventBus.post(capture(eventCapture));
    Process process = createMockProcess(watchmanOutput);
    replay(eventBus, process);
    WatchmanWatcher watcher = createWatcher(eventBus, process);
    watcher.postEvents();
    verify(eventBus, process);
    assertEquals("Should be modify event.",
        StandardWatchEventKinds.ENTRY_CREATE,
        eventCapture.getValue().kind());
  }

  @Test
  public void whenExistsIsFalseThenDeleteEventIsGenerated() throws IOException {
    String watchmanOutput = Joiner.on('\n').join(
        "{\"files\": [",
            "{",
                "\"name\": \"/foo/bar/baz\",",
                "\"exists\": false",
            "}",
        "]}");
    Capture<WatchEvent<Path>> eventCapture = new Capture<>();
    EventBus eventBus = createStrictMock(EventBus.class);
    eventBus.post(capture(eventCapture));
    Process process = createMockProcess(watchmanOutput);
    replay(eventBus, process);
    WatchmanWatcher watcher = createWatcher(eventBus, process);
    watcher.postEvents();
    verify(eventBus, process);
    assertEquals("Should be modify event.",
        StandardWatchEventKinds.ENTRY_DELETE,
        eventCapture.getValue().kind());
  }

  @Test
  public void whenNewAndNotExistsThenDeleteEventIsGenerated() throws IOException {
    String watchmanOutput = Joiner.on('\n').join(
        "{\"files\": [",
            "{",
                "\"name\": \"/foo/bar/baz\",",
                "\"new\": true,",
                "\"exists\": false",
             "}",
        "]}");
    Capture<WatchEvent<Path>> eventCapture = new Capture<>();
    EventBus eventBus = createStrictMock(EventBus.class);
    eventBus.post(capture(eventCapture));
    Process process = createMockProcess(watchmanOutput);
    replay(eventBus, process);
    WatchmanWatcher watcher = createWatcher(eventBus, process);
    watcher.postEvents();
    verify(eventBus, process);
    assertEquals("Should be modify event.",
        StandardWatchEventKinds.ENTRY_DELETE,
        eventCapture.getValue().kind());
  }

  @Test
  public void whenMultipleFilesThenMultipleEventsGenerated() throws IOException {
    String watchmanOutput = Joiner.on('\n').join(
        "{\"files\": [",
            "{",
                "\"name\": \"/foo/bar/baz\"",
            "},",
            "{",
                "\"name\": \"/foo/bar/boz\"",
            "}",
        "]}");
    EventBus eventBus = createStrictMock(EventBus.class);
    eventBus.post(anyObject(WatchEvent.class));
    eventBus.post(anyObject(WatchEvent.class));
    Process process = createMockProcess(watchmanOutput);
    replay(eventBus, process);
    WatchmanWatcher watcher = createWatcher(eventBus, process);
    watcher.postEvents();
    verify(eventBus, process);
  }

  @Test
  public void whenNameExcludedThenNoEventsGenerated() throws IOException {
    String watchmanOutput = Joiner.on('\n').join(
        "{\"files\": [",
            "{",
                "\"name\": \"/foo/bar/baz\"",
            "}",
        "]}");
    EventBus eventBus = createStrictMock(EventBus.class);
    Process process = createMockProcess(watchmanOutput);
    replay(eventBus, process);
    WatchmanWatcher watcher = createWatcher(
        eventBus,
        process,
        ImmutableSet.<Path>of(new File("/foo/bar/").toPath()),
        200 /* overflow */);
    watcher.postEvents();
    verify(eventBus, process);
  }

  @Test
  public void whenTooManyChangesThenOverflowEventGenerated() throws IOException {
    String watchmanOutput = Joiner.on('\n').join(
        "{\"files\": [",
            "{",
                "\"name\": \"/foo/bar/baz\"",
            "}",
        "]}");
    Capture<WatchEvent<Path>> eventCapture = new Capture<>();
    EventBus eventBus = createStrictMock(EventBus.class);
    eventBus.post(capture(eventCapture));
    Process process = createMockProcess(watchmanOutput);
    replay(eventBus, process);
    WatchmanWatcher watcher = createWatcher(
        eventBus,
        process,
        ImmutableSet.<Path>of() /* excludeDirectories */,
        -1 /* overflow */);
    watcher.postEvents();
    verify(eventBus, process);
    assertEquals("Should be overflow event.",
        StandardWatchEventKinds.OVERFLOW,
        eventCapture.getValue().kind());
  }

  private WatchmanWatcher createWatcher(EventBus eventBus, Process process) {
    return createWatcher(
        eventBus,
        process,
        ImmutableSet.<Path>of() /* excludeDirectories */,
        200 /* overflow */);
  }

  private WatchmanWatcher createWatcher(EventBus eventBus,
                                        Process process,
                                        ImmutableSet<Path> excludeDirectories,
                                        int overflow) {
    return new WatchmanWatcher(
        Suppliers.ofInstance(process),
        eventBus,
        excludeDirectories,
        overflow,
        "" /* query */);
  }

  private Process createMockProcess(String output) {
    Process process = createMock(Process.class);
    expect(process.getInputStream()).andReturn(
        new ByteArrayInputStream(output.getBytes(Charsets.US_ASCII)));
    expect(process.getOutputStream()).andReturn(
        new ByteArrayOutputStream());
    return process;
  }
}