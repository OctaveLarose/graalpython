/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.exception;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;

@ExportLibrary(InteropLibrary.class)
public final class PBaseException extends PythonObject {
    private static final ErrorMessageFormatter FORMATTER = new ErrorMessageFormatter();

    private PTuple args; // can be null for lazily generated message

    // in case of lazily generated messages, these will be used to construct the message:
    private final boolean hasMessageFormat;
    private final String messageFormat;
    private final Object[] messageArgs;

    private PException exception;
    private LazyTraceback traceback;

    private PBaseException context;
    private PBaseException cause;
    private boolean suppressContext = false;

    public PBaseException getContext() {
        return context;
    }

    public void setContext(PBaseException context) {
        this.context = context;
    }

    public PBaseException getCause() {
        return cause;
    }

    public void setCause(PBaseException cause) {
        this.cause = cause;
        this.suppressContext = true;
    }

    public boolean getSuppressContext() {
        return suppressContext;
    }

    public void setSuppressContext(boolean suppressContext) {
        this.suppressContext = suppressContext;
    }

    public PBaseException(Object cls, Shape instanceShape, PTuple args) {
        super(cls, instanceShape);
        this.args = args;
        this.hasMessageFormat = false;
        this.messageFormat = null;
        this.messageArgs = null;
    }

    public PBaseException(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        this.args = null;
        this.hasMessageFormat = false;
        this.messageFormat = null;
        this.messageArgs = null;
    }

    public PBaseException(Object cls, Shape instanceShape, String format, Object[] args) {
        super(cls, instanceShape);
        this.args = null;
        this.hasMessageFormat = true;
        this.messageFormat = format;
        this.messageArgs = args;
    }

    public PException getException() {
        return exception;
    }

    public void setException(PException exception) {
        this.exception = exception;
    }

    public void ensureReified() {
        if (exception != null) {
            // If necessary, this will call back to this object to set the traceback
            exception.ensureReified();
        }
    }

    public LazyTraceback getTraceback() {
        ensureReified();
        return traceback;
    }

    public void setTraceback(LazyTraceback traceback) {
        ensureReified();
        this.traceback = traceback;
    }

    public void setTraceback(PTraceback traceback) {
        setTraceback(new LazyTraceback(traceback));
    }

    public void clearTraceback() {
        this.traceback = null;
    }

    /**
     * Can be null in case of lazily formatted arguments.
     */
    public PTuple getArgs() {
        return args;
    }

    public void setArgs(PTuple args) {
        this.args = args;
    }

    public String getMessageFormat() {
        return messageFormat;
    }

    public boolean hasMessageFormat() {
        return hasMessageFormat;
    }

    public Object[] getMessageArgs() {
        // clone message args to ensure that they stay unmodified
        return messageArgs != null ? messageArgs.clone() : PythonUtils.EMPTY_OBJECT_ARRAY;
    }

