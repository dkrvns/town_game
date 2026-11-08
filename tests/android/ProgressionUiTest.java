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

/** Disposable emulator only: exercises real touch input and stage/decor screens. */
public final class ProgressionUiTest extends Instrumentation {
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
    private void edge(int r,int c) {tap(200+(c-r-.38f)*19,309.5f+(r+c+.38f)*9.5f);}
    private void load(GameState seed) {
        if(activity!=null) {runOnMainSync(()->activity.finish());SystemClock.sleep(250);}
        prefs.edit().putBoolean("welcomed",true).putString("city",seed.encode()).commit();
        activity=startActivitySync(new Intent().setClassName("com.cloudtown.game","com.cloudtown.game.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        SystemClock.sleep(1100);tap(353,210);
    }
    private void screenshot(String name) throws Exception {
        Bitmap shot=getUiAutomation().takeScreenshot();
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getCacheDir(),name))) {shot.compress(Bitmap.CompressFormat.PNG,100,out);}
        shot.recycle();
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            check(getTargetContext().getResources().getDisplayMetrics().widthPixels==1080,"reset emulator to 1080x2160");
            prefs=getTargetContext().getSharedPreferences("cloudtown",0);prefs.edit().clear().commit();
            GameState seed=new GameState();seed.coins=999999;seed.build(0,0,GameState.PARK);seed.build(0,1,GameState.PARK);
            for(int r=0;r<9&&seed.population()<56;r++)for(int c=0;c<9&&seed.population()<56;c++)
                if(seed.tile(r,c)==0&&seed.nearRoad(r,c))seed.build(r,c,GameState.HOUSE);
            seed.coins=5000;seed.food=30;seed.stageRewardMask=1;seed.stageSeenMask=1;load(seed);
            check(city().population()==56,"population fixture");
            tap(200,346.5f);tap(200,664);
            check(city().level(3,3)==1&&city().coins==5000,"level 2 blocked before 60 in UI");
            tap(363,579);tap(141,579);tap(36,653);edge(0,0);
            check(city().decor(0,0,2)==1&&city().coins==4990,"buy edge flower");
            edge(0,0);tap(200,337);edge(0,1);
            check(city().decor(0,0,2)==0&&city().decor(0,1,2)==1&&city().coins==4990,"free decoration move");
            tap(55,579);tap(124,366.5f);
            check(city().population()==60&&city().maxLevel()==2,"real construction unlocks level 2 at 60");
            check(city().nextStageNotice()==1&&city().stageCoinsReceived(1)==200,"stage notification and reward");
            screenshot("stage-reward.png");tap(200,517);
            tap(200,346.5f);tap(200,664);
            check(city().level(3,3)==2&&city().coins==4980,"second level costs 120");
            tap(200,664);check(city().level(3,3)==2&&city().coins==4980,"third level blocked below 240");
            tap(363,579);tap(141,579);tap(198,653);edge(0,2);
            // Kind 4 is the fourth decoration card, centred at x=198.
            check(city().decor(0,2,2)==4&&city().coins==4960,"bench opens with 60 residents");
            tap(239,579);screenshot("progression-roadmap.png");tap(200,568);
            GameState next=city();next.peakPopulation=240;next.stageRewardMask=15;next.stageSeenMask=15;next.eventId=1;load(next);
            tap(200,346.5f);tap(200,664);
            check(city().level(3,3)==3&&city().coins==4780,"third level costs 180 after 240 milestone");
            tap(363,579);tap(292,210);tap(200,353);
            check(city().eventId==0&&city().coins==4880&&city().food==30,"event choice through UI");
            tap(141,579);tap(360,653);tap(252,653);edge(0,3);
            check(city().decor(0,3,2)==11&&city().coins==4835,"new decor page at 240");
            edge(0,1);tap(200,396);
            check(city().decor(0,1,2)==0&&city().coins==4840,"decoration refund");
            GameState restored=city();load(restored);
            check(city().decorCount()==2&&city().maxLevel()==3&&city().level(3,3)==3,"decor and milestones persist");
            GameState finalStage=city();finalStage.peakPopulation=800;finalStage.stageRewardMask=63;finalStage.stageSeenMask=63;finalStage.coins=5000;finalStage.eventId=2;finalStage.nextEventTick=9999;load(finalStage);
            tap(292,210);tap(200,353);
            check(city().coins==4960&&city().festivalUntil==city().ticks+12,"festival choice and bonus");
            tap(55,579);tap(100,655);tap(143,395);
            check(city().count(GameState.GARDEN)==1&&city().coins==3960,"garden unlock and placement");
            tap(290,655);tap(181,414);
            check(city().count(GameState.TOWN_HALL)==1&&city().coins==1960,"town hall unlock and placement");
            tap(141,579);screenshot("decorations-preview.png");
            result.putString("stream","PASS: 60/240 gates, new prices, real population unlock, edge decor, free move, removal/refund, roadmap, event choice, persistence, garden and town hall\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable e) {result.putString("stream","FAIL: "+e.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
