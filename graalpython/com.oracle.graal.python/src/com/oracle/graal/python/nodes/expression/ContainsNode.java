/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.expression;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;

import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public abstract class ContainsNode extends BinaryOpNode {
    @Child private LookupAndCallBinaryNode callNode = LookupAndCallBinaryNode.create(__CONTAINS__);
    @Child private CoerceToBooleanNode castBool = CoerceToBooleanNode.createIfTrueNode();

    @Child private GetNextNode next;
    @Child private IsBuiltinClassProfile errorProfile;

    public static ExpressionNode create(ExpressionNode left, ExpressionNode right) {
        return ContainsNodeGen.create(left, right);
    }

    public static ContainsNode create() {
        return ContainsNodeGen.create(null, null);
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    boolean doBoolean(VirtualFrame frame, boolean item, Object iter,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode) throws UnexpectedResultException {
        Object result = callNode.executeObject(frame, iter, item);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            Object iterator = getIter.execute(frame, iter);
            return sequenceContains(frame, iterator, item, eqNode);
        }
        return castBool.executeBoolean(frame, result);
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    boolean doInt(VirtualFrame frame, int item, Object iter,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode) throws UnexpectedResultException {
        Object result = callNode.executeObject(frame, iter, item);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            return sequenceContains(frame, getIter.execute(frame, iter), item, eqNode);
        }
        return castBool.executeBoolean(frame, result);
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    boolean doLong(VirtualFrame frame, long item, Object iter,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode) throws UnexpectedResultException {
        Object result = callNode.executeObject(frame, iter, item);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            return sequenceContains(frame, getIter.execute(frame, iter), item, eqNode);
        }
        return castBool.executeBoolean(frame, result);
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    boolean doDouble(VirtualFrame frame, double item, Object iter,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode) throws UnexpectedResultException {
        Object result = callNode.executeObject(frame, iter, item);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            return sequenceContains(frame, getIter.execute(frame, iter), item, eqNode);
        }
        return castBool.executeBoolean(frame, result);
    }

    @Specialization
    boolean doGeneric(VirtualFrame frame, Object item, Object iter,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode) {
        Object result = callNode.executeObject(frame, iter, item);
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            return sequenceContainsObject(frame, getIter.execute(frame, iter), item, eqNode);
        }
        return castBool.executeBoolean(frame, result);
    }

    private void handleUnexpectedResult(VirtualFrame frame, Object iterator, Object item, UnexpectedResultException e, PyObjectRichCompareBool.EqNode eqNode) throws UnexpectedResultException {
        // If we got an unexpected (non-primitive) result from the iterator, we need to compare it
        // and continue iterating with "next" through the generic case. However, we also want the
        // specialization to go away, so we wrap the boolean result in a new
        // UnexpectedResultException. This will cause the DSL to disable the specialization with the
        // primitive value and return the result we got, without iterating again.
        Object result = e.getResult();
        if (eqNode.execute(frame, result, item)) {
            result = true;
        } else {
            result = sequenceContainsObject(frame, iterator, item, eqNode);
        }
        throw new UnexpectedResultException(result);
    }

    private boolean sequenceContains(VirtualFrame frame, Object iterator, boolean item, PyObjectRichCompareBool.EqNode eqNode) throws UnexpectedResultException {
        while (true) {
            try {
                if (getNext().executeBoolean(frame, iterator) == item) {
                    return true;
                }
            } catch (PException e) {
                e.expectStopIteration(getErrorProfile());
                return false;
            } catch (UnexpectedResultException e) {
                handleUnexpectedResult(frame, iterator, item, e, eqNode);
            }
        }
    }

    private boolean sequenceContains(VirtualFrame frame, Object iterator, int item, PyObjectRichCompareBool.EqNode eqNode) throws UnexpectedResultException {
        while (true) {
            try {
                if (getNext().executeInt(frame, iterator) == item) {
                    return true;
                }
            } catch (PException e) {
                e.expectStopIteration(getErrorProfile());
                return false;
            } catch (UnexpectedResultException e) {
                handleUnexpectedResult(frame, iterator, item, e, eqNode);
            }
        }
    }

    private boolean sequenceContains(VirtualFrame frame, Object iterator, long item, PyObjectRichCompareBool.EqNode eqNode) throws UnexpectedResultException {
        while (true) {
            try {
                if (getNext().executeLong(frame, iterator) == item) {
                    return true;
                }
            } catch (PException e) {
                e.expectStopIteration(getErrorProfile());
                return false;
            } catch (UnexpectedResultException e) {
                handleUnexpectedResult(frame, iterator, item, e, eqNode);
            }
        }
    }

    private boolean sequenceContains(VirtualFrame frame, Object iterator, double item, PyObjectRichCompareBool.EqNode eqNode) throws UnexpectedResultException {
        while (true) {
            try {
                if (getNext().executeDouble(frame, iterator) == item) {
                    return true;
                }
            } catch (PException e) {
                e.expectStopIteration(getErrorProfile());
                return false;
            } catch (UnexpectedResultException e) {
                handleUnexpectedResult(frame, iterator, item, e, eqNode);
            }
        }
    }

    private boolean sequenceContainsObject(VirtualFrame frame, Object iterator, Object item, PyObjectRichCompareBool.EqNode eqNode) {
        while (true) {
            try {
                if (eqNode.execute(frame, getNext().execute(frame, iterator), item)) {
                    return true;
                }
            } catch (PException e) {
                e.expectStopIteration(getErrorProfile());
                return false;
            }
        }
    }

    private IsBuiltinClassProfile getErrorProfile() {
        if (errorProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            errorProfile = insert(IsBuiltinClassProfile.create());
        }
        return errorProfile;
    }

    private GetNextNode getNext() {
        if (next == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            next = insert(GetNextNode.create());
        }
        return next;
    }
}
