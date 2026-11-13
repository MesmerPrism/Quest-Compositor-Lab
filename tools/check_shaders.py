"""Compile sanitized ES310 fixtures, link/render on a Windows desktop GL driver.

Compiler is required via --compiler or GLSL_COMPILER; absence is an error.
Output is a local qualification receipt, never native/Quest qualification.
Host rendering requires numpy and PyOpenGL plus an ES310-capable GL driver.
"""
import argparse
from contextlib import contextmanager
import ctypes
import json
import math
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
EFFECTS = ("identity", "blur-horizontal", "blur-vertical", "clean-rgb")


def assemble(stage, body, defines=""):
    prefix = (ROOT / "tests/fixtures" / (stage + "-prefix.glsl")).read_text()
    return prefix + "\n" + defines + "\n" + (ROOT / "examples" / body).read_text()


def compile_external(compiler):
    if not compiler or not Path(compiler).is_file():
        raise ValueError("configure an existing compiler with --compiler or GLSL_COMPILER")
    name = Path(compiler).stem.lower()
    if name not in ("glslc", "glslangvalidator"):
        raise ValueError("supported compiler names: glslc or glslangValidator")
    records = []
    with tempfile.TemporaryDirectory(prefix="neutral-shaders-") as directory:
        temporary = Path(directory)
        vertex = temporary / "fullscreen.vert"
        vertex.write_text(assemble("vertex", "fullscreen.vert.glsl"))
        for effect in EFFECTS:
            fragment = temporary / (effect + ".frag")
            fragment.write_text(assemble("fragment", effect + ".frag.glsl"))
            if name == "glslangvalidator":
                commands = [[compiler, "-l", str(vertex), str(fragment)]]
            else:
                commands = [[compiler, "--target-env=opengl", "-fauto-map-locations",
                             "-fauto-bind-uniforms", str(source), "-o", str(temporary / "out.spv")]
                            for source in (vertex, fragment)]
            for command in commands:
                result = subprocess.run(command, capture_output=True, text=True, check=False)
                if result.returncode:
                    raise RuntimeError(result.stdout + result.stderr)
            records.append(effect)
    version = subprocess.run([compiler, "--version"], capture_output=True, text=True, check=True)
    return {"tool": name, "version": version.stdout.strip(), "compiled": records,
            "external_link": name == "glslangvalidator"}


