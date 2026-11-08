package com.cloudtown.game;

import java.util.ArrayList;
import java.util.List;

/** Offline simulation. World coordinates keep the original 9x9 city at (0,0). */
public final class GameState {
    public static final int SIZE=9, OFFSET=16, WORLD_SIZE=41, PLOT_SIZE=4;
    public static final int EMPTY=0,HOUSE=1,FARM=2,SHOP=3,PARK=4,ROAD=5,WATER=6,FISH=7,GARDEN=8,TOWN_HALL=9;
    public static final String[] NAMES={"Лужайка","Домик","Ферма","Магазин","Парк","Дорога","Река","Домик рыбака","Большой сад","Ратуша"};
    public static final int[] COST={0,90,120,150,70,12,0,120,1000,2000};
    public final int[][] tiles=new int[WORLD_SIZE][WORLD_SIZE];
    public final int[][] levels=new int[WORLD_SIZE][WORLD_SIZE];
    private final int[][][] decorations=new int[WORLD_SIZE][WORLD_SIZE][4];
    public static final int QUEST_COUNT=15;
    public int peakPopulation=4,legacyLevel=1,eventId=0,nextEventTick=12,eventNumber=0,festivalUntil=0;
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
    public static boolean isBuilding(int kind) {return (kind>=HOUSE&&kind<=PARK)||kind>=FISH&&kind<=TOWN_HALL;}
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
    public int happiness() {return Math.min(100,Math.max(20,65+units(PARK)*9+units(GARDEN)*12-units(HOUSE)*3-(food==0?25:0)+(ticks<festivalUntil?10:0)));}
    public int income() {return 8+units(HOUSE)*5+units(SHOP)*9+units(TOWN_HALL)*40+happiness()/10+(ticks<festivalUntil?4:0);}
    private boolean near(int r,int c,int kind) {return tile(r-1,c)==kind||tile(r+1,c)==kind||tile(r,c-1)==kind||tile(r,c+1)==kind;}
    public boolean nearRoad(int r,int c) {return near(r,c,ROAD);}
    public boolean nearRiver(int r,int c) {return near(r,c,WATER);}
    public String build(int r,int c,int kind) {
        if(!valid(r,c)||(!isBuilding(kind)&&kind!=ROAD))return "Выберите купленный участок на острове";
        if(tile(r,c)!=EMPTY)return "Этот участок уже занят";
        if(kind==FISH&&!nearRiver(r,c))return "Домик рыбака строится только на берегу, рядом с рекой";
        if(kind!=ROAD&&kind!=PARK&&kind!=FISH&&!nearRoad(r,c))return "Здание должно стоять рядом с дорогой";
        if(kind==GARDEN||kind==TOWN_HALL) {
            int required=kind==GARDEN?480:800;
            if(peakPopulation<required)return "Откроется при "+required+" жителях";
            if(count(kind)>0)return "Такая особая постройка уже есть в городе";
        }
        if(coins<COST[kind])return "Не хватает монет. Город приносит доход каждые 10 секунд";
        coins-=COST[kind];put(r,c,kind);refreshProgress();return null;
    }
    public void refreshProgress() {peakPopulation=Math.max(peakPopulation,population());}
    public int maxLevel() {refreshProgress();return Math.max(legacyLevel,peakPopulation>=240?3:peakPopulation>=60?2:1);}
    public int buildingMax(int kind) {return kind>=GARDEN?1:3;}
    public String upgradeLock(int r,int c) {
        if(level(r,c)>=buildingMax(tile(r,c)))return "Уже максимальный уровень";
        if(level(r,c)>=maxLevel())return "Уровень "+(level(r,c)+1)+" откроется при "+(level(r,c)==1?60:240)+" жителях";
        return null;
    }
    public int upgradeCost(int r,int c) {
        int kind=tile(r,c);
        if(level(r,c)==1) {
            if(kind==HOUSE)return 120;if(kind==FARM||kind==FISH)return 160;
            if(kind==SHOP)return 200;if(kind==PARK)return 100;
        }
        return COST[kind]*2;
    }
    public String upgrade(int r,int c) {
        if(!isBuilding(tile(r,c)))return "Это здание нельзя улучшить";
        String lock=upgradeLock(r,c);if(lock!=null)return lock;
        int cost=upgradeCost(r,c);
        if(coins<cost)return "Для улучшения нужно "+cost+" монет";
        coins-=cost;levels[r+OFFSET][c+OFFSET]++;refreshProgress();return null;
    }
    public int decor(int r,int c,int slot) {return valid(r,c)&&slot>=0&&slot<4?decorations[r+OFFSET][c+OFFSET][slot]:0;}
    public int decorCount() {
        int n=0;for(int[][] row:decorations)for(int[] cell:row)for(int d:cell)if(d!=0)n++;return n;
    }
    public String placeDecor(int r,int c,int slot,int kind) {
        refreshProgress();
        if(!DecorCatalog.valid(kind))return "Выберите украшение";
        if(peakPopulation<DecorCatalog.required(kind))return "Откроется при "+DecorCatalog.required(kind)+" жителях";
        if(!valid(r,c)||tile(r,c)==WATER||slot<0||slot>3)return "Украшения размещаются по краям купленной суши";
        if(decor(r,c,slot)!=0)return "Здесь уже есть украшение. Его можно переставить";
        if(coins<DecorCatalog.COST[kind])return "Не хватает монет для украшения";
        coins-=DecorCatalog.COST[kind];decorations[r+OFFSET][c+OFFSET][slot]=kind;return null;
    }
    public String moveDecor(int r,int c,int slot,int toR,int toC,int toSlot) {
        int kind=decor(r,c,slot);if(kind==0)return "Украшение уже убрано";
        if(!valid(toR,toC)||tile(toR,toC)==WATER||toSlot<0||toSlot>3)return "Выберите край клетки суши";
        if(r==toR&&c==toC&&slot==toSlot)return null;
        if(decor(toR,toC,toSlot)!=0)return "Это место занято другим украшением";
        decorations[toR+OFFSET][toC+OFFSET][toSlot]=kind;decorations[r+OFFSET][c+OFFSET][slot]=0;return null;
    }
    public void removeDecor(int r,int c,int slot) {
        int kind=decor(r,c,slot);if(kind==0)return;
        coins=Math.min(999999,coins+DecorCatalog.COST[kind]/2);decorations[r+OFFSET][c+OFFSET][slot]=0;
    }
    public String eventTitle() {return eventId==1?"Путешественники у причала":eventId==2?"Праздник соседей":"Помощь садовников";}
    public String eventDescription() {
        return eventId==1?"Гости предлагают обмен. Выберите, что сейчас нужнее городу.":eventId==2?"Праздник даст +10 к уюту и +4 монеты за цикл на 2 минуты. Или выберите общий стол для пополнения еды.":"Садовники готовы помочь с запасами или провести маленькую ярмарку.";
    }
    public String eventChoice(int choice) {
        if(eventId==1)return choice==0?"20 еды → 100 монет":"Подарок гостей: +10 еды";
        if(eventId==2)return choice==0?"40 монет → праздник на 2 минуты":"Общий стол: +12 еды";
        return choice==0?"60 монет → +16 еды":"Ярмарка: +40 монет";
    }
    public String resolveEvent(int choice) {
        if(eventId==0||choice<0||choice>1)return "Событие уже завершено";
        if(eventId==1) {
            if(choice==0) {if(food<20)return "Нужно 20 еды";food-=20;coins=Math.min(999999,coins+100);}
            else food=Math.min(999,food+10);
        } else if(eventId==2) {
            if(choice==0) {if(coins<40)return "Нужно 40 монет";coins-=40;festivalUntil=ticks+12;}
            else food=Math.min(999,food+12);
        } else {
            if(choice==0) {if(coins<60)return "Нужно 60 монет";coins-=60;food=Math.min(999,food+16);}
            else coins=Math.min(999999,coins+40);
        }
        eventId=0;eventNumber++;nextEventTick=ticks+8;return null;
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
        coins=Math.min(999999,coins+income());refreshProgress();
        if(peakPopulation>=120&&eventId==0&&ticks>=nextEventTick)eventId=1+eventNumber%3;return true;
    }
    public String questTitle() {
        String[] titles={"Первые соседи","Запасы для всех","Торговая улица","Своя деревня","Пора расти выше","Первые улучшения","Больше зелени","Небольшой город","Город с характером","Новые горизонты","Третий этаж","Большие мечты","Городской сад","Город для всех","Сердце города"};
        return quest<QUEST_COUNT?titles[quest]:"Ваш город мечты";
    }
    private int upgraded(int minLevel) {int n=0;for(int r=0;r<WORLD_SIZE;r++)for(int c=0;c<WORLD_SIZE;c++)if(owned[r][c]&&levels[r][c]>=minLevel)n++;return n;}
    public int questProgress() {
        refreshProgress();int value;
        switch(quest) {
            case 0:value=count(HOUSE);break;case 1:value=count(FARM)+count(FISH);break;case 2:value=count(SHOP);break;
            case 3:case 4:case 7:case 9:case 11:case 13:value=peakPopulation;break;
            case 5:value=upgraded(2);break;case 6:value=count(PARK);break;case 8:value=decorCount();break;
            case 10:value=upgraded(3);break;case 12:value=count(GARDEN);break;case 14:value=count(TOWN_HALL);break;
            default:value=1;
        }
        return Math.min(questTarget(),value);
    }
    public int questTarget() {int[] targets={3,2,2,24,60,2,3,120,6,240,2,480,1,800,1};return quest<QUEST_COUNT?targets[quest]:1;}
    public int questReward() {return 100+quest*60;}
    public String questHint() {
        String[] hints={"Постройте 3 домика","2 фермы или домика рыбака","Откройте 2 магазина","Поселите 24 жителя","60 жителей → уровень 2","Улучшите 2 здания до уровня 2","Посадите 3 парка","Поселите 120 жителей","Разместите 6 украшений","240 жителей → уровень 3","Улучшите 2 здания до уровня 3","Поселите 480 жителей","Постройте большой сад","Поселите 800 жителей","Постройте ратушу"};
        return quest<QUEST_COUNT?hints[quest]:"Украшайте и развивайте город в своём ритме";
    }
    public boolean canClaim() {return quest<QUEST_COUNT&&questProgress()>=questTarget();}
    public int claim() {if(!canClaim())return 0;int reward=questReward();coins=Math.min(999999,coins+reward);quest++;return reward;}
    public String encode() {
        StringBuilder b=new StringBuilder("3;").append(coins).append(';').append(food).append(';').append(ticks).append(';')
            .append(quest).append(';').append(elapsed).append(';').append(sound?1:0).append(';').append(plots.size()).append(';');
        for(Plot p:plots)b.append(p.r).append(',').append(p.c).append(';');
        for(int r=-OFFSET;r<WORLD_SIZE-OFFSET;r++)for(int c=-OFFSET;c<WORLD_SIZE-OFFSET;c++)if(valid(r,c))
            b.append(r).append(',').append(c).append(',').append(tile(r,c)).append(',').append(level(r,c)).append(';');
        refreshProgress();
        b.append("P,").append(peakPopulation).append(',').append(legacyLevel).append(',').append(eventId).append(',')
            .append(nextEventTick).append(',').append(eventNumber).append(',').append(festivalUntil).append(';');
        for(int r=-OFFSET;r<WORLD_SIZE-OFFSET;r++)for(int c=-OFFSET;c<WORLD_SIZE-OFFSET;c++)if(valid(r,c))
            for(int slot=0;slot<4;slot++)if(decor(r,c,slot)!=0)b.append("D,").append(r).append(',').append(c).append(',').append(slot).append(',').append(decor(r,c,slot)).append(';');
        return b.toString();
    }
    public static GameState decode(String data) {
        if(data==null)return new GameState();
        try {
            String[] p=data.split(";");GameState s=new GameState();
            s.coins=Integer.parseInt(p[1]);s.food=Integer.parseInt(p[2]);s.ticks=Integer.parseInt(p[3]);s.quest=Integer.parseInt(p[4]);
            s.elapsed=Float.parseFloat(p[5]);s.sound=p[6].equals("1");
            if(s.coins<0||s.coins>999999||s.food<0||s.food>999||s.ticks<0||s.quest<0||s.quest>(p[0].equals("3")?QUEST_COUNT:6)||Float.isNaN(s.elapsed)||Float.isInfinite(s.elapsed)||s.elapsed<0||s.elapsed>=10)return new GameState();
            if(p[0].equals("1")) {
                if(p.length!=7+81)return new GameState();
                for(int r=0;r<9;r++)for(int c=0;c<9;c++) {
                    String[] cell=p[7+r*9+c].split(",");int t=Integer.parseInt(cell[0]),l=Integer.parseInt(cell[1]);
                    if(t<0||t>WATER||!validLevel(t,l))return new GameState();
                    s.put(r,c,t);s.levels[r+OFFSET][c+OFFSET]=l;
                }
            } else if(p[0].equals("2")||p[0].equals("3")) {
                int n=Integer.parseInt(p[7]),end=8+n+81+16*n;boolean modern=p[0].equals("3");
                if(n<0||n>100||(modern?p.length<end+1:p.length!=end))return new GameState();
                for(int i=0;i<n;i++) {
                    String[] xy=p[8+i].split(",");int r=Integer.parseInt(xy[0]),c=Integer.parseInt(xy[1]);
                    if(!s.offered(r,c))return new GameState();s.addPlot(r,c);
                }
                boolean[][] seen=new boolean[WORLD_SIZE][WORLD_SIZE];
                for(int i=8+n;i<end;i++) {
                    String[] cell=p[i].split(",");int r=Integer.parseInt(cell[0]),c=Integer.parseInt(cell[1]);
                    int t=Integer.parseInt(cell[2]),l=Integer.parseInt(cell[3]);
                    if(!s.valid(r,c)||seen[r+OFFSET][c+OFFSET]||t<0||t>(modern?TOWN_HALL:FISH)||!validLevel(t,l))return new GameState();
                    seen[r+OFFSET][c+OFFSET]=true;s.put(r,c,t);s.levels[r+OFFSET][c+OFFSET]=l;
                }
                if(modern) {
                    String[] meta=p[end].split(",");if(meta.length!=7||!meta[0].equals("P"))return new GameState();
                    s.peakPopulation=Integer.parseInt(meta[1]);s.legacyLevel=Integer.parseInt(meta[2]);s.eventId=Integer.parseInt(meta[3]);
                    s.nextEventTick=Integer.parseInt(meta[4]);s.eventNumber=Integer.parseInt(meta[5]);s.festivalUntil=Integer.parseInt(meta[6]);
                    if(s.peakPopulation<0||s.peakPopulation>WORLD_SIZE*WORLD_SIZE*12||s.legacyLevel<1||s.legacyLevel>3||s.eventId<0||s.eventId>3||s.nextEventTick<0||s.eventNumber<0||s.festivalUntil<0)return new GameState();
                    for(int i=end+1;i<p.length;i++) {
                        String[] d=p[i].split(",");if(d.length!=5||!d[0].equals("D"))return new GameState();
                        int r=Integer.parseInt(d[1]),c=Integer.parseInt(d[2]),slot=Integer.parseInt(d[3]),kind=Integer.parseInt(d[4]);
                        if(!s.valid(r,c)||s.tile(r,c)==WATER||slot<0||slot>3||!DecorCatalog.valid(kind)||s.decor(r,c,slot)!=0)return new GameState();
                        s.decorations[r+OFFSET][c+OFFSET][slot]=kind;
                    }
                }
            } else return new GameState();
            if(!p[0].equals("3")) {
                for(int[] row:s.levels)for(int l:row)s.legacyLevel=Math.max(s.legacyLevel,l);
                int[] map={0,1,2,3,6,7,7};s.quest=map[s.quest];
            }
            s.refreshProgress();return s;
        } catch(RuntimeException e) {return new GameState();}
    }
    private static boolean validLevel(int kind,int level) {return level>=0&&level<=3&&(!isBuilding(kind)||level>0);}
}
