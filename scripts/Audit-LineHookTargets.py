"""Check version inheritance and directly configured hook targets against an actual APK.

Usage: python scripts/Audit-LineHookTargets.py base.apk Version26160
This checks target existence, not behavior, overload signatures, audio or server responses.
Use Verify-LineApk.py for the separately reviewed exact signatures.
"""
import importlib.util
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/dev/vector/lineextension'
spec = importlib.util.spec_from_file_location('apk_declarations', ROOT / 'scripts/Verify-LineApk.py')
parser = importlib.util.module_from_spec(spec)
spec.loader.exec_module(parser)


def effective_config(version):
    values = {}
    errors = []

    def read(name):
        source = (SOURCE / 'versions' / (name + '.java')).read_text(encoding='utf8')
        parent = re.search(r'Config v = (Version\d+)\.create\(', source)
        if parent:
            read(parent[1])
        assigned = set()
        # Only string values are relevant to this existence audit. Expressions such as
        # resource-ID arithmetic are intentionally outside its scope.
        for key, value in re.findall(r'v\.([\w.]+)\s*=\s*"([^"]*)"\s*;', source):
            if key in assigned:
                errors.append(f'{name}: duplicate assignment of {key}')
            assigned.add(key)
            values[key] = value
        for helper in re.findall(r'\b(Line\d+\w+)\.apply\(v\);', source):
            read(helper)

    read(version)
    return values, errors


def run(apk, version):
    classes = {}
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                classes.update(parser.declarations(archive.read(name)))
    values, errors = effective_config(version)

    def descriptor(name):
        return 'L' + name.replace('.', '/') + ';'

    def has_method(owner, method, seen=None):
        seen = set() if seen is None else seen
        if owner in seen or owner not in classes:
            return False
        seen.add(owner)
        entry = classes[owner]
        return any(m[0] == method for m in entry['methods']) or any(
            has_method(parent, method, seen) for parent in entry['parents'])

    files = list((SOURCE / 'hooks').glob('*.java'))
    targets = set()
    class_keys = {key for key in values if key.lower().endswith(('class', 'controller'))}
    prefix = r'(?:cfg|config|v)\.'
    patterns = [
        r'Reflect\.findMethodExact\(\s*' + prefix + r'([\w.]+),\s*[^,]+,\s*' + prefix + r'([\w.]+)',
        r'Vector\.hookAll\(\s*Reflect\.findClass\(' + prefix + r'([\w.]+),[^)]+\),\s*' + prefix + r'([\w.]+)',
    ]
    for path in files:
        source = path.read_text(encoding='utf8')
        class_keys.update(re.findall(r'Reflect\.findClass\(\s*' + prefix + r'([\w.]+),', source))
        for pattern in patterns:
            targets.update((path.name, owner, method) for owner, method in re.findall(pattern, source))
    class_count = 0
    for key in sorted(class_keys):
        name = values.get(key, '')
        if not name or '.' not in name or name.startswith(('android.', 'java.', 'javax.')):
            continue
        class_count += 1
        if descriptor(name) not in classes:
            errors.append(f'{key}: missing APK class {name}')
    method_count = 0
    for filename, owner, method in sorted(targets):
        name, member = values.get(owner), values.get(method)
        if not name or not member:
            continue  # optional/disabled targets and defaults not assigned in version tables
        method_count += 1
        if not has_method(descriptor(name), member):
            errors.append(f'{filename}: {owner}={name} has no {method}={member}')
    print(f'{len(files)} hook/policy source files scanned; {class_count} configured classes; '
          f'{method_count} direct method targets; {len(errors)} errors.')
    for error in errors:
        print('FAIL:', error)
    print('Indirect reflection, runtime paths and feature behavior still require separate verification.')
    return bool(errors)


if __name__ == '__main__':
    sys.exit(run(sys.argv[1], sys.argv[2]))
