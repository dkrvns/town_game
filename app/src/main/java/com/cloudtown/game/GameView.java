package com.cloudtown.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.Random;

/** All art is drawn locally: no sprites to download, no WebView, no Internet permission. */
public final class GameView extends View {
    private static final int INK = 0xFF294D48, MUTED = 0xFF71877E, GREEN = 0xFF277B64;
    private static final int CREAM = 0xFFF8FAF1, GOLD = 0xFFEFB947;
    private final Paint p = new Paint(3);
    private final Path path = new Path();
    private final Typeface normal = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final SharedPreferences prefs;
    private GameState s;
    private ToneGenerator tone;
    private Canvas canvas;
    private float scale = 1, height = 800, insetTop, insetBottom;
    private float tileW = 37, tileH = 18.5f, originY = 300, phase;
    private long lastFrame;
    private boolean suspended;
    private int selected = GameState.HOUSE, inspectR = -1, inspectC = -1;
    private boolean erase;
    private int modal; // 1 guide, 2 settings, 3 reset confirmation
    private String toast = "";
    private float toastTime;
    private final ArrayList<Hit> hits = new ArrayList<>();
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final Random random = new Random(41);
    private float touchX, touchY;
    private final MapCamera camera = new MapCamera();
    private final GestureDetector taps;
    private final int[] drawOrder = new int[81];
    private boolean mapTouch, multiTouch, pinchReady;
    private float lastSpan, lastAngle, lastMidX, lastMidY;
    private Runnable pendingTap;
    private float pendingX,pendingY;
    private static final class Hit {
        RectF area; Runnable action;
        Hit(float x, float y, float w, float h, Runnable action) { area = new RectF(x,y,x+w,y+h); this.action=action; }
    }
    private static final class Particle {
        float x,y,vx,vy,life; int color;
    }
    public GameView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("cloudtown", Context.MODE_PRIVATE);
        s = GameState.decode(prefs.getString("city", null));
        camera.restore(prefs.getFloat("camera_zoom",1),prefs.getFloat("camera_angle",0),
            prefs.getFloat("camera_x",0),prefs.getFloat("camera_y",0));
        taps = new GestureDetector(context,new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                queueMapTap(e.getX()/scale,(e.getY()-insetTop)/scale);
                return true;
            }
            @Override public boolean onScroll(MotionEvent a,MotionEvent b,float dx,float dy) {
                cancelMapTap();
                camera.pan(-dx/scale,-dy/scale);invalidate();return true;
            }
        });
        taps.setOnDoubleTapListener(null);
        taps.setIsLongpressEnabled(false);
        modal = prefs.getBoolean("welcomed", false) ? 0 : 1;
        setFocusable(true);
        setContentDescription("Облачный городок. Стройте здания касанием участков на острове.");
        try { tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 22); } catch (RuntimeException ignored) { }
    }
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            insetTop = bars.top; insetBottom = bars.bottom;
        }
        return insets;
    }
    public void save() { prefs.edit().putString("city",s.encode()).apply(); }
    private void saveCamera() {
        prefs.edit().putFloat("camera_zoom",camera.zoom).putFloat("camera_angle",camera.angle)
            .putFloat("camera_x",camera.panX).putFloat("camera_y",camera.panY).apply();
    }
    public void suspend() { suspended = true; lastFrame=0;cancelMapTap();save();saveCamera(); }
    public void resume() { suspended = false; lastFrame=0; invalidate(); }
    public void release() { if (tone != null) { tone.release(); tone=null; } }
    public boolean back() {
        if (modal != 0) { modal=0; welcomeDone(); invalidate(); return true; }
        if (inspectR >= 0) { inspectR=-1; invalidate(); return true; }
        return false;
    }
    private void welcomeDone() { prefs.edit().putBoolean("welcomed",true).apply(); }
    private void sound() {
        if (s.sound && tone != null) tone.startTone(ToneGenerator.TONE_PROP_ACK, 65);
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }
    private void message(String text) { toast=text; toastTime=3.8f; announceForAccessibility(text); }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setText("Город: " + s.population() + " жителей, " + s.coins + " монет, " + s.food + " еды. " + s.questHint());
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        long now=SystemClock.elapsedRealtime();
        float dt=lastFrame==0?0:Math.min(0.1f,(now-lastFrame)/1000f);
        lastFrame=now;
        if (!suspended && modal==0) {
            if (!s.paused) phase+=dt;
            if (s.advance(dt)) { save(); }
        }
        toastTime-=dt;
        canvas=c; scale=getWidth()/400f;
        height=(getHeight()-insetTop-insetBottom)/scale;
        c.drawColor(CREAM);
        c.save(); c.translate(0,insetTop); c.scale(scale,scale);
        hits.clear();
        background(); header(); quest(); world(); toolbar();
        drawParticles(dt);
        if (toastTime>0 && modal==0) {
            float y=height-254;
            round(16,y,368,54,16,0xED294D48);
            wrapped(toast,30,y+21,340,12,Color.WHITE,17);
        }
        if (modal!=0) dialog();
        c.restore();
        if (!suspended) postInvalidateDelayed(33);
    }
    private void background() {
        p.setShader(new LinearGradient(0,160,0,height-180,new int[]{CREAM,0xFFD2E9DF,0xFFBFDDD6},null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,400,height,p); p.setShader(null);
        for(int i=0;i<6;i++) {
            float x=((i*113+phase*(i%2==0?1.4f:-0.9f))%560+560)%560-80;
            cloud(x,215+(i%3)*70,0.55f+i%2*0.2f);
        }
        // Tiny drifting seeds give life to the empty sky.
        for(int i=0;i<9;i++) circle((i*71+phase*5)%400,220+(i*43)%(Math.max(80,(int)height-440)),1.4f,0x66FFFFFF);
    }
    private void header() {
        circle(31,31,17,0xFFDCECDD);
        tree(31,40,0.65f,1);
        text("Облачный городок",57,32,21,INK,true);
        text("МАЛЕНЬКОЕ МЕСТО ДЛЯ БОЛЬШИХ ИДЕЙ",58,49,7.7f,MUTED,true);
        round(349,14,36,36,12,0xFFECF0E7); gear(367,32);
        hit(341,8,50,48,()->modal=2);
        stat(16,66,135,"МОНЕТЫ",Integer.toString(s.coins),GOLD,0);
        stat(159,66,108,"ЖИТЕЛИ",Integer.toString(s.population()),0xFF82B4D4,1);
        stat(275,66,109,"ЗАПАС ЕДЫ",Integer.toString(s.food),0xFF92B66F,2);
    }
    private void stat(float x,float y,float w,String label,String value,int color,int icon) {
        round(x,y,w,48,15,0xDFFFFFFF);
        circle(x+23,y+24,13,color);
        if(icon==0) text("$",x+19,y+29,15,0xFF88651F,true);
        if(icon==1) { circle(x+23,y+20,4,Color.WHITE); round(x+17,y+25,12,8,4,Color.WHITE); }
        if(icon==2) { line(x+23,y+32,x+23,y+16,Color.WHITE,1.5f); for(int j=0;j<3;j++) { oval(x+18,y+17+j*4,5,3,Color.WHITE); oval(x+23,y+15+j*4,5,3,Color.WHITE); } }
        text(label,x+43,y+16,8,MUTED,true);
        text(value,x+43,y+36,20,INK,true);
    }
    private void quest() {
        round(16,124,368,64,17,0xFFFCFDF7);
        circle(42,150,14,0xFFFFE9B8); star(42,150,8,0xFFDC9C36);
        text(s.quest>5?"Город мечты готов!":s.questTitle(),64,145,13,INK,true);
        text(s.quest>5?"Стройте дальше в своём ритме":s.questHint(),64,161,10.5f,MUTED,false);
        round(64,172,208,4,2,0xFFE7EDE1);
        round(64,172,208*(s.quest>5?1:(float)s.questProgress()/s.questTarget()),4,2,0xFF83AD7B);
        if(s.canClaim()) {
            round(287,136,84,39,12,GREEN); text("Забрать",302,152,11,Color.WHITE,true);
            text("+"+s.questReward()+" монет",300,166,9,0xFFD5EBDD,false);
            hit(282,129,96,52,()->{ int reward=s.claim(); message("Задание выполнено! +"+reward+" монет"); burst(330,151); sound(); save(); });
        } else {
            text(s.quest>5?"ГОТОВО":s.questProgress()+" / "+s.questTarget(),309,148,13,GREEN,true);
            text(s.quest>5?"♥":"+"+s.questReward()+" монет",302,166,9,MUTED,false);
        }
    }
    private void world() {
        float top=225, bottom=height-205;
        float available=Math.max(130,bottom-top);
        tileW=Math.min(38,Math.max(12,(height-495)*2/9)); tileH=tileW/2;
        originY=top+(available-9*tileH-40)/2+40;
        originY=Math.min(originY,height-240-8.5f*tileH);
        text("ДЕНЬ "+(s.ticks/24+1),19,211,9,MUTED,true);
        text("+"+s.income()+" монет / 10 сек",19,226,10,GREEN,true);
        text("Уют "+s.happiness()+"%",176,211,9,MUTED,true);
        int netFood=s.foodProduction()-s.foodUse();
        text("Еда "+(netFood>=0?"+":"")+netFood+" / цикл",176,226,9,netFood>=0?GREEN:0xFFB87958,false);
        round(267,196,50,30,11,0xBFFFFFFF);
        text("?",287,216,16,GREEN,true); hit(263,191,57,41,()->modal=1);
        round(325,196,59,30,11,0xBFFFFFFF);
        text(s.paused?"▶":"Ⅱ",336,216,15,GREEN,true);
        text(s.paused?"пуск":"пауза",352,214,8,GREEN,false);
        hit(321,191,67,41,()->{s.paused=!s.paused; save();});
        canvas.save();
        canvas.clipRect(0,233,400,height-222);
        canvas.translate(200+camera.panX,originY+4*tileH+camera.panY);
        canvas.scale(camera.zoom,camera.zoom);
        canvas.translate(-200,-originY-4*tileH);
        float half=GameState.SIZE*tileW/2, mid=originY+4*tileH;
        oval(200-half-10,mid+tileH*3.3f,half*2+20,tileH*4,0x207A9D8E);
        float[] rs={-.5f,-.5f,8.5f,8.5f},cs={-.5f,8.5f,8.5f,-.5f};
        for(int i=0;i<4;i++) {
            int j=(i+1)%4;
            float x=tx(rs[i],cs[i]),y=ty(rs[i],cs[i]),xx=tx(rs[j],cs[j]),yy=ty(rs[j],cs[j]);
            poly((x+xx)/2<200?0xFF89AF8C:0xFF689680,x,y,xx,yy,xx,yy+17,x,y+17);
        }
        sortTiles();
        for(int cell:drawOrder) ground(cell/9,cell%9);
        for(int cell:drawOrder) decoration(cell/9,cell%9);
        citizens();
        canvas.restore();
        // Gentle evening tint, one full day every four real minutes.
        float night=(float)Math.max(0,Math.sin((s.ticks*10+s.elapsed)/240*Math.PI*2-Math.PI/2));
        if(night>0) { p.setColor(Color.argb((int)(night*32),36,65,107)); canvas.drawRect(0,232,400,height-200,p); }
        float labelY=height-205;
        if (s.paused) textCentered("Город отдыхает • нажмите «пуск»",200,labelY,11,MUTED,false);
        else {
            textCentered("2 пальца: масштаб и поворот · 2 тапа: сброс",200,labelY,9,MUTED,false);
            round(135,labelY+8,130,3,2,0x60FFFFFF);
            round(135,labelY+8,130*s.elapsed/10,3,2,0xFF6AA68B);
        }
    }
    private float tx(float r,float c) { return 200+camera.x(r,c,tileW); }
    private float ty(float r,float c) { return originY+4*tileH+camera.y(r,c,tileH); }
    private void sortTiles() {
        for(int i=0;i<81;i++) {
            int j=i;float depth=ty(i/9,i%9);
            while(j>0 && ty(drawOrder[j-1]/9,drawOrder[j-1]%9)>depth) { drawOrder[j]=drawOrder[j-1];j--; }
            drawOrder[j]=i;
        }
    }
    private void tile(float r,float c,float inset,int color) {
        float d=.5f-inset;
        poly(color,tx(r-d,c-d),ty(r-d,c-d),tx(r-d,c+d),ty(r-d,c+d),
            tx(r+d,c+d),ty(r+d,c+d),tx(r+d,c-d),ty(r+d,c-d));
    }
    private void diamond(float x,float y,float w,float h,int color) { poly(color,x,y-h/2,x+w/2,y,x,y+h/2,x-w/2,y); }
    private void ground(int r,int c) {
        float x=tx(r,c), y=ty(r,c);
        int kind=s.tiles[r][c];
        int grass=(r+c)%2==0?0xFFA7CC83:0xFFA0C67C;
        tile(r,c,-.008f,kind==GameState.WATER?0xFF79C8C8:kind==GameState.ROAD?0xFFE1D4AB:grass);
        if(kind==GameState.WATER) {
            float offset=(float)Math.sin(phase*1.8f+c)*2;
            line(x-7+offset,y,x+3+offset,y-1,0x99D8F8E8,1);
            line(x+2-offset,y+3,x+8-offset,y+2,0x77E1FFEE,1);
        }
        if(kind==GameState.ROAD) {
            if(r==7) {
                tile(r,c,0,0xFFC8AC86);
                line(tx(r-.4f,c-.4f),ty(r-.4f,c-.4f),tx(r+.4f,c-.4f),ty(r+.4f,c-.4f),0xFF947957,2);
                line(tx(r-.4f,c+.4f),ty(r-.4f,c+.4f),tx(r+.4f,c+.4f),ty(r+.4f,c+.4f),0xFF947957,2);
            } else circle(x,y,1,0xFFD0BE94);
        }
        if(kind==GameState.EMPTY && inspectR<0 && !erase && (selected==GameState.ROAD||selected==GameState.PARK||s.nearRoad(r,c))) {
            tile(r,c,.05f,0x2374A44E);
            if((r*7+c)%3==0) { line(x-2,y,x+2,y,0x50658C50,1); line(x,y-2,x,y+2,0x50658C50,1); }
        }
        if(inspectR==r && inspectC==c) tile(r,c,0,0xFFEDD273);
    }
    private void decoration(int r,int c) {
        float x=tx(r,c),y=ty(r,c),unit=tileW/38;
        int k=s.tiles[r][c];
        if(k>=1 && k<=4) {
            canvas.save(); canvas.translate(x,y); canvas.scale(unit,unit);
            building(k,0,0,s.levels[r][c]); canvas.restore();
        } else if(k==GameState.EMPTY) {
            int hash=r*19+c*7;
            if(hash%5==0) tree(x,y,unit*.65f,hash%3);
            else if(hash%3==0) {
                circle(x-3,y,1.2f,0xFFECD98F); circle(x+1,y+1,1,0xFFE9F1C5);
                line(x+5,y-1,x+6,y-3,0xFF77AA65,1);
            }
        }
    }
    private void building(int kind,float x,float y,int level) {
        oval(x-14,y-3,28,10,0x23705437);
        if(kind==GameState.HOUSE) {
            float h=17+(level-1)*10;
            box(x,y,11,6,h,0xFFFFE8B2,0xFFE7C68D,0xFFFFEDC5);
            poly(0xFFF39578,x-13,y-h-5,x,y-h-15,x+13,y-h-5,x,y-h+2);
            poly(0xFFD87264,x,y-h+2,x+13,y-h-5,x+13,y-h-7,x,y-h);
            box(x+6,y-h-10,2,1,7,0xFFDEAB82,0xFFC48B6C,0xFFEEC49A);
            poly(0xFF699CA0,x-8,y-7,x-3,y-4,x-3,y-11,x-8,y-14);
            poly(0xFF679895,x+3,y-2,x+7,y-4,x+7,y-14,x+3,y-12);
            for(int l=1;l<level;l++) {
                poly(0xFF7CAAA3,x-8,y-10-l*9,x-4,y-8-l*9,x-4,y-13-l*9,x-8,y-15-l*9);
                poly(0xFF7CAAA3,x+3,y-9-l*9,x+7,y-11-l*9,x+7,y-16-l*9,x+3,y-14-l*9);
            }
            circle(x-11,y+2,2.7f,0xFF6FA170);
        } else if(kind==GameState.FARM) {
            diamond(x,y+1,30,14,0xFFB68C58);
            for(int i=0;i<4;i++) {
                line(x-10+i*5,y+1-i*2,x-2+i*5,y+5-i*2,0xFFEDD27B,2);
                for(int j=0;j<2;j++) {
                    float xx=x-9+i*5+j*4, yy=y-i*2+j*2;
                    line(xx,yy,xx,yy-4-level,0xFF6B963F,1.4f);
                    circle(xx-1,yy-4-level,1.8f,0xFFECCB60);
                }
            }
            box(x-3,y-5,6,3,12+level*2,0xFFDF8E74,0xFFC67360,0xFFECA58B);
            poly(0xFF638D83,x-11,y-20-level*2,x-3,y-27-level*2,x+5,y-20-level*2,x-3,y-16-level*2);
            poly(0xFFFFEAC1,x-6,y-9,x-2,y-7,x-2,y-15,x-6,y-17);
        } else if(kind==GameState.SHOP) {
            float h=18+(level-1)*8;
            box(x,y,13,6,h,0xFFFFD4A4,0xFFE8AE8C,0xFFFFE1BA);
            box(x,y-h,14,7,4,0xFF72ADA5,0xFF4F8B84,0xFF9FC9AE);
            poly(0xFF7EAFB2,x-10,y-6,x-2,y-2,x-2,y-12,x-10,y-16);
            poly(0xFF779FA3,x+3,y-2,x+10,y-5,x+10,y-14,x+3,y-11);
            for(int i=0;i<5;i++) poly(i%2==0?0xFFF9F4D5:0xFFED8F78,x-14+i*3,y-h+5+i*1.5f,x-11+i*3,y-h+6.5f+i*1.5f,x-11+i*3,y-h+11+i*1.5f,x-14+i*3,y-h+9.5f+i*1.5f);
            round(x-4,y-h-11,12,7,2,0xFFFAEBD0); text("☀",x-2,y-h-5,6,0xFFB2844B,true);
        } else if(kind==GameState.PARK) {
            diamond(x,y,33,16,0xFF83B36F);
            tree(x-7,y-1,0.65f+level*.05f,0); tree(x+8,y-3,0.55f+level*.04f,1);
            diamond(x+1,y+3,13,6,0xFFE4CE9D);
            oval(x-3,y,8,4,0xFF8BCACE);
            line(x-7,y+6,x-1,y+9,0xFFB18E5B,2.3f);
            if(level>1) { circle(x+10,y+3,2,0xFFEFA794); circle(x-10,y+3,2,0xFFFFD788); }
        }
        if(level>1) { circle(x+13,y-3,4.5f,0xFFF7F4D8); text(Integer.toString(level),x+11,y-1,6,GREEN,true); }
    }
    private void box(float x,float y,float w,float d,float h,int left,int right,int top) {
        poly(left,x-w,y-d,x,y,x,y-h,x-w,y-d-h);
        poly(right,x,y,x+w,y-d,x+w,y-d-h,x,y-h);
        poly(top,x,y-h,x-w,y-d-h,x,y-2*d-h,x+w,y-d-h);
    }
    private void tree(float x,float y,float size,int variant) {
        line(x,y,x,y-13*size,0xFF9B835B,2*size);
        if(variant==2) {
            poly(0xFF5D9971,x-7*size,y-7*size,x,y-27*size,x+7*size,y-7*size);
            poly(0xFF78AD7D,x-5*size,y-14*size,x,y-28*size,x+5*size,y-14*size);
        } else {
            circle(x,y-17*size,9*size,variant==0?0xFF72A96E:0xFF83B57A);
            circle(x-4*size,y-14*size,6*size,0xFF69A16C);
            circle(x+2*size,y-21*size,5*size,0xFF98C485);
        }
    }
    private void citizens() {
        for(int i=0;i<Math.min(12,s.population()/2+2);i++) {
            float v=(phase*(.22f+i*.017f)+i*1.37f)%8;
            float rr=i%2==0?4:v, cc=i%2==0?v:4;
            float x=tx(rr,cc), y=ty(rr,cc);
            float bounce=(float)Math.sin(phase*8+i)*.5f;
            oval(x-2,y,4,2,0x205A7657);
            line(x-1,y,x-1.5f,y+2+(float)Math.sin(phase*7+i),0xFF5B685F,1);
            line(x+1,y,x+1.5f,y+2-(float)Math.sin(phase*7+i),0xFF5B685F,1);
            round(x-1.7f,y-4+bounce,3.4f,5,1.3f,new int[]{0xFFDD8F73,0xFF79A2BF,0xFFF2CE70}[i%3]);
            circle(x,y-5+bounce,1.8f,0xFFFBE0B2);
        }
        // Little boat moves along the river.
        float v=(phase*.14f+1)%8, x=tx(7,v), y=ty(7,v);
        poly(0xFFFAF2D9,x-5,y-1,x+5,y-1,x+2,y+2,x-2,y+2);
        line(x,y-1,x,y-10,0xFF9C886D,1);
        poly(0xFFE9A085,x+1,y-10,x+1,y-2,x+6,y-3);
        // Birds circle slowly above the island.
        for(int i=0;i<3;i++) {
            float bx=200+(float)Math.sin(phase*.17+i)*130, by=originY-35+(float)Math.cos(phase*.2+i)*10;
            float flap=(float)Math.sin(phase*4+i)*2;
            line(bx-4,by-flap,bx,by,0x88839B90,1); line(bx,by,bx+4,by-flap,0x88839B90,1);
        }
    }
    private void toolbar() {
        float y=height-185;
        round(0,y,400,210,24,CREAM);
        round(178,y+8,44,3,2,0xFFDBE3D6);
        if(inspectR>=0) {
            inspector(y); return;
        }
        text(erase?"Навести порядок":"Построим что-нибудь?",18,y+34,17,INK,true);
        text(erase?"Коснитесь здания · возврат 50% стоимости":"Выберите здание и коснитесь лужайки",18,y+51,10.5f,MUTED,false);
        String[] labels={"Домик","Ферма","Магазин","Парк","Дорога","Снос"};
        for(int i=0;i<6;i++) {
            final int index=i;
            float x=14+i*63, top=y+64;
            boolean active=i==5?erase:(!erase && selected==i+1);
            round(x,top,57,88,13,active?0xFFDFEBD5:0xFFEDF1E5);
            if(active) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f); p.setColor(0xFF77A781); canvas.drawRoundRect(new RectF(x,top,x+57,top+88),13,13,p); p.setStyle(Paint.Style.FILL); }
            canvas.save(); canvas.translate(x+28,top+38); canvas.scale(.85f,.85f);
            if(i<4) building(i+1,0,0,1);
            else if(i==4) { diamond(0,0,30,16,0xFFD8C69C); line(-8,-1,4,5,0xFFF9F2D9,2); }
            else { line(-9,3,6,-12,0xFFA18767,4); line(1,-15,11,-5,0xFF798F85,6); }
            canvas.restore();
            textCentered(labels[i],x+28,top+59,9.5f,INK,true);
            textCentered(i==5?"½ цены":Integer.toString(GameState.COST[i+1]),x+28,top+76,10,i==5?MUTED:GREEN,true);
            hit(x-1,top-2,61,92,()->{inspectR=-1; erase=index==5; if(index<5) selected=index+1;});
        }
        textCentered(erase?"Дороги и река защищены от сноса":toolHint(),200,y+172,9.5f,MUTED,false);
    }
    private String toolHint() {
        switch(selected) {
            case 1:return "+4 жителя · +5 монет · −1 еда за цикл";
            case 2:return "+4 еды за цикл · кормит 4 домика";
            case 3:return "+9 монет за цикл · больше уютных витрин";
            case 4:return "+9 к настроению · можно сажать где угодно";
            default:return "Соединяйте участки: зданиям нужна дорога";
        }
    }
    private void inspector(float y) {
        int kind=s.tiles[inspectR][inspectC], level=s.levels[inspectR][inspectC];
        canvas.save(); canvas.translate(48,y+70); building(kind,0,0,level); canvas.restore();
        text(GameState.NAMES[kind],80,y+39,20,INK,true);
        text("УРОВЕНЬ "+level+" ИЗ 3",81,y+57,9,GREEN,true);
        String detail=kind==1?(level*4)+" жителя · +"+(level*5)+" монет за цикл":kind==2?"Урожай: +"+(level*4)+" еды за цикл":kind==3?"Выручка: +"+(level*9)+" монет за цикл":"Настроение: +"+(level*9);
        text(detail,81,y+76,10.5f,MUTED,false);
        round(343,y+20,40,40,12,0xFFE7EDDF); text("×",356,y+47,23,MUTED,false);
        hit(339,y+14,49,49,()->inspectR=-1);
        if(level<3) {
            int cost=s.upgradeCost(inspectR,inspectC);
            round(18,y+95,364,48,14,s.coins>=cost?GREEN:0xFF9EAEA0);
            textCentered("Улучшить · "+cost+" монет",200,y+125,14,Color.WHITE,true);
            hit(18,y+95,364,48,()->{String error=s.upgrade(inspectR,inspectC); if(error!=null)message(error);else{sound();burstTile(inspectR,inspectC);save();}});
        } else {
            round(18,y+95,364,48,14,0xFFE3EBD8); textCentered("Красота! Максимальный уровень",200,y+125,13,GREEN,true);
        }
        textCentered("Улучшение увеличивает пользу здания",200,y+166,10,MUTED,false);
    }
    private void dialog() {
        // Remove all underlying controls from input routing.
        hits.clear();
        p.setColor(0x99304E48); canvas.drawRect(0,0,400,height,p);
        float h=modal==1?435:modal==2?358:235, top=(height-h)/2;
        round(20,top,360,h,25,CREAM);
        if(modal==1) {
            circle(200,top+57,31,0xFFDEECD5);
            building(1,200,top+75,1); tree(220,top+75,.6f,0);
            textCentered("Ваш маленький город",200,top+115,22,INK,true);
            textCentered("Пусть путешествие будет уютным",200,top+137,12,MUTED,false);
            guideRow(top+169,"1","Стройте касанием","Выберите здание внизу и свободную клетку.");
            guideRow(top+224,"2","Заботьтесь о жителях","Фермы дают еду, парки — настроение.");
            guideRow(top+279,"3","Рассмотрите город","Два пальца: масштаб и поворот. Один: сдвиг. Двойной тап: сброс вида.");
            round(42,top+344,316,47,14,GREEN);
            textCentered("В городок!",200,top+374,15,Color.WHITE,true);
            hit(42,top+344,316,47,()->{modal=0;welcomeDone();sound();});
            textCentered("Офлайн · автосохранение · никаких таймеров ожидания",200,top+414,9,MUTED,false);
        } else if(modal==2) {
            text("На минутку отдохнуть",42,top+42,21,INK,true);
            text("Всё сохранено на вашем телефоне",42,top+64,11,MUTED,false);
            round(42,top+86,316,48,13,0xFFE7EEDC);
            text("Звуки строительства",57,top+116,13,INK,true);
            text(s.sound?"ВКЛ":"ВЫКЛ",306,top+115,10,GREEN,true);
            hit(42,top+86,316,48,()->{s.sound=!s.sound;save();if(s.sound)sound();});
            round(42,top+145,316,44,13,0xFFE7EEDC); text("Как играть",57,top+173,13,INK,true);
            hit(42,top+145,316,44,()->modal=1);
            round(42,top+200,316,44,13,0xFFF4E7DC); text("Начать новый город",57,top+228,13,0xFFA86E58,true);
            hit(42,top+200,316,44,()->modal=3);
            round(42,top+264,316,46,14,GREEN); textCentered("Продолжить",200,top+293,14,Color.WHITE,true);
            hit(42,top+264,316,46,()->modal=0);
            textCentered("Пока приложение закрыто, город отдыхает",200,top+335,10,MUTED,false);
        } else {
            textCentered("Начать с чистого острова?",200,top+44,19,INK,true);
            textCentered("Текущий город будет удалён.",200,top+77,12,MUTED,false);
            textCentered("Это действие нельзя отменить.",200,top+98,12,MUTED,false);
            round(42,top+123,316,44,13,0xFFB87860); textCentered("Да, новый город",200,top+151,14,Color.WHITE,true);
            hit(42,top+123,316,44,()->{boolean sound=s.sound;s=new GameState();s.sound=sound;inspectR=-1;erase=false;selected=1;phase=0;particles.clear();camera.reset();saveCamera();save();modal=0;message("Новый остров ждёт ваших идей");});
            round(42,top+178,316,37,12,0xFFE5EBDD); textCentered("Оставить мой город",200,top+202,13,GREEN,true);
            hit(42,top+178,316,37,()->modal=2);
        }
    }
    private void guideRow(float y,String number,String title,String subtitle) {
        circle(57,y+8,14,0xFFE1ECD6); textCentered(number,57,y+13,12,GREEN,true);
        text(title,81,y+6,13,INK,true);
        wrapped(subtitle,81,y+24,266,10,MUTED,14);
    }
    private void mapTap(float x,float y) {
        float screenX=x,screenY=y,centerY=originY+4*tileH;
        int c=Math.round(camera.column(x-200,y-centerY,tileW,tileH));
        int r=Math.round(camera.row(x-200,y-centerY,tileW,tileH));
        x=200+(x-200-camera.panX)/camera.zoom;
        y=centerY+(y-centerY-camera.panY)/camera.zoom;
        // Roofs extend beyond their plots. Pick the frontmost visible building first.
        boolean found=false;
        sortTiles();
        for(int i=80;i>=0 && !found;i--) {
            int rr=drawOrder[i]/9,cc=drawOrder[i]%9;
            int k=s.tiles[rr][cc]; if(k<1||k>4)continue;
            float unit=tileW/38, tall=(k==1?32+(s.levels[rr][cc]-1)*10:k==3?39+(s.levels[rr][cc]-1)*8:34)*unit;
            if(x>=tx(rr,cc)-14*unit && x<=tx(rr,cc)+14*unit && y>=ty(rr,cc)-tall && y<=ty(rr,cc)+3*unit) {
                r=rr;c=cc;found=true;break;
            }
        }
        if(!s.valid(r,c)) { if(inspectR>=0)inspectR=-1; return; }
        int kind=s.tiles[r][c];
        if(erase) {
            String error=s.demolish(r,c);
            if(error!=null)message(error);else{sound();save();burst(screenX,screenY);message("Участок свободен. Часть монет возвращена");}
        } else if(kind>=GameState.HOUSE && kind<=GameState.PARK) { inspectR=r;inspectC=c; }
        else if(kind==GameState.EMPTY) {
            String error=s.build(r,c,selected);
            if(error!=null)message(error);else{inspectR=-1;sound();save();burstTile(r,c);}
        } else if(kind==GameState.WATER) message("По реке плывёт кораблик. Здесь строить нельзя");
        else {inspectR=-1;message("Дорога соединяет город. Стройте рядом с ней");}
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        float x=event.getX()/scale,y=(event.getY()-insetTop)/scale;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN) {
            touchX=x;touchY=y;multiTouch=false;pinchReady=false;
            mapTouch=modal==0 && y>233 && y<height-222;
            if(mapTouch)taps.onTouchEvent(event);
            return true;
        }
        if(action==MotionEvent.ACTION_POINTER_DOWN) {
            multiTouch=true;cancelMapTap();
            MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);
            taps.onTouchEvent(cancel);cancel.recycle();
            if(mapTouch && event.getPointerCount()==2)pinch(event,false);
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE && mapTouch) {
            if(event.getPointerCount()>=2)pinch(event,pinchReady);
            else if(!multiTouch)taps.onTouchEvent(event);
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_POINTER_UP) { pinchReady=false;return true; }
        if(action==MotionEvent.ACTION_CANCEL) { taps.onTouchEvent(event);cancelMapTap();saveCamera();mapTouch=false;return true; }
        if(action==MotionEvent.ACTION_UP) {
            if(mapTouch) {
                if(!multiTouch)taps.onTouchEvent(event);
                saveCamera();mapTouch=false;invalidate();return true;
            }
            if(multiTouch || Math.abs(x-touchX)+Math.abs(y-touchY)>20)return true;
            performClick();
            for(int i=hits.size()-1;i>=0;i--) if(hits.get(i).area.contains(x,y)) {hits.get(i).action.run();invalidate();return true;}
            invalidate();return true;
        }
        return true;
    }
    private void cancelMapTap() { if(pendingTap!=null) {removeCallbacks(pendingTap);pendingTap=null;} }
    private void queueMapTap(float x,float y) {
        if(pendingTap!=null) {
            if(Math.hypot(x-pendingX,y-pendingY)<=18) {
                cancelMapTap();camera.reset();saveCamera();invalidate();return;
            }
            Runnable first=pendingTap;removeCallbacks(first);first.run();
        }
        pendingX=x;pendingY=y;
        pendingTap=()->{pendingTap=null;if(modal==0 && !suspended){performClick();mapTap(x,y);invalidate();}};
        postDelayed(pendingTap,300);
    }
    private void pinch(MotionEvent e,boolean update) {
        float dx=(e.getX(1)-e.getX(0))/scale,dy=(e.getY(1)-e.getY(0))/scale;
        float span=(float)Math.hypot(dx,dy),angle=(float)Math.atan2(dy,dx);
        float midX=(e.getX(0)+e.getX(1))/(2*scale)-200;
        float midY=((e.getY(0)+e.getY(1))/2-insetTop)/scale-originY-4*tileH;
        if(update && lastSpan>8 && span>8) {
            float rotation=(float)Math.atan2(Math.sin(angle-lastAngle),Math.cos(angle-lastAngle));
            camera.gesture(lastMidX,lastMidY,midX,midY,span/lastSpan,rotation,tileW,tileH);
        }
        lastSpan=span;lastAngle=angle;lastMidX=midX;lastMidY=midY;pinchReady=true;
    }
    @Override public boolean performClick() {super.performClick();return true;}
    private void burstTile(int r,int c) {
        burst(200+camera.screenX(r,c,tileW),originY+4*tileH+camera.screenY(r,c,tileH)-15*camera.zoom);
    }
    private void burst(float x,float y) {
        for(int i=0;i<18;i++) {
            Particle q=new Particle();q.x=x;q.y=y;q.vx=(random.nextFloat()-.5f)*70;q.vy=-25-random.nextFloat()*65;q.life=1+random.nextFloat()*.5f;
            q.color=new int[]{0xFFF3C966,0xFFECA68A,0xFFFFFFFF,0xFF8CBD8A}[i%4];particles.add(q);
        }
    }
    private void drawParticles(float dt) {
        for(int i=particles.size()-1;i>=0;i--) {
            Particle q=particles.get(i);q.life-=dt;
            if(q.life<=0){particles.remove(i);continue;}
            q.x+=q.vx*dt;q.y+=q.vy*dt;q.vy+=65*dt;
            circle(q.x,q.y,2*Math.min(1,q.life),q.color);
        }
    }
    private void cloud(float x,float y,float size) {
        oval(x-28*size,y,65*size,15*size,0x6FFFFFFF);
        circle(x-8*size,y+2*size,13*size,0x6FFFFFFF); circle(x+10*size,y,18*size,0x6FFFFFFF);
    }
    private void gear(float x,float y) {
        for(int i=0;i<8;i++){double a=i*Math.PI/4;line(x+(float)Math.cos(a)*6,y+(float)Math.sin(a)*6,x+(float)Math.cos(a)*9,y+(float)Math.sin(a)*9,MUTED,3);}
        circle(x,y,7,MUTED);circle(x,y,3,0xFFECF0E7);
    }
    private void star(float x,float y,float rad,int color) {
        path.reset();
        for(int i=0;i<10;i++){double a=i*Math.PI/5-Math.PI/2;float r=i%2==0?rad:rad*.48f;float px=x+(float)Math.cos(a)*r,py=y+(float)Math.sin(a)*r;if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}
        path.close();p.setColor(color);canvas.drawPath(path,p);
    }
    private void hit(float x,float y,float w,float h,Runnable action) { hits.add(new Hit(x,y,w,h,action)); }
    private void round(float x,float y,float w,float h,float radius,int color) { if(w<=0)return;p.setColor(color);canvas.drawRoundRect(x,y,x+w,y+h,radius,radius,p); }
    private void circle(float x,float y,float r,int color) { p.setColor(color);canvas.drawCircle(x,y,r,p); }
    private void oval(float x,float y,float w,float h,int color) { p.setColor(color);canvas.drawOval(x,y,x+w,y+h,p); }
    private void line(float x,float y,float xx,float yy,int color,float width) { p.setColor(color);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);canvas.drawLine(x,y,xx,yy,p); }
    private void poly(int color,float... points) {
        path.reset();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);path.close();p.setColor(color);canvas.drawPath(path,p);
    }
    private void text(String text,float x,float y,float size,int color,boolean strong) {
        p.setTypeface(strong?bold:normal);p.setTextSize(size);p.setColor(color);canvas.drawText(text,x,y,p);
    }
    private void textCentered(String text,float x,float y,float size,int color,boolean strong) {
        p.setTypeface(strong?bold:normal);p.setTextSize(size);text(text,x-p.measureText(text)/2,y,size,color,strong);
    }
    private void wrapped(String value,float x,float y,float width,float size,int color,float leading) {
        p.setTypeface(normal);p.setTextSize(size);String current="";
        for(String word:value.split(" ")) {
            if(p.measureText(current+word)>width && !current.isEmpty()) { text(current,x,y,size,color,false);y+=leading;current=""; }
            current+=word+" ";
        }
        text(current,x,y,size,color,false);
    }
}
