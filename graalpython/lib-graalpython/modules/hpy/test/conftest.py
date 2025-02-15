# MIT License
# 
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Copyright (c) 2019 pyhandle
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
import sys
import pytest
from .support import ExtensionCompiler, DefaultExtensionTemplate
from hpy.debug.leakdetector import LeakDetector

GRAALPYTHON_NATIVE = sys.implementation.name == 'graalpython' and __graalpython__.platform_id == 'native'

def pytest_addoption(parser):
    parser.addoption(
        "--compiler-v", action="store_true",
        help="Print to stdout the commands used to invoke the compiler")

@pytest.fixture(scope='session')
def hpy_devel(request):
    from hpy.devel import HPyDevel
    return HPyDevel()

@pytest.fixture(params=['cpython', 'universal', 'debug', 'nfi'] if GRAALPYTHON_NATIVE else ['cpython', 'universal', 'debug'])
def hpy_abi(request):
    abi = request.param
    if abi == 'debug':
        with LeakDetector():
            yield abi
    else:
        yield abi

@pytest.fixture
def ExtensionTemplate():
    return DefaultExtensionTemplate


@pytest.fixture
def compiler(request, tmpdir, hpy_devel, hpy_abi, ExtensionTemplate):
    compiler_verbose = request.config.getoption('--compiler-v')
    return ExtensionCompiler(tmpdir, hpy_devel, hpy_abi,
                             compiler_verbose=compiler_verbose,
                             ExtensionTemplate=ExtensionTemplate)

@pytest.fixture()
def skip_nfi(hpy_abi):
    # skip all tests in this class for NFI mode
    if hpy_abi == 'nfi':
        pytest.skip()
