/* MIT License
 *
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates.
 * Copyright (c) 2019 pyhandle
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifndef HPY_CPYTHON_HPYFUNC_TRAMPOLINES_H
#define HPY_CPYTHON_HPYFUNC_TRAMPOLINES_H

#define _HPyFunc_TRAMPOLINE_HPyFunc_NOARGS(SYM, IMPL)                   \
    static PyObject *                                                   \
    SYM(PyObject *self, PyObject *noargs)                               \
    {                                                                   \
        return _h2py(IMPL(_HPyGetContext(), _py2h(self)));              \
    }

#define _HPyFunc_TRAMPOLINE_HPyFunc_O(SYM, IMPL)                        \
    static PyObject *                                                   \
    SYM(PyObject *self, PyObject *arg)                                  \
    {                                                                   \
        return _h2py(IMPL(_HPyGetContext(), _py2h(self), _py2h(arg)));  \
    }

#define _HPyFunc_TRAMPOLINE_HPyFunc_VARARGS(SYM, IMPL)                  \
    static PyObject*                                                    \
    SYM(PyObject *self, PyObject *args)                                 \
    {                                                                   \
        /* get the tuple elements as an array of "PyObject *", which */ \
        /* is equivalent to an array of "HPy" with enough casting... */ \
        HPy *items = (HPy *)&PyTuple_GET_ITEM(args, 0);                 \
        Py_ssize_t nargs = PyTuple_GET_SIZE(args);                      \
        return _h2py(IMPL(_HPyGetContext(),                             \
                                 _py2h(self), items, nargs));           \
    }

#define _HPyFunc_TRAMPOLINE_HPyFunc_KEYWORDS(SYM, IMPL)                 \
    static PyObject *                                                   \
    SYM(PyObject *self, PyObject *args, PyObject *kw)                   \
    {                                                                   \
        /* get the tuple elements as an array of "PyObject *", which */ \
        /* is equivalent to an array of "HPy" with enough casting... */ \
        HPy *items = (HPy *)&PyTuple_GET_ITEM(args, 0);                 \
        Py_ssize_t nargs = PyTuple_GET_SIZE(args);                      \
        return _h2py(IMPL(_HPyGetContext(), _py2h(self),                \
                                 items, nargs, _py2h(kw)));             \
    }

#define _HPyFunc_TRAMPOLINE_HPyFunc_INITPROC(SYM, IMPL)                 \
    static int                                                          \
    SYM(PyObject *self, PyObject *args, PyObject *kw)                   \
    {                                                                   \
        /* get the tuple elements as an array of "PyObject *", which */ \
        /* is equivalent to an array of "HPy" with enough casting... */ \
        HPy *items = (HPy *)&PyTuple_GET_ITEM(args, 0);                 \
        Py_ssize_t nargs = PyTuple_GET_SIZE(args);                      \
        return IMPL(_HPyGetContext(), _py2h(self),                      \
                    items, nargs, _py2h(kw));                           \
    }

#define _HPyFunc_TRAMPOLINE_HPyFunc_DESTROYFUNC(SYM, IMPL)              \
    static void                                                         \
    SYM(PyObject *self)                                                 \
    {                                                                   \
        void *data = (void *) self;                                     \
        if (self->ob_type->tp_flags & HPy_TPFLAGS_INTERNAL_PURE) {      \
            data = ((char *) data) + HPyPure_PyObject_HEAD_SIZE;        \
        }                                                               \
        IMPL(data);                                                     \
        Py_TYPE(self)->tp_free(self);                                   \
    }

/* this needs to be written manually because HPy has a different type for
   "op": HPy_RichCmpOp instead of int */
#define _HPyFunc_TRAMPOLINE_HPyFunc_RICHCMPFUNC(SYM, IMPL)                 \
    static cpy_PyObject *                                                  \
    SYM(PyObject *self, PyObject *obj, int op)                             \
    {                                                                      \
        return _h2py(IMPL(_HPyGetContext(), _py2h(self), _py2h(obj), op)); \
    }

/* With the cpython ABI, Py_buffer and HPy_buffer are ABI-compatible.
 * Even though casting between them is technically undefined behavior, it
 * should always work. That way, we avoid a costly allocation and copy. */
#define _HPyFunc_TRAMPOLINE_HPyFunc_GETBUFFERPROC(SYM, IMPL) \
    static int SYM(PyObject *arg0, Py_buffer *arg1, int arg2) \
    { \
        return (IMPL(_HPyGetContext(), _py2h(arg0), (HPy_buffer*)arg1, arg2)); \
    }
#define _HPyFunc_TRAMPOLINE_HPyFunc_RELEASEBUFFERPROC(SYM, IMPL) \
    static void SYM(PyObject *arg0, Py_buffer *arg1) \
    { \
        IMPL(_HPyGetContext(), _py2h(arg0), (HPy_buffer*)arg1); \
        return; \
    }

#endif // HPY_CPYTHON_HPYFUNC_TRAMPOLINES_H
