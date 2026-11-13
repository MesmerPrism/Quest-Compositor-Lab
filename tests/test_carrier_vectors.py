"""Interoperability vectors, not another mutable implementation of the supplier."""
import json
from pathlib import Path
import unittest


def crc_reference(data):
    # Polynomial long division, deliberately distinct from the supplier loop.
    value = int.from_bytes(data, 'big') << 8
    for bit in range(value.bit_length() - 1, 7, -1):
        if value & (1 << bit):
            value ^= 0x107 << (bit - 8)
    return value


class CarrierVectors(unittest.TestCase):
    def test_known_crc_check_value(self):
        self.assertEqual(0xf4, crc_reference(b'123456789'))

    def test_frozen_fields_quantization_crc_and_bit_order(self):
        vectors = json.loads((Path(__file__).parent / 'fixtures/carrier-v1.json').read_text())
        self.assertEqual(4, len(vectors['vectors']))
        for row in vectors['vectors']:
            with self.subTest(name=row['name']):
                packet = bytes.fromhex(row['hex'])
                self.assertEqual(12, len(packet))
                self.assertEqual(bytes.fromhex('b65a'), packet[:2])
                self.assertEqual(0x11 if row['valid'] else 0x10, packet[2])
                self.assertEqual(row['draw_sequence'], packet[3])
                self.assertEqual(row['level_bytes'], list(packet[4:8]))
                self.assertEqual(row['phase_bytes'], list(packet[8:10]))
                self.assertEqual(42, packet[10])
                self.assertEqual(crc_reference(packet[:-1]), packet[-1])
                bits = ''.join(format(byte, '08b') for byte in packet)
                self.assertEqual(96, len(bits))
                self.assertEqual('1011011001011010', bits[:16])
                # Each single payload bit error must be detectable by the CRC.
                for bit in range(88):
                    corrupt = bytearray(packet)
                    corrupt[bit // 8] ^= 1 << (7 - bit % 8)
                    self.assertNotEqual(crc_reference(corrupt[:-1]), corrupt[-1])

    def test_draw_sequence_wrap_is_not_source_freshness(self):
        vectors = json.loads((Path(__file__).parent / 'fixtures/carrier-v1.json').read_text())['vectors']
        packets = [bytes.fromhex(row['hex']) for row in vectors[:2]]
        self.assertEqual([255, 0], [packet[3] for packet in packets])
        self.assertEqual(packets[0][4:11], packets[1][4:11])


if __name__ == '__main__':
    unittest.main()
