#!/usr/bin/env python3
"""Builds an application jar: merges platform jars (concatenating META-INF/services/*), adds application
resources (branding bundles), embeds module jars under WEBBEANS-INF/modules/ with a modules.list, and writes a
manifest with Main-Class org.webbeans.launcher.Main. Offline stand-in for maven-shade-plugin.

usage: fatjar.py OUT.jar --lib a.jar b.jar ... --modules m1.jar m2.jar ... [--resources DIR ...]
"""
import os, sys, zipfile

def main(argv):
    out = argv[1]; mode = None; libs = []; mods = []; res = []
    for a in argv[2:]:
        if a in ('--lib', '--modules', '--resources'): mode = a; continue
        {'--lib': libs, '--modules': mods, '--resources': res}[mode].append(a)
    services = {}
    seen = set()
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nMain-Class: org.webbeans.launcher.Main\nCreated-By: WebBeans build\n')
        seen.add('META-INF/MANIFEST.MF')
        for lib in libs:
            with zipfile.ZipFile(lib) as src:
                for info in src.infolist():
                    n = info.filename
                    if n.endswith('/') or n == 'META-INF/MANIFEST.MF' or n.startswith('META-INF/maven/') or n.endswith('.SF') or n.endswith('.RSA') or n.endswith('.DSA'):
                        continue
                    data = src.read(n)
                    if n.startswith('META-INF/services/'):
                        services.setdefault(n, []).append(data.decode('utf-8'))
                        continue
                    if n in seen:
                        continue
                    seen.add(n); z.writestr(n, data)
        for n, parts in services.items():
            lines = []
            for p in parts:
                for l in p.splitlines():
                    l = l.strip()
                    if l and l not in lines: lines.append(l)
            z.writestr(n, '\n'.join(lines) + '\n')
        for d in res:
            for root, _, files in os.walk(d):
                for f in files:
                    full = os.path.join(root, f); rel = os.path.relpath(full, d).replace(os.sep, '/')
                    if rel in seen: continue
                    seen.add(rel); z.write(full, rel)
        names = []
        for m in mods:
            base = os.path.basename(m); names.append(base)
            z.write(m, 'WEBBEANS-INF/modules/' + base)
        z.writestr('WEBBEANS-INF/modules.list', '\n'.join(names) + '\n')
    print('wrote', out, 'with', len(seen), 'entries and', len(names), 'embedded modules')

if __name__ == '__main__':
    main(sys.argv)
