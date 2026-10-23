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

# Two shorter alternatives use filtered, non-tonal impulses rather than a voiced
# logo sting. All sounds are synthesized locally from deterministic noise.
for style in ['minimal','electric']:
 for idx,(name,length) in enumerate([('clip_saved',.105),('record_started',.075),('marker',.052),('button',.024)]):
  if style=='electric':length*=1.5
  rate=48000;rng=random.Random(814+idx+(100 if style=='electric' else 0));low=last=air=0.;samples=[]
  taps=[(0.,1.)]
  if name in ['clip_saved','record_started']:taps.append((.024,.30))
  if style=='electric' and name!='button':taps.extend([(.012,.25),(.047,.12)])
  for i in range(round(rate*length)):
   t=i/rate;n=rng.uniform(-1,1);low+=.30*(n-low);high=low-last;last=low;air+=.11*(n-air)
   envelope=sum(gain*(1-math.exp(-max(0,t-at)/.00045))*math.exp(-max(0,t-at)/.0038) for at,gain in taps if t>=at)
   tail=(1-math.exp(-t/.001))*math.exp(-t/.018)*(1-t/length)**3
   value=.24*(high*envelope*2.3+air*tail*(.12 if style=='minimal' else .32))
   samples.append(round(max(-.5,min(.5,value))*32767))
  wav=out/(style+'_'+name+'.wav')
  with wave.open(str(wav),'wb') as f:f.setparams((1,2,rate,len(samples),'NONE','not compressed'));f.writeframes(struct.pack('<'+'h'*len(samples),*samples))
  subprocess.run(['ffmpeg','-y','-v','error','-i',str(wav),'-af','highpass=f=450,lowpass=f=6200,afade=t=out:st='+str(max(0,length-.008))+':d=0.008','-c:a','libvorbis','-q:a','5',str(out/(style+'_'+name+'.ogg'))],check=True);wav.unlink()

events={name:{'sounds':[{'name':'volt:'+name,'stream':False}]} for name in ['clip_saved','record_started','marker','button']}
for style in ['minimal','electric']:
 for name in ['clip_saved','record_started','marker','button']:
  key=style+'_'+name;events[key]={'sounds':[{'name':'volt:'+key,'stream':False}]}
(out.parent/'sounds.json').write_text(json.dumps(events,indent=2)+'\n')
