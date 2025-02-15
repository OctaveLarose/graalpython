/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python;

import static com.oracle.graal.python.runtime.PosixConstants.AF_INET;
import static com.oracle.graal.python.runtime.PosixConstants.AF_INET6;
import static com.oracle.graal.python.runtime.PosixConstants.AF_UNSPEC;
import static com.oracle.graal.python.runtime.PosixConstants.AI_CANONNAME;
import static com.oracle.graal.python.runtime.PosixConstants.AI_PASSIVE;
import static com.oracle.graal.python.runtime.PosixConstants.EAI_ADDRFAMILY;
import static com.oracle.graal.python.runtime.PosixConstants.EAI_BADFLAGS;
import static com.oracle.graal.python.runtime.PosixConstants.EAI_FAMILY;
import static com.oracle.graal.python.runtime.PosixConstants.EAI_NONAME;
import static com.oracle.graal.python.runtime.PosixConstants.EAI_SERVICE;
import static com.oracle.graal.python.runtime.PosixConstants.EAI_SOCKTYPE;
import static com.oracle.graal.python.runtime.PosixConstants.IN6ADDR_ANY;
import static com.oracle.graal.python.runtime.PosixConstants.IN6ADDR_LOOPBACK;
import static com.oracle.graal.python.runtime.PosixConstants.INADDR_ANY;
import static com.oracle.graal.python.runtime.PosixConstants.INADDR_LOOPBACK;
import static com.oracle.graal.python.runtime.PosixConstants.INADDR_NONE;
import static com.oracle.graal.python.runtime.PosixConstants.IPPROTO_TCP;
import static com.oracle.graal.python.runtime.PosixConstants.IPPROTO_UDP;
import static com.oracle.graal.python.runtime.PosixConstants.NI_DGRAM;
import static com.oracle.graal.python.runtime.PosixConstants.NI_NAMEREQD;
import static com.oracle.graal.python.runtime.PosixConstants.NI_NUMERICHOST;
import static com.oracle.graal.python.runtime.PosixConstants.NI_NUMERICSERV;
import static com.oracle.graal.python.runtime.PosixConstants.SHUT_RD;
import static com.oracle.graal.python.runtime.PosixConstants.SHUT_WR;
import static com.oracle.graal.python.runtime.PosixConstants.SOCK_DGRAM;
import static com.oracle.graal.python.runtime.PosixConstants.SOCK_STREAM;
import static com.oracle.graal.python.runtime.PosixConstants.SOL_SOCKET;
import static com.oracle.graal.python.runtime.PosixConstants.SO_ACCEPTCONN;
import static com.oracle.graal.python.runtime.PosixConstants.SO_DOMAIN;
import static com.oracle.graal.python.runtime.PosixConstants.SO_PROTOCOL;
import static com.oracle.graal.python.runtime.PosixConstants.SO_TYPE;
import static com.oracle.graal.python.runtime.PosixConstants.TCP_USER_TIMEOUT;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.runtime.PosixConstants.MandatoryIntConstant;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AcceptResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursor;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursorLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.FamilySpecificSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.GetAddrInfoException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet4SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet6SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.InvalidAddressException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.RecvfromResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddrLibrary;
import com.oracle.truffle.api.memory.ByteArraySupport;

@RunWith(Parameterized.class)
public class SocketTests {

    @Parameter(0) public String backendName;

    @Parameters(name = "{0}")
    public static String[] params() {
        return new String[]{"native", "java"};
    }

    @Rule public WithPythonContextRule withPythonContextRule = new WithPythonContextRule((o) -> o.put("python.PosixModuleBackend", backendName));

    @Rule public CleanupRule cleanup = new CleanupRule();
    @Rule public ExpectedException expectedException = ExpectedException.none();

