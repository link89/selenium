// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.grid.sessionqueue.local;

import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.events.EventBus;
import org.openqa.selenium.events.local.GuavaEventBus;
import org.openqa.selenium.grid.data.NewSessionRequestEvent;
import org.openqa.selenium.grid.data.RequestId;
import org.openqa.selenium.grid.sessionqueue.NewSessionQueue;
import org.openqa.selenium.remote.NewSessionPayload;
import org.openqa.selenium.remote.http.HttpMethod;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.tracing.DefaultTestTracer;
import org.openqa.selenium.remote.tracing.Tracer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openqa.selenium.remote.http.Contents.utf8String;
import static org.openqa.selenium.remote.http.HttpMethod.POST;

public class LocalNewSessionQueueTest {

  private EventBus bus;
  private Capabilities caps;
  private NewSessionQueue sessionQueue;
  private HttpRequest expectedSessionRequest;
  private RequestId requestId;

  @Before
  public void setUp() {
    Tracer tracer = DefaultTestTracer.createTracer();
    caps = new ImmutableCapabilities("browserName", "chrome");
    bus = new GuavaEventBus();
    requestId = new RequestId(UUID.randomUUID());
    sessionQueue = new LocalNewSessionQueue(tracer, bus, Duration.ofSeconds(1));

    NewSessionPayload payload = NewSessionPayload.create(caps);
    expectedSessionRequest = createRequest(payload, POST, "/session");
  }

  @Test
  public void shouldBeAbleToAddToEndOfQueue() {
    boolean added = sessionQueue.offerLast(expectedSessionRequest, requestId);
    assertTrue(added);

    bus.addListener(NewSessionRequestEvent.listener(reqId -> assertThat(reqId).isEqualTo(requestId)));
  }

  @Test
  public void shouldBeAbleToRemoveFromFrontOfQueue() {
    boolean added = sessionQueue.offerLast(expectedSessionRequest, requestId);
    assertTrue(added);

    Optional<HttpRequest> receivedRequest = sessionQueue.poll();

    assertTrue(receivedRequest.isPresent());
    assertEquals(expectedSessionRequest, receivedRequest.get());
  }

  @Test
  public void shouldAddTimestampHeader() {
    boolean added = sessionQueue.offerLast(expectedSessionRequest, requestId);
    assertTrue(added);

    Optional<HttpRequest> receivedRequest = sessionQueue.poll();

    assertTrue(receivedRequest.isPresent());
    HttpRequest request = receivedRequest.get();
    assertEquals(expectedSessionRequest, request);
    assertTrue(request.getHeader(NewSessionQueue.SESSIONREQUEST_TIMESTAMP_HEADER) != null);
  }

  @Test
  public void shouldAddRequestIdHeader() {
    boolean added = sessionQueue.offerLast(expectedSessionRequest, requestId);
    assertTrue(added);

    Optional<HttpRequest> receivedRequest = sessionQueue.poll();

    assertTrue(receivedRequest.isPresent());
    HttpRequest request = receivedRequest.get();
    assertEquals(expectedSessionRequest, request);
    String polledRequestId = request.getHeader(NewSessionQueue.SESSIONREQUEST_ID_HEADER);
    assertTrue(polledRequestId != null);
    assertEquals(requestId, new RequestId(UUID.fromString(polledRequestId)));
  }

  @Test
  public void shouldBeAbleToAddToFrontOfQueue() {
    ImmutableCapabilities chromeCaps = new ImmutableCapabilities("browserName", "chrome");
    NewSessionPayload chromePayload = NewSessionPayload.create(chromeCaps);
    HttpRequest chromeRequest = createRequest(chromePayload, POST, "/session");
    RequestId chromeRequestId = new RequestId(UUID.randomUUID());

    ImmutableCapabilities firefoxCaps = new ImmutableCapabilities("browserName", "firefox");
    NewSessionPayload firefoxpayload = NewSessionPayload.create(firefoxCaps);
    HttpRequest firefoxRequest = createRequest(firefoxpayload, POST, "/session");
    RequestId firefoxRequestId = new RequestId(UUID.randomUUID());

    boolean addedChromeRequest = sessionQueue.offerFirst(chromeRequest, chromeRequestId);
    assertTrue(addedChromeRequest);

    boolean addFirefoxRequest = sessionQueue.offerFirst(firefoxRequest, firefoxRequestId);
    assertTrue(addFirefoxRequest);

    Optional<HttpRequest> polledFirefoxRequest = sessionQueue.poll();
    assertTrue(polledFirefoxRequest.isPresent());
    assertEquals(firefoxRequest, polledFirefoxRequest.get());

    Optional<HttpRequest> polledChromeRequest = sessionQueue.poll();
    assertTrue(polledChromeRequest.isPresent());
    assertEquals(chromeRequest, polledChromeRequest.get());
  }

  @Test
  public void shouldBeClearAPopulatedQueue() {
    sessionQueue.offerLast(expectedSessionRequest, requestId);
    sessionQueue.offerLast(expectedSessionRequest, requestId);

    int count = sessionQueue.clear();
    assertEquals(count, 2);
  }

  @Test
  public void shouldBeClearAEmptyQueue() {
    int count = sessionQueue.clear();
    assertEquals(count, 0);
  }

  private HttpRequest createRequest(NewSessionPayload payload, HttpMethod httpMethod, String uri) {
    StringBuilder builder = new StringBuilder();
    try {
      payload.writeTo(builder);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    HttpRequest request = new HttpRequest(httpMethod, uri);
    request.setContent(utf8String(builder.toString()));

    return request;
  }
}
