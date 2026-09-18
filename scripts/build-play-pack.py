#!/usr/bin/env python3
"""Validate and package Rainline's copy, native screenshots and privacy pages."""
import hashlib
from html import escape
import json
from pathlib import Path
import re
import shutil
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/rainline-play'
CAPTURES = ROOT / 'output/play-captures'
LISTINGS = json.loads((ROOT / 'play/listings.json').read_text())
PRIVACY = json.loads((ROOT / 'play/privacy.json').read_text())
LOCALES = ['en-GB', 'no-NO', 'sv-SE', 'fi-FI', 'da-DK']
PAGES = dict(zip(LOCALES, ['index.html', 'nb.html', 'sv.html', 'fi.html', 'da.html']))
assert set(LISTINGS) == set(PRIVACY) == set(LOCALES), 'Locale sets differ'
files = set()

def write(name, value):
    target = OUT / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(value, encoding='utf-8')
    files.add(name)

def copy(source, name):
    target = OUT / name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)
    files.add(name)

def validate_png(path, dimensions, colour):
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', path
    width, height, depth, mode = struct.unpack('>IIBB', data[16:26])
    assert (width, height) == dimensions and depth == 8 and mode == colour, (path, width, height, depth, mode)
    assert len(data) <= (1024 * 1024 if dimensions == (512, 512) else 8 * 1024 * 1024), path

style = '''body{margin:0;background:#080808;color:#eee;font:17px/1.65 system-ui,sans-serif}main{max-width:1080px;margin:auto;padding:32px 24px}h1,h2{font-weight:400;line-height:1.2}h1{font-size:44px}h2{margin-top:48px}a{color:inherit;text-underline-offset:4px}nav{display:flex;flex-wrap:wrap;gap:22px;margin:24px 0}section{border-top:1px solid #555;margin:40px 0;padding-bottom:24px}pre{white-space:pre-wrap;font:inherit}.screens{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.screens img{width:100%;height:auto;border:1px solid #333}figure{margin:0}figcaption{font-size:13px;line-height:1.4;margin:8px 0}.feature{display:block;width:100%;max-width:700px;height:auto;border:1px solid #333;margin:20px 0}summary{cursor:pointer;padding:12px 0}small{color:#bbb}p{max-width:850px}code{overflow-wrap:anywhere}@media(max-width:750px){.screens{grid-template-columns:repeat(2,minmax(0,1fr))}h1{font-size:34px}}'''

def html_page(lang, title, body):
    return f'<!doctype html>\n<html lang="{lang}"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{escape(title)}</title><style>{style}</style><main>{body}</main></html>\n'

review = ['<h1>Rainline · Google Play</h1><p>Prepared for Hardly Working · 18 September 2026.<br>Support: <a href="mailto:workinghardlyforyou@gmail.com">workinghardlyforyou@gmail.com</a></p>',
          '<p>Copy-ready listings and actual app screenshots in five languages. Forecast previews use the app’s labelled sample data. This pack has not been submitted to Google Play.</p>',
          '<p><a href="icon.png"><img src="icon.png" width="128" height="128" alt="Rainline store icon: a black rain graph on white"></a></p>',
          '<nav><a href="README.md">Publishing walkthrough</a><a href="data-safety.md">Data safety draft</a><a href="background-location.md">Location review</a><a href="privacy/index.html">Privacy pages</a></nav>',
          '<nav>' + ''.join(f'<a href="#{tag}">{escape(LISTINGS[tag]["language"])}</a>' for tag in LOCALES) + '</nav>']