    // "::FFFF:127.0.0.1" - IPv4 localhost mapped into IPv6 address
    private static final byte[] MAPPED_LOOPBACK = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, 127, 0, 0, 1};
    private static final byte[] DATA = new byte[]{1, 2, 3};
    private static final byte[] DATA2 = new byte[]{4, 5, 6, 7};

    private Object posixSupport;
    private PosixSupportLibrary lib;
    private UniversalSockAddrLibrary usaLib;
    private AddrInfoCursorLibrary aicLib;

    @Before
    public void setUp() {
        posixSupport = withPythonContextRule.getPythonContext().getPosixSupport();
        lib = PosixSupportLibrary.getUncached();
        usaLib = UniversalSockAddrLibrary.getUncached();
        aicLib = AddrInfoCursorLibrary.getUncached();
    }

    @Test
    public void fillUniversalSockAddrInet4() {
        Inet4SockAddr addr = new Inet4SockAddr(12345, INADDR_LOOPBACK.value);
        checkUsa(addr, createUsa(addr));
    }

    @Test
    public void fillUniversalSockAddrInet6() {
        assumeTrue(isInet6Supported());
        Inet6SockAddr addr = new Inet6SockAddr(12345, IN6ADDR_LOOPBACK, 12, 1);
        checkUsa(addr, createUsa(addr));
    }

    @Test
    public void dgramUnboundGetsocknameInet4() throws PosixException {
        int s = createSocket(AF_INET.value, SOCK_DGRAM.value, 0);
        checkUsa(new Inet4SockAddr(0, INADDR_ANY.value), lib.getsockname(posixSupport, s));
    }

    @Test
    public void dgramUnboundGetsocknameInet6() throws PosixException {
        assumeTrue(isInet6Supported());
        int s = createSocket(AF_INET6.value, SOCK_DGRAM.value, 0);
        checkUsa(new Inet6SockAddr(0, IN6ADDR_ANY, 0, 0), lib.getsockname(posixSupport, s));
    }

    @Test
    public void dgramUnconnectedSendtoRecvfromInet4() throws PosixException {
        UdpServer srv = new UdpServer(AF_INET.value);
        UdpClient cli = new UdpClient(AF_INET.value);

        cli.sendto(DATA, 0, srv.usa());
        checkUsa(cli.address(), srv.recvfrom(DATA, 0));
    }

    @Test
    public void dgramUnconnectedSendtoRecvfromInet6() throws PosixException {
        assumeTrue(isInet6Supported());
        UdpServer srv = new UdpServer(AF_INET6.value);
        UdpClient cli = new UdpClient(AF_INET6.value);

        cli.sendto(DATA, 0, srv.usa());
        checkUsa(cli.address(), srv.recvfrom(DATA, 0));
    }

    @Test
    public void dgramUnconnectedSendtoRecvfromMapped() throws PosixException {
        assumeTrue(isInet6Supported());
        UdpServer srv = new UdpServer(AF_INET6.value);
        UdpClient cli = new UdpClient(AF_INET.value);

        cli.sendto(DATA, 0, createUsa(localAddress(AF_INET.value, srv.port)));
        int cliPort = checkBound(AF_INET.value, cli.getsockname());

        checkUsa(new Inet6SockAddr(cliPort, MAPPED_LOOPBACK, 0, 0), srv.recvfrom(DATA, 0));
    }

    @Test
    public void dgramConnectedSendtoInet4() throws PosixException {
        // Native sendto() on Linux allows sending datagrams to any address (the address passed
        // in connect() is just a default). On Darwin, the call fails with EISCONN.
        assumeTrue(runsOnLinux());
        UdpServer srv1 = new UdpServer(AF_INET.value);
        UdpServer srv2 = new UdpServer(AF_INET.value);
        UdpClient cli = new UdpClient(AF_INET.value);

        cli.connect(srv1.usa());
        int cliPort = checkBound(AF_INET.value, cli.getsockname());

        if ("native".equals(backendName)) {
            // Emulated backend: DatagramChannel.send() explicitly checks the destination address
            cli.sendto(DATA, 0, srv2.usa());
            checkUsa(new Inet4SockAddr(cliPort, INADDR_LOOPBACK.value), srv2.recvfrom(DATA, 0));
        }

        cli.sendto(DATA2, 0, srv1.usa());
        checkUsa(new Inet4SockAddr(cliPort, INADDR_LOOPBACK.value), srv1.recvfrom(DATA2, 0));
    }

    @Test
    public void dgramConnectedRecvfromInet6() throws PosixException {
        assumeTrue(isInet6Supported());
        UdpServer srv1 = new UdpServer(AF_INET6.value);
        UdpServer srv2 = new UdpServer(AF_INET6.value);
        UdpClient cli = new UdpClient(AF_INET6.value);

        // from connect(7): If the socket sockfd is of type SOCK_DGRAM then addr is [...] the only
        // address from which datagrams are received.
        cli.connect(srv1.usa());

        srv2.sendto(DATA2, 0, cli.usa());    // this should not arrive
        srv1.sendto(DATA, 0, cli.usa());
        checkUsa(srv1.address(), cli.recvfrom(DATA, 0));

        srv1.sendto(DATA2, 0, cli.usa());
        checkUsa(srv1.address(), cli.recvfrom(DATA2, 0));
        // TODO check properly that the first packet is never delivered
    }

    @Test
    public void dgramConnectedGetpeernameSendRecvInet4() throws PosixException {
        UdpServer srv = new UdpServer(AF_INET.value);
        UdpClient cli = new UdpClient(AF_INET.value);

        cli.connect(srv.usa());
        checkBound(AF_INET.value, cli.getsockname());
        checkUsa(srv.address(), cli.getpeername());

        cli.send(DATA, 0);
        srv.recvfrom(DATA, 0);

        srv.sendto(DATA, 0, cli.getsockname());
        cli.recv(DATA, 0);
    }

    @Test
    public void dgramUnconnectedRecvInet6() throws PosixException {
        assumeTrue(isInet6Supported());
        UdpServer srv = new UdpServer(AF_INET6.value);
        UdpClient cli = new UdpClient(AF_INET6.value);

        cli.sendto(DATA, 0, srv.usa());
        srv.recv(DATA, 0);
    }

    @Test
    public void dgramUnconnectedSendInet6() throws PosixException {
        assumeTrue(isInet6Supported());
        // From send(2):
        // EDESTADDRREQ - The socket is not connection-mode, and no peer address is set.
        // ENOTCONN - The socket is not connected, and no target has been given.
        // BUGS: Linux may return EPIPE instead of ENOTCONN.
        expectErrno(OSErrorEnum.EDESTADDRREQ, OSErrorEnum.ENOTCONN, OSErrorEnum.EPIPE);
        UdpClient cli = new UdpClient(AF_INET6.value);
        cli.send(DATA, 0);
    }

    @Test
    public void dgramConnectedGetpeernameWriteReadInet6() throws PosixException {
        assumeTrue(isInet6Supported());
        UdpServer srv = new UdpServer(AF_INET6.value);
        UdpClient cli = new UdpClient(AF_INET6.value);

        cli.connect(srv.usa());
        checkBound(AF_INET6.value, cli.getsockname());
        checkUsa(new Inet6SockAddr(srv.port, IN6ADDR_LOOPBACK, 0, 0), cli.getpeername());

        cli.write(DATA);
        checkUsa(cli.address(), srv.recvfrom(DATA, 0));

        srv.sendto(DATA2, 0, cli.usa());
        cli.read(DATA2);
    }

    @Test
    public void dgramUnconnectedReadInet4() throws PosixException {
        UdpServer srv = new UdpServer(AF_INET.value);
        UdpClient cli = new UdpClient(AF_INET.value);

        cli.sendto(DATA, 0, srv.usa());
        srv.read(DATA);
    }

    @Test
    public void dgramUnconnectedWriteInet4() throws PosixException {
        // From send(2):
        // The only difference between send() and write(2) is the presence of flags.
        // EDESTADDRREQ - The socket is not connection-mode, and no peer address is set.
        // ENOTCONN - The socket is not connected, and no target has been given.
        // BUGS: Linux may return EPIPE instead of ENOTCONN.
        expectErrno(OSErrorEnum.EDESTADDRREQ, OSErrorEnum.ENOTCONN, OSErrorEnum.EPIPE);
        UdpClient cli = new UdpClient(AF_INET.value);
        cli.write(DATA);
    }

    @Test
    public void streamListenConnectInet() throws PosixException {
        expectErrno(OSErrorEnum.EISCONN, OSErrorEnum.EOPNOTSUPP);
        TcpServer srv = new TcpServer(AF_INET.value);
        srv.connect(srv.usa());
    }

    @Test
    public void streamDoubleListenInet6() throws PosixException {
        assumeTrue(isInet6Supported());
        TcpServer srv = new TcpServer(AF_INET.value);
        srv.listen(5);
    }

    @Test
    public void streamConnectListenInet() throws PosixException {
        expectErrno(OSErrorEnum.EINVAL);
        TcpServer srv = new TcpServer(AF_INET.value);
        TcpClient cli = new TcpClient(AF_INET.value);
        cli.connect(srv.usa());
        cli.listen(5);
    }

    @Test
    public void streamDoubleConnectInet6() throws PosixException {
        assumeTrue(isInet6Supported());
        expectErrno(OSErrorEnum.EISCONN);
        TcpServer srv = new TcpServer(AF_INET6.value);
        TcpClient cli = new TcpClient(AF_INET6.value);
        cli.connect(srv.usa());
        cli.connect(srv.usa());
    }

    @Test
    public void streamSendtoInet() throws PosixException {
        // We don't test native sendto - it may either ignore the address or return EISCONN
        // Emulated sendto always fails with EISCONN
        assumeTrue("java".equals(backendName));
        expectErrno(OSErrorEnum.EISCONN);
        TcpServer srv = new TcpServer(AF_INET.value);
        TcpClient cli = new TcpClient(AF_INET.value);

        cli.connect(srv.usa());
        TcpClient c = srv.accept(cli.address());

        checkUsa(c.address(), cli.getpeername());
        checkUsa(cli.address(), c.getpeername());

        c.sendto(DATA, 0, cli.usa());
    }

    @Test
    public void streamRecvfromInet() throws PosixException {
        TcpServer srv = new TcpServer(AF_INET.value);
        TcpClient cli = new TcpClient(AF_INET.value);

        cli.connect(srv.usa());
        TcpClient c = srv.accept(cli.address());

        checkUsa(c.address(), cli.getpeername());
        checkUsa(cli.address(), c.getpeername());

        c.send(DATA, 0);
        assertEquals(AF_UNSPEC.value, usaLib.getFamily(cli.recvfrom(DATA, 0)));
    }

    @Test
    public void streamSendRecvInet() throws PosixException {
        TcpServer srv = new TcpServer(AF_INET.value);
        TcpClient cli = new TcpClient(AF_INET.value);

        cli.connect(srv.usa());
        TcpClient c = srv.accept(cli.address());

        checkUsa(c.address(), cli.getpeername());
        checkUsa(cli.address(), c.getpeername());

        c.shutdown(SHUT_RD.value);

        c.send(DATA, 0);
        cli.recv(DATA, 0);
    }

    @Test
    public void streamWriteReadInet6() throws PosixException {
        assumeTrue(isInet6Supported());
        TcpServer srv = new TcpServer(AF_INET6.value);
        TcpClient cli = new TcpClient(AF_INET6.value);

        cli.connect(srv.usa());
        TcpClient c = srv.accept(cli.address());

        checkUsa(c.address(), cli.getpeername());
        checkUsa(cli.address(), c.getpeername());

        cli.shutdown(SHUT_WR.value);

        c.write(DATA);
        cli.read(DATA);
    }

    @Test
    public void dgramUnconnectedGetpeername() throws PosixException {
        expectErrno(OSErrorEnum.ENOTCONN);
        new UdpClient(AF_INET.value).getpeername();
    }

    @Test
    public void dgramListen() throws PosixException {
        expectErrno(OSErrorEnum.EOPNOTSUPP);
        lib.listen(posixSupport, new UdpServer(AF_INET.value).fd, 5);
    }

    @Test
    public void streamDoubleBind() throws PosixException {
        expectErrno(OSErrorEnum.EINVAL);
        new TcpServer(AF_INET.value).bind();
    }

    @Test
    public void streamUnconnectedGetpeername() throws PosixException {
        expectErrno(OSErrorEnum.ENOTCONN);
        new TcpClient(AF_INET.value).getpeername();
    }

    @Test
    public void streamUnconnectedRead() throws PosixException {
        expectErrno(OSErrorEnum.ENOTCONN);
        lib.read(posixSupport, new TcpClient(AF_INET.value).fd, 10);
    }

    @Test
    public void streamUnconnectedWrite() throws PosixException {
        // From send(2): Linux may return EPIPE instead of ENOTCONN.
        expectErrno(OSErrorEnum.ENOTCONN, OSErrorEnum.EPIPE);
        lib.write(posixSupport, new TcpClient(AF_INET.value).fd, Buffer.wrap(DATA));
    }

    @Test
    public void streamListeningGetpeername() throws PosixException {
        expectErrno(OSErrorEnum.ENOTCONN);
        new TcpServer(AF_INET.value).getpeername();
    }

    @Test
    public void streamListeningRecv() throws PosixException {
        expectErrno(OSErrorEnum.ENOTCONN);
        lib.recv(posixSupport, new TcpServer(AF_INET.value).fd, new byte[10], 0, 10, 0);
    }

    @Test
    public void streamListeningSend() throws PosixException {
        // From send(2): Linux may return EPIPE instead of ENOTCONN.
        expectErrno(OSErrorEnum.ENOTCONN, OSErrorEnum.EPIPE);
        lib.send(posixSupport, new TcpServer(AF_INET.value).fd, DATA, 0, DATA.length, 0);
    }

    @Test
    public void streamListeningRead() throws PosixException {
        expectErrno(OSErrorEnum.ENOTCONN);
        lib.read(posixSupport, new TcpServer(AF_INET.value).fd, 10);
    }

    @Test
    public void streamListeningWrite() throws PosixException {
        // From send(2): Linux may return EPIPE instead of ENOTCONN.
        expectErrno(OSErrorEnum.ENOTCONN, OSErrorEnum.EPIPE);
        lib.write(posixSupport, new TcpServer(AF_INET.value).fd, Buffer.wrap(DATA));
    }

    @Test
    public void getSocketOptions() throws PosixException {
        int socket = createSocket(AF_INET.value, SOCK_STREAM.value, 0);
        assertEquals(SOCK_STREAM.value, getIntSockOpt(socket, SOL_SOCKET.value, SO_TYPE.value));
        if (SO_DOMAIN.defined) {
            assertEquals(AF_INET.value, getIntSockOpt(socket, SOL_SOCKET.value, SO_DOMAIN.getValueIfDefined()));
        }
        if (SO_PROTOCOL.defined) {
            assertEquals(IPPROTO_TCP.value, getIntSockOpt(socket, SOL_SOCKET.value, SO_PROTOCOL.getValueIfDefined()));
        }
    }

    @Test
    public void getSocketOptionsAcceptConn() throws PosixException {
        assumeTrue("native".equals(backendName));
        assumeTrue(runsOnLinux());  // darwin defines but does not support SO_ACCEPTCONN
        int socket = createSocket(AF_INET.value, SOCK_STREAM.value, 0);
        lib.bind(posixSupport, socket, createUsa(new Inet4SockAddr(0, INADDR_LOOPBACK.value)));
        assertEquals(0, getIntSockOpt(socket, SOL_SOCKET.value, SO_ACCEPTCONN.value));
        lib.listen(posixSupport, socket, 5);
        assertEquals(1, getIntSockOpt(socket, SOL_SOCKET.value, SO_ACCEPTCONN.value));
    }

    @Test
    public void setSocketOptions() throws PosixException {
        assumeTrue("native".equals(backendName));
        assumeTrue(TCP_USER_TIMEOUT.defined);
        int socket = createSocket(AF_INET.value, SOCK_STREAM.value, 0);
        int origTimeout = getIntSockOpt(socket, IPPROTO_TCP.value, TCP_USER_TIMEOUT.getValueIfDefined());
        int newTimeout = origTimeout + 1;
        setIntSockOpt(socket, IPPROTO_TCP.value, TCP_USER_TIMEOUT.getValueIfDefined(), newTimeout);
        assertEquals(newTimeout, getIntSockOpt(socket, IPPROTO_TCP.value, TCP_USER_TIMEOUT.getValueIfDefined()));
    }

    @Test
    public void nonBlockingDgramRecv() throws PosixException {
        expectErrno(OSErrorEnum.EWOULDBLOCK);
        UdpClient cli = new UdpClient(AF_INET.value);
        cli.setBlocking(false);
        assertFalse(cli.getBlocking());
        lib.recv(posixSupport, cli.fd, new byte[10], 0, 10, 0);
    }

    @Test
    public void nonBlockingAccept() throws PosixException {
        TcpServer srv = new TcpServer(AF_INET.value);
        TcpClient cli = new TcpClient(AF_INET.value);
        srv.setBlocking(false);
        try {
            lib.accept(posixSupport, srv.fd);
            fail("Expected accept() to fail with EWOULDBLOCK");
        } catch (PosixException e) {
            assertEquals(OSErrorEnum.EWOULDBLOCK.getNumber(), e.getErrorCode());
        }
        cli.connect(srv.usa());
        srv.accept(cli.address());
    }

    @Test
    public void getnameinfo() throws GetAddrInfoException {
        Object[] res = lib.getnameinfo(posixSupport, createUsa(new Inet6SockAddr(443, IN6ADDR_LOOPBACK, 0, 0)), NI_NUMERICSERV.value | NI_NUMERICHOST.value);
        assertThat(p2s(res[0]), anyOf(equalTo("::1"), equalTo("0:0:0:0:0:0:0:1%0")));
        assertEquals("443", p2s(res[1]));

        res = lib.getnameinfo(posixSupport, createUsa(new Inet4SockAddr(443, INADDR_LOOPBACK.value)), 0);
        assertEquals("localhost", p2s(res[0]));
        assertEquals("https", p2s(res[1]));

        res = lib.getnameinfo(posixSupport, createUsa(new Inet4SockAddr(53535, INADDR_LOOPBACK.value)), NI_NUMERICHOST.value);
        assertEquals("53535", p2s(res[1]));
    }

    @Test
    public void getnameinfoUdp() throws GetAddrInfoException {
        assumeTrue(runsOnLinux());
        Object[] res = lib.getnameinfo(posixSupport, createUsa(new Inet4SockAddr(512, INADDR_LOOPBACK.value)), NI_NUMERICHOST.value);
        assertEquals("exec", p2s(res[1]));
        res = lib.getnameinfo(posixSupport, createUsa(new Inet4SockAddr(512, INADDR_LOOPBACK.value)), NI_NUMERICHOST.value | NI_DGRAM.value);
        assertThat(p2s(res[1]), anyOf(equalTo("biff"), equalTo("comsat")));
    }

    @Test
    public void getnameinfoErr() throws GetAddrInfoException {
        expectGetAddrInfoException(EAI_NONAME);
        lib.getnameinfo(posixSupport, createUsa(new Inet4SockAddr(443, INADDR_LOOPBACK.value)), NI_NUMERICHOST.value | NI_NAMEREQD.value);
    }

    @Test
    public void getaddrinfoErrNoInput() throws GetAddrInfoException {
        expectGetAddrInfoException(EAI_NONAME);
        lib.getaddrinfo(posixSupport, null, null, AF_UNSPEC.value, 0, 0, 0);
    }

    @Test
    public void getaddrinfoErrFamily() throws GetAddrInfoException {
        expectGetAddrInfoException(EAI_FAMILY);
        lib.getaddrinfo(posixSupport, null, s2p("http"), -42, 0, 0, 0);
    }

    @Test
    public void getaddrinfoErrSockType() throws GetAddrInfoException {
        assumeTrue(runsOnLinux());
        expectGetAddrInfoException(EAI_SOCKTYPE);
        lib.getaddrinfo(posixSupport, null, s2p("http"), AF_UNSPEC.value, -42, 0, 0);
    }

    @Test
    public void getaddrinfoErrService() throws GetAddrInfoException {
        assumeTrue(runsOnLinux());
        expectGetAddrInfoException(EAI_SERVICE);
        lib.getaddrinfo(posixSupport, null, s2p("invalid service"), AF_UNSPEC.value, SOCK_DGRAM.value, 0, 0);
    }

    @Test
    public void getaddrinfoErrAddrFamily() throws GetAddrInfoException {
        assumeTrue(runsOnLinux());
        expectGetAddrInfoException(EAI_ADDRFAMILY);
        lib.getaddrinfo(posixSupport, s2p("::1"), null, AF_INET.value, 0, 0, 0);
    }

    @Test
    public void getaddrinfoBadFlags() throws GetAddrInfoException {
        assumeTrue(runsOnLinux());
        expectGetAddrInfoException(EAI_BADFLAGS);
        lib.getaddrinfo(posixSupport, null, s2p("https"), AF_INET.value, 0, 0, AI_CANONNAME.value);
    }

    @Test
    public void getaddrinfoServiceOnly() throws GetAddrInfoException {
        Object service = s2p("https");
        AddrInfoCursor aic = lib.getaddrinfo(posixSupport, null, service, AF_UNSPEC.value, SOCK_STREAM.value, 0, 0);
        cleanup.add(() -> aicLib.release(aic));
        do {
            int family = aicLib.getFamily(aic);

            assertEquals(SOCK_STREAM.value, aicLib.getSockType(aic));
            assertNull(aicLib.getCanonName(aic));

            UniversalSockAddr usa = aicLib.getSockAddr(aic);
            assertEquals(family, usaLib.getFamily(usa));
            if (family == AF_INET.value) {
                Inet4SockAddr addr2 = usaLib.asInet4SockAddr(usa);
                assertEquals(INADDR_LOOPBACK.value, addr2.getAddress());
                assertEquals(443, addr2.getPort());
            }
        } while (aicLib.next(aic));
    }

    @Test
    public void getaddrinfoPassive() throws GetAddrInfoException {
        Object service = s2p("https");
        AddrInfoCursor aic = lib.getaddrinfo(posixSupport, null, service, AF_INET.value, 0, IPPROTO_TCP.value, AI_PASSIVE.value);
        cleanup.add(() -> aicLib.release(aic));
        assertEquals(AF_INET.value, aicLib.getFamily(aic));
        assertEquals(SOCK_STREAM.value, aicLib.getSockType(aic));
        assertEquals(IPPROTO_TCP.value, aicLib.getProtocol(aic));
        assertNull(aicLib.getCanonName(aic));

        UniversalSockAddr usa = aicLib.getSockAddr(aic);
        Inet4SockAddr addr = usaLib.asInet4SockAddr(usa);
        assertEquals(INADDR_ANY.value, addr.getAddress());
        assertEquals(443, addr.getPort());
    }

    @Test
    public void getaddrinfoServerOnlyNoCanon() throws GetAddrInfoException {
        Object node = s2p("localhost");
        AddrInfoCursor aic = lib.getaddrinfo(posixSupport, node, null, AF_UNSPEC.value, SOCK_DGRAM.value, 0, 0);
        cleanup.add(() -> aicLib.release(aic));
        do {
            assertEquals(SOCK_DGRAM.value, aicLib.getSockType(aic));
            assertEquals(IPPROTO_UDP.value, aicLib.getProtocol(aic));
            assertNull(aicLib.getCanonName(aic));

            if (aicLib.getFamily(aic) == AF_INET.value) {
                UniversalSockAddr usa = aicLib.getSockAddr(aic);
                assertEquals(AF_INET.value, usaLib.getFamily(usa));
                Inet4SockAddr addr = usaLib.asInet4SockAddr(usa);
                assertEquals(INADDR_LOOPBACK.value, addr.getAddress());
                assertEquals(0, addr.getPort());
            }
        } while (aicLib.next(aic));
    }

    @Test
    public void getaddrinfo() throws GetAddrInfoException {
        Object node = s2p("localhost");
        Object service = s2p("https");
        AddrInfoCursor aic = lib.getaddrinfo(posixSupport, node, service, AF_INET.value, 0, IPPROTO_TCP.value, AI_CANONNAME.value);
        cleanup.add(() -> aicLib.release(aic));
        assertEquals(AF_INET.value, aicLib.getFamily(aic));
        assertEquals(SOCK_STREAM.value, aicLib.getSockType(aic));
        assertEquals(IPPROTO_TCP.value, aicLib.getProtocol(aic));
        assertEquals("localhost", p2s(aicLib.getCanonName(aic)));

        UniversalSockAddr usa = aicLib.getSockAddr(aic);
        assertEquals(AF_INET.value, usaLib.getFamily(usa));
        Inet4SockAddr addr2 = usaLib.asInet4SockAddr(usa);
        assertEquals(INADDR_LOOPBACK.value, addr2.getAddress());
        assertEquals(443, addr2.getPort());
    }

    @Test
    public void inet4Address() {
        Inet4SockAddr addr = new Inet4SockAddr(1234, 0x01020304);
        assertEquals(AF_INET.value, addr.getFamily());
        assertEquals(1234, addr.getPort());
        assertEquals(0x01020304, addr.getAddress());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, addr.getAddressAsBytes());

        addr = new Inet4SockAddr(65535, new byte[]{6, 7, 8, 9, 10});
        assertEquals(AF_INET.value, addr.getFamily());
        assertEquals(65535, addr.getPort());
        assertEquals(0x06070809, addr.getAddress());
        assertArrayEquals(new byte[]{6, 7, 8, 9}, addr.getAddressAsBytes());
    }

    static final Map<String, Integer> ip4Addresses = new HashMap<>();
    static {
        ip4Addresses.put("text", null);
        ip4Addresses.put("1.2.3.", null);
        ip4Addresses.put(".1.2.3", null);
        ip4Addresses.put("1.2.65536", null);
        ip4Addresses.put("1.2.3.4.5", null);
        ip4Addresses.put("1.2.3.4.5.6", null);
        ip4Addresses.put("1.2.-3.4", null);
        ip4Addresses.put("1.2. 3.4", null);
        ip4Addresses.put(" 1.2.3.4", null);
        ip4Addresses.put("1.2.3.4@", null);
        ip4Addresses.put("1.2.3.4a", null);
        ip4Addresses.put("1.2..4", null);
        ip4Addresses.put("1.2.3.4", 0x01020304);
        ip4Addresses.put("1.2.0x3456", 0x01023456);
        ip4Addresses.put("1.2.0xffff", 0x0102ffff);
        ip4Addresses.put("1.0xffffff", 0x01ffffff);
        ip4Addresses.put("1.234567", 0x01039447);
        ip4Addresses.put("0x12345678", 0x12345678);
        ip4Addresses.put("0xff.0377.65535", 0xffffffff);
        ip4Addresses.put("0xa.012.10.0", 0x0a0a0a00);
        ip4Addresses.put("00.0x00000.0", 0x00000000);
        ip4Addresses.put("00.0x100.0", null);
    }

    @Test
    public void inet_addr() {
        for (Map.Entry<String, Integer> a : ip4Addresses.entrySet()) {
            String src = a.getKey();
            Integer expected = a.getValue();
            int actual = lib.inet_addr(posixSupport, s2p(src));
            assertEquals("inet_addr(\"" + src + "\")", expected == null ? INADDR_NONE.value : expected, actual);
        }
    }

    @Test
    public void inet_aton() {
        for (Map.Entry<String, Integer> a : ip4Addresses.entrySet()) {
            String src = a.getKey();
            Integer expected = a.getValue();
            Integer actual;
            try {
                actual = lib.inet_aton(posixSupport, s2p(src));
            } catch (InvalidAddressException e) {
                actual = null;
            }
            assertEquals("inet_aton(\"" + src + "\")", expected, actual);
        }
    }

    @Test
    public void inet_ntoa() {
        assertEquals("0.0.0.0", p2s(lib.inet_ntoa(posixSupport, 0x00000000)));
        assertEquals("1.2.3.4", p2s(lib.inet_ntoa(posixSupport, 0x01020304)));
        assertEquals("18.52.86.120", p2s(lib.inet_ntoa(posixSupport, 0x12345678)));
        assertEquals("255.255.255.255", p2s(lib.inet_ntoa(posixSupport, 0xffffffff)));
    }

    @Test
    public void inet_pton() throws PosixException, InvalidAddressException {
        assertArrayEquals(new byte[]{1, 2, -2, -1}, lib.inet_pton(posixSupport, AF_INET.value, s2p("1.2.254.255")));
        assertArrayEquals(new byte[]{0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1}, lib.inet_pton(posixSupport, AF_INET6.value, s2p("1::FF")));
        assertArrayEquals(MAPPED_LOOPBACK, lib.inet_pton(posixSupport, AF_INET6.value, s2p("::ffff:127.0.0.1")));
    }

    @Test
    public void inet_pton_eafnosupport() throws PosixException, InvalidAddressException {
        expectErrno(OSErrorEnum.EAFNOSUPPORT);
        lib.inet_pton(posixSupport, AF_UNSPEC.value, s2p(""));
    }

    @Test
    public void inet_pton_invalid_inet6() throws PosixException, InvalidAddressException {
        expectedException.expect(InvalidAddressException.class);
        lib.inet_pton(posixSupport, AF_INET6.value, s2p(":"));
    }

    @Test
    public void inet_pton_invalid_inet4_as_inet6() throws PosixException, InvalidAddressException {
        expectedException.expect(InvalidAddressException.class);
        lib.inet_pton(posixSupport, AF_INET6.value, s2p("127.0.0.1"));
    }

    @Test
    public void inet_pton_invalid_inet4() throws PosixException {
        String[] addresses = {
                        "1.2.3.4.5",  // too many bytes
                        "1.2.65535",  // unlike inet_aton, inet_pton requires exactly four bytes
                        "1.2.0x10.4", // hexadecimal is not allowed
                        "1::FF",      // IPv6 address is not allowed
        };
        for (String src : addresses) {
            try {
                lib.inet_pton(posixSupport, AF_INET.value, s2p(src));
                fail("inet_pton(AF_INET, \"" + src + "\") was expected to fail");
            } catch (InvalidAddressException e) {
                // expected
            }
        }
    }

    @Test
    public void inet_pton_inet4_octal() throws PosixException, InvalidAddressException {
        // native inet_pton on darwin accepts leading zeroes (but handles them as decimal)
        assumeTrue("java".equals(backendName) || runsOnLinux());
        expectedException.expect(InvalidAddressException.class);
        lib.inet_pton(posixSupport, AF_INET.value, s2p("1.2.010.4"));
    }

    @Test
    public void inet_ntop() throws PosixException {
        assertEquals("1.0.255.254", p2s(lib.inet_ntop(posixSupport, AF_INET.value, new byte[]{1, 0, -1, -2, -3})));
        assertThat(p2s(lib.inet_ntop(posixSupport, AF_INET6.value, new byte[]{-3, -2, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4})),
                        anyOf(equalTo("fdfe:0:ff00::1:203"), equalTo("fdfe:0:ff00:0:0:0:1:203")));
        assertEquals("::ffff:127.0.0.1", p2s(lib.inet_ntop(posixSupport, AF_INET6.value, MAPPED_LOOPBACK)));
    }

    @Test
    public void inet_ntop_eafnosupport() throws PosixException {
        expectErrno(OSErrorEnum.EAFNOSUPPORT);
        lib.inet_ntop(posixSupport, AF_UNSPEC.value, new byte[16]);
    }

    @Test
    public void inet_ntop_len() throws PosixException {
        expectedException.expect(IllegalArgumentException.class);
        lib.inet_ntop(posixSupport, AF_INET6.value, new byte[15]);
    }

    @Test
    public void gethostname() throws PosixException {
        assertTrue(p2s(lib.gethostname(posixSupport)).length() > 0);
    }

    private static FamilySpecificSockAddr localAddress(int family, int port) {
        if (family == AF_INET.value) {
            return new Inet4SockAddr(port, INADDR_LOOPBACK.value);
        } else {
            return new Inet6SockAddr(port, IN6ADDR_LOOPBACK, 0, 0);
        }
    }

    private int checkBound(int family, UniversalSockAddr usa) {
        assertEquals(family, usaLib.getFamily(usa));
        int port;
        if (family == AF_INET.value) {
            port = usaLib.asInet4SockAddr(usa).getPort();
        } else {
            port = usaLib.asInet6SockAddr(usa).getPort();
        }
        assertTrue(port != 0);
        return port;
    }

    private void checkUsa(FamilySpecificSockAddr expectedAddr, UniversalSockAddr actualUsa) {
        if (expectedAddr instanceof Inet4SockAddr) {
            Inet4SockAddr expected = (Inet4SockAddr) expectedAddr;
            assertEquals(AF_INET.value, usaLib.getFamily(actualUsa));
            Inet4SockAddr actual = usaLib.asInet4SockAddr(actualUsa);
            assertEquals(expected.getPort(), actual.getPort());
            assertEquals(expected.getAddress(), actual.getAddress());
        } else {
            Inet6SockAddr expected = (Inet6SockAddr) expectedAddr;
            assertEquals(AF_INET6.value, usaLib.getFamily(actualUsa));
            Inet6SockAddr actual = usaLib.asInet6SockAddr(actualUsa);
            assertEquals(expected.getPort(), actual.getPort());
            assertArrayEquals(expected.getAddress(), actual.getAddress());
            assertEquals(expected.getScopeId(), actual.getScopeId());
            if ("native".equals(backendName)) {
                assertEquals(expected.getFlowInfo(), actual.getFlowInfo());
            }
        }
    }

    private Object s2p(String s) {
        return lib.createPathFromString(posixSupport, s);
    }

    private String p2s(Object p) {
        return lib.getPathAsString(posixSupport, p);
    }

    private void expectErrno(OSErrorEnum... expectedErrorCodes) {
        expectedException.expect(new TypeSafeMatcher<PosixException>() {
            @Override
            protected boolean matchesSafely(PosixException item) {
                return Stream.of(expectedErrorCodes).anyMatch(e -> item.getErrorCode() == e.getNumber());
            }

            @Override
            public void describeTo(Description description) {
                String names = Stream.of(expectedErrorCodes).map(OSErrorEnum::name).collect(Collectors.joining(" or "));
                description.appendText("PosixException with error code ").appendText(names);
            }

            @Override
            protected void describeMismatchSafely(PosixException item, Description mismatchDescription) {
                mismatchDescription.appendText("the actual error code was ").appendValue(item.getErrorCode()).appendText(" (").appendValue(item).appendText(")");
            }
        });
    }

    private void expectGetAddrInfoException(MandatoryIntConstant expectedErrorCode) {
        expectedException.expect(new TypeSafeMatcher<GetAddrInfoException>() {
            @Override
            protected boolean matchesSafely(GetAddrInfoException item) {
                return item.getErrorCode() == expectedErrorCode.value;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("GetAddrInfoException with error code ").appendValue(expectedErrorCode.name);
            }

            @Override
            protected void describeMismatchSafely(GetAddrInfoException item, Description mismatchDescription) {
                mismatchDescription.appendText("the actual error code was ").appendValue(item.getErrorCode()).appendText(" (").appendValue(item).appendText(")");
            }
        });
    }

    private int createSocket(int family, int type, int protocol) throws PosixException {
        int sockfd = lib.socket(posixSupport, family, type, protocol);
        cleanup.add(() -> lib.close(posixSupport, sockfd));
        return sockfd;
    }

    private UniversalSockAddr createUsa(FamilySpecificSockAddr src) {
        return lib.createUniversalSockAddr(posixSupport, src);
    }

    private int getIntSockOpt(int socket, int level, int option) throws PosixException {
        byte[] buf = new byte[4];
        assertEquals(4, lib.getsockopt(posixSupport, socket, level, option, buf, 4));
        return nativeByteArraySupport().getInt(buf, 0);
    }

    private void setIntSockOpt(int socket, int level, int option, int value) throws PosixException {
        byte[] buf = new byte[4];
        nativeByteArraySupport().putInt(buf, 0, value);
        lib.setsockopt(posixSupport, socket, level, option, buf, 4);
    }

    private static ByteArraySupport nativeByteArraySupport() {
        return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? ByteArraySupport.littleEndian() : ByteArraySupport.bigEndian();
    }

    private static boolean isInet6Supported() {
        // Linux CI machines currently do not support IPv6 reliably
        return !(runsOnCi() && runsOnLinux());
    }

    private static boolean runsOnLinux() {
        String property = System.getProperty("os.name");
        return (property != null && property.toLowerCase().contains("linux"));
    }

    private static boolean runsOnCi() {
        return "true".equals(System.getenv("CI"));
    }

    private class Socket {
        final int family;
        final int fd;
        private FamilySpecificSockAddr cachedAddress;

        Socket(int fd, int family, @SuppressWarnings("unused") int type) {
            this.fd = fd;
            this.family = family;
        }

        Socket(int family, int type) throws PosixException {
            this(createSocket(family, type, 0), family, type);
        }

        void bind() throws PosixException {
            if (family == AF_INET.value) {
                lib.bind(posixSupport, fd, createUsa(new Inet4SockAddr(0, INADDR_ANY.value)));
            } else {
                lib.bind(posixSupport, fd, createUsa(new Inet6SockAddr(0, IN6ADDR_ANY, 0, 0)));
            }
        }

        void connect(UniversalSockAddr addr) throws PosixException {
            lib.connect(posixSupport, fd, addr);
        }

        void listen(int backlog) throws PosixException {
            lib.listen(posixSupport, fd, backlog);
        }

        UniversalSockAddr getpeername() throws PosixException {
            return lib.getpeername(posixSupport, fd);
        }

        UniversalSockAddr getsockname() throws PosixException {
            return lib.getsockname(posixSupport, fd);
        }

        void recv(byte[] expectedData, int flags) throws PosixException {
            byte[] buf = new byte[expectedData.length * 2];
            assertEquals(expectedData.length, lib.recv(posixSupport, fd, buf, 0, buf.length, flags));
            assertArrayEquals(expectedData, Arrays.copyOf(buf, expectedData.length));
        }

        UniversalSockAddr recvfrom(byte[] expectedData, int flags) throws PosixException {
            byte[] buf = new byte[expectedData.length * 2];
            RecvfromResult recvfromResult = lib.recvfrom(posixSupport, fd, buf, 0, buf.length, flags);
            assertEquals(expectedData.length, recvfromResult.readBytes);
            assertArrayEquals(expectedData, Arrays.copyOf(buf, expectedData.length));
            return recvfromResult.sockAddr;
        }

        void send(byte[] data, int flags) throws PosixException {
            assertEquals(data.length, lib.send(posixSupport, fd, data, 0, data.length, flags));
        }

        void sendto(byte[] data, int flags, UniversalSockAddr destAddr) throws PosixException {
            assertEquals(data.length, lib.sendto(posixSupport, fd, data, 0, data.length, flags, destAddr));
        }

        void write(byte[] data) throws PosixException {
            assertEquals(data.length, lib.write(posixSupport, fd, Buffer.wrap(data)));
        }

        void read(byte[] expectedData) throws PosixException {
            Buffer buf = lib.read(posixSupport, fd, expectedData.length * 2);
            assertEquals(expectedData.length, buf.length);
            assertArrayEquals(expectedData, Arrays.copyOf(buf.data, expectedData.length));
        }

        void shutdown(int how) throws PosixException {
            lib.shutdown(posixSupport, fd, how);
        }

        void setBlocking(boolean block) throws PosixException {
            lib.setBlocking(posixSupport, fd, block);
        }

        boolean getBlocking() throws PosixException {
            return lib.getBlocking(posixSupport, fd);
        }

        FamilySpecificSockAddr address() throws PosixException {
            if (cachedAddress == null) {
                UniversalSockAddr addr = getsockname();
                int port = checkBound(family, addr);
                cachedAddress = localAddress(family, port);
            }
            return cachedAddress;
        }

        UniversalSockAddr usa() throws PosixException {
            return createUsa(address());
        }
    }

    private class Server extends Socket {
        final int port;

        Server(int family, int type) throws PosixException {
            super(family, type);
            bind();
            if (type == SOCK_STREAM.value) {
                listen(5);
            }
            UniversalSockAddr boundUsa = lib.getsockname(posixSupport, fd);
            port = checkBound(family, boundUsa);
        }
    }

    private class UdpClient extends Socket {
        UdpClient(int family) throws PosixException {
            super(family, SOCK_DGRAM.value);
        }
    }

    private class UdpServer extends Server {
        UdpServer(int family) throws PosixException {
            super(family, SOCK_DGRAM.value);
        }
    }

    private class TcpClient extends Socket {
        TcpClient(int family) throws PosixException {
            super(family, SOCK_STREAM.value);
        }

        TcpClient(int fd, int family) {
            super(fd, family, SOCK_STREAM.value);
        }
    }

    private class TcpServer extends Server {
        TcpServer(int family) throws PosixException {
            super(family, SOCK_STREAM.value);
        }

        TcpClient accept(FamilySpecificSockAddr expectedAddress) throws PosixException {
            AcceptResult acceptResult = lib.accept(posixSupport, fd);
            cleanup.add(() -> lib.close(posixSupport, acceptResult.socketFd));
            checkUsa(expectedAddress, acceptResult.sockAddr);
            return new TcpClient(acceptResult.socketFd, family);
        }
    }
}