@contextmanager
def host_context():
    """Create a hidden, disposable WGL surface; no shared service or device."""
    if os.name != "nt":
        raise RuntimeError("host renderer currently supports Windows WGL only")
    from ctypes import wintypes as wt
    user = ctypes.WinDLL("user32", use_last_error=True)
    gdi = ctypes.WinDLL("gdi32", use_last_error=True)
    wgl = ctypes.WinDLL("opengl32", use_last_error=True)

    class PixelFormat(ctypes.Structure):
        _fields_ = [("nSize", wt.WORD), ("nVersion", wt.WORD), ("dwFlags", wt.DWORD),
                    ("iPixelType", ctypes.c_byte), ("cColorBits", ctypes.c_byte),
                    ("cRedBits", ctypes.c_byte), ("cRedShift", ctypes.c_byte),
                    ("cGreenBits", ctypes.c_byte), ("cGreenShift", ctypes.c_byte),
                    ("cBlueBits", ctypes.c_byte), ("cBlueShift", ctypes.c_byte),
                    ("cAlphaBits", ctypes.c_byte), ("cAlphaShift", ctypes.c_byte),
                    ("cAccumBits", ctypes.c_byte), ("cAccumRedBits", ctypes.c_byte),
                    ("cAccumGreenBits", ctypes.c_byte), ("cAccumBlueBits", ctypes.c_byte),
                    ("cAccumAlphaBits", ctypes.c_byte), ("cDepthBits", ctypes.c_byte),
                    ("cStencilBits", ctypes.c_byte), ("cAuxBuffers", ctypes.c_byte),
                    ("iLayerType", ctypes.c_byte), ("bReserved", ctypes.c_byte),
                    ("dwLayerMask", wt.DWORD), ("dwVisibleMask", wt.DWORD),
                    ("dwDamageMask", wt.DWORD)]

    user.CreateWindowExW.restype = wt.HWND
    user.CreateWindowExW.argtypes = [wt.DWORD, wt.LPCWSTR, wt.LPCWSTR, wt.DWORD,
                                   ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int,
                                   wt.HWND, wt.HMENU, wt.HINSTANCE, wt.LPVOID]
    user.GetDC.restype = wt.HDC
    user.GetDC.argtypes = [wt.HWND]
    user.ReleaseDC.argtypes = [wt.HWND, wt.HDC]
    user.DestroyWindow.argtypes = [wt.HWND]
    gdi.ChoosePixelFormat.argtypes = [wt.HDC, ctypes.POINTER(PixelFormat)]
    gdi.SetPixelFormat.argtypes = [wt.HDC, ctypes.c_int, ctypes.POINTER(PixelFormat)]
    wgl.wglCreateContext.restype = wt.HANDLE
    wgl.wglCreateContext.argtypes = [wt.HDC]
    wgl.wglMakeCurrent.argtypes = [wt.HDC, wt.HANDLE]
    wgl.wglDeleteContext.argtypes = [wt.HANDLE]
    window = user.CreateWindowExW(0, "STATIC", "Neutral shader fixture", 0,
                                 0, 0, 32, 32, None, None, None, None)
    if not window:
        raise ctypes.WinError(ctypes.get_last_error())
    dc = user.GetDC(window)
    context = None
    try:
        descriptor = PixelFormat()
        descriptor.nSize = ctypes.sizeof(PixelFormat)
        descriptor.nVersion = 1
        descriptor.dwFlags = 0x04 | 0x20  # DRAW_TO_WINDOW | SUPPORT_OPENGL
        descriptor.cColorBits = 32
        descriptor.cAlphaBits = 8
        pixel_format = gdi.ChoosePixelFormat(dc, ctypes.byref(descriptor))
        if not pixel_format or not gdi.SetPixelFormat(dc, pixel_format, ctypes.byref(descriptor)):
            raise RuntimeError("cannot establish WGL pixel format")
        context = wgl.wglCreateContext(dc)
        if not context or not wgl.wglMakeCurrent(dc, context):
            raise RuntimeError("cannot establish WGL context")
        yield
    finally:
        wgl.wglMakeCurrent(None, None)
        if context:
            wgl.wglDeleteContext(context)
        user.ReleaseDC(window, dc)
        user.DestroyWindow(window)


def config_defines(config):
    mapping = {"mode": "MODE", "edge": "EDGE", "direction": "DIRECTION", "rate": "RATE",
               "strength": "STRENGTH", "scale": "SCALE", "coverage": "COVERAGE",
               "time": "TIME_SECONDS"}
    lines = []
    def scalar(value):
        value = float(value)
        if math.isnan(value):
            return "uintBitsToFloat(0x7fc00000u)"
        if math.isinf(value):
            return ("-" if value < 0 else "") + "uintBitsToFloat(0x7f800000u)"
        return str(value)
    for key, value in config.items():
        if isinstance(value, list):
            literal = "vec3(" + ",".join(scalar(v) for v in value) + ")"
        elif key in ("mode", "edge"):
            literal = str(int(value))
        else:
            literal = scalar(value)
        lines.append("#define LAB_RGB_" + mapping[key] + " " + literal)
    return "\n".join(lines)


