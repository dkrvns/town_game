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

/** Disposable emulator only: penalties, recovery actions and persistent reward popups. */
public final class EconomyUiTest extends Instrumentation {
    private SharedPreferences prefs;
    private Activity activity;
    @Override public void onCreate(Bundle b) {super.onCreate(b);start();}
    private void check(boolean ok,String label) {if(!ok)throw new AssertionError(label);}
    private GameState city() {return GameState.decode(prefs.getString("city",null));}
    private void tap(float x,float y) {
        long now=SystemClock.uptimeMillis();
        for(int action:new int[]{0,1}) {
            MotionEvent e=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,x*2.7f,63+y*2.7f,0);
            e.setSource(InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(e);e.recycle();SystemClock.sleep(20);
        }
        SystemClock.sleep(430);
    }
    private void load(GameState seed) {
        if(activity!=null){runOnMainSync(()->activity.finish());SystemClock.sleep(250);}
        prefs.edit().putBoolean("welcomed",true).putString("city",seed.encode()).commit();
        activity=startActivitySync(new Intent().setClassName("com.cloudtown.game","com.cloudtown.game.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        SystemClock.sleep(1100);if(city().nextStageNotice()<0)tap(353,210);
    }
    private void screenshot(String name) throws Exception {
        Bitmap bitmap=getUiAutomation().takeScreenshot();
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getCacheDir(),name))) {bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
        bitmap.recycle();
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            check(getTargetContext().getResources().getDisplayMetrics().widthPixels==1080,"reset emulator screen to 1080x2160");
            prefs=getTargetContext().getSharedPreferences("cloudtown",0);prefs.edit().clear().commit();
            GameState seed=new GameState();seed.demolish(5,3);seed.coins=500;seed.food=0;load(seed);
            check(city().foodPenaltyPercent()==50&&city().income()==14,"starvation affects displayed income");
            tap(36,653);tap(181,357);
            check(city().tile(3,2)==0&&city().coins==500,"new housing rejected during starvation");
            tap(80,90);screenshot("economy-penalties.png");
            tap(117,469);tap(162,385.5f);
            check(city().tile(5,3)==GameState.FARM&&city().foodPenaltyPercent()==0&&city().coins==380,"farm recovery button works");
            tap(36,653);tap(181,357);
            check(city().population()==8&&city().coins==290,"growth resumes after food repair");
            GameState comfort=new GameState();comfort.coins=999999;
            for(int r=0;r<9&&comfort.population()<44;r++)for(int c=0;c<9&&comfort.population()<44;c++)
                if(comfort.tile(r,c)==0&&comfort.nearRoad(r,c))comfort.build(r,c,GameState.HOUSE);
            comfort.demolish(5,5);comfort.coins=500;comfort.food=100;comfort.stageRewardMask=1;comfort.stageSeenMask=1;load(comfort);
            check(city().happiness()==32&&city().comfortPenaltyPercent()==50,"low comfort penalty");
            tap(36,653);tap(124,366.5f);
            check(city().population()==44&&city().coins==500,"low comfort blocks new residents");
            tap(80,90);tap(282,469);tap(200,404.5f);tap(200,309.5f);
            check(city().happiness()==50&&city().coins==360,"parks improve comfort and income");
            tap(36,653);tap(124,366.5f);
            check(city().population()==48&&city().coins==270,"growth resumes after parks");
            // An unread award must survive closing the app without a second payment.
            GameState reward=new GameState();reward.coins=500;reward.peakPopulation=60;load(reward);
            check(city().coins==800&&city().food==60&&city().nextStageNotice()==0,"automatic retroactive rewards");
            screenshot("stage-reward-24.png");load(city());
            check(city().coins==800&&city().nextStageNotice()==0,"unread popup survives recreation exactly once");
            tap(200,517);check(city().nextStageNotice()==1&&city().coins==800,"next popup queued");
            screenshot("stage-reward-60.png");tap(200,517);tap(353,210);
            check(city().nextStageNotice()==-1&&city().coins==800,"both stages acknowledged");
            load(city());check(city().nextStageNotice()==-1&&city().coins==800,"acknowledged popups do not reappear");
            result.putString("stream","PASS: visible penalties, food recovery, comfort recovery, housing blocks, resource panel, reward popup queue, restart-before-ack, exactly-once rewards\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable e) {result.putString("stream","FAIL: "+e.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
