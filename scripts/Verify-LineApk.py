"""Verify exact DEX declarations against a checked-in version-specific audit manifest.

Usage: python scripts/Verify-LineApk.py base.apk docs/line-26.16.0-audit-mappings.json
Declaration checks do not prove runtime behavior or network/audio delivery.
Only the Python standard library is required. APK contents are never executed.
"""
import json
import struct
import sys
import zipfile


def uleb(data, pos):
    value = shift = 0
    while True:
        byte = data[pos]
        pos += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, pos
        shift += 7


def declarations(data):
    def u32(pos):
        return struct.unpack_from('<I', data, pos)[0]

    def u16(pos):
        return struct.unpack_from('<H', data, pos)[0]

    strings = []
    for i in range(u32(56)):
        _, start = uleb(data, u32(u32(60) + i * 4))
        strings.append(data[start:data.index(0, start)].decode('utf8', 'replace'))
    types = [strings[u32(u32(68) + i * 4)] for i in range(u32(64))]
    prototypes = []
    for i in range(u32(72)):
        offset = u32(76) + i * 12
        args = u32(offset + 8)
        parameters = ''.join(types[u16(args + 4 + j * 2)] for j in range(u32(args))) if args else ''
        prototypes.append('(' + parameters + ')' + types[u32(offset + 4)])
    fields = []
    for i in range(u32(80)):
        offset = u32(84) + i * 8
        fields.append((strings[u32(offset + 4)], types[u16(offset + 2)]))
    methods = []
    for i in range(u32(88)):
        offset = u32(92) + i * 8
        methods.append((strings[u32(offset + 4)], prototypes[u16(offset + 2)]))
    for i in range(u32(96)):
        offset = u32(100) + i * 32
        superclass = u32(offset + 8)
        interfaces = u32(offset + 12)
        parents = [types[superclass]] if superclass != 0xffffffff else []
        if interfaces:
            parents += [types[u16(interfaces + 4 + j * 2)] for j in range(u32(interfaces))]
        entry = {'parents': parents, 'fields': [], 'methods': []}
        pos = u32(offset + 24)
        if pos:
            counts = []
            for _ in range(4):
                count, pos = uleb(data, pos)
                counts.append(count)
            for kind, count in enumerate(counts):
                index = 0
                for _ in range(count):
                    delta, pos = uleb(data, pos)
                    index += delta
                    flags, pos = uleb(data, pos)
                    table = fields if kind < 2 else methods
                    entry['fields' if kind < 2 else 'methods'].append((*table[index], bool(flags & 8)))
                    if kind >= 2:
                        _, pos = uleb(data, pos)
        yield types[u32(offset)], entry


def verify(apk, manifest):
    classes = {}
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if name.startswith('classes') and name.endswith('.dex'):
                classes.update(declarations(archive.read(name)))
    def matches(owner, spec, seen=None):
        seen = set() if seen is None else seen
        if owner in seen or owner not in classes:
            return False
        seen.add(owner)
        entry = classes[owner]
        if 'method' not in spec and 'field' not in spec:
            return True
        kind = 'methods' if 'method' in spec else 'fields'
        name = spec.get('method', spec.get('field'))
        for member, signature, static in entry[kind]:
            if member == name and signature == spec['descriptor'] and ('static' not in spec or static == spec['static']):
                return True
        return any(matches(parent, spec, seen) for parent in entry['parents'])
    with open(manifest, encoding='utf8') as source:
        specs = json.load(source)
    failures = [spec for spec in specs if not matches(spec['class'], spec)]
    for failure in failures:
        print('FAIL', json.dumps(failure, ensure_ascii=False))
    print(f'{len(specs) - len(failures)}/{len(specs)} declaration checks passed; {len(classes)} classes scanned.')
    return 1 if failures else 0


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    raise SystemExit(verify(sys.argv[1], sys.argv[2]))
