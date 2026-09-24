import os, math, wave, struct
OUT=os.path.join(os.path.dirname(__file__),"../app/src/main/res/raw")
os.makedirs(OUT,exist_ok=True)
def wav(name,freq,dur,vol=.2):
    rate=22050; n=int(rate*dur)
    data=bytearray()
    for i in range(n):
        x=math.sin(2*math.pi*freq*i/rate)*vol
        data+=struct.pack("<h",int(max(-1,min(1,x))*32767))
    with wave.open(os.path.join(OUT,name),"wb") as w:
        w.setnchannels(1);w.setsampwidth(2);w.setframerate(rate);w.writeframes(data)
for name,f,d in [("sfx_click.wav",500,.05),("sfx_highscore.wav",880,.25),("sfx_coin.wav",1200,.08),("sfx_gameover.wav",260,.3),("bg_music.wav",220,2.0),("sfx_powerup.wav",760,.15),("sfx_land.wav",180,.08),("sfx_crash.wav",100,.25),("sfx_jump.wav",650,.12)]:
    wav(name,f,d,.18)
