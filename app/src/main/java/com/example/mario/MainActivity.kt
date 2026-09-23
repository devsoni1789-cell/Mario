package com.example.mario
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
class MainActivity:Activity(){
 private lateinit var gameView:GameView
 override fun onCreate(b:Bundle?){super.onCreate(b);window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);window.decorView.systemUiVisibility=(View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);gameView=GameView(this);setContentView(gameView)}
 override fun onResume(){super.onResume();gameView.resumeGame()}
 override fun onPause(){gameView.pauseLoop();super.onPause()}
 @Suppress("DEPRECATION","MissingSuperCall") override fun onBackPressed(){if(!gameView.handleBackPressed())super.onBackPressed()}
}