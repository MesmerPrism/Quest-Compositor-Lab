"""Independently authored synthetic checks; no native/runtime qualification."""
import json
import math
from pathlib import Path
import unittest

import numpy as np

from tools import shader_reference as ref
from tools import check_shaders

ROOT = Path(__file__).resolve().parents[1]


class ShaderNumerics(unittest.TestCase):
    def setUp(self):
        self.image = np.empty((8, 24, 4), dtype=np.float32)
        self.image[:, :12] = (0.1, 0.2, 0.3, 0.4)
        self.image[:, 12:] = (0.9, 0.8, 0.7, 0.6)

    def test_identity_and_zero_rgb(self):
        rng = np.random.default_rng(431)
        image = rng.random((8, 24, 4), dtype=np.float32)
        np.testing.assert_allclose(ref.render(image, "identity"), image, atol=1e-6)
        for mode in (0, 1, 2):
            for edge in range(3):
                np.testing.assert_allclose(ref.render(image, "clean-rgb", {"mode": mode, "edge": edge}),
                                           image, atol=1e-6)

    def test_weights_normalized_constant_and_alpha(self):
        self.assertAlmostEqual(sum(ref.BLUR_WEIGHTS), 1.0)
        for effect in ("blur-horizontal", "blur-vertical"):
            np.testing.assert_allclose(ref.render(self.image, effect), self.image, atol=1e-6)

    def test_asymmetric_seam_and_boundaries(self):
        for edge in range(3):
            config = {"mode": 1, "edge": edge, "strength": [0.08]*3,
                      "direction": [0, 0.5, 0.25], "scale": [0.5]*3}
            result = ref.render(self.image, "clean-rgb", config)
            self.assertTrue(np.isfinite(result).all())
            self.assertGreaterEqual(result.min(), 0)
            self.assertLessEqual(result.max(), 1)
            np.testing.assert_allclose(result[..., 3], self.image[..., 3], atol=1e-6)
            self.assertLessEqual(result[:, :12, :3].max(), 0.3 + 1e-6)
        for value in (0.0, 1.0):
            image = np.full((8, 24, 4), value, dtype=np.float32)
            for effect in ("identity", "blur-horizontal", "blur-vertical", "clean-rgb"):
                np.testing.assert_allclose(ref.render(image, effect), image, atol=1e-6)

    def test_public_normalization_and_linked(self):
        config = ref.normalize({"mode": 2, "edge": 8, "direction": [-1.25, 2, 3],
                                "rate": [-8, 8, 0], "strength": [-1, 1, 0],
                                "scale": [0, 8, 1], "coverage": [0, 2, 1]})
        self.assertEqual(config["edge"], 0)
        self.assertEqual(config["direction"], [0.75]*3)
        self.assertEqual(config["rate"], [-2.0]*3)
        self.assertEqual(config["strength"], [0.0]*3)
        self.assertEqual(config["scale"], [0.5]*3)
        self.assertEqual(config["coverage"], [0.5]*3)
        self.assertEqual(ref.normalize({"mode": 7})["mode"], 0)
        for invalid in (float("nan"), float("inf")):
            normalized = ref.normalize({"direction": [invalid]*3})
            self.assertTrue(all(math.isfinite(x) for x in normalized["direction"]))

    def test_body_and_fixture_contract(self):
        for name in ("fullscreen.vert", "identity.frag", "blur-horizontal.frag",
                     "blur-vertical.frag", "clean-rgb.frag"):
            body = (ROOT / "examples" / (name + ".glsl")).read_text()
            self.assertNotIn("#version", body)
            self.assertNotIn("uniform ", body)
        fixture = json.loads((ROOT / "tests/fixtures/stereo-synthetic.json").read_text())
        self.assertEqual(fixture["source"], "independently-authored-synthetic")

    def test_missing_compiler_is_failure(self):
        with self.assertRaisesRegex(ValueError, "configure"):
            check_shaders.compile_external(None)

    def test_actual_es310_compile_link_and_render(self):
        # Hard failure on unsupported/missing host driver; never a silent skip.
        result = check_shaders.host_validate()
        self.assertGreaterEqual(result["linked_programs"], 20)
        self.assertGreaterEqual(len(result["cases"]), 48)
        default_random = [case for case in result["cases"] if case["case"] == "body-default-random"]
        self.assertEqual(len(default_random), 1)
        self.assertIsNone(default_random[0]["config"])
        self.assertEqual(default_random[0]["expected_reference_config"],
                         {"mode": 1, "direction": [0.0, 0.0, 0.5], "strength": [0.01, 0.0, 0.01]})


if __name__ == "__main__":
    unittest.main()
