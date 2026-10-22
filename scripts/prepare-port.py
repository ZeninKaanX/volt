#!/usr/bin/env python3
"""Generate explicit Minecraft API adaptations; never changes the main sources."""
import argparse
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('minecraft', choices=['1.21.9', '1.21.10'])
    args = parser.parse_args()
    output = ROOT / 'build/ports' / args.minecraft / 'java'
    if output.exists():
        shutil.rmtree(output)
    for source in (ROOT / 'src/main/java').rglob('*.java'):
        target = output / source.relative_to(ROOT / 'src/main/java')
        target.parent.mkdir(parents=True, exist_ok=True)
        text = source.read_text().replace('Identifier', 'ResourceLocation')
        text = text.replace('net.minecraft.util.Util', 'net.minecraft.Util')
        text = text.replace('.dimension().identifier()', '.dimension().location()')
        text = text.replace('k.getCategory().equals(KeyMapping.Category.DEBUG)',
                            'k.getCategory().id().getPath().equals("debug")')
        text = text.replace('renderContents(', 'renderWidget(')
        text = text.replace('void resize(int w,int h){physicalWidth=w;physicalHeight=h;super.resize(w,h);}',
                            'void resize(Minecraft client,int w,int h){physicalWidth=w;physicalHeight=h;super.resize(client,w,h);}')
        text = text.replace('this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);',
                            'setFilter(true, false); setClamp(true);')
        target.write_text(text)
    smoke = output.parent / 'smoke'
    if smoke.exists():
        shutil.rmtree(smoke)
    for source in (ROOT / 'src/smoke/java').rglob('*.java'):
        target = smoke / source.relative_to(ROOT / 'src/smoke/java')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source.read_text().replace('net.minecraft.world.level.gamerules.GameRules',
                                                     'net.minecraft.world.level.GameRules'))
    print('Prepared source adaptation for', args.minecraft)


if __name__ == '__main__':
    main()
