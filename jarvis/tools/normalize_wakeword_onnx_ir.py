"""Make the trained wake-word model loadable by the Android ONNX Runtime.

The Android runtime used by the app supports ONNX IR 10.  The classifier was
exported with IR 13, while its operators remain compatible with the runtime.
ONNX ModelProto stores ir_version as the first protobuf varint, so lowering
that field is sufficient and avoids re-exporting the trained weights.
"""

from pathlib import Path


MODEL = Path(__file__).resolve().parents[1] / "android/app/src/main/assets/wakeword/hey_jarvis.onnx"
SUPPORTED_IR = 10


def main() -> None:
    data = bytearray(MODEL.read_bytes())
    if data[:1] != b"\x08":
        raise RuntimeError("unexpected ONNX ModelProto header")
    old = data[1]
    if old < SUPPORTED_IR:
        raise RuntimeError(f"model IR {old} is older than target {SUPPORTED_IR}")
    if old != SUPPORTED_IR:
        data[1] = SUPPORTED_IR
        MODEL.write_bytes(data)
        print(f"normalized {MODEL} IR {old} -> {SUPPORTED_IR}")
    else:
        print(f"already normalized: IR {SUPPORTED_IR}")


if __name__ == "__main__":
    main()