rows = []
for tag in LOCALES:
    item = LISTINGS[tag]
    for field, filename, limit in [('title','title.txt',30), ('short_description','short-description.txt',80),
                                   ('full_description','full-description.txt',4000), ('release_notes','release-notes.txt',500)]:
        value = item[field]
        assert value.strip() == value and 0 < len(value) <= limit, (tag, field, len(value))
        assert not re.search(r'TODO|PLACEHOLDER|\[INSERT', value), (tag, field)
        write(f'{tag}/{filename}', value + '\n')
    assert len(item['alt_text']) == 4
    assert all(0 < len(text) <= 140 for text in item['alt_text'] + [item['feature_alt']])
    rows.append(f'| {tag} | {len(item["title"])} | {len(item["short_description"])} | {len(item["full_description"])} | {len(item["release_notes"])} |')
    review.append(f'<section id="{tag}" lang="{PRIVACY[tag]["lang"]}"><h2>{escape(item["language"])}</h2><h3>{escape(item["title"])}</h3><p>{escape(item["short_description"])}</p>')
    validate_png(CAPTURES / tag / 'feature-graphic.png', (1024,500), 2)
    copy(CAPTURES / tag / 'feature-graphic.png', f'{tag}/feature-graphic.png')
    review.append(f'<a href="{tag}/feature-graphic.png"><img class="feature" src="{tag}/feature-graphic.png" alt="{escape(item["feature_alt"])}"></a><div class="screens">')
    alts = ['Feature graphic: ' + item['feature_alt']]
    for name, alt in zip(['01-forecast','02-appearance','03-rainfall','04-time-axis'], item['alt_text']):
        source = CAPTURES / tag / f'{name}.png'
        validate_png(source, (1080,1920), 2)
        dest = f'{tag}/phone-screenshots/{name}.png'
        copy(source, dest)
        review.append(f'<figure><a href="{dest}"><img src="{dest}" alt="{escape(alt)}" loading="lazy"></a><figcaption>{escape(alt)}</figcaption></figure>')
        alts.append(name + '.png: ' + alt)
    write(f'{tag}/image-descriptions.txt', '\n\n'.join(alts) + '\n')
    review.append(f'</div><details><summary>Full description · {len(item["full_description"])} / 4000</summary><pre>{escape(item["full_description"])}</pre><a href="{tag}/full-description.txt">Plain text</a></details><details><summary>Release notes</summary><pre>{escape(item["release_notes"])}</pre></details></section>')
    policy = PRIVACY[tag]
    links = '<nav>' + ''.join(f'<a href="{PAGES[other]}">{escape(LISTINGS[other]["language"])}</a>' for other in LOCALES) + '</nav>'
    body = '<h1>' + escape(policy['title']) + '</h1><p>' + escape(policy['updated']) + '</p>' + links
    for heading, *paragraphs in policy['sections']:
        body += '<h2>' + escape(heading) + '</h2>' + ''.join('<p>' + escape(p) + '</p>' for p in paragraphs)
    body += '<nav><a href="https://www.met.no/en/About-us/privacy">MET Norway</a><a href="https://docs.api.met.no/doc/TermsOfService">MET API</a><a href="https://github.com/erikhardlyworking/minimalistRainline">Rainline · GitHub</a><a href="mailto:workinghardlyforyou@gmail.com">workinghardlyforyou@gmail.com</a></nav>'
    page = html_page(policy['lang'], policy['title'], body)
    write('privacy/' + PAGES[tag], page)
    host = ROOT / 'docs/privacy' / PAGES[tag]
    host.parent.mkdir(parents=True, exist_ok=True)
    host.write_text(page, encoding='utf-8')
validate_png(CAPTURES / 'icon.png', (512,512), 6)
copy(CAPTURES / 'icon.png', 'icon.png')
write('review.html', html_page('en', 'Rainline · Google Play handoff', ''.join(review)))
for name in ['README.md','data-safety.md','background-location.md']:
    copy(ROOT / 'play' / name, name)
write('validation.md', '# Pack validation\n\n| Locale | Title / 30 | Short / 80 | Full / 4000 | Notes / 500 |\n| --- | --- | --- | --- | --- |\n' + '\n'.join(rows) + '\n\n20 screenshots: 1080×1920 RGB PNG. Five feature graphics: 1024×500 RGB PNG. Icon: 512×512 RGBA PNG. Native captures use the existing labelled sample preview.\n')
(ROOT / 'docs/.nojekyll').touch()
(ROOT / 'docs/index.html').write_text(html_page('en','Rainline','<h1>Rainline</h1><p>A minimal, open-source rain forecast widget for Android.</p><nav><a href="privacy/">Privacy policy · Personvern · Integritet · Tietosuoja · Privatliv</a><a href="https://github.com/erikhardlyworking/minimalistRainline">Source code</a><a href="mailto:workinghardlyforyou@gmail.com">Support</a></nav>'), encoding='utf-8')
bundle = ROOT / 'app/build/outputs/bundle/release/app-release.aab'
with zipfile.ZipFile(bundle) as archive:
    assert not any(re.match(r'META-INF/.*\.(RSA|DSA|EC|SF)$', name) for name in archive.namelist()), 'Expected an unsigned bundle'
    assert 'base/manifest/AndroidManifest.xml' in archive.namelist(), 'Invalid app bundle'
version = re.search(r"versionName '([^']+)'", (ROOT / 'app/build.gradle').read_text()).group(1)
copy(bundle, f'rainline-{version}-UNSIGNED.aab')
write('SHA256SUMS', ''.join(f'{hashlib.sha256((OUT/name).read_bytes()).hexdigest()}  {name}\n' for name in sorted(files)))
zip_path = ROOT / 'output/rainline-google-play-pack.zip'
with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as archive:
    for name in sorted(files):
        archive.write(OUT / name, 'rainline-play/' + name)
print('\n'.join(rows))
print(f'Validated and packaged {len(files)} files: {zip_path} ({zip_path.stat().st_size:,} bytes)')
