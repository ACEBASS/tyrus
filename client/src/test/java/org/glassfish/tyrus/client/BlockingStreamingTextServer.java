/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.client;

import java.io.Reader;
import java.io.Writer;
import javax.net.websocket.Endpoint;
import javax.net.websocket.MessageHandler;
import javax.net.websocket.Session;
import javax.net.websocket.annotations.WebSocketEndpoint;
import javax.net.websocket.annotations.WebSocketOpen;
import static org.junit.Assert.assertEquals;

/**
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 */
@WebSocketEndpoint("/blockingstreaming")
public class BlockingStreamingTextServer extends Endpoint {
    class MyCharacterStreamHandler implements MessageHandler.CharacterStream {
        Session session;

        MyCharacterStreamHandler(Session session) {
            this.session = session;
        }

        @Override
        public void onMessage(Reader r) {
            System.out.println("BLOCKINGSTREAMSERVER: on message reader called");

            try {
                for (int i = 0; i < 10; i++) {
                    System.out.println("Reading bulk #" + i);
                    assertEquals('b', (char) r.read());
                    assertEquals('l', (char) r.read());
                    assertEquals('k', (char) r.read());
                    assertEquals(Character.forDigit(i, 10), (char) r.read());
                    System.out.println("Resuming the client");
                    synchronized (BlockingStreamingTextServer.class) {
                        BlockingStreamingTextServer.class.notify();
                    }
                }
                System.out.println("Reading END");
                assertEquals('E', (char) r.read());
                assertEquals('N', (char) r.read());
                assertEquals('D', (char) r.read());
                assertEquals(-1, r.read());

                Writer w = session.getRemote().getSendWriter();
                for (int i = 0; i < 10; i++) {
                    System.out.println("Streaming char to the client: " + i);
                    w.write(Character.forDigit(i, 10));
                    w.flush();
                    System.out.println("Waiting for the client to process it");
                    synchronized (BlockingStreamingTextServer.class) {
                        BlockingStreamingTextServer.class.wait(5000);
                    }
                }
                System.out.println("Writing #");
                w.write('#');
                w.close();


            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @WebSocketOpen
    public void onOpen(Session session) {
        System.out.println("BLOCKINGSERVER opened !");
        session.addMessageHandler(new MyCharacterStreamHandler(session));
    }

}

