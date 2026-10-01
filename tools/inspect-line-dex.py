"""Read-only DEX declaration/literal index for verifying obfuscated LINE mappings.

Literal hits scan aligned code units and are candidates only: verify in smali before use.
"""
import argparse
import pathlib
import re
import struct
import json


def inspect(path, args):
    data = path.read_bytes()
    u32 = lambda off: struct.unpack_from('<I', data, off)[0]
    u16 = lambda off: struct.unpack_from('<H', data, off)[0]

    def leb(off):
        value = shift = 0
        while True:
            byte = data[off]
            off += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value, off
            shift += 7

    strings = []
    for idx in range(u32(56)):
        _, off = leb(u32(u32(60) + idx * 4))
        end = data.index(b'\0', off)
        strings.append(data[off:end].decode('utf-8', 'replace'))
    types = [strings[u32(u32(68) + idx * 4)] for idx in range(u32(64))]
    protos = []
    for idx in range(u32(72)):
        off = u32(76) + idx * 12
        params = u32(off + 8)
        signature = ''.join(types[u16(params + 4 + n * 2)] for n in range(u32(params))) if params else ''
        protos.append('(' + signature + ')' + types[u32(off + 4)])
    methods = []
    for idx in range(u32(88)):
        off = u32(92) + idx * 8
        methods.append((types[u16(off)], strings[u32(off + 4)], protos[u16(off + 2)]))
    wanted_literals = {idx for idx, value in enumerate(strings) if args.literal and re.search(args.literal, value)}
    for idx in range(u32(96)):
        off = u32(100) + idx * 32
        owner = types[u32(off)]
        for required in args.required:
            if required['class'] == owner and 'method' not in required and 'field' not in required:
                args.found.add(json.dumps(required, sort_keys=True))
        if args.type and not re.search(args.type, owner):
            continue
        class_data = u32(off + 24)
        if not class_data:
            continue
        counts = []
        for _ in range(4):
            count, class_data = leb(class_data)
            counts.append(count)
        fields = []
        for count in counts[:2]:
            field_idx = 0
            for _ in range(count):
                diff, class_data = leb(class_data)
                access, class_data = leb(class_data)
                field_idx += diff
                field_off = u32(84) + field_idx * 8
                fields.append((strings[u32(field_off + 4)], types[u16(field_off + 2)], access))
        for required in args.required:
            if required['class'] == owner and 'field' in required:
                if any(name == required['field'] and typ == required['descriptor'] and (not required.get('static') or access & 8) for name, typ, access in fields):
                    args.found.add(json.dumps(required, sort_keys=True))
        matched = []
        for count in counts[2:]:
            method_idx = 0
            for _ in range(count):
                diff, class_data = leb(class_data)
                access, class_data = leb(class_data)
                code, class_data = leb(class_data)
                method_idx += diff
                method = methods[method_idx]
                for required in args.required:
                    if required['class'] == owner and required.get('method') == method[1] and required['descriptor'] == method[2] and (not required.get('static') or access & 8):
                        args.found.add(json.dumps(required, sort_keys=True))
                hits = set()
                if (wanted_literals or args.integer is not None) and code:
                    start = code + 16
                    end = start + u32(code + 12) * 2
                    for pos in range(start, end - 2, 2):
                        opcode = data[pos]
                        literal_idx = u16(pos + 2) if opcode == 0x1a else u32(pos + 2) if opcode == 0x1b and pos + 6 <= end else -1
                        if literal_idx in wanted_literals:
                            hits.add(strings[literal_idx])
                        if args.integer is not None and opcode == 0x14 and pos + 6 <= end and u32(pos + 2) == args.integer:
                            hits.add(hex(args.integer))
                if (args.method and re.search(args.method, method[1])) or (args.signature and re.search(args.signature, method[2])) or hits or (args.type and not args.method and not args.signature and not args.literal and args.integer is None):
                    matched.append((method, access, hits))
        if matched:
            print(path.name, owner, 'extends', types[u32(off + 8)] if u32(off + 8) != 0xffffffff else '-')
            if args.type:
                for name, typ, access in fields:
                    print('  field', name, typ, hex(access))
            for (_, name, signature), access, hits in matched:
                print(' ', name, signature, hex(access), ' | '.join(sorted(hits)))


parser = argparse.ArgumentParser()
parser.add_argument('directory', type=pathlib.Path)
parser.add_argument('--type')
parser.add_argument('--method')
parser.add_argument('--signature')
parser.add_argument('--literal')
parser.add_argument('--integer', type=lambda value: int(value, 0))
parser.add_argument('--verify-json', type=pathlib.Path)
options = parser.parse_args()
options.required = json.loads(options.verify_json.read_text(encoding='utf-8')) if options.verify_json else []
options.found = set()
for dex in options.directory.glob('*.dex'):
    inspect(dex, options)
if options.verify_json:
    missing = [item for item in options.required if json.dumps(item, sort_keys=True) not in options.found]
    for item in missing:
        print('MISSING', json.dumps(item))
    print(f'APK declaration verification: {len(options.required) - len(missing)}/{len(options.required)} passed')
    if missing:
        raise SystemExit(1)
