package com.cloudtown.game.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import com.cloudtown.game.GameState;
import com.cloudtown.game.MapCamera;
import java.io.File;
import java.io.FileOutputStream;

/** Injects actual Android multi-pointer input into a disposable emulator. */
public final class CameraGestureTest extends Instrumentation {
    private SharedPreferences prefs;
    private long down;
    @Override public void onCreate(Bundle b) { super.onCreate(b);start(); }
    private void check(boolean value,String name) { if(!value)throw new AssertionError(name); }
    private void near(float a,float b,String name) { check(Math.abs(a-b)<.03f,name+": "+a+" != "+b); }
    private void sleep(long ms) { SystemClock.sleep(ms); }
    private void event(int action,float... points) {
        int n=points.length/2;
        MotionEvent.PointerProperties[] ps=new MotionEvent.PointerProperties[n];
        MotionEvent.PointerCoords[] cs=new MotionEvent.PointerCoords[n];
        for(int i=0;i<n;i++) {
            ps[i]=new MotionEvent.PointerProperties();ps[i].id=i;ps[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            cs[i]=new MotionEvent.PointerCoords();cs[i].x=points[i*2]*2.7f;cs[i].y=63+points[i*2+1]*2.7f;
            cs[i].pressure=1;cs[i].size=.05f;
        }
        if(action==MotionEvent.ACTION_DOWN)down=SystemClock.uptimeMillis();
        MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,n,ps,cs,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        sendPointerSync(e);e.recycle();sleep(20);
    }
    private void tap(float x,float y,long delay) {event(0,x,y);event(1,x,y);sleep(delay);}
    private GameState city() {return GameState.decode(prefs.getString("city",null));}
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            prefs=getTargetContext().getSharedPreferences("cloudtown",0);
            GameState seed=new GameState();seed.peakPopulation=60;seed.stageRewardMask=3;seed.stageSeenMask=3;
            prefs.edit().clear().putBoolean("welcomed",true).putString("city",seed.encode()).commit();
            Activity activity=startActivitySync(new Intent().setClassName("com.cloudtown.game","com.cloudtown.game.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            sleep(1100);tap(353,210,100); // pause simulation
            String before=prefs.getString("city","");
            event(0,150,385.5f);event(5|(1<<8),150,385.5f,250,385.5f);
            float x0=0,y0=0,x1=0,y1=0;
            for(int i=1;i<=15;i++) {
                float f=i/15f,rad=(float)Math.PI/4*f,span=50*(1+.8f*f),cx=200+20*f,cy=385.5f-10*f;
                x0=cx-(float)Math.cos(rad)*span;y0=cy-(float)Math.sin(rad)*span;
                x1=cx+(float)Math.cos(rad)*span;y1=cy+(float)Math.sin(rad)*span;
                event(2,x0,y0,x1,y1);
            }
            event(6|(1<<8),x0,y0,x1,y1);event(1,x0,y0);sleep(400);
            near(prefs.getFloat("camera_zoom",0),1.8f,"pinch zoom");
            near(prefs.getFloat("camera_angle",0),(float)Math.PI/4,"twist rotation");
            near(prefs.getFloat("camera_x",0),20,"pinch focal x");
            near(prefs.getFloat("camera_y",0),-10,"pinch focal y");
            check(before.equals(prefs.getString("city","")),"multitouch must not build or change city");
            event(0,210,380);event(2,195,392);event(1,195,392);sleep(400);
            near(prefs.getFloat("camera_x",0),5,"one finger pan x");
            near(prefs.getFloat("camera_y",0),2,"one finger pan y");
            check(before.equals(prefs.getString("city","")),"drag must not build");
            runOnMainSync(()->activity.recreate());sleep(1100);tap(353,210,100);
            near(prefs.getFloat("camera_zoom",0),1.8f,"camera survives recreation");
            MapCamera cam=new MapCamera();cam.restore(1.8f,(float)Math.PI/4,5,2);
            tap(200+cam.screenX(3,3,38),385.5f+cam.screenY(3,3,19)-20*cam.zoom,400);
            tap(200,664,200);
            check(city().level(3,3)==2 && city().coins==280,"select roof and upgrade after zoom/rotation/pan");
            tap(363,579,100); // close inspector
            tap(200+cam.screenX(2,3,38),385.5f+cam.screenY(2,3,19),400);
            check(city().tile(2,3)==GameState.HOUSE && city().coins==190,"build on correct plot after camera gesture");
            sleep(800);
            Bitmap shot=getUiAutomation().takeScreenshot();
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getCacheDir(),"camera-preview.png"))) {
                shot.compress(Bitmap.CompressFormat.PNG,100,out);
            }
            shot.recycle();
            tap(320,300,80);tap(320,300,400);
            near(prefs.getFloat("camera_zoom",0),1,"double tap reset zoom");
            near(prefs.getFloat("camera_angle",1),0,"double tap reset rotation");
            near(prefs.getFloat("camera_x",1),0,"double tap reset pan");
            check(city().coins==190 && city().count(GameState.HOUSE)==2,"double tap must not build");
            result.putString("stream","PASS: pinch zoom, twist, focal anchor, pan, gesture isolation, camera restore, rotated roof selection, upgrade and construction, double-tap reset\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable e) {
            result.putString("stream","FAIL: "+e.toString()+"\n");finish(Activity.RESULT_CANCELED,result);
        }
    }
}
