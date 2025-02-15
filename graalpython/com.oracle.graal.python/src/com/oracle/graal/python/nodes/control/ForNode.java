/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.control;

import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PObjectSequenceIterator;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

final class ForRepeatingNode extends PNodeWithContext implements RepeatingNode {
    @CompilationFinal FrameSlot iteratorSlot;
    @Child ForNextElementNode nextElement;
    @Child StatementNode body;

    public ForRepeatingNode(StatementNode target, StatementNode body) {
        this.nextElement = ForNextElementNodeGen.create(target);
        this.body = body;
    }

    public boolean executeRepeating(VirtualFrame frame) {
        try {
            if (!nextElement.execute(frame, frame.getObject(iteratorSlot))) {
                return false;
            }
        } catch (FrameSlotTypeException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException(e);
        }
        body.executeVoid(frame);
        return true;
    }
}

@ImportStatic({PythonOptions.class, SpecialMethodNames.class})
abstract class ForNextElementNode extends PNodeWithContext {

    @Child StatementNode target;

    public ForNextElementNode(StatementNode target) {
        this.target = target;
    }

    public abstract boolean execute(VirtualFrame frame, Object range);

    /*
     * There's a limited number of iterator types - specialize to all of them.
     */

    @Specialization(guards = "iterator.getClass() == clazz", limit = "99")
    protected boolean doIntegerIterator(VirtualFrame frame, PIntegerIterator iterator,
                    @Cached("iterator.getClass()") Class<? extends PIntegerIterator> clazz,
                    @Cached("createCountingProfile()") ConditionProfile profile) {
        PIntegerIterator profiledIterator = clazz.cast(iterator);
        if (!profile.profile(profiledIterator.hasNext())) {
            profiledIterator.setExhausted();
            return false;
        }
        ((WriteNode) target).executeInt(frame, profiledIterator.next());
        return true;
    }

    @Specialization
    protected boolean doObjectIterator(VirtualFrame frame, PObjectSequenceIterator iterator,
                    @Cached("createCountingProfile()") ConditionProfile profile) {
        if (!profile.profile(iterator.hasNext())) {
            iterator.setExhausted();
            return false;
        }
        ((WriteNode) target).executeObject(frame, iterator.next());
        return true;
    }

    @Specialization
    protected boolean doLongIterator(VirtualFrame frame, PLongSequenceIterator iterator,
                    @Cached("createCountingProfile()") ConditionProfile profile) {
        if (!profile.profile(iterator.hasNext())) {
            iterator.setExhausted();
            return false;
        }
        ((WriteNode) target).executeLong(frame, iterator.next());
        return true;
    }

    @Specialization
    protected boolean doDoubleIterator(VirtualFrame frame, PDoubleSequenceIterator iterator,
                    @Cached("createCountingProfile()") ConditionProfile profile) {
        if (!profile.profile(iterator.hasNext())) {
            iterator.setExhausted();
            return false;
        }
        ((WriteNode) target).executeDouble(frame, iterator.next());
        return true;
    }

    @Specialization
    protected boolean doIterator(VirtualFrame frame, Object object,
                    @Cached GetNextNode next,
                    @Cached IsBuiltinClassProfile errorProfile,
                    @Cached PRaiseNode raise) {
        try {
            ((WriteNode) target).executeObject(frame, next.execute(frame, object));
            return true;
        } catch (PException e) {
            e.expectStopIteration(errorProfile, raise, object);
            return false;
        }
    }
}

@NodeInfo(shortName = "for")
public final class ForNode extends LoopNode {

    @CompilationFinal private FrameSlot iteratorSlot;

    @Child private com.oracle.truffle.api.nodes.LoopNode loopNode;
    @Child private ExpressionNode iterator;

    public ForNode(StatementNode body, StatementNode target, ExpressionNode iterator) {
        this.iterator = iterator;
        this.loopNode = Truffle.getRuntime().createLoopNode(new ForRepeatingNode(target, body));
    }

    public StatementNode getTarget() {
        return ((ForRepeatingNode) loopNode.getRepeatingNode()).nextElement.target;
    }

    public ExpressionNode getIterator() {
        return iterator;
    }

    @Override
    public StatementNode getBody() {
        return ((ForRepeatingNode) loopNode.getRepeatingNode()).body;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        if (iteratorSlot == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getLock().lock();
            try {
                if (iteratorSlot == null) {
                    iteratorSlot = frame.getFrameDescriptor().addFrameSlot(new Object(), FrameSlotKind.Object);
                    ((ForRepeatingNode) loopNode.getRepeatingNode()).iteratorSlot = iteratorSlot;
                }
            } finally {
                getLock().unlock();
            }
        }
        frame.setObject(iteratorSlot, iterator.execute(frame));
        try {
            loopNode.execute(frame);
        } finally {
            frame.setObject(iteratorSlot, null);
        }
    }
}
