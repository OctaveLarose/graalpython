# Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
from builtins import BaseException


# Stub audit hooks implementation for PEP 578
def audit(str, *args):
    pass

def addaudithook(hook):
    pass


# CPython builds for distros report empty strings too, because they are built from tarballs, not git
_git = ("graalpython", '', '')


@__graalpython__.builtin
def exit(arg=None):
    # see SystemExit_init, tuple of size 1 is unpacked
    code = arg
    if isinstance(arg, tuple) and len(arg) == 1:
        code = arg[0]
    raise SystemExit(code)


def make_excepthook():
    def simple_print_traceback(e):
        print("Traceback (most recent call last):", file=stderr);
        tb = e.__traceback__
        while tb is not None:
            print('  File "%s", line %d, in %s' % (
                tb.tb_frame.f_code.co_filename,
                tb.tb_lineno,
                tb.tb_frame.f_code.co_name
            ), file=stderr)
            tb = tb.tb_next
        msg = str(e)
        if msg:
            print("%s: %s" % (type(e).__qualname__, msg), file=stderr)
        else:
            print(type(e).__qualname__, file=stderr)

    def __print_traceback__(typ, value, tb):
        if not isinstance(value, BaseException):
            msg = "TypeError: print_exception(): Exception expected for value, {} found\n".format(type(value).__name__)
            print(msg, file=stderr, end="")
            return
        try:
            # CPython's C traceback printer diverges from traceback.print_exception in some details marked as (*)
            import sys
            import traceback
            no_traceback = False
            limit = getattr(sys, 'tracebacklimit', None)
            if isinstance(limit, int):
                if limit <= 0:
                    # (*) the C traceback printer does nothing if the limit is <= 0,
                    # but the exception message is still printed
                    limit = 0
                    no_traceback = True
                else:
                    # (*) CPython convert 'limit' to C long and if it overflows, it uses 'LONG_MAX'; we use Java int
                    if limit > sys.maxsize:
                        limit = sys.maxsize
                    # (*) in the C printer limit is interpreted as -limit in format_exception
                    limit = -limit
            else:
                # (*) non integer values of limit are interpreted as the default limit
                limit = None
            lines = traceback.format_exception(typ, value, tb, limit=limit)
            # (*) if the exception cannot be printed, then the message differs between the C driver and format_exception
            # We'd like to contribute to CPython to fix the divergence, but for now we do just a string substitution
            # to pass the tests
            lines[-1] = lines[-1].replace(f'<unprintable {typ.__name__} object>', f'<exception str() failed>')
            if no_traceback:
                lines = lines[-1:]
            for line in lines:
                print(line, file=stderr, end="")
        except BaseException as exc:
            print("Error in sys.excepthook:\n", file=stderr)
            simple_print_traceback(exc)
            print("\nOriginal exception was:\n", file=stderr)
            simple_print_traceback(value)

    return __print_traceback__


__excepthook__ = make_excepthook()
excepthook = __excepthook__
del make_excepthook


def make_unraisablehook():
    def __unraisablehook__(unraisable, /):
        try:
            if unraisable.object:
                try:
                    r = repr(unraisable.object)
                except Exception:
                    r = "<object repr() failed>"
                if unraisable.err_msg:
                    print(f"{unraisable.err_msg}: {r}", file=stderr)
                else:
                    print(f"Exception ignored in: {r}", file=stderr)
            elif unraisable.err_msg:
                print(f"{unraisable.err_msg}:", file=stderr)
        except BaseException:
            # let it fall through to the exception printer
            pass
        __excepthook__(unraisable.exc_type, unraisable.exc_value, unraisable.exc_traceback)

    return __unraisablehook__


__unraisablehook__ = make_unraisablehook()
unraisablehook = __unraisablehook__
del make_unraisablehook


@__graalpython__.builtin
def breakpointhook(*args, **kws):
    import importlib, os, warnings
    hookname = os.getenv('PYTHONBREAKPOINT')
    if hookname is None or len(hookname) == 0:
        warnings.warn('Graal Python cannot run pdb, yet, consider using `--inspect` on the commandline', RuntimeWarning)
        hookname = 'pdb.set_trace'
    elif hookname == '0':
        return None
    modname, dot, funcname = hookname.rpartition('.')
    if dot == '':
        modname = 'builtins'
    try:
        module = importlib.import_module(modname)
        hook = getattr(module, funcname)
    except:
        warnings.warn(
            'Ignoring unimportable $PYTHONBREAKPOINT: "{}"'.format(
                hookname),
            RuntimeWarning)
    else:
        return hook(*args, **kws)


__breakpointhook__ = breakpointhook

@__graalpython__.builtin
def getrecursionlimit():
    return __graalpython__.sys_state.recursionlimit

@__graalpython__.builtin
def setrecursionlimit(value):
    if not isinstance(value, int):
        raise TypeError("an integer is required")
    if value <= 0:
        raise ValueError("recursion limit must be greater or equal than 1")
    __graalpython__.sys_state.recursionlimit = value

@__graalpython__.builtin
def getcheckinterval():
    return __graalpython__.sys_state.checkinterval

@__graalpython__.builtin
def setcheckinterval(value):
    import warnings
    warnings.warn("sys.getcheckinterval() and sys.setcheckinterval() are deprecated. Use sys.setswitchinterval() instead.", DeprecationWarning)
    if not isinstance(value, int):
        raise TypeError("an integer is required")
    __graalpython__.sys_state.checkinterval = value

@__graalpython__.builtin
def getswitchinterval():
    return __graalpython__.sys_state.switchinterval

@__graalpython__.builtin
def setswitchinterval(value):
    if not isinstance(value, (int, float)):
        raise TypeError("must be real number, not str")
    if value <= 0:
        raise ValueError("switch interval must be strictly positive")
    __graalpython__.sys_state.switchinterval = value

@__graalpython__.builtin
def displayhook(value):
    if value is None:
        return
    builtins = modules['builtins']
    # Set '_' to None to avoid recursion
    builtins._ = None
    text = repr(value)
    try:
        local_stdout = stdout
    except NameError as e:
        raise RuntimeError("lost sys.stdout") from e
    try:
        local_stdout.write(text)
    except UnicodeEncodeError:
        bytes = text.encode(local_stdout.encoding, 'backslashreplace')
        if hasattr(local_stdout, 'buffer'):
            local_stdout.buffer.write(bytes)
        else:
            text = bytes.decode(local_stdout.encoding, 'strict')
            local_stdout.write(text)
    local_stdout.write("\n")
    builtins._ = value


__displayhook__ = displayhook
