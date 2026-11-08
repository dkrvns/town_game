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
import java.io.File;
import java.io.FileOutputStream;

/** Run only on a disposable 1080x2160 emulator: replaces its test city. */
public final class ExpansionUiTest extends Instrumentation {
    private SharedPreferences prefs;
    @Override public void onCreate(Bundle b) {super.onCreate(b);start();}
    private void check(boolean ok,String label) {if(!ok)throw new AssertionError(label);}
    private GameState city() {return GameState.decode(prefs.getString("city",null));}
    private void tap(float x,float y) {
        long now=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
            MotionEvent e=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,x*2.7f,63+y*2.7f,0);
            e.setSource(InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(e);e.recycle();SystemClock.sleep(20);
        }
        SystemClock.sleep(420);
    }
    private void plot(float r,float c) {tap(200+(c-r)*19*.7f,385.5f+(r+c-8)*9.5f*.7f);}
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            check(getTargetContext().getResources().getDisplayMetrics().widthPixels==1080,"test requires a 1080x2160 emulator; reset wm size");
            prefs=getTargetContext().getSharedPreferences("cloudtown",0);
            GameState old=new GameState();old.coins=5000;old.upgrade(3,3);
            StringBuilder legacy=new StringBuilder("1;5000;30;0;0;0.0;1;");
            for(int r=0;r<9;r++)for(int c=0;c<9;c++)legacy.append(old.tile(r,c)).append(',').append(old.level(r,c)).append(';');
            prefs.edit().clear().putBoolean("welcomed",true).putString("city",legacy.toString()).commit();
            Activity activity=startActivitySync(new Intent().setClassName("com.cloudtown.game","com.cloudtown.game.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(1100);tap(353,210);
            check(city().coins==5000&&city().level(3,3)==2,"old city survives update");
            check(prefs.getString("city","").startsWith("2;"),"save migrated to v2");
            tap(306,653);tap(200,309.5f);
            check(city().count(GameState.FISH)==0&&city().coins==5000,"inland fishing placement rejected");
            tap(105,376);check(city().tile(6,1)==GameState.FISH&&city().foodProduction()==8,"fishing from toolbar");
            tap(105,360);tap(200,664);
            check(city().level(6,1)==2&&city().foodProduction()==12,"fishing upgrade");
            tap(200,664);check(city().level(6,1)==3&&city().foodProduction()==16,"fishing level three");
            tap(363,579);tap(105,340);tap(363,579); // select the tip of the level-three roof
            tap(342,579);plot(5.5f,-2.5f);tap(200,395);
            check(city().plotCount()==1&&city().ownedCount()==97&&city().coins==4120,"buy west 4x4");
            check(city().tile(7,-2)==GameState.WATER,"river on new territory");
            plot(-2.5f,1.5f);tap(200,395);
            check(city().plotCount()==2&&city().coins==3900,"buy north 4x4");
            tap(342,579);tap(252,653);plot(-1,3);
            tap(36,653);plot(-2,3);
            check(city().tile(-2,3)==GameState.HOUSE,"construction in purchased north plot");
            tap(200+5*19*.7f,385.5f-7*9.5f*.7f-20*.7f);tap(200,664);
            check(city().level(-2,3)==2,"negative-coordinate building inspector and upgrade");
            tap(363,579);tap(306,653);plot(6,-2);
            check(city().count(GameState.FISH)==2&&city().foodProduction()==20&&city().coins==3498,"fishing on new coast");
            runOnMainSync(()->activity.recreate());SystemClock.sleep(1100);tap(353,210);
            check(city().plotCount()==2&&city().level(-2,3)==2&&city().count(GameState.FISH)==2,"expanded city survives recreation");
            tap(342,579);SystemClock.sleep(3500);
            Bitmap shot=getUiAutomation().takeScreenshot();
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getCacheDir(),"expansion-preview.png"))) {
                shot.compress(Bitmap.CompressFormat.PNG,100,out);
            }
            shot.recycle();
            result.putString("stream","PASS: legacy migration, shore-only fishing, fishing upgrade, land purchase, river extension, negative-coordinate construction and upgrade, expanded city persistence\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable e) {result.putString("stream","FAIL: "+e.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
