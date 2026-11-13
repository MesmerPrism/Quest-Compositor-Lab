"""Independent CPU reference for the laboratory's authored generic equations.

RGB config names abbreviate the public renderer-neutral contract. Sampling is
bilinear in eye-local coordinates; alpha remains the undisplaced source alpha.
This is a numerical reference, not evidence that a shader was executed.
"""
import math
import numpy as np

BLUR_WEIGHTS = (1/16, 4/16, 6/16, 4/16, 1/16)


def normalize(config=None):
    config = config or {}
    result = {"mode": config.get("mode", 0), "edge": config.get("edge", 0)}
    if result["mode"] not in (0, 1, 2):
        result["mode"] = 0
    if result["edge"] not in (0, 1, 2):
        result["edge"] = 0
    for key, default, low, high in (("direction", 0., None, None), ("rate", 0., -2., 2.),
                                  ("strength", 0., 0., .08), ("scale", 1., .5, 2.),
                                  ("coverage", 1., .5, 1.)):
        values = config.get(key, [default]*3)
        if len(values) != 3:
            raise ValueError("channel vectors require three values")
        normalized = []
        for value in values:
            value = float(value)
            if not math.isfinite(value):
                value = default
            normalized.append(value % 1. if key == "direction" else min(high, max(low, value)))
        result[key] = [normalized[0]]*3 if result["mode"] == 2 else normalized
    time = float(config.get("time", 0.))
    result["time"] = time if math.isfinite(time) else 0.
    return result


def _sample(image, uv, eye):
    height, width, _ = image.shape
    half = width // 2
    x = np.clip(uv[..., 0]*half - .5, 0, half-1)
    y = np.clip(uv[..., 1]*height - .5, 0, height-1)
    x0, y0 = np.floor(x).astype(int), np.floor(y).astype(int)
    x1, y1 = np.minimum(x0+1, half-1), np.minimum(y0+1, height-1)
    dx, dy = (x-x0)[..., None], (y-y0)[..., None]
    x0, x1 = x0 + eye*half, x1 + eye*half
    return ((image[y0, x0]*(1-dx)+image[y0, x1]*dx)*(1-dy)
            +(image[y1, x0]*(1-dx)+image[y1, x1]*dx)*dy)


def render(image, effect, config=None):
    image = np.asarray(image, dtype=np.float32)
    height, width, channels = image.shape
    if width % 2 or channels != 4 or width < 4 or height < 1:
        raise ValueError("requires RGBA packed stereo with two nonempty eyes")
    config = normalize(config)
    half = width//2
    x, y = np.meshgrid((np.arange(half)+.5)/half, (np.arange(height)+.5)/height)
    uv = np.stack((x, y), axis=-1)
    result = np.empty_like(image)
    for eye in (0, 1):
        base = _sample(image, uv, eye)
        if effect == "identity":
            out = base
        elif effect in ("blur-horizontal", "blur-vertical"):
            direction = np.array([1/half, 0] if effect.endswith("horizontal") else [0, 1/height])
            out = sum(weight*_sample(image, uv + (tap-2)*direction, eye)
                      for tap, weight in enumerate(BLUR_WEIGHTS))
        elif effect == "clean-rgb":
            out = base.copy()
            if config["mode"]:
                for channel in range(3):
                    angle = math.tau*((config["direction"][channel]
                                      + config["time"]*config["rate"][channel]) % 1.)
                    offset = config["strength"][channel]*np.array([math.cos(angle), math.sin(angle)])
                    moved = (uv-.5)/config["scale"][channel]+.5+offset
                    coverage = config["coverage"][channel]
                    active = np.max(np.abs(uv-.5), axis=-1) <= .5*coverage
                    if config["edge"] == 1:
                        moved = 1.-np.abs(np.mod(moved, 2.)-1.)
                    sampled = _sample(image, moved, eye)[..., channel]
                    if config["edge"] == 2:
                        weight = np.clip(1+np.minimum(moved, 1-moved)*[half, height], 0, 1)
                        sampled = sampled * weight[..., 0] * weight[..., 1]
                    out[..., channel] = np.where(active, sampled, base[..., channel])
        else:
            raise ValueError("unknown example")
        result[:, eye*half:(eye+1)*half] = out
    return result
