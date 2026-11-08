import com.cloudtown.game.GameState;
import com.cloudtown.game.DecorCatalog;

public final class ProgressionTest {
    private static int checks;
    private static void check(boolean ok,String label) {checks++;if(!ok)throw new AssertionError(label);}
    private static void grow(GameState s,int target) {
        for(int guard=0;s.population()<target&&guard<1200;guard++) {
            s.coins=999999;boolean changed=false;
            int support=s.foodProduction()<s.foodUse()+1?GameState.FARM:s.happiness()<43?GameState.PARK:0;
            if(support==0)for(int r=-16;r<=24&&!changed;r++)for(int c=-16;c<=24&&!changed;c++)
                if(s.tile(r,c)==GameState.HOUSE&&s.level(r,c)<s.maxLevel())changed=s.upgrade(r,c)==null;
            if(changed)continue;
            int kind=support==0?GameState.HOUSE:support;
            for(int r=-16;r<=24&&!changed;r++)for(int c=-16;c<=24&&!changed;c++)
                if(s.tile(r,c)==GameState.EMPTY&&(kind==GameState.PARK||s.nearRoad(r,c)))changed=s.build(r,c,kind)==null;
            if(changed)continue;
            check(!s.availablePlots().isEmpty(),"reachable growth frontier");
            GameState.Plot p=s.availablePlots().get(0);check(s.buyPlot(p.r,p.c)==null,"growth land purchase");
            for(int c=p.c;c<p.c+4;c++)if(s.tile(p.r+1,c)==GameState.EMPTY)s.build(p.r+1,c,GameState.ROAD);
        }
        check(s.population()>=target,"reach "+target+" residents with food and parks");
    }
    private static void buildSomewhere(GameState s,int kind) {
        s.coins=999999;
        for(int r=-16;r<=24;r++)for(int c=-16;c<=24;c++)if(s.tile(r,c)==0&&(kind==GameState.PARK||s.nearRoad(r,c)))
            if(s.build(r,c,kind)==null)return;
        throw new AssertionError("no placement for "+kind);
    }
    public static void main(String[] args) {
        GameState s=new GameState();s.coins=999999;
        int before=s.coins;check(s.maxLevel()==1&&s.upgrade(3,3)!=null&&s.coins==before,"fresh city upgrade locked atomically");
        check(s.placeDecor(0,0,0,1)!=null,"decor locked before 24");
        grow(s,24);check(s.maxLevel()==1&&s.placeDecor(0,0,0,1)==null,"first decor at 24");
        check(s.placeDecor(0,0,0,1)!=null,"occupied decor slot");
        before=s.coins;check(s.moveDecor(0,0,0,0,1,1)==null&&s.coins==before&&s.decor(0,0,0)==0,"free atomic decor move");
        check(s.moveDecor(0,1,1,7,1,1)!=null&&s.decor(0,1,1)==1,"failed move retains source");
        check(s.build(0,1,GameState.ROAD)==null&&s.decor(0,1,1)==1,"decor does not block buildings");
        grow(s,60);check(s.maxLevel()==2,"level 2 at 60");
        check(s.upgradeCost(3,3)==120,"house second level price");
        check(s.upgrade(3,3)==null&&s.level(3,3)==2&&s.foodUse()==s.population()/4,"level 2 capacity and food consumption");
        before=s.coins;check(s.upgrade(3,3)!=null&&s.coins==before,"level 3 still locked");
        check(s.placeDecor(0,0,0,5)==null,"lamp at 60");
        int peak=s.peakPopulation;s.demolish(3,3);check(s.peakPopulation==peak&&s.maxLevel()==2,"unlock survives demolition");
        check(GameState.decode(s.encode()).maxLevel()==2,"unlock persists after population drop and restart");
        grow(s,240);check(s.maxLevel()==3,"level 3 at 240");
        check(s.upgradeCost(5,3)==160,"farm second level price");
        check(s.upgrade(5,3)==null&&s.upgradeCost(5,3)==240,"farm third level price");
        check(s.placeDecor(0,0,2,11)==null,"sculpture at 240");
        check(GameState.decode(s.encode()).encode().equals(s.encode()),"all new save fields roundtrip");
        for(int threshold:DecorCatalog.MILESTONES) {
            GameState t=new GameState();t.coins=999999;t.peakPopulation=threshold;
            int kind=1;while(DecorCatalog.required(kind)<threshold)kind++;
            check(t.placeDecor(0,0,0,kind)==null,"decor tier "+threshold);
            if(threshold>24){t.peakPopulation=threshold-1;check(t.placeDecor(0,1,0,kind)!=null,"exact decor boundary "+threshold);}
        }
        GameState event=new GameState();for(int i=0;i<130;i++)event.advance(1);
        check(event.eventId==0,"no events before 120");event.peakPopulation=120;
        for(int i=0;i<10;i++)event.advance(1);check(event.eventId==1,"event starts after 120");
        event.food=0;check(event.resolveEvent(0)!=null&&event.eventId==1,"failed event choice retains event");
        int ticks=event.ticks;event.paused=true;for(int i=0;i<100;i++)event.advance(1);
        check(event.ticks==ticks&&event.eventId==1,"events wait on pause");
        event=GameState.decode(event.encode());check(event.eventId==1,"event survives restart");
        check(event.resolveEvent(1)==null&&event.food==10&&event.eventId==0,"event alternative");
        event.eventId=2;event.coins=100;int normalIncome=event.income();
        check(event.resolveEvent(0)==null&&event.festivalUntil==event.ticks+12&&event.coins==60,"festival lasts active game time");
        check(event.income()>=normalIncome+4,"festival has an economic benefit");
        // Complete the full chain through 800 residents using real construction and upgrades.
        s=new GameState();s.coins=999999;buildSomewhere(s,1);buildSomewhere(s,1);check(s.claim()==100,"quest houses");
        buildSomewhere(s,2);check(s.claim()==160,"quest food");buildSomewhere(s,3);check(s.claim()==220,"quest shops");
        grow(s,24);check(s.claim()==280,"quest 24 precedes upgrades");grow(s,60);check(s.claim()==340,"quest 60 precedes upgrades");
        s.upgrade(3,3);for(int r=0;r<9;r++)for(int c=0;c<9;c++)if(s.tile(r,c)==1&&s.level(r,c)==1){s.upgrade(r,c);r=9;break;}
        check(s.claim()==400,"quest second-level buildings");buildSomewhere(s,4);buildSomewhere(s,4);check(s.claim()==460,"quest parks");
        grow(s,120);check(s.claim()==520,"quest 120");
        for(int c=0;c<2;c++)for(int slot=0;slot<3;slot++)check(s.placeDecor(0,c,slot,1)==null,"quest decor placement");
        check(s.claim()==580,"quest decor");grow(s,240);check(s.claim()==640,"quest 240");
        int up=0;for(int r=-16;r<=24&&up<2;r++)for(int c=-16;c<=24&&up<2;c++)if(s.tile(r,c)==1&&s.level(r,c)==2){s.upgrade(r,c);up++;}
        check(s.claim()==700,"quest third-level buildings");grow(s,480);check(s.claim()==760,"quest 480");
        buildSomewhere(s,GameState.GARDEN);check(s.claim()==820,"quest garden");
        grow(s,800);check(s.claim()==880,"quest 800");buildSomewhere(s,GameState.TOWN_HALL);check(s.claim()==940&&s.quest==15,"quest town hall and completion");
        before=s.coins;check(s.build(3,2,GameState.TOWN_HALL)!=null&&s.coins==before,"landmark uniqueness");
        check(GameState.decode(s.encode()).quest==15,"finished progression save");
        GameState old=new GameState();old.legacyLevel=3;old.coins=5000;
        old.buyPlot(-4,0);old.upgrade(3,3);old.upgrade(3,3);old.quest=6;
        String modern=old.encode();String v2=modern.substring(0,modern.indexOf("P,")).replaceFirst("^[34];","2;");
        GameState migrated=GameState.decode(v2);
        check(migrated.plotCount()==1&&migrated.coins==old.coins&&migrated.level(3,3)==3,"version 1.2 city and land migrate");
        check(migrated.maxLevel()==3&&migrated.peakPopulation==12,"legacy upgrades retained without fictitious population");
        check(migrated.quest==7,"completed old quest chain continues at the new city goal");
        migrated.demolish(3,3);check(GameState.decode(migrated.encode()).maxLevel()==3,"legacy level survives demolition and another save");
        System.out.println("PASS: "+checks+" progression, decorations and event checks");
    }
}
