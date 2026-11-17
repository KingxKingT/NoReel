package com.kingxkingt.noreel
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.graphics.Rect
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
class ReelsAccessibilityService:AccessibilityService(){
 companion object{private const val TAG="NoReel";private const val INSTAGRAM="com.instagram.android";private const val PAGER="com.instagram.android:id/clips_viewer_view_pager";private const val SCAN_MS=350L;private const val RETRY_MS=650L;private const val COOLDOWN_MS=1800L;private const val MAX_TRIES=3;private const val MAX_DEPTH=28}
 private val handler=Handler(Looper.getMainLooper());private var lastScan=0L;private var lastBack=0L;private var tries=0;private var closing=false
 private var wm:WindowManager?=null;private var cover:View?=null;private var coverBounds:Rect?=null
 private val verify=Runnable{if(!closing)return@Runnable;if(!hasPager()){closing=false;tries=0;lastBack=0L}else if(tries<MAX_TRIES)sendBack("retry")else{Log.w(TAG,"Reels viewer remains after retry limit");closing=false}}
 override fun onServiceConnected(){super.onServiceConnected();wm=getSystemService(WINDOW_SERVICE) as WindowManager;serviceInfo=serviceInfo.apply{eventTypes=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_SCROLLED;packageNames=arrayOf(INSTAGRAM);notificationTimeout=100;flags=flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}}
 override fun onAccessibilityEvent(e:AccessibilityEvent?){
  e?:return
  if(e.packageName?.toString()!=INSTAGRAM){removeCover();return}
  if(getSharedPreferences("noreel",MODE_PRIVATE).getLong("until",0L)<=System.currentTimeMillis()){removeCover();return}
  if(e.eventType==AccessibilityEvent.TYPE_VIEW_SCROLLED){val n=e.source?:return;try{if(n.viewIdResourceName==PAGER)closeViewer("pager scroll")else scanIfNeeded()}finally{n.recycle()}}
  else if(e.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)scan()
  else if(e.eventType==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)scanIfNeeded()
 }
 private fun scanIfNeeded(){if(System.currentTimeMillis()-lastScan>=SCAN_MS)scan()}
 private fun scan(){
  lastScan=System.currentTimeMillis();val root=rootInActiveWindow?:return
  try{if(hasPager(root,0)){removeCover();closeViewer("viewer detected")}else{val r=findReels(root,0);if(r==null)removeCover()else if(r!=coverBounds)showCover(r)}}finally{root.recycle()}
 }
 private fun findReels(n:AccessibilityNodeInfo,depth:Int):Rect?{
  if(depth>MAX_DEPTH)return null
  val r=Rect();n.getBoundsInScreen(r);val h=resources.displayMetrics.heightPixels
  val s=(n.text?.toString()?:n.contentDescription?.toString()?: "").trim().lowercase()
  if((s=="reels"||s=="reel")&&n.isVisibleToUser&&r.top>=h*.76&&r.bottom<=h&&r.width()>0&&r.height()>0)return r
  for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;val found=try{findReels(c,depth+1)}finally{c.recycle()};if(found!=null)return found}
  return null
 }
 private fun showCover(r:Rect){
  removeCover();val p=(5*resources.displayMetrics.density).toInt()
  val lp=WindowManager.LayoutParams(r.width()+2*p,r.height()+2*p,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.OPAQUE).apply{gravity=Gravity.TOP or Gravity.START;x=(r.left-p).coerceAtLeast(0);y=(r.top-p).coerceAtLeast(0)}
  val dark=(resources.configuration.uiMode and 0x30)==0x20
  val v=View(this).apply{setBackgroundColor(if(dark)Color.BLACK else Color.WHITE);isClickable=true;contentDescription="Reels blocked by NoReel"}
  try{wm?.addView(v,lp);cover=v;coverBounds=Rect(r)}catch(ex:Exception){Log.w(TAG,"Could not cover Reels tab",ex)}
 }
 private fun removeCover(){val v=cover?:return;runCatching{wm?.removeView(v)};cover=null;coverBounds=null}
 private fun closeViewer(reason:String){if(System.currentTimeMillis()-lastBack<COOLDOWN_MS)return;if(!closing){closing=true;tries=0};sendBack(reason)}
 private fun sendBack(reason:String){lastBack=System.currentTimeMillis();tries++;val ok=performGlobalAction(GLOBAL_ACTION_BACK);Log.d(TAG,"Back ($reason) attempt=$tries sent=$ok");handler.removeCallbacks(verify);handler.postDelayed(verify,RETRY_MS)}
 private fun hasPager()=rootInActiveWindow?.let{r->try{hasPager(r,0)}finally{r.recycle()}}?:false
 private fun hasPager(n:AccessibilityNodeInfo,d:Int):Boolean{
  if(d>MAX_DEPTH)return false
  if(n.viewIdResourceName==PAGER&&n.isVisibleToUser){val r=Rect();n.getBoundsInScreen(r);val m=resources.displayMetrics;val area=m.widthPixels.toDouble()*m.heightPixels;return area>0&&r.width().toDouble()*r.height()/area>=.60}
  for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;val yes=try{hasPager(c,d+1)}finally{c.recycle()};if(yes)return true};return false
 }
 override fun onInterrupt(){removeCover()}
 override fun onDestroy(){handler.removeCallbacks(verify);removeCover();super.onDestroy()}
}