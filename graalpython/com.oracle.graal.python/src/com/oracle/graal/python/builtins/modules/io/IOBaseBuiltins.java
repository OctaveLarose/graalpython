/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.PEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READLINE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READLINES;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.TELL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.TRUNCATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITELINES;
import static com.oracle.graal.python.builtins.modules.io.IONodes._CHECKCLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes._CHECKREADABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes._CHECKSEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes._CHECKWRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.__IOBASE_CLOSED;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.append;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.createOutputStream;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.toByteArray;
import static com.oracle.graal.python.nodes.ErrorMessages.S_SHOULD_RETURN_BYTES_NOT_P;
import static com.oracle.graal.python.nodes.SpecialMethodNames.FILENO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IOUnsupportedOperation;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.ByteArrayOutputStream;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PIOBase)
public class IOBaseBuiltins extends PythonBuiltins {

    // taken from usr/include/stdio.h
    public static final int BUFSIZ = 8192;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IOBaseBuiltinsFactory.getFactories();
    }

    @Builtin(name = CLOSED, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean closed(VirtualFrame frame, PythonObject self,
                        @Cached PyObjectLookupAttr lookup) {
            return isClosed(self, frame, lookup);
        }
    }

    @Builtin(name = SEEKABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SeekableNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean seekable(@SuppressWarnings("unused") PythonObject self) {
            return false;
        }
    }

    @Builtin(name = READABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean readable(@SuppressWarnings("unused") PythonObject self) {
            return false;
        }
    }

    @Builtin(name = WRITABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WritableNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean writable(@SuppressWarnings("unused") PythonObject self) {
            return false;
        }
    }

    @Builtin(name = _CHECKCLOSED, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CheckClosedNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doCheckClosed(VirtualFrame frame, PythonObject self,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            if (isTrueNode.execute(frame, getAttr.execute(frame, self, CLOSED))) {
                throw raise(ValueError, ErrorMessages.IO_CLOSED);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = _CHECKSEEKABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CheckSeekableNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doCheckSeekable(VirtualFrame frame, PythonObject self,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached IsNode isNode) {
            Object v = callMethod.execute(frame, self, SEEKABLE);
            if (isNode.isTrue(v)) {
                return true;
            }
            throw unsupported(getRaiseNode(), ErrorMessages.FILE_OR_STREAM_IS_NOT_SEEKABLE);
        }
    }

    @Builtin(name = _CHECKREADABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CheckReadableNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doCheckReadable(VirtualFrame frame, PythonObject self,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached IsNode isNode) {
            Object v = callMethod.execute(frame, self, READABLE);
            if (isNode.isTrue(v)) {
                return true;
            }
            throw unsupported(getRaiseNode(), ErrorMessages.FILE_OR_STREAM_IS_NOT_READABLE);
        }
    }

    @Builtin(name = _CHECKWRITABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CheckWritableNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doCheckWritable(VirtualFrame frame, PythonObject self,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached IsNode isNode) {
            Object result = callMethod.execute(frame, self, WRITABLE);
            if (isNode.isTrue(result)) {
                return true;
            }
            throw unsupported(getRaiseNode(), ErrorMessages.FILE_OR_STREAM_IS_NOT_WRITABLE);
        }
    }

    @Builtin(name = CLOSE, minNumOfPositionalArgs = 1)
    @ImportStatic(IONodes.class)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {
        @Specialization
        PNone close(VirtualFrame frame, PythonObject self,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached("create(__IOBASE_CLOSED)") SetAttributeNode setAttributeNode,
                        @Cached BranchProfile errorProfile) {
            if (!isClosed(self, frame, lookup)) {
                try {
                    callMethod.execute(frame, self, FLUSH);
                } catch (PException e) {
                    errorProfile.enter();
                    try {
                        setAttributeNode.executeVoid(frame, self, true);
                    } catch (PException e1) {
                        PBaseException ee = e1.getEscapedException();
                        ee.setContext(e.setCatchingFrameAndGetEscapedException(frame, this));
                        throw getRaiseNode().raiseExceptionObject(ee);
                    }
                    throw e.getExceptionForReraise();
                }
                setAttributeNode.executeVoid(frame, self, true);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = FLUSH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FlushNode extends PythonUnaryBuiltinNode {

        @Specialization
        PNone flush(VirtualFrame frame, PythonObject self,
                        @Cached PyObjectLookupAttr lookup) {
            if (isClosed(self, frame, lookup)) {
                throw raise(ValueError, ErrorMessages.IO_CLOSED);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = SEEK, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class SeekNode extends PythonBuiltinNode {
        @Specialization
        Object seek(@SuppressWarnings("unused") PythonObject self, @SuppressWarnings("unused") Object args) {
            throw unsupported(getRaiseNode(), SEEK);
        }
    }

    @Builtin(name = TRUNCATE, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class TruncateNode extends PythonBuiltinNode {
        @Specialization
        Object truncate(@SuppressWarnings("unused") PythonObject self) {
            throw unsupported(getRaiseNode(), TRUNCATE);
        }
    }

    @Builtin(name = TELL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TellNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object tell(VirtualFrame frame, PythonObject self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self, SEEK, 0, 1);
        }
    }

    @Builtin(name = __ENTER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class EnterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PythonObject enter(VirtualFrame frame, PythonObject self,
                        @Cached CheckClosedNode checkClosedNode) {
            checkClosedNode.execute(frame, self);
            return self;
        }
    }

    @Builtin(name = __EXIT__, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class ExitNode extends PythonBuiltinNode {
        @Specialization
        static Object exit(VirtualFrame frame, PythonObject self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self, CLOSE);
        }
    }

    @Builtin(name = FILENO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FilenoNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object fileno(@SuppressWarnings("unused") PythonObject self) {
            throw unsupported(getRaiseNode(), FILENO);
        }
    }

    @Builtin(name = ISATTY, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsattyNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean isatty(VirtualFrame frame, PythonObject self,
                        @Cached CheckClosedNode checkClosedNode) {
            checkClosedNode.execute(frame, self);
            return false;
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PythonObject iter(VirtualFrame frame, PythonObject self,
                        @Cached CheckClosedNode checkClosedNode) {
            checkClosedNode.execute(frame, self);
            return self;
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object next(VirtualFrame frame, PythonObject self,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectSizeNode sizeNode) {
            Object line = callMethod.execute(frame, self, READLINE);
            if (sizeNode.execute(frame, line) <= 0) {
                throw raise(StopIteration);
            }
            return line;
        }
    }

    @Builtin(name = WRITELINES, minNumOfPositionalArgs = 2, parameterNames = {"$self", "lines"})
    @GenerateNodeFactory
    abstract static class WriteLinesNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object writeLines(VirtualFrame frame, PythonObject self, Object lines,
                        @Cached CheckClosedNode checkClosedNode,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectGetIter getIter) {
            checkClosedNode.execute(frame, self);
            Object iter = getIter.execute(frame, lines);
            while (true) {
                Object line;
                try {
                    line = getNextNode.execute(frame, iter);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
                callMethod.execute(frame, self, WRITE, line);
                // TODO _PyIO_trap_eintr [GR-23297]
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = READLINE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadlineNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IOBaseBuiltinsClinicProviders.ReadlineNodeClinicProviderGen.INSTANCE;
        }

        /**
         * implementation of cpython/Modules/_io/iobase.c:_io__IOBase_readline_impl
         */
        @Specialization
        PBytes readline(VirtualFrame frame, Object self, int limit,
                        @Cached PyObjectLookupAttr lookupPeek,
                        @Cached CallNode callPeek,
                        @Cached PyObjectCallMethodObjArgs callRead,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached ConditionProfile hasPeek,
                        @Cached ConditionProfile isBytes) {
            /* For backwards compatibility, a (slowish) readline(). */
            Object peek = lookupPeek.execute(frame, self, PEEK);
            ByteArrayOutputStream buffer = createOutputStream();
            while (limit < 0 || buffer.size() < limit) {
                int nreadahead = 1;
                if (hasPeek.profile(peek != PNone.NO_VALUE)) {
                    Object readahead = callPeek.execute(frame, peek, 1);
                    // TODO _PyIO_trap_eintr [GR-23297]
                    if (isBytes.profile(!(readahead instanceof PBytes))) {
                        throw raise(OSError, S_SHOULD_RETURN_BYTES_NOT_P, "peek()", readahead);
                    }
                    byte[] buf = bufferLib.getInternalOrCopiedByteArray(readahead);
                    int bufLen = bufferLib.getBufferLength(readahead);
                    if (bufLen > 0) {
                        int n = 0;
                        while ((limit < 0 || n < limit) && n < bufLen) {
                            if (buf[n++] == '\n') {
                                break;
                            }
                        }
                        nreadahead = n;
                    }
                }

                Object b = callRead.execute(frame, self, READ, nreadahead);
                if (isBytes.profile(!(b instanceof PBytes))) {
                    throw raise(OSError, S_SHOULD_RETURN_BYTES_NOT_P, "read()", b);
                }
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(b);
                int bytesLen = bufferLib.getBufferLength(b);
                if (bytesLen == 0) {
                    break;
                }

                append(buffer, bytes, bytesLen);
                if (bytes[bytesLen - 1] == '\n') {
                    break;
                }
            }

            return factory().createBytes(toByteArray(buffer));
        }
    }

    @Builtin(name = READLINES, minNumOfPositionalArgs = 1, parameterNames = {"$self", "hint"})
    @ArgumentClinic(name = "hint", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadlinesNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IOBaseBuiltinsClinicProviders.ReadlinesNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "hint <= 0")
        Object doall(VirtualFrame frame, Object self, @SuppressWarnings("unused") int hint,
                        @Cached GetNextNode next,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyObjectSizeNode sizeNode) {
            return withHint(frame, self, Integer.MAX_VALUE, next, errorProfile, getIter, sizeNode);
        }

        @Specialization(guards = "hint > 0")
        Object withHint(VirtualFrame frame, Object self, int hint,
                        @Cached GetNextNode next,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyObjectSizeNode sizeNode) {
            int length = 0;
            Object iterator = getIter.execute(frame, self);
            ArrayBuilder<Object> list = new ArrayBuilder<>();
            while (true) {
                try {
                    Object line = next.execute(frame, iterator);
                    list.add(line);
                    int lineLength = sizeNode.execute(frame, line);
                    if (lineLength > hint - length) {
                        break;
                    }
                    length += lineLength;
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
            }
            return factory().createList(list.toArray(new Object[0]));
        }
    }

    /**
     * Equivalent of {@code iobase_is_closed}.
     * 
     * @param self the IOBase instance
     * @return true if the {@link IONodes#__IOBASE_CLOSED} attribute exists
     */
    private static boolean isClosed(PythonObject self, VirtualFrame frame, PyObjectLookupAttr lookup) {
        return !PGuards.isNoValue(lookup.execute(frame, self, __IOBASE_CLOSED));
    }

    /**
     * Equivalent of {@code iobase_unsupported}.
     */
    private static PException unsupported(PRaiseNode raiseNode, String message) {
        throw raiseNode.raise(IOUnsupportedOperation, message);
    }
}
