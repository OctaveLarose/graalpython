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
package com.oracle.graal.python.builtins.objects.function;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.NodeFactory;

/**
 * Context independent wrapper of a method that can be stored in special method slots. These
 * wrappers are context and also language instance independent.
 *
 * @see com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot
 */
public abstract class BuiltinMethodDescriptor {

    /**
     * Size of this cache is limited by the number of builtins in GraalPython. First few contexts
     * may, in theory, experience lock contention while this cache is being filled up, but after
     * that there should be no cache misses and no locking to update the cache.
     *
     * Another way to look at this is that it is a map of all builtins, like
     * {@link PythonBuiltinClassType} is list of all builtin types, but initialized at runtime.
     * 
     * Not having this cache per {@link com.oracle.graal.python.PythonLanguage} allows to save the
     * indirection when comparing to some well known {@link BuiltinMethodDescriptor} in guards.
     */
    private static final ConcurrentHashMap<BuiltinMethodDescriptor, BuiltinMethodDescriptor> CACHE = new ConcurrentHashMap<>();

    public static BuiltinMethodDescriptor get(PBuiltinFunction function) {
        CompilerAsserts.neverPartOfCompilation();
        NodeFactory<? extends PythonBuiltinBaseNode> factory = function.getBuiltinNodeFactory();
        if (factory == null || needsFrame(factory)) {
            return null;
        }

        PythonBuiltinClassType type = null;
        Object enclosing = function.getEnclosingType();
        if (enclosing instanceof PythonBuiltinClassType) {
            type = (PythonBuiltinClassType) enclosing;
        } else if (enclosing instanceof PythonBuiltinClass) {
            type = ((PythonBuiltinClass) enclosing).getType();
        } else {
            assert enclosing == null;
        }

        return get(factory, type);
    }

    public static BuiltinMethodDescriptor get(NodeFactory<? extends PythonBuiltinBaseNode> factory, PythonBuiltinClassType type) {
        CompilerAsserts.neverPartOfCompilation();
        Class<? extends PythonBuiltinBaseNode> nodeClass = factory.getNodeClass();
        BuiltinMethodDescriptor result = null;
        if (PythonUnaryBuiltinNode.class.isAssignableFrom(nodeClass)) {
            result = new UnaryBuiltinDescriptor(factory, type);
            assert result.getBuiltinAnnotation().minNumOfPositionalArgs() <= 1 : result.getBuiltinAnnotation().name();
        } else if (PythonBinaryBuiltinNode.class.isAssignableFrom(nodeClass)) {
            result = new BinaryBuiltinDescriptor(factory, type);
            assert result.getBuiltinAnnotation().minNumOfPositionalArgs() <= 2 : result.getBuiltinAnnotation().name();
        } else if (PythonTernaryBuiltinNode.class.isAssignableFrom(nodeClass)) {
            result = new TernaryBuiltinDescriptor(factory, type);
            assert result.getBuiltinAnnotation().minNumOfPositionalArgs() <= 3 : result.getBuiltinAnnotation().name();
        }
        if (result != null) {
            return CACHE.computeIfAbsent(result, x -> x);
        }
        return null;
    }

    public static boolean isInstance(Object obj) {
        return obj instanceof UnaryBuiltinDescriptor || obj instanceof BinaryBuiltinDescriptor || obj instanceof TernaryBuiltinDescriptor;
    }

    private static boolean needsFrame(NodeFactory<? extends PythonBuiltinBaseNode> factory) {
        for (Builtin builtin : factory.getNodeClass().getAnnotationsByType(Builtin.class)) {
            if (builtin.needsFrame()) {
                return true;
            }
        }
        return false;
    }

    private final NodeFactory<? extends PythonBuiltinBaseNode> factory;
    private final PythonBuiltinClassType type;

    private BuiltinMethodDescriptor(NodeFactory<? extends PythonBuiltinBaseNode> factory, PythonBuiltinClassType type) {
        this.factory = factory;
        this.type = type;
    }

    public final NodeFactory<? extends PythonBuiltinBaseNode> getFactory() {
        return factory;
    }

    public Builtin getBuiltinAnnotation() {
        return factory.getNodeClass().getAnnotationsByType(Builtin.class)[0];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BuiltinMethodDescriptor that = (BuiltinMethodDescriptor) o;
        return factory == that.factory && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(factory, type);
    }

    // Note: manually written subclass for each builtin works better with Truffle DSL than one
    // generic class that would parametrize the 'factory' field

    public static final class UnaryBuiltinDescriptor extends BuiltinMethodDescriptor {
        public UnaryBuiltinDescriptor(NodeFactory<? extends PythonBuiltinBaseNode> factory, PythonBuiltinClassType type) {
            super(factory, type);
        }

        public PythonUnaryBuiltinNode createNode() {
            return (PythonUnaryBuiltinNode) getFactory().createNode();
        }
    }

    public static final class BinaryBuiltinDescriptor extends BuiltinMethodDescriptor {
        public BinaryBuiltinDescriptor(NodeFactory<? extends PythonBuiltinBaseNode> factory, PythonBuiltinClassType type) {
            super(factory, type);
        }

        public PythonBinaryBuiltinNode createNode() {
            return (PythonBinaryBuiltinNode) getFactory().createNode();
        }
    }

    public static final class TernaryBuiltinDescriptor extends BuiltinMethodDescriptor {
        public TernaryBuiltinDescriptor(NodeFactory<? extends PythonBuiltinBaseNode> factory, PythonBuiltinClassType type) {
            super(factory, type);
        }

        public PythonTernaryBuiltinNode createNode() {
            return (PythonTernaryBuiltinNode) getFactory().createNode();
        }
    }
}
