import re
from svglib.svglib import svg2rlg
from reportlab.graphics import renderPM

s = open('branding/guardia_logo.svg').read()
s = re.sub(r'filter="url\(#glow\)"', '', s)
s = (s.replace('url(#bg)', '#0A1416')
      .replace('url(#shield)', '#1FE0C6')
      .replace('url(#sheen)', '#BFFFF4')
      .replace('url(#ink)', '#06181B')
      .replace('url(#gmark)', '#A6FFF1'))
open('branding/_flat.svg', 'w').write(s)
d = svg2rlg('branding/_flat.svg')
renderPM.drawToFile(d, 'branding/_preview.png', fmt='PNG')
print('ok')
