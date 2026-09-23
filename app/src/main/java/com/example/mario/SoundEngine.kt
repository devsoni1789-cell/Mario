package com.example.mario
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
object SoundEngine{
 var enabled=true
 fun beep(freq:Int,duration:Int=55){if(!enabled)return;Thread{try{val rate=22050;val n=rate*duration/1000;val a=ShortArray(n);for(i in a.indices){val env=1.0-i.toDouble()/n;a[i]=(sin(2*PI*freq*i/rate)*0.18*env*Short.MAX_VALUE).toInt().toShort()};val t=AudioTrack(AudioManager.STREAM_MUSIC,rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,a.size*2,AudioTrack.MODE_STATIC);t.write(a,0,a.size);t.play();Thread.sleep(duration.toLong()+10);t.release()}catch(_:Throwable){}}.start()}
}