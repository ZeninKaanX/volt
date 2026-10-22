"""Create Volt's original short, dry percussive UI sounds. No voice, melody or reverb."""
from pathlib import Path
import math,random,struct,wave,subprocess,json
root=Path(__file__).resolve().parents[1]
out=root/'src/main/resources/assets/volt/sounds';out.mkdir(parents=True,exist_ok=True)
# A soft mechanical snap followed by a very brief air tail, without a pitched beep.
for idx,(name,length,gain,tail) in enumerate([('clip_saved',.24,.20,.12),('record_started',.16,.16,.085),('marker',.095,.13,.055),('button',.038,.13,.023)]):
 rate=48000;rng=random.Random(517+idx);low=prev=air=0.;samples=[]
 for i in range(round(rate*length)):
  t=i/rate;n=rng.uniform(-1,1)
  low+=.23*(n-low);high=low-prev;prev=low
  air+=.085*(n-air)
  snap=(1-math.exp(-t/.0006))*math.exp(-t/.0055)
  body=(1-math.exp(-t/.002))*math.exp(-t/tail)*(1-t/length)**3
  value=gain*(high*snap*2.1+air*body*.35)
  samples.append(round(max(-.5,min(.5,value))*32767))
 wav=out/(name+'.wav')
 with wave.open(str(wav),'wb') as f:f.setparams((1,2,rate,len(samples),'NONE','not compressed'));f.writeframes(struct.pack('<'+'h'*len(samples),*samples))
 subprocess.run(['ffmpeg','-y','-v','error','-i',str(wav),'-af','highpass=f=350,lowpass=f=6800,afade=t=out:st='+str(length-.01)+':d=0.01','-c:a','libvorbis','-q:a','5',str(out/(name+'.ogg'))],check=True);wav.unlink()
