"""Bundle the existing design prototype; no third-party dependency or network needed."""
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parent
source = (root / 'index.html').read_text()

def bundle(platform=None):
    html = source
    if platform:
        name = 'iOS' if platform == 'ios' else 'Android'
        html = html.replace('<title>SHADOW — Mobile design & flow demo</title>', f'<title>SHADOW — {name} UI & Prototype</title>')
        html = html.replace('PROTOTYPE · dữ liệu minh họa', f'{name.upper()} PROTOTYPE · minh họa')
        html = html.replace('AI Outfits + Học & làm theo · mobile concept v2', f'{name} · AI Outfits + Học & làm theo · concept v2')
        html = html.replace('27 màn hình · iOS & Android', f'27 màn hình · {name}')
        html = html.replace('<div class="links">', '<div class="links"><a href="start.html">← Chọn bản iOS hoặc Android</a>')
        html = html.replace('<script src="assets.js"></script>', f'<script>window.SHADOW_PLATFORM="{platform}";</script><script src="assets.js"></script>')
    for name in ['assets', 'design', 'controller', 'local-store', 'preview']:
        html = html.replace(f'<script src="{name}.js"></script>', '<script>\n' + (root / f'{name}.js').read_text() + '\n</script>')
    return html

# Reuse the same runtime for the inline camera/history preview.
inline = (root / 'inline-shell.html').read_text() + '<script>\n(function(){\n'
for name in ['assets', 'design', 'controller', 'local-store', 'preview', 'inline-tail']:
    code = (root / f'{name}.js').read_text()
    if name == 'design':
        code = code.replace('a{cursor:pointer}a:focus .hit', '#shadow-prototype-root a{cursor:pointer}#shadow-prototype-root a:focus .hit').replace('text{pointer-events:none}', '#shadow-prototype-root text{pointer-events:none}')
    inline += code + '\n'
inline += '})();\n</script>\n'
(root / 'inline-preview.html').write_text(inline)

(root / 'demo.html').write_text(bundle())
for platform in ['ios', 'android']:
    (root / f'{platform}-prototype.html').write_text(bundle(platform))
with zipfile.ZipFile(root.parent / 'SHADOW-design-demo.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for file in root.rglob('*'):
        if file.is_file() and '__pycache__' not in file.parts:
            archive.write(file, Path('shadow-mobile') / file.relative_to(root))
print('Packaged standalone iOS / Android prototypes and complete design ZIP.')
