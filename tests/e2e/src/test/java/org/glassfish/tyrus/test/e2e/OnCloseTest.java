/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.tyrus.test.e2e;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class OnCloseTest {

    @ServerEndpoint(value = "/close")
    public static class OnCloseEndpoint {
        public static Session session;

        @OnMessage
        public String message(String message, Session session) {
            try {
                session.close();
                return null;
            } catch (IOException e) {
                // do nothing.
            }
            return "message";
        }
    }

    @Test
    public void testOnClose() {
        Server server = new Server(OnCloseEndpoint.class);

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager.createClient().connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }

                @Override
                public void onOpen(Session session) {
                    session.addMessageHandler(new TestTextMessageHandler(this));
                    try {
                        session.getBasicRemote().sendText("message");
                    } catch (IOException e) {
                        // do nothing.
                    }
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    if (closeReason != null && closeReason.getCloseCode().getCode() == 1000) {
                        messageLatch.countDown();
                    }
                }

                @Override
                public void onMessage(String message) {
                    // do nothing
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/close"));

            messageLatch.await(1, TimeUnit.SECONDS);

            assertEquals(0L, messageLatch.getCount());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    static final String CUSTOM_REASON = "When nine hundred years old you reach, look as good, you will not, hmmm?";

    @ServerEndpoint(value = "/close")
    public static class OnCloseWithCustomReasonEndpoint {
        public static Session session;
        public static volatile CloseReason closeReason;

        @OnMessage
        public String message(String message, Session session) {
            try {
                session.close(new CloseReason(new CloseReason.CloseCode() {
                    @Override
                    public int getCode() {
                        // custom close codes (4000-4999)
                        return 4000;
                    }
                }, CUSTOM_REASON));
                return null;
            } catch (IOException e) {
                // do nothing.
            }
            return "message";
        }

        @OnClose
        public void onClose(Session s, CloseReason c) {
            closeReason = c;
        }

        @OnError
        public void onError(Throwable t) {
            t.printStackTrace();
        }
    }

    @Test
    public void testOnCloseCustomCloseReasonServerInitiated() {
        Server server = new Server(OnCloseWithCustomReasonEndpoint.class);

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager.createClient().connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }

                @Override
                public void onOpen(Session session) {
                    session.addMessageHandler(new TestTextMessageHandler(this));
                    try {
                        session.getBasicRemote().sendText("message");
                    } catch (IOException e) {
                        // do nothing.
                    }
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    if (closeReason != null &&
                            closeReason.getCloseCode().getCode() == 4000 &&
                            closeReason.getReasonPhrase().equals(CUSTOM_REASON)) {
                        messageLatch.countDown();
                    }
                }

                @Override
                public void onMessage(String message) {
                    // do nothing
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/close"));

            messageLatch.await(1, TimeUnit.SECONDS);

            assertEquals(0L, messageLatch.getCount());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testOnCloseCustomCloseReasonClientInitiated() {
        Server server = new Server(OnCloseWithCustomReasonEndpoint.class);

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager.createClient().connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }

                @Override
                public void onOpen(Session session) {
                    session.addMessageHandler(new TestTextMessageHandler(this));
                    try {
                        session.close(new CloseReason(new CloseReason.CloseCode() {
                            @Override
                            public int getCode() {
                                // custom close codes (4000-4999)
                                return 4000;
                            }
                        }, CUSTOM_REASON));
                    } catch (IOException e) {
                        // do nothing.
                    } finally {
                        messageLatch.countDown();
                    }
                }

                @Override
                public void onMessage(String message) {
                    // do nothing
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/close"));

            messageLatch.await(100, TimeUnit.SECONDS);
            Thread.sleep(1000);

            assertNotNull(OnCloseWithCustomReasonEndpoint.closeReason);
            assertEquals(4000, OnCloseWithCustomReasonEndpoint.closeReason.getCloseCode().getCode());
            assertEquals(CUSTOM_REASON, OnCloseWithCustomReasonEndpoint.closeReason.getReasonPhrase());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @ServerEndpoint(value = "/close")
    public static class OnCloseAllSupportedReasonsEndpoint {

        @OnMessage
        public String message(final Integer message, Session session) {
            try {
                session.close(new CloseReason(new CloseReason.CloseCode() {
                    @Override
                    public int getCode() {
                        // custom close codes (4000-4999)
                        return message;
                    }
                }, null));
                return null;
            } catch (IOException e) {
                // do nothing.
            }
            return "message";
        }

        @OnError
        public void onError(Throwable t) {
            t.printStackTrace();
        }
    }

    @Test
    public void testOnCloseServerInitiated() {
        Server server = new Server(OnCloseAllSupportedReasonsEndpoint.class);

        // close codes 1000 - 1015
        for (int i = 1000; i < 1016; i++) {
            final CountDownLatch messageLatch = new CountDownLatch(1);

            final int closeCode = i;
            System.out.println("### Testing CloseCode " + i);

            try {
                server.start();
                final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

                ClientManager.createClient().connectToServer(new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig endpointConfig) {
                        try {
                            session.getBasicRemote().sendObject(closeCode);
                        } catch (Exception e) {
                            // do nothing.
                        }
                    }

                    @Override
                    public void onClose(Session session, CloseReason closeReason) {
                        System.out.println("#### received: " + closeReason);
                        if (closeReason != null &&
                                closeReason.getCloseCode().getCode() == closeCode) {
                            messageLatch.countDown();
                        }
                    }
                }, cec, new URI("ws://localhost:8025/websockets/tests/close"));

                messageLatch.await(1, TimeUnit.SECONDS);

                assertEquals(0L, messageLatch.getCount());
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                server.stop();
            }
        }
    }

    @ServerEndpoint(value = "/close")
    public static class OnCloseAllSupportedReasonsClientInitEndpoint {

        public static CloseReason closeReason;

        @OnMessage
        public String message(final Integer message, Session session) {
            return "message";
        }

        @OnClose
        public void onClose(CloseReason closeReason, Session session) {
            this.closeReason = closeReason;
            myMessageLatch.countDown();
        }

        @OnError
        public void onError(Throwable t) {
            t.printStackTrace();
        }
    }

    static CountDownLatch myMessageLatch = new CountDownLatch(1);

    @Test
    public void testOnCloseClientInitiated() {
        Server server = new Server(OnCloseAllSupportedReasonsClientInitEndpoint.class);

        // close codes 1000 - 1015
        for (int i = 1000; i < 1016; i++) {

            final int closeCode = i;
            System.out.println("### Testing CloseCode " + i);
            myMessageLatch = new CountDownLatch(1);

            try {
                server.start();
                final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

                ClientManager.createClient().connectToServer(new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig endpointConfig) {
                        try {
                            session.close(new CloseReason(new CloseReason.CloseCode() {
                                @Override
                                public int getCode() {
                                    // custom close codes (4000-4999)
                                    return closeCode;
                                }
                            }, null));
                        } catch (Exception e) {
                            // do nothing.
                        }
                    }
                }, cec, new URI("ws://localhost:8025/websockets/tests/close"));

                myMessageLatch.await(1, TimeUnit.SECONDS);
                assertEquals(closeCode, OnCloseAllSupportedReasonsClientInitEndpoint.closeReason.getCloseCode().getCode());

                assertEquals(0L, myMessageLatch.getCount());
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                server.stop();
            }
        }
    }
}
