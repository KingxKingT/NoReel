package com.kingxkingt.noreel
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
class MainActivity : Activity() {
 private val bg=Color.rgb(12,16,22); private val panel=Color.rgb(24,30,39); private val muted=Color.rgb(160,171,185); private val accent=Color.rgb(91,220,154); private val white=Color.rgb(242,246,250)
 private lateinit var status:TextView; private lateinit var detail:TextView; private lateinit var adminStep:Button; private lateinit var accessibilityStep:Button; private lateinit var days:EditText; private lateinit var hours:EditText; private lateinit var timerFields:LinearLayout
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState); buildScreen()}
 override fun onResume(){super.onResume(); CommitmentTimer.restoreOrExpire(this); if(::status.isInitialized) refreshScreen()}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun label(t:String,s:Int,c:Int,b:Boolean=false)=TextView(this).apply{text=t;textSize=s.toFloat();setTextColor(c);if(b)setTypeface(typeface,Typeface.BOLD)}
 private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(16),dp(18),dp(16));setBackgroundColor(panel)}
 private fun action(t:String,fn:()->Unit)=Button(this).apply{text=t;isAllCaps=false;textSize=14f;gravity=Gravity.CENTER;setOnClickListener{fn()}}
 private fun buildScreen(){
  val root=LinearLayout(this).apply{orientation=1;setPadding(dp(22),dp(26),dp(22),dp(24));setBackgroundColor(bg)}
  val scroll=ScrollView(this); val body=LinearLayout(this).apply{orientation=1}
  body.addView(label("N O R E E L",12,accent,true))
  body.addView(label("Your time.\nYour choice.",32,white,true).apply{setPadding(0,dp(10),0,dp(8))})
  body.addView(label("Keep Instagram for friends and posts. Leave Reels behind.",15,muted).apply{setPadding(0,0,0,dp(22))})
  val statusCard=card(); statusCard.addView(label("PROTECTION STATUS",11,muted,true))
  status=label("Not protected yet",23,white,true).apply{setPadding(0,dp(9),0,dp(5))}
  detail=label("Complete the two setup steps, then choose your commitment.",13,muted)
  statusCard.addView(status);statusCard.addView(detail);body.addView(statusCard)
  body.addView(label("Get ready",18,white,true).apply{setPadding(0,dp(24),0,dp(10))})
  val setup=card()
  adminStep=action("1. Enable uninstall protection"){requestDeviceAdmin()}
  accessibilityStep=action("2. Turn on Reels blocking"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
  setup.addView(adminStep);setup.addView(accessibilityStep);body.addView(setup)
  body.addView(label("Choose your commitment",18,white,true).apply{setPadding(0,dp(24),0,dp(10))})
  timerFields=card();timerFields.addView(label("How long do you want to stay away?",15,white,true))
  val row=LinearLayout(this).apply{orientation=0}
  days=number("Days","1");hours=number("Hours","0")
  row.addView(days,LinearLayout.LayoutParams(0,dp(54),1f).apply{marginEnd=dp(6)})
  row.addView(hours,LinearLayout.LayoutParams(0,dp(54),1f).apply{marginStart=dp(6)})
  timerFields.addView(row)
  timerFields.addView(action("Start my commitment"){beginCommitment()}.apply{setBackgroundColor(accent);setTextColor(Color.rgb(10,24,18))})
  body.addView(timerFields)
  body.addView(label("Normal Instagram posts and messages stay available. NoReel closes the full-screen Reels player. Android may still allow protection to be disabled in system settings.",12,muted).apply{setPadding(dp(2),dp(18),dp(2),0)})
  scroll.addView(body);root.addView(scroll);setContentView(root);refreshScreen()
 }
 private fun number(h:String,v:String)=EditText(this).apply{hint=h;setText(v);textSize=16f;setTextColor(white);setHintTextColor(muted);inputType=InputType.TYPE_CLASS_NUMBER;setSingleLine(true);setPadding(dp(12),0,dp(12),0);setBackgroundColor(Color.rgb(39,47,59))}
 private fun requestDeviceAdmin(){
  val admin=ComponentName(this,NoReelAdminReceiver::class.java)
  startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,admin).putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"NoReel makes uninstalling harder during your commitment. You can deactivate it in Android security settings."))
 }
 private fun enabled():Boolean{
  val expected=ComponentName(this,ReelsAccessibilityService::class.java).flattenToString()
  val list=Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?:return false
  return list.split(':').any{it.equals(expected,true)}
 }
 private fun beginCommitment(){
  val d=days.text.toString().toLongOrNull()?:0L;val h=hours.text.toString().toLongOrNull()?:0L
  if(d !in 0..365||h !in 0..23||(d==0L&&h==0L)){Toast.makeText(this,"Choose at least 1 hour, up to 365 days.",Toast.LENGTH_LONG).show();return}
  if(!ProtectionPolicy.isAdminActive(this)){Toast.makeText(this,"First, enable uninstall protection.",Toast.LENGTH_LONG).show();requestDeviceAdmin();return}
  if(!enabled()){Toast.makeText(this,"First, turn on NoReel in Accessibility settings.",Toast.LENGTH_LONG).show();startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return}
  val until=CommitmentTimer.start(this,(d*24L+h)*3600000L)
  val strong=runCatching{ProtectionPolicy.apply(this)}.getOrDefault(false)
  Toast.makeText(this,if(strong)"Commitment started. Extra device restrictions are active." else "Commitment started. Reels blocking is active.",Toast.LENGTH_LONG).show()
  refreshScreen(until)
 }
 private fun refreshScreen(overrideUntil:Long?=null){
  val until=overrideUntil?:CommitmentTimer.activeUntil(this);val active=until>System.currentTimeMillis()
  val admin=ProtectionPolicy.isAdminActive(this);val a11y=enabled();val owner=ProtectionPolicy.isDeviceOwner(this)
  status.text=if(active)"You're Reels-free" else "Not protected yet";status.setTextColor(if(active)accent else white)
  detail.text=if(active)"Your commitment ends\n"+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(until))+"\n\nReels blocking: "+if(a11y)"ON" else "OFF" else "Two quick setup steps, then choose how long you want to commit."
  adminStep.text=if(admin)"✓ Uninstall protection enabled" else "1. Enable uninstall protection"
  accessibilityStep.text=if(a11y)"✓ Reels blocking enabled" else "2. Turn on Reels blocking"
  adminStep.isEnabled=!admin;accessibilityStep.isEnabled=!a11y;timerFields.visibility=if(active)View.GONE else View.VISIBLE
  if(active&&owner)detail.append("\nExtra Android restrictions: ON")
 }
}