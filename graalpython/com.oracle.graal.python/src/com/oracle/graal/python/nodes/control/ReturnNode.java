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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.exception.ReturnException;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ValueProfile;

public class ReturnNode extends StatementNode {

    @Override
    public void executeVoid(VirtualFrame frame) {
        throw ReturnException.INSTANCE;
    }

    @Override
    public Object returnExecute(VirtualFrame frame) {
        return PNone.NONE;
    }

    public static final class FrameReturnNode extends ReturnNode {
        protected final FrameSlot slot;
        @Child private ExpressionNode right;

        public FrameReturnNode(ExpressionNode right, FrameSlot slot) {
            this.right = right;
            this.slot = slot;
        }

        @Override
        public void executeVoid(VirtualFrame frame) {
            frame.setObject(slot, right.execute(frame));
            throw ReturnException.INSTANCE;
        }

        @Override
        public Object returnExecute(VirtualFrame frame) {
            return right.execute(frame);
        }
    }

    public static final class GeneratorFrameReturnNode extends ReturnNode {
        private final ValueProfile frameProfile = ValueProfile.createClassProfile();
        protected final FrameSlot slot;
        @Child private ExpressionNode right;

        public GeneratorFrameReturnNode(ExpressionNode right, FrameSlot slot) {
            this.right = right;
            this.slot = slot;
        }

        @Override
        public void executeVoid(VirtualFrame frame) {
            MaterializedFrame generatorFrame = frameProfile.profile(PArguments.getGeneratorFrame(frame));
            generatorFrame.setObject(slot, right.execute(frame));
            throw ReturnException.INSTANCE;
        }

        @Override
        public Object returnExecute(VirtualFrame frame) {
            return right.execute(frame);
        }
    }
}