def host_validate():
    import numpy as np
    from OpenGL import GL as gl
    # Permit both `python tools/check_shaders.py` and import by unittest.
    try:
        from tools import shader_reference as ref
    except ModuleNotFoundError:
        import shader_reference as ref

    fixture = json.loads((ROOT / "tests/fixtures/stereo-synthetic.json").read_text())
    height, width = fixture["height"], fixture["width"]
    stereo = np.empty((height, width, 4), dtype=np.float32)
    stereo[:, :width//2] = fixture["left_rgba"]
    stereo[:, width//2:] = fixture["right_rgba"]
    random = np.random.default_rng(fixture["random_seed"]).random(stereo.shape, dtype=np.float32)
    images = {"asymmetric": stereo, "random": random,
              "black": np.zeros_like(stereo), "white": np.ones_like(stereo)}
    variants = [(effect, {}, label, image) for effect in EFFECTS for label, image in images.items()]
    # Compile the unmodified body separately. The oracle is specified here
    # independently of the shader's preprocessor defaults.
    variants.extend(("clean-rgb", None, "body-default-" + label, image)
                    for label, image in images.items())
    for mode in (0, 1, 2):
        for edge in range(3):
            variants.append(("clean-rgb", {"mode": mode, "edge": edge}, "zero-rgb", random))
    # Exercise normalization and every edge policy with non-identity settings.
    for mode in (0, 1, 2, 7):
        for edge in (0, 1, 2, 7):
            config = {"mode": mode, "edge": edge, "direction": [-1.25, 0.5, 2.25],
                      "rate": [-8, 8, 0.2], "strength": [0.08, 1, -1],
                      "scale": [0, 8, 0.75], "coverage": [0, 8, 0.8], "time": 0.125}
            variants.append(("clean-rgb", config, "normalized-random", random))
            variants.append(("clean-rgb", config, "normalized-asymmetric", stereo))
    nonfinite = [float("nan"), float("inf"), -float("inf")]
    variants.append(("clean-rgb", {"mode": 1, "direction": nonfinite, "rate": nonfinite,
                                   "strength": nonfinite, "scale": nonfinite,
                                   "coverage": nonfinite, "time": float("nan")},
                     "nonfinite-controls", random))
    records = []
    with host_context():
        info = {key: gl.glGetString(value).decode("ascii", "replace") for key, value in
                (("vendor", gl.GL_VENDOR), ("renderer", gl.GL_RENDERER),
                 ("version", gl.GL_VERSION), ("glsl", gl.GL_SHADING_LANGUAGE_VERSION))}
        vao = gl.glGenVertexArrays(1)
        gl.glBindVertexArray(vao)
        textures = gl.glGenTextures(2)
        framebuffer = gl.glGenFramebuffers(1)
        shaders, programs = [], []
        try:
            for texture in textures:
                gl.glBindTexture(gl.GL_TEXTURE_2D, texture)
                for pname in (gl.GL_TEXTURE_MIN_FILTER, gl.GL_TEXTURE_MAG_FILTER):
                    gl.glTexParameteri(gl.GL_TEXTURE_2D, pname, gl.GL_LINEAR)
                for pname in (gl.GL_TEXTURE_WRAP_S, gl.GL_TEXTURE_WRAP_T):
                    gl.glTexParameteri(gl.GL_TEXTURE_2D, pname, gl.GL_CLAMP_TO_EDGE)
                gl.glTexImage2D(gl.GL_TEXTURE_2D, 0, gl.GL_RGBA32F, width, height, 0,
                                gl.GL_RGBA, gl.GL_FLOAT, None)
            gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, framebuffer)
            gl.glFramebufferTexture2D(gl.GL_FRAMEBUFFER, gl.GL_COLOR_ATTACHMENT0,
                                      gl.GL_TEXTURE_2D, textures[1], 0)
            if gl.glCheckFramebufferStatus(gl.GL_FRAMEBUFFER) != gl.GL_FRAMEBUFFER_COMPLETE:
                raise RuntimeError("float fixture framebuffer incomplete")
            gl.glDisable(gl.GL_BLEND)
            gl.glDisable(gl.GL_DITHER)
            vertex_source = assemble("vertex", "fullscreen.vert.glsl")
            cache = {}
            for effect, config, label, image in variants:
                # Explicit controls keep zero-strength tests independent of the
                # body's useful default sample. None selects the unmodified body.
                reference_config = ({"mode": 1, "direction": [0.0, 0.0, 0.5],
                                     "strength": [0.01, 0.0, 0.01]}
                                    if config is None else config)
                shader_config = ref.normalize({})
                shader_config.update(config or {})
                defines = "" if config is None else config_defines(shader_config)
                key = (effect, defines)
                if key not in cache:
                    program = gl.glCreateProgram()
                    programs.append(program)
                    for stage, source in ((gl.GL_VERTEX_SHADER, vertex_source),
                                          (gl.GL_FRAGMENT_SHADER,
                                           assemble("fragment", effect + ".frag.glsl", defines))):
                        shader = gl.glCreateShader(stage)
                        shaders.append(shader)
                        gl.glShaderSource(shader, source)
                        gl.glCompileShader(shader)
                        if not gl.glGetShaderiv(shader, gl.GL_COMPILE_STATUS):
                            raise RuntimeError(gl.glGetShaderInfoLog(shader).decode())
                        gl.glAttachShader(program, shader)
                    gl.glLinkProgram(program)
                    if not gl.glGetProgramiv(program, gl.GL_LINK_STATUS):
                        raise RuntimeError(gl.glGetProgramInfoLog(program).decode())
                    cache[key] = program
                program = cache[key]
                gl.glUseProgram(program)
                gl.glActiveTexture(gl.GL_TEXTURE0)
                gl.glBindTexture(gl.GL_TEXTURE_2D, textures[0])
                gl.glTexSubImage2D(gl.GL_TEXTURE_2D, 0, 0, 0, width, height,
                                  gl.GL_RGBA, gl.GL_FLOAT, np.ascontiguousarray(image))
                gl.glUniform1i(gl.glGetUniformLocation(program, "tex"), 0)
                gl.glUniform1i(gl.glGetUniformLocation(program, "frameId"), 0)
                gl.glUniform4f(gl.glGetUniformLocation(program, "screenOrientationAdjustment"), 1, 0, 0, 1)
                for eye in (0, 1):
                    gl.glViewport(eye*width//2, 0, width//2, height)
                    gl.glUniform1f(gl.glGetUniformLocation(program, "eye"), eye)
                    gl.glDrawArrays(gl.GL_TRIANGLES, 0, 3)
                actual = np.empty_like(image)
                gl.glReadPixels(0, 0, width, height, gl.GL_RGBA, gl.GL_FLOAT, actual)
                expected = ref.render(image, effect, reference_config)
                error = float(np.max(np.abs(actual - expected)))
                if not np.isfinite(actual).all() or error > fixture["host_float_absolute_tolerance"]:
                    raise AssertionError(f"{effect}/{label}/{config}: max absolute error {error}")
                if actual.min() < -1e-6 or actual.max() > 1+1e-6:
                    raise AssertionError("normalized fixture escaped finite RGBA range")
                records.append({"effect": effect, "case": label, "config": config,
                                "expected_reference_config": reference_config,
                                "max_absolute_error": error})
        finally:
            gl.glUseProgram(0)
            for program in programs:
                gl.glDeleteProgram(program)
            for shader in shaders:
                gl.glDeleteShader(shader)
            gl.glDeleteFramebuffers(1, [framebuffer])
            gl.glDeleteTextures(list(textures))
            gl.glDeleteVertexArrays(1, [vao])
    return {"driver": info, "linked_programs": len(cache), "cases": records,
            "scope": "desktop GL ES310 fixture only; native/Quest/Adreno unqualified"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compiler", default=os.environ.get("GLSL_COMPILER"))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        if args.output and args.output.exists():
            raise ValueError("receipt output already exists")
        result = {"compiler": compile_external(args.compiler), "host": host_validate()}
        if args.output:
            output = args.output.resolve()
            if not output.is_relative_to(ROOT / "local"):
                raise ValueError("receipt output must be under ignored local/")
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(json.dumps(result, indent=2) + "\n")
        print(json.dumps({"compiled": result["compiler"]["compiled"],
                          "host_driver": result["host"]["driver"],
                          "linked_programs": result["host"]["linked_programs"],
                          "rendered_cases": len(result["host"]["cases"])}, indent=2))
        return 0
    except (ValueError, RuntimeError, AssertionError, OSError, ImportError) as error:
        print(str(error))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
