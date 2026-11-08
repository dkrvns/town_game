package com.cloudtown.game;

import java.util.ArrayList;
import java.util.List;

/** Offline simulation. World coordinates keep the original 9x9 city at (0,0). */
public final class GameState {
    public static final int SIZE=9, OFFSET=16, WORLD_SIZE=41, PLOT_SIZE=4;
    public static final int EMPTY=0,HOUSE=1,FARM=2,SHOP=3,PARK=4,ROAD=5,WATER=6,FISH=7;
    public static final String[] NAMES={"Лужайка","Домик","Ферма","Магазин","Парк","Дорога","Река","Домик рыбака"};
    public static final int[] COST={0,90,120,150,70,12,0,120};
    public final int[][] tiles=new int[WORLD_SIZE][WORLD_SIZE];
    public final int[][] levels=new int[WORLD_SIZE][WORLD_SIZE];
    private final boolean[][] owned=new boolean[WORLD_SIZE][WORLD_SIZE];
    private final ArrayList<Plot> plots=new ArrayList<>();
    public int coins=400,food=30,ticks=0,quest=0;
    public float elapsed=0;
    public boolean paused=false,sound=true;
    public static final class Plot {
        public final int r,c;
        public Plot(int r,int c) {this.r=r;this.c=c;}
        public boolean contains(int row,int col) {return row>=r&&row<r+4&&col>=c&&col<c+4;}
    }
    public GameState() {
        own(0,0,9);
        for(int i=0;i<9;i++) {put(4,i,ROAD);if(i!=7)put(i,4,ROAD);}
        put(7,4,ROAD);put(3,3,HOUSE);put(5,3,FARM);put(3,5,SHOP);put(5,5,PARK);
    }
    public static boolean isBuilding(int kind) {return (kind>=HOUSE&&kind<=PARK)||kind==FISH;}
    private static boolean inside(int r,int c) {return r>=-OFFSET&&c>=-OFFSET&&r<WORLD_SIZE-OFFSET&&c<WORLD_SIZE-OFFSET;}
    public boolean valid(int r,int c) {return inside(r,c)&&owned[r+OFFSET][c+OFFSET];}
    public int tile(int r,int c) {return valid(r,c)?tiles[r+OFFSET][c+OFFSET]:-1;}
    public int level(int r,int c) {return valid(r,c)?levels[r+OFFSET][c+OFFSET]:0;}
    private void put(int r,int c,int kind) {tiles[r+OFFSET][c+OFFSET]=kind;levels[r+OFFSET][c+OFFSET]=isBuilding(kind)?1:0;}
    private void own(int r,int c,int size) {
        for(int rr=r;rr<r+size;rr++)for(int cc=c;cc<c+size;cc++) {
            owned[rr+OFFSET][cc+OFFSET]=true;put(rr,cc,rr==7?WATER:EMPTY);
        }
    }
    public int ownedCount() {return 81+plots.size()*16;}
    public int plotCount() {return plots.size();}
    public int landCost() {return 160+plots.size()*60;}
    private void candidate(List<Plot> result,int r,int c) {
        if(!inside(r,c)||!inside(r+3,c+3))return;
        for(Plot p:result)if(p.r==r&&p.c==c)return;
        for(int rr=r;rr<r+4;rr++)for(int cc=c;cc<c+4;cc++)if(valid(rr,cc))return;
        result.add(new Plot(r,c));
    }
    public List<Plot> availablePlots() {
        ArrayList<Plot> result=new ArrayList<>();
        for(int start:new int[]{0,4}) {
            candidate(result,-4,start);candidate(result,9,start);
            candidate(result,start,-4);candidate(result,start,9);
        }
        for(Plot p:plots) {
            candidate(result,p.r-4,p.c);candidate(result,p.r+4,p.c);
            candidate(result,p.r,p.c-4);candidate(result,p.r,p.c+4);
        }
        return result;
    }
    private boolean offered(int r,int c) {
        for(Plot p:availablePlots())if(p.r==r&&p.c==c)return true;
        return false;
    }
    private void addPlot(int r,int c) {own(r,c,4);plots.add(new Plot(r,c));}
    public String buyPlot(int r,int c) {
        if(!offered(r,c))return "Выберите свободный участок 4×4 у края города";
        if(coins<landCost())return "Для участка нужно "+landCost()+" монет";
        coins-=landCost();addPlot(r,c);return null;
    }
    public int count(int kind) {
        int n=0;for(int r=0;r<WORLD_SIZE;r++)for(int c=0;c<WORLD_SIZE;c++)if(owned[r][c]&&tiles[r][c]==kind)n++;
        return n;
    }
    public int units(int kind) {
        int n=0;for(int r=0;r<WORLD_SIZE;r++)for(int c=0;c<WORLD_SIZE;c++)if(owned[r][c]&&tiles[r][c]==kind)n+=levels[r][c];
        return n;
    }
    public int population() {return units(HOUSE)*4;}
    public int foodUse() {return units(HOUSE);}
    public int foodProduction() {return (units(FARM)+units(FISH))*4;}
    public int happiness() {return Math.min(100,Math.max(20,65+units(PARK)*9-units(HOUSE)*3-(food==0?25:0)));}
    public int income() {return 8+units(HOUSE)*5+units(SHOP)*9+happiness()/10;}
    private boolean near(int r,int c,int kind) {return tile(r-1,c)==kind||tile(r+1,c)==kind||tile(r,c-1)==kind||tile(r,c+1)==kind;}
    public boolean nearRoad(int r,int c) {return near(r,c,ROAD);}
    public boolean nearRiver(int r,int c) {return near(r,c,WATER);}
    public String build(int r,int c,int kind) {
        if(!valid(r,c)||(!isBuilding(kind)&&kind!=ROAD))return "Выберите купленный участок на острове";
        if(tile(r,c)!=EMPTY)return "Этот участок уже занят";
        if(kind==FISH&&!nearRiver(r,c))return "Домик рыбака строится только на берегу, рядом с рекой";
        if(kind!=ROAD&&kind!=PARK&&kind!=FISH&&!nearRoad(r,c))return "Здание должно стоять рядом с дорогой";
        if(coins<COST[kind])return "Не хватает монет. Город приносит доход каждые 10 секунд";
        coins-=COST[kind];put(r,c,kind);return null;
    }
    public int upgradeCost(int r,int c) {return COST[tile(r,c)]*(level(r,c)+1);}
    public String upgrade(int r,int c) {
        if(!isBuilding(tile(r,c)))return "Это здание нельзя улучшить";
        if(level(r,c)>=3)return "Уже максимальный уровень";
        int cost=upgradeCost(r,c);
        if(coins<cost)return "Для улучшения нужно "+cost+" монет";
        coins-=cost;levels[r+OFFSET][c+OFFSET]++;return null;
    }
    public String demolish(int r,int c) {
        if(tile(r,c)==ROAD)return "Главные дороги оставим для жителей";
        if(!isBuilding(tile(r,c)))return "Здесь нечего сносить";
        coins=Math.min(999999,coins+COST[tile(r,c)]*level(r,c)/2);put(r,c,EMPTY);return null;
    }
    public boolean advance(float seconds) {
        if(paused||seconds<=0)return false;
        elapsed+=Math.min(seconds,1f);if(elapsed<10)return false;
        elapsed-=10;ticks++;food=Math.max(0,Math.min(999,food+foodProduction()-foodUse()));
        coins=Math.min(999999,coins+income());return true;
    }
    public String questTitle() {
        switch(quest) {
            case 0:return "Первые соседи";case 1:return "Своя маленькая ферма";case 2:return "Живая торговая улица";
            case 3:return "Город становится выше";case 4:return "Больше зелени";default:return "Уютный городок";
        }
    }
    public int questProgress() {
        switch(quest) {
            case 0:return Math.min(3,count(HOUSE));case 1:return Math.min(2,count(FARM));case 2:return Math.min(2,count(SHOP));
            case 3:
                int n=0;for(int[] row:levels)for(int l:row)if(l>=2)n++;return Math.min(2,n);
            case 4:return Math.min(3,count(PARK));default:return Math.min(60,population());
        }
    }
    public int questTarget() {return quest==0||quest==4?3:(quest>=5?60:2);}
    public int questReward() {return 100+quest*60;}
    public String questHint() {
        switch(quest) {
            case 0:return "Постройте 3 домика";case 1:return "Постройте 2 фермы";case 2:return "Откройте 2 магазина";
            case 3:return "Улучшите 2 здания до уровня 2";case 4:return "Посадите 3 парка";default:return "Поселите 60 жителей";
        }
    }
    public boolean canClaim() {return quest<=5&&questProgress()>=questTarget();}
    public int claim() {if(!canClaim())return 0;int reward=questReward();coins=Math.min(999999,coins+reward);quest++;return reward;}
    public String encode() {
        StringBuilder b=new StringBuilder("2;").append(coins).append(';').append(food).append(';').append(ticks).append(';')
            .append(quest).append(';').append(elapsed).append(';').append(sound?1:0).append(';').append(plots.size()).append(';');
        for(Plot p:plots)b.append(p.r).append(',').append(p.c).append(';');
        for(int r=-OFFSET;r<WORLD_SIZE-OFFSET;r++)for(int c=-OFFSET;c<WORLD_SIZE-OFFSET;c++)if(valid(r,c))
            b.append(r).append(',').append(c).append(',').append(tile(r,c)).append(',').append(level(r,c)).append(';');
        return b.toString();
    }
    public static GameState decode(String data) {
        if(data==null)return new GameState();
        try {
            String[] p=data.split(";");GameState s=new GameState();
            s.coins=Integer.parseInt(p[1]);s.food=Integer.parseInt(p[2]);s.ticks=Integer.parseInt(p[3]);s.quest=Integer.parseInt(p[4]);
            s.elapsed=Float.parseFloat(p[5]);s.sound=p[6].equals("1");
            if(s.coins<0||s.coins>999999||s.food<0||s.food>999||s.ticks<0||s.quest<0||s.quest>6||Float.isNaN(s.elapsed)||Float.isInfinite(s.elapsed)||s.elapsed<0||s.elapsed>=10)return new GameState();
            if(p[0].equals("1")) {
                if(p.length!=7+81)return new GameState();
                for(int r=0;r<9;r++)for(int c=0;c<9;c++) {
                    String[] cell=p[7+r*9+c].split(",");int t=Integer.parseInt(cell[0]),l=Integer.parseInt(cell[1]);
                    if(t<0||t>WATER||!validLevel(t,l))return new GameState();
                    s.put(r,c,t);s.levels[r+OFFSET][c+OFFSET]=l;
                }
            } else if(p[0].equals("2")) {
                int n=Integer.parseInt(p[7]);if(n<0||n>100||p.length!=8+n+81+16*n)return new GameState();
                for(int i=0;i<n;i++) {
                    String[] xy=p[8+i].split(",");int r=Integer.parseInt(xy[0]),c=Integer.parseInt(xy[1]);
                    if(!s.offered(r,c))return new GameState();s.addPlot(r,c);
                }
                boolean[][] seen=new boolean[WORLD_SIZE][WORLD_SIZE];
                for(int i=8+n;i<p.length;i++) {
                    String[] cell=p[i].split(",");int r=Integer.parseInt(cell[0]),c=Integer.parseInt(cell[1]);
                    int t=Integer.parseInt(cell[2]),l=Integer.parseInt(cell[3]);
                    if(!s.valid(r,c)||seen[r+OFFSET][c+OFFSET]||t<0||t>FISH||!validLevel(t,l))return new GameState();
                    seen[r+OFFSET][c+OFFSET]=true;s.put(r,c,t);s.levels[r+OFFSET][c+OFFSET]=l;
                }
            } else return new GameState();
            return s;
        } catch(RuntimeException e) {return new GameState();}
    }
    private static boolean validLevel(int kind,int level) {return level>=0&&level<=3&&(!isBuilding(kind)||level>0);}
}