    @TruffleBoundary
    public String getFormattedMessage() {
        final Object clazz = GetClassNode.getUncached().execute(this);
        String typeName = GetNameNode.doSlowPath(clazz);
        if (args == null) {
            if (messageArgs != null && messageArgs.length > 0) {
                return typeName + ": " + FORMATTER.format(messageFormat, getMessageArgs());
            } else if (hasMessageFormat) {
                return typeName + ": " + messageFormat;
            } else {
                return typeName;
            }
        } else if (args.getSequenceStorage().length() == 0) {
            return typeName;
        } else if (args.getSequenceStorage().length() == 1) {
            SequenceStorage store = args.getSequenceStorage();
            Object item = store instanceof BasicSequenceStorage ? store.getItemNormalized(0) : "<unknown>";
            return typeName + ": " + item.toString();
        } else {
            return typeName + ": " + args.toString();
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        // We *MUST NOT* call anything here that may need a context!
        StringBuilder sb = new StringBuilder(this.getInitialPythonClass().toString());
        if (messageArgs != null && messageArgs.length > 0) {
            sb.append("(fmt=\"").append(messageFormat).append("\", args = (");
            for (Object arg : messageArgs) {
                if (arg instanceof PythonObject) {
                    sb.append(arg);
                } else {
                    String fqn = arg.getClass().getName();
                    if (fqn.startsWith("java.lang.")) {
                        sb.append(arg);
                    } else {
                        // we do not risk printing arbitrary objects
                        sb.append("a ").append(fqn);
                    }
                }
                sb.append(", ");
            }
            sb.append(") )");
        } else if (hasMessageFormat) {
            sb.append("(fmt=\"").append(messageFormat).append('"');
        }
        return sb.toString();
    }

    public LazyTraceback internalReifyException(PFrame.Reference curFrameInfo) {
        assert curFrameInfo != PFrame.Reference.EMPTY;
        traceback = new LazyTraceback(curFrameInfo, exception, traceback);
        return traceback;
    }

    /**
     * Prepare a {@link PException} for reraising this exception, as done by <code>raise</code>
     * without arguments.
     *
     * <p>
     * We must be careful to never rethrow a PException that has already been caught and exposed to
     * the program, because its Truffle lazy stacktrace may have been already materialized, which
     * would prevent it from capturing frames after the rethrow. So we need a new PException even
     * though its just a dumb rethrow. We also need a new PException for the reason below.
     * </p>
     *
     * <p>
     * CPython reraises the exception with the traceback captured in sys.exc_info, discarding the
     * traceback in the exception, which may have changed since the time the exception had been
     * caught. This can happen due to explicit modification by the program or, more commonly, by
     * accumulating more frames by being reraised in the meantime. That's why this method takes an
     * explicit traceback argument
     * </p>
     *
     * <p>
     * Reraises shouldn't be visible in the stacktrace. We mark them as such.
     * </p>
     **/
    public PException getExceptionForReraise(LazyTraceback reraiseTraceback) {
        setTraceback(reraiseTraceback);
        PException newException = PException.fromObject(this, exception.getLocation(), false);
        newException.setHideLocation(true);
        return newException;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isException() {
        return true;
    }

    @ExportMessage
    RuntimeException throwException(
                    @Cached PRaiseNode raiseNode,
                    @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            throw raiseNode.raiseExceptionObject(this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    ExceptionType getExceptionType(
                    @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            Object clazz = getClassNode.execute(this);
            if (clazz instanceof PythonBuiltinClass) {
                clazz = ((PythonBuiltinClass) clazz).getType();
            }
            // these are the only ones we'll raise, we don't want to report user subtypes of
            // SyntaxError as Truffle syntax errors
            if (clazz == PythonBuiltinClassType.SyntaxError || clazz == PythonBuiltinClassType.IndentationError || clazz == PythonBuiltinClassType.TabError) {
                return ExceptionType.PARSE_ERROR;
            }
            if (clazz == PythonBuiltinClassType.SystemExit) {
                return ExceptionType.EXIT;
            }
            return ExceptionType.RUNTIME_ERROR;
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isExceptionIncompleteSource() {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasExceptionMessage() {
        return true;
    }

    @ExportMessage
    String getExceptionMessage(@Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return getFormattedMessage();
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    int getExceptionExitStatus(
                    @Cached CastToJavaIntExactNode castToInt,
                    @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Cached ReadAttributeFromDynamicObjectNode readNode,
                    @Shared("unsupportedProfile") @Cached BranchProfile unsupportedProfile,
                    @Shared("gil") @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            if (getExceptionType(getClassNode, gil) == ExceptionType.EXIT) {
                try {
                    // Avoiding getattr because this message shouldn't have side-effects
                    Object code = readNode.execute(this, "code");
                    if (code == PNone.NO_VALUE) {
                        return 1;
                    }
                    // Avoid side-effects in integer conversion too
                    try {
                        return castToInt.execute(code);
                    } catch (CannotCastException | PException e) {
                        return 1;
                    }
                } catch (PException e) {
                    return 1;
                }
            }
            unsupportedProfile.enter();
            throw UnsupportedMessageException.create();
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    boolean hasExceptionCause(@Shared("gil") @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return cause != null || (!suppressContext && context != null);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    Object getExceptionCause(
                    @Shared("unsupportedProfile") @Cached BranchProfile unsupportedProfile,
                    @Shared("gil") @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            if (cause != null) {
                return cause;
            }
            if (!suppressContext && context != null) {
                return context;
            }
            unsupportedProfile.enter();
            throw UnsupportedMessageException.create();
        } finally {
            gil.release(mustRelease);
        }
    }
}
