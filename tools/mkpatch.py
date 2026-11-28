#!/usr/bin/env python3
"""mkpatch.py old.apk new.apk out.patch : يشغّل bsdiff ثم يحوّل الأقسام من bzip2 إلى zlib (عشان أندرويد يفكّها بدون مكتبات)."""
import bz2, struct, subprocess, sys, tempfile, os, zlib

def rewrap(bs: bytes) -> bytes:
    if bs[:8] != b"BSDIFF40":
        raise SystemExit("not a BSDIFF40 file")
    cl, dl, nsz = struct.unpack("<qqq", bs[8:32])
    ctrl = bz2.decompress(bs[32:32 + cl])
    diff = bz2.decompress(bs[32 + cl:32 + cl + dl])
    extra = bz2.decompress(bs[32 + cl + dl:])
    c, d, e = (zlib.compress(x, 9) for x in (ctrl, diff, extra))
    return b"HSNPT001" + struct.pack("<qqqq", nsz, len(c), len(d), len(e)) + c + d + e

def apply(old: bytes, patch: bytes) -> bytes:
    """مرجع بايثون لتطبيق الصيغة (للتحقق في الـCI)."""
    assert patch[:8] == b"HSNPT001"
    nsz, cl, dl, el = struct.unpack("<qqqq", patch[8:40])
    ctrl = zlib.decompress(patch[40:40 + cl]); diff = zlib.decompress(patch[40 + cl:40 + cl + dl]); extra = zlib.decompress(patch[40 + cl + dl:40 + cl + dl + el])
    def offtin(b):
        y = b[7] & 0x7F
        for i in range(6, -1, -1): y = y * 256 + b[i]
        return -y if b[7] & 0x80 else y
    out = bytearray(); op = 0; ci = di = ei = 0
    while len(out) < nsz:
        x, y, z = offtin(ctrl[ci:ci+8]), offtin(ctrl[ci+8:ci+16]), offtin(ctrl[ci+16:ci+24]); ci += 24
        for i in range(x):
            o = old[op + i] if 0 <= op + i < len(old) else 0
            out.append((diff[di + i] + o) & 0xFF)
        di += x; op += x
        out += extra[ei:ei + y]; ei += y
        op += z
    return bytes(out)

if __name__ == "__main__":
    old, new, out = sys.argv[1:4]
    with tempfile.TemporaryDirectory() as t:
        raw = os.path.join(t, "raw.bsdiff")
        subprocess.check_call(["bsdiff", old, new, raw])
        patch = rewrap(open(raw, "rb").read())
    if apply(open(old, "rb").read(), patch) != open(new, "rb").read():
        raise SystemExit("patch verification failed")
    open(out, "wb").write(patch)
    print(f"patch {os.path.getsize(out)} bytes (new apk {os.path.getsize(new)})")
