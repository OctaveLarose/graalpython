/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;

public class PNodeWithRaise extends PNodeWithContext {
    @Child private PRaiseNode raiseNode;

    protected final PRaiseNode getRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (isAdoptable()) {
                raiseNode = insert(PRaiseNode.create());
            } else {
                raiseNode = PRaiseNode.getUncached();
            }
        }
        return raiseNode;
    }

    public PException raise(PythonBuiltinClassType type, String string) {
        return getRaiseNode().raise(type, string);
    }

    public PException raise(PythonBuiltinClassType exceptionType) {
        return getRaiseNode().raise(exceptionType);
    }

    public final PException raise(PythonBuiltinClassType type, PBaseException cause, String format, Object... arguments) {
        return getRaiseNode().raise(type, cause, format, arguments);
    }

    public final PException raise(PythonBuiltinClassType type, String format, Object... arguments) {
        return getRaiseNode().raise(type, format, arguments);
    }

    public final PException raise(PythonBuiltinClassType type, Object... arguments) {
        return getRaiseNode().raise(type, arguments);
    }

    public final PException raise(PythonBuiltinClassType type, Exception e) {
        return getRaiseNode().raise(type, e);
    }

    public final PException raiseBadInternalCall() {
        return getRaiseNode().raise(PythonBuiltinClassType.SystemError, BAD_ARG_TO_INTERNAL_FUNC);
    }

    public final PException raiseOverflow() {
        return getRaiseNode().raiseNumberTooLarge(OverflowError, 0);
    }
}
