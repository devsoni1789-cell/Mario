package com.example.mario
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

@SuppressLint("ViewConstructor")
class GameView(ctx:Context):View(ctx),Choreographer.FrameCallback{
 private enum class S{SPLASH,MENU,HOW,PLAY,PAUSE,OVER,WIN}
 private data class P(val x:Float,val y:Float,val w:Float,val h:Float=32f)
 private data class C(var x:Float,var y:Float,var got:Boolean=false,var a:Float=0f)
 private data class E(var x:Float,var y:Float,var vx:Float,var alive:Boolean=true)
 private data class B(val x:Float,val y:Float,val q:Boolean)
 private data class F(var x:Float,var y:Float,var vx:Float,var vy:Float,var life:Float,val color:Int)
 private val prefs=ctx.getSharedPreferences("super_run",0)
 private var best=prefs.getInt("best",0);private var sound=prefs.getBoolean("sound",true);private var vib=prefs.getBoolean("vib",true)
 private val vibrator=ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
 private var s=S.SPLASH;private var splash=0f;private var running=false;private var last=0L
 private var w=0f;private var h=0f;private var ground=0f;private var cam=0f;private val end=17000f
 private var score=0;private var coin=0;private var lives=3;private var timer=180f;private var msg="";private var msgT=0f
 private var x=180f;private var y=0f;private val pw=54f;private val ph=72f;private var vx=0f;private var vy=0f
 private var grounded=false;private var coyote=0f;private var buffer=0f;private var inv=0f;private var anim=0f
 private var left=false;private var right=false;private var jump=false;private var jumpPress=false
 private val ps=mutableListOf<P>();private val cs=mutableListOf<C>();private val es=mutableListOf<E>();private val bs=mutableListOf<B>();private val fs=mutableListOf<F>()
 private val paint=Paint(3);private val txt=Paint(3).apply{typeface=Typeface.DEFAULT_BOLD;textAlign=Paint.Align.CENTER}
 init{isClickable=true}
 override fun onAttachedToWindow(){super.onAttachedToWindow();resumeGame()}
 override fun onDetachedFromWindow(){pauseLoop();super.onDetachedFromWindow()}
 fun resumeGame(){if(running)return;running=true;last=0;Choreographer.getInstance().postFrameCallback(this)}
 fun pauseLoop(){running=false;Choreographer.getInstance().removeFrameCallback(this)}
 override fun doFrame(n:Long){if(last!=0L)update(min(.05f,(n-last)/1e9f));last=n;invalidate();if(running)Choreographer.getInstance().postFrameCallback(this)}
 override fun onSizeChanged(a:Int,b:Int,oa:Int,ob:Int){w=a.toFloat();h=b.toFloat();ground=h*.78f;if(s!=S.PLAY)y=ground-ph}
 fun handleBackPressed():Boolean=when(s){S.PLAY->{s=S.PAUSE;true};S.PAUSE,S.HOW,S.OVER,S.WIN->{s=S.MENU;true};S.MENU->false;S.SPLASH->{s=S.MENU;true}}
 private fun start(){score=0;coin=0;lives=3;timer=180f;cam=0f;x=180f;y=ground-ph;vx=0f;vy=0f;build();s=S.PLAY}
 private fun build(){
  ps.clear();cs.clear();es.clear();bs.clear();fs.clear()
  val gaps=listOf(1550f,3250f,5050f,7350f,9800f,12200f,14800f);var z=0f
  while(z<end){if(gaps.none{z>=it&&z<it+190})ps+=P(z,ground,180f);z+=180}
  val hi=listOf(P(650f,ground-150,300f),P(1150f,ground-240,240f),P(1850f,ground-150,310f),P(2350f,ground-270,260f),P(2850f,ground-150,340f),P(3600f,ground-180,320f),P(4150f,ground-290,260f),P(4650f,ground-150,330f),P(5450f,ground-190,340f),P(6100f,ground-300,300f),P(6650f,ground-150,360f),P(7600f,ground-240,300f),P(8200f,ground-140,380f),P(8900f,ground-300,300f),P(9400f,ground-170,330f),P(10100f,ground-250,360f),P(10800f,ground-150,300f),P(11400f,ground-310,300f),P(11900f,ground-170,300f),P(12700f,ground-240,350f),P(13400f,ground-150,320f),P(14000f,ground-280,330f),P(14600f,ground-160,300f),P(15300f,ground-250,380f),P(16000f,ground-150,500f));ps+=hi
  repeat(72){i->cs+=C(360+i*220f+(i%3)*35,ground-90-(i%5)*24, a=i*.7f)}
  hi.forEach{cs+=C(it.x+it.w*.35f,it.y-62);cs+=C(it.x+it.w*.65f,it.y-62)}
  listOf(900f,1000f,2030f,2430f,2970f,3780f,4230f,4780f,5570f,6180f,6800f,7720f,8350f,9050f,10250f,10900f,11500f,12820f,13520f,14120f,15400f).forEachIndexed{i,v->bs+=B(v,ground-150-(i%3)*10,i%3==0)}
  listOf(1250f,1500f,2150f,3000f,3900f,4700f,5700f,6900f,8050f,9200f,10400f,11100f,12100f,13200f,14300f,15150f,16250f).forEachIndexed{i,v->es+=E(v,ground-42,if(i%2==0)90f else -80f)}
  es+=E(2020f,ground-192,70f);es+=E(4180f,ground-332,-65f);es+=E(6150f,ground-342,75f);es+=E(8950f,ground-342,-70f);es+=E(11450f,ground-352,75f);es+=E(14050f,ground-322,-75f)
 }
 private fun update(dt:Float){if(s==S.SPLASH){splash+=dt;if(splash>1.4)s=S.MENU};if(msgT>0)msgT-=dt;if(inv>0)inv-=dt;fs.forEach{it.x+=it.vx*dt;it.y+=it.vy*dt;it.vy+=700*dt;it.life-=dt};fs.removeAll{it.life<=0};if(s==S.PLAY)play(dt)}
 private fun play(dt:Float){
  timer-=dt;if(timer<=0){lose();return};if(jumpPress){buffer=.14f;jumpPress=false};if(buffer>0)buffer-=dt;if(coyote>0)coyote-=dt
  val d=when{left&&!right->-1;right&&!left->1;else->0};if(d!=0)vx=(vx+d*1900*dt).coerceIn(-520f,520f) else{vx*=if(grounded).8f else .95f;if(abs(vx)<8)vx=0f}
  if(buffer>0&&(grounded||coyote>0)){vy=-900f;grounded=false;coyote=0f;buffer=0f;burst(x+27,y+72,0xffffd54f);beep(760)}
  if(!jump&&vy<-350)vy+=1300*dt;vy=min(1500f,vy+2200*dt);val old=y+ph;x+=vx*dt;y+=vy*dt
  if(y>ground+180){lose();return};grounded=false
  for(p in ps)if(x+43>p.x&&x+11<p.x+p.w&&old<=p.y+10&&y+ph>=p.y&&vy>=0){y=p.y-ph;vy=0f;grounded=true;break}
  for(c in cs)if(!c.got){c.a+=dt*5;if(RectF(x+7,y+7,x+pw-7,y+ph-5).contains(c.x,c.y)){c.got=true;coin++;score+=100;burst(c.x,c.y,0xffffd54f);beep(880)}}
  for(b in bs)if(RectF(x,y,x+pw,y+ph).intersects(RectF(b.x,b.y,b.x+58,b.y+58))&&vy<0&&y<b.y+58){y=b.y+58;vy=80f;if(b.q){score+=150;msg="BONUS!";msgT=1f;burst(b.x+29,b.y,0xffffd54f);beep(1040)}}
  for(e in es)if(e.alive){e.x+=e.vx*dt;val support=ps.firstOrNull{e.x+46>it.x&&e.x<it.x+it.w&&abs(e.y+42-it.y)<8};if(support!=null)e.y=support.y-42 else if(e.y<ground-30)e.y+=2200*dt;if(support!=null&&(e.x<support.x||e.x+46>support.x+support.w))e.vx*=-1;if(RectF(x+7,y+8,x+pw-7,y+ph-3).intersects(RectF(e.x,e.y,e.x+46,e.y+42))){if(vy>250&&y+ph<e.y+30){e.alive=false;vy=-650f;score+=250;burst(e.x+23,e.y,0xffff8a4c);beep(420)}else if(inv<=0){lose();return}}}
  if(x>end-520){s=S.WIN;best=max(best,score);prefs.edit().putInt("best",best).apply();beep(1200);return};cam=(x-w*.34f).coerceIn(0f,end-w);score+=(abs(vx)*dt*.04f).toInt();anim+=dt*(3+abs(vx)/100)
 }
 private fun lose(){lives--;vibrate(80);if(lives<=0){s=S.OVER;best=max(best,score);prefs.edit().putInt("best",best).apply();return};x=max(100f,x-220);y=ground-ph;vx=0f;vy=0f;inv=2f;msg="TRY AGAIN!";msgT=1.1f}
 private fun burst(a:Float,b:Float,color:Int){repeat(10){val q=Random.nextFloat()*6.28f;val sp=80+Random.nextFloat()*180;fs+=F(a,b,cos(q)*sp,sin(q)*sp,.5f,color)}}
 private fun vibrate(ms:Long){if(!vib)return;if(Build.VERSION.SDK_INT>=26)vibrator?.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE))else @Suppress("DEPRECATION") vibrator?.vibrate(ms)}
 private fun beep(f:Int){if(sound){SoundEngine.enabled=true;SoundEngine.beep(f)}}
 override fun onDraw(c:Canvas){super.onDraw(c);when(s){S.SPLASH->splash(c);S.MENU->menu(c);S.HOW->how(c);S.PLAY->game(c);S.PAUSE->{game(c);overlay(c,"PAUSED","TAP TO RESUME")};S.OVER->{game(c);overlay(c,"GAME OVER","TAP TO RETRY")};S.WIN->{game(c);overlay(c,"LEVEL COMPLETE","TAP TO PLAY AGAIN")}}}
 private fun sky(c:Canvas){paint.shader=LinearGradient(0f,0f,0f,h,Color.rgb(105,195,255),Color.rgb(224,247,255),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,paint);paint.shader=null;paint.color=0x55ffffff;for(i in 0..7){val cx=(i*310f-cam*.08f)%(w+300)-120;c.drawCircle(cx,110f+(i%2)*70,42f,paint);c.drawCircle(cx+45,112f+(i%2)*70,32f,paint)};paint.color=0xff78c86b;for(i in -2..9){val xx=i*220f-cam*.12f;val path=Path();path.moveTo(xx,h*.78f+100);path.quadTo(xx+100,h*.68f-80,xx+220,h*.78f+100);path.close();c.drawPath(path,paint)}}
 private fun splash(c:Canvas){sky(c);txt.color=Color.WHITE;txt.textSize=min(w*.14f,74f);c.drawText("SUPER RUN",w/2,h*.42f,txt);txt.textSize=22f;c.drawText("original platform adventure",w/2,h*.49f,txt)}
 private fun menu(c:Canvas){sky(c);txt.color=0xff18202a;txt.textSize=min(w*.14f,76f);c.drawText("SUPER RUN",w/2,h*.24f,txt);txt.textSize=20f;c.drawText("BEST  "+best,w/2,h*.31f,txt);button(c,"PLAY",w*.22f,h*.40f,w*.78f,h*.50f);button(c,"HOW TO PLAY",w*.22f,h*.54f,w*.78f,h*.64f);button(c,"SOUND: "+if(sound)"ON" else "OFF",w*.22f,h*.68f,w*.48f,h*.77f);button(c,"VIBRATION: "+if(vib)"ON" else "OFF",w*.52f,h*.68f,w*.78f,h*.77f);txt.textSize=14f;c.drawText("Run • jump • collect • stomp • reach the flag",w/2,h*.86f,txt)}
 private fun how(c:Canvas){sky(c);txt.color=0xff18202a;txt.textSize=34f;c.drawText("HOW TO PLAY",w/2,h*.18f,txt);txt.textSize=19f;listOf("◀ / ▶  Move","JUMP  Tap or hold for a higher jump","Collect coins for points","Stomp enemies from above","Avoid gaps and reach the finish flag","Phone BACK pauses / returns to menu").forEachIndexed{i,v->c.drawText(v,w/2,h*(.31f+i*.075f),txt)};button(c,"BACK",w*.30f,h*.78f,w*.70f,h*.88f)}
 private fun game(c:Canvas){sky(c);c.save();c.translate(-cam,0f);for(q in ps){paint.color=0xff7b4f2c;c.drawRect(q.x,q.y,q.x+q.w,q.y+q.h,paint);paint.color=0xff4caf50;c.drawRect(q.x,q.y,q.x+q.w,q.y+9,paint)};val fx=end-300;paint.color=0xff6d4c41;c.drawRect(fx,ground-250,fx+12,ground,paint);paint.color=0xffef5350;val flag=Path();flag.moveTo(fx+12,ground-245);flag.lineTo(fx+105,ground-215);flag.lineTo(fx+12,ground-180);flag.close();c.drawPath(flag,paint);for(b in bs){paint.color=if(b.q)0xffffb52e else 0xff9b6a3c;c.drawRoundRect(b.x,b.y,b.x+58,b.y+58,7f,7f,paint);if(b.q){txt.color=Color.WHITE;txt.textSize=34f;c.drawText("?",b.x+29,b.y+40,txt)}};for(co in cs)if(!co.got){paint.color=0xffffd54f;c.drawCircle(co.x,co.y,13f+2f*sin(co.a),paint)};for(e in es)if(e.alive){paint.color=0xff7b3f24;c.drawRoundRect(e.x,e.y,e.x+46,e.y+42,12f,12f,paint);paint.color=Color.WHITE;c.drawCircle(e.x+14,e.y+15,7f,paint);c.drawCircle(e.x+32,e.y+15,7f,paint);paint.color=Color.BLACK;c.drawCircle(e.x+14,e.y+15,3f,paint);c.drawCircle(e.x+32,e.y+15,3f,paint)};if(!(inv>0&&(inv*12).toInt()%2==0)){val bob=if(grounded)sin(anim*8)*2 else 0f;paint.color=0xffe53935;c.drawRoundRect(x+8,y+18+bob,x+pw-8,y+ph-18,13f,13f,paint);paint.color=0xffffc49b;c.drawCircle(x+pw/2,y+17+bob,20f,paint);paint.color=0xffb71c1c;c.drawRoundRect(x+8,y+2+bob,x+pw-6,y+16+bob,10f,10f,paint);paint.color=0xff1565c0;c.drawRect(x+10,y+ph-25+bob,x+pw/2-2,y+ph-2+bob,paint);c.drawRect(x+pw/2+2,y+ph-25+bob,x+pw-10,y+ph-2+bob,paint)};for(f in fs){paint.color=f.color;c.drawCircle(f.x,f.y,max(1f,f.life*5),paint)};c.restore();paint.color=0xaaffffff;c.drawRoundRect(12f,12f,w-12,66f,18f,18f,paint);txt.color=0xff18202a;txt.textSize=17f;c.drawText("SCORE "+score,90f,46f,txt);c.drawText("COINS "+coin,w*.34f,46f,txt);c.drawText("LIVES "+lives,w*.57f,46f,txt);c.drawText("TIME "+timer.toInt(),w-70f,46f,txt);button(c,"◀",24f,h-112,112f,h-24);button(c,"▶",128f,h-112,216f,h-24);button(c,"JUMP",w-122f,h-124,w-20f,h-34);if(msgT>0){txt.color=Color.WHITE;txt.textSize=32f;c.drawText(msg,w/2,h*.22f,txt)}}
 private fun button(c:Canvas,label:String,l:Float,t:Float,r:Float,b:Float){paint.color=0xccffffff;c.drawRoundRect(l,t,r,b,18f,18f,paint);txt.color=0xff18202a;txt.textSize=if(label.length>6)15f else 24f;c.drawText(label,(l+r)/2,(t+b)/2-txt.ascent()/2,txt)}
 private fun overlay(c:Canvas,a:String,b:String){paint.color=0x99000000;c.drawRect(0f,0f,w,h,paint);txt.color=Color.WHITE;txt.textSize=min(w*.09f,54f);c.drawText(a,w/2,h*.39f,txt);txt.textSize=20f;c.drawText(b,w/2,h*.49f,txt);txt.textSize=18f;c.drawText("BEST "+best,w/2,h*.57f,txt)}
 override fun onTouchEvent(e:MotionEvent):Boolean{val a=e.x;val b=e.y;if(e.action==MotionEvent.ACTION_DOWN){when(s){S.MENU->{when{b in h*.40f..h*.50f->{start();return true};b in h*.54f..h*.64f->{s=S.HOW;return true};b in h*.68f..h*.77f->{if(a<w*.5f)sound=!sound else vib=!vib;prefs.edit().putBoolean("sound",sound).putBoolean("vib",vib).apply();return true}}};S.HOW->{s=S.MENU;return true};S.PLAY->{if(b>h-160){when{a<120->left=true;a<250->right=true;else->{jump=true;jumpPress=true}}}else if(a>w*.78f){jump=true;jumpPress=true};return true};S.PAUSE->{s=S.PLAY;return true};S.OVER,S.WIN->{start();return true};S.SPLASH->{s=S.MENU;return true}}}else if(e.action==MotionEvent.ACTION_MOVE&&s==S.PLAY&&b>h-160){left=a<120;right=a in 110f..250f;jump=a>w*.72f}else if(e.action==MotionEvent.ACTION_UP||e.action==MotionEvent.ACTION_CANCEL){left=false;right=false;jump=false};return true}
}